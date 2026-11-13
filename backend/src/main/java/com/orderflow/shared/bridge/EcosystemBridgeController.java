package com.orderflow.shared.bridge;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hub de Sincronização em Tempo Real (REST + STOMP WebSocket) entre o Frontend Master (:5173)
 * e o Frontend KDS (:5174) através do Backend Spring Boot (:8080).
 * Garante propagação imediata de novos pedidos, transições da cozinha (EM_PREPARO, PRONTO,
 * REFAZER, ENTREGUE), replay monotônico (desdeSeq) e pausas de insumos (Ruptura 86).
 */
@RestController
@RequestMapping("/api/v1/bridge")
public class EcosystemBridgeController {

    private static final String LOJA_MATRIZ_ID = "019325f0-8a00-7000-8000-000000000001";

    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicLong seqGenerator = new AtomicLong(20L);

    // Pedidos completos no formato do Frontend Master (indexados por pedidoId)
    private final Map<String, Map<String, Object>> masterOrders = new LinkedHashMap<>();
    // Pedidos projetados para o KDS (sem dados financeiros, indexados por pedidoId)
    private final Map<String, Map<String, Object>> kdsOrders = new LinkedHashMap<>();
    // Outbox monotônica de eventos do KDS
    private final List<Map<String, Object>> kdsOutbox = new CopyOnWriteArrayList<>();
    // Estado compartilhado de disponibilidade de insumos (Ruptura 86)
    private final Map<String, Map<String, Object>> insumosOverrides = new ConcurrentHashMap<>();

    public EcosystemBridgeController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Recebe um pedido recém-lançado ou atualizado pelo Frontend Master (:5173),
     * converte para a projeção operacional do KDS (sem valores financeiros),
     * grava na outbox monotônica e publica via STOMP WebSocket para o KDS (:5174).
     */
    @PostMapping("/orders")
    public synchronized ResponseEntity<Map<String, Object>> upsertOrderFromMaster(
            @RequestBody Map<String, Object> payload
    ) {
        @SuppressWarnings("unchecked")
        Map<String, Object> pedidoMaster = (Map<String, Object>) payload.getOrDefault("pedidoMaster", payload);
        String pedidoId = String.valueOf(pedidoMaster.get("id"));
        String status = String.valueOf(pedidoMaster.getOrDefault("status", "CONFIRMADO"));
        int versao = ((Number) pedidoMaster.getOrDefault("versao", 1)).intValue();
        int numeroDiario = ((Number) pedidoMaster.getOrDefault("numeroDiario", 105)).intValue();

        masterOrders.put(pedidoId, new LinkedHashMap<>(pedidoMaster));

        Map<String, Object> pedidoKds = convertMasterToKdsDto(pedidoMaster);
        kdsOrders.put(pedidoId, pedidoKds);

        long seq = seqGenerator.incrementAndGet();
        String tipoEvento = String.valueOf(payload.getOrDefault("tipoEvento",
                "CONFIRMADO".equals(status) ? "PEDIDO_CONFIRMADO" : "PEDIDO_" + status));

        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("seq", seq);
        evento.put("tipo", tipoEvento);
        evento.put("pedidoId", pedidoId);
        evento.put("numeroDiario", numeroDiario);
        evento.put("versao", versao);
        evento.put("status", status);
        evento.put("ocorridoEm", Instant.now().toString());
        evento.put("pedido", pedidoKds);

        kdsOutbox.add(evento);

        // Broadcast STOMP para todos os clientes conectados em /ws
        messagingTemplate.convertAndSend("/topic/lojas/" + LOJA_MATRIZ_ID + "/kds", evento);
        messagingTemplate.convertAndSend("/topic/lojas/" + LOJA_MATRIZ_ID + "/cozinha/pedidos", evento);

        return ResponseEntity.ok(evento);
    }

    /**
     * Lista os pedidos sincronizados para o Frontend Master (:5173) refletir instantaneamente
     * as mudanças de status feitas na cozinha (:5174).
     */
    @GetMapping("/orders")
    public synchronized ResponseEntity<List<Map<String, Object>>> getMasterOrders() {
        List<Map<String, Object>> list = new ArrayList<>(masterOrders.values());
        Collections.reverse(list);
        return ResponseEntity.ok(list);
    }

    /**
     * Snapshot ativo para o Frontend KDS (:5174) com suporte a filtro por estação.
     */
    @GetMapping("/kds/snapshot")
    public synchronized ResponseEntity<Map<String, Object>> getKdsSnapshot(
            @RequestParam(value = "estacao", required = false, defaultValue = "TODAS") String estacao
    ) {
        List<Map<String, Object>> ativos = new ArrayList<>();
        for (Map<String, Object> p : kdsOrders.values()) {
            String st = String.valueOf(p.get("status"));
            if ("CONFIRMADO".equals(st) || "EM_PREPARO".equals(st) || "PRONTO".equals(st)) {
                Map<String, Object> filtrado = filterKdsOrderByStation(p, estacao);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> itens = (List<Map<String, Object>>) filtrado.get("itens");
                if (itens != null && !itens.isEmpty()) {
                    ativos.add(filtrado);
                }
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("seqAtual", seqGenerator.get());
        resp.put("geradoEm", Instant.now().toString());
        resp.put("pedidos", ativos);
        return ResponseEntity.ok(resp);
    }

    /**
     * Replay monotônico de eventos da cozinha (`GET /api/v1/bridge/kds/events?desdeSeq=...`).
     */
    @GetMapping("/kds/events")
    public ResponseEntity<List<Map<String, Object>>> getKdsEventsSince(
            @RequestParam("desdeSeq") long desdeSeq,
            @RequestParam(value = "estacao", required = false, defaultValue = "TODAS") String estacao
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> ev : kdsOutbox) {
            long seq = ((Number) ev.get("seq")).longValue();
            if (seq > desdeSeq) {
                Map<String, Object> copy = new LinkedHashMap<>(ev);
                @SuppressWarnings("unchecked")
                Map<String, Object> ped = (Map<String, Object>) copy.get("pedido");
                if (ped != null) {
                    copy.put("pedido", filterKdsOrderByStation(ped, estacao));
                }
                result.add(copy);
            }
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Transição de estado acionada pela cozinha no Frontend KDS (:5174):
     * iniciar-preparo, marcar-pronto, refazer ou entregar.
     * Atualiza tanto a visão do KDS quanto o pedido correspondente no Frontend Master (:5173).
     */
    @PostMapping("/kds/orders/{pedidoId}/transition")
    public synchronized ResponseEntity<Map<String, Object>> transitionOrderFromKds(
            @PathVariable("pedidoId") String pedidoId,
            @RequestBody Map<String, Object> body
    ) {
        String acao = String.valueOf(body.getOrDefault("acao", "iniciar-preparo"));
        String motivo = body.get("motivo") != null ? String.valueOf(body.get("motivo")) : null;
        String agora = Instant.now().toString();

        // Se o pedido era do seed local do KDS e ainda não estava no bridge, podemos registrá-lo a partir do snapshot enviado
        if (!kdsOrders.containsKey(pedidoId) && body.get("pedidoSnapshot") instanceof Map<?, ?> snap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> kdsSnap = new LinkedHashMap<>((Map<String, Object>) snap);
            kdsOrders.put(pedidoId, kdsSnap);
        }

        Map<String, Object> kdsOrder = kdsOrders.get(pedidoId);
        Map<String, Object> masterOrder = masterOrders.get(pedidoId);

        String novoStatus = switch (acao) {
            case "iniciar-preparo" -> "EM_PREPARO";
            case "marcar-pronto" -> "PRONTO";
            case "refazer" -> "EM_PREPARO";
            case "entregar" -> "ENTREGUE";
            default -> "EM_PREPARO";
        };

        String tipoEvento = switch (acao) {
            case "iniciar-preparo" -> "PREPARO_INICIADO";
            case "marcar-pronto" -> "PEDIDO_PRONTO";
            case "refazer" -> "PEDIDO_REFEITO";
            case "entregar" -> "PEDIDO_ENTREGUE";
            default -> "PREPARO_INICIADO";
        };

        int novaVersao = 2;

        if (kdsOrder != null) {
            int vAtual = ((Number) kdsOrder.getOrDefault("versao", 1)).intValue();
            novaVersao = vAtual + 1;
            kdsOrder.put("status", novoStatus);
            kdsOrder.put("versao", novaVersao);
            if ("iniciar-preparo".equals(acao)) {
                kdsOrder.put("preparoIniciadoEm", agora);
            } else if ("marcar-pronto".equals(acao)) {
                kdsOrder.put("prontoEm", agora);
            } else if ("refazer".equals(acao)) {
                kdsOrder.put("prontoEm", null);
            }
        }

        if (masterOrder != null) {
            int vMaster = ((Number) masterOrder.getOrDefault("versao", 1)).intValue();
            novaVersao = Math.max(novaVersao, vMaster + 1);
            masterOrder.put("status", novoStatus);
            masterOrder.put("versao", novaVersao);
            masterOrder.put("updatedAt", agora);
            if ("iniciar-preparo".equals(acao)) {
                masterOrder.put("preparoIniciadoEm", agora);
            } else if ("marcar-pronto".equals(acao)) {
                masterOrder.put("prontoEm", agora);
            } else if ("entregar".equals(acao)) {
                masterOrder.put("entregueEm", agora);
            }
            if (kdsOrder != null) {
                kdsOrder.put("versao", novaVersao);
            }
        }

        long seq = seqGenerator.incrementAndGet();
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("seq", seq);
        evento.put("tipo", tipoEvento);
        evento.put("pedidoId", pedidoId);
        evento.put("versao", novaVersao);
        evento.put("status", novoStatus);
        evento.put("ocorridoEm", agora);
        evento.put("motivo", motivo);
        evento.put("pedido", kdsOrder);

        kdsOutbox.add(evento);

        messagingTemplate.convertAndSend("/topic/lojas/" + LOJA_MATRIZ_ID + "/kds", evento);
        messagingTemplate.convertAndSend("/topic/lojas/" + LOJA_MATRIZ_ID + "/cozinha/pedidos", evento);

        if ("PRONTO".equals(novoStatus) && kdsOrder != null) {
            Map<String, Object> prontoEv = new LinkedHashMap<>();
            prontoEv.put("pedidoId", pedidoId);
            prontoEv.put("numeroDiario", kdsOrder.get("numeroDiario"));
            prontoEv.put("identificadorCliente", kdsOrder.get("identificadorCliente"));
            prontoEv.put("mesa", kdsOrder.get("mesa"));
            messagingTemplate.convertAndSend("/topic/lojas/" + LOJA_MATRIZ_ID + "/pedidos/prontos", prontoEv);
        }

        return ResponseEntity.ok(kdsOrder != null ? kdsOrder : evento);
    }

    /**
     * Sincroniza pausa/reativação de insumos (Ruptura 86) entre KDS (:5174) e Master (:5173).
     */
    @PatchMapping("/insumos/{insumoId}/disponibilidade")
    public ResponseEntity<Map<String, Object>> syncInsumoDisponibilidade(
            @PathVariable("insumoId") String insumoId,
            @RequestBody Map<String, Object> body
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", insumoId);
        state.put("disponivel", Boolean.TRUE.equals(body.get("disponivel")));
        state.put("motivoIndisponibilidade", body.get("motivo"));
        state.put("updatedAt", Instant.now().toString());
        insumosOverrides.put(insumoId, state);

        Map<String, Object> stompPayload = new LinkedHashMap<>();
        stompPayload.put("versao", System.currentTimeMillis());
        stompPayload.put("tipo", "DISPONIBILIDADE_ALTERADA");
        stompPayload.put("ocorridoEm", Instant.now().toString());
        stompPayload.put("disponibilidade", List.of(Map.of(
                "entidade", "INSUMO",
                "id", insumoId,
                "disponivel", state.get("disponivel"),
                "motivo", state.get("motivoIndisponibilidade") != null ? state.get("motivoIndisponibilidade") : ""
        )));
        messagingTemplate.convertAndSend("/topic/lojas/" + LOJA_MATRIZ_ID + "/disponibilidade", stompPayload);

        return ResponseEntity.ok(state);
    }

    @GetMapping("/insumos/disponibilidade")
    public ResponseEntity<Map<String, Map<String, Object>>> getInsumosDisponibilidade() {
        return ResponseEntity.ok(insumosOverrides);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertMasterToKdsDto(Map<String, Object> pedidoMaster) {
        Map<String, Object> kds = new LinkedHashMap<>();
        String pedidoId = String.valueOf(pedidoMaster.get("id"));
        kds.put("pedidoId", pedidoId);
        kds.put("numeroDiario", pedidoMaster.getOrDefault("numeroDiario", 105));
        kds.put("identificadorCliente", pedidoMaster.get("identificadorCliente"));
        kds.put("mesa", pedidoMaster.get("mesa"));
        kds.put("tipoAtendimento", pedidoMaster.getOrDefault("tipoAtendimento", "BALCAO"));
        kds.put("status", pedidoMaster.getOrDefault("status", "CONFIRMADO"));
        kds.put("versao", pedidoMaster.getOrDefault("versao", 1));
        kds.put("observacao", pedidoMaster.get("observacao"));
        kds.put("confirmadoEm", pedidoMaster.getOrDefault("confirmadoEm", Instant.now().toString()));
        kds.put("preparoIniciadoEm", pedidoMaster.get("preparoIniciadoEm"));
        kds.put("prontoEm", pedidoMaster.get("prontoEm"));

        List<Map<String, Object>> itensKds = new ArrayList<>();
        List<Map<String, Object>> itensMaster = (List<Map<String, Object>>) pedidoMaster.getOrDefault("itens", List.of());

        for (Map<String, Object> item : itensMaster) {
            List<Map<String, Object>> filhos = (List<Map<String, Object>>) item.get("filhos");
            if (filhos != null && !filhos.isEmpty()) {
                // Item de Combo: projeta cada filho para sua respectiva estação com comboNome
                String comboNome = String.valueOf(item.get("produtoNome"));
                for (Map<String, Object> filho : filhos) {
                    Map<String, Object> ik = new LinkedHashMap<>();
                    ik.put("itemId", filho.get("id"));
                    ik.put("produtoNome", filho.get("produtoNome"));
                    ik.put("quantidade", filho.getOrDefault("quantidade", 1));
                    String est = String.valueOf(filho.getOrDefault("estacaoPreparo", "BAR"));
                    ik.put("estacao", "NENHUMA".equals(est) ? "BAR" : est);
                    ik.put("modificadores", List.of());
                    ik.put("observacao", filho.get("observacao"));
                    ik.put("comboNome", comboNome);
                    itensKds.add(ik);
                }
            } else {
                Map<String, Object> ik = new LinkedHashMap<>();
                ik.put("itemId", item.get("id"));
                ik.put("produtoNome", item.get("produtoNome"));
                ik.put("quantidade", item.getOrDefault("quantidade", 1));
                String est = String.valueOf(item.getOrDefault("estacaoPreparo", "BAR"));
                ik.put("estacao", "NENHUMA".equals(est) ? "BAR" : est);

                List<String> modsNomes = new ArrayList<>();
                List<Map<String, Object>> mods = (List<Map<String, Object>>) item.get("modificadores");
                if (mods != null) {
                    for (Map<String, Object> m : mods) {
                        int qtdMod = ((Number) m.getOrDefault("quantidade", 1)).intValue();
                        String nomeMod = String.valueOf(m.get("modificadorNome"));
                        modsNomes.add(qtdMod > 1 ? qtdMod + "x " + nomeMod : nomeMod);
                    }
                }
                ik.put("modificadores", modsNomes);
                ik.put("observacao", item.get("observacao"));
                ik.put("comboNome", null);
                itensKds.add(ik);
            }
        }

        kds.put("itens", itensKds);
        return kds;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> filterKdsOrderByStation(Map<String, Object> pedido, String estacao) {
        if (estacao == null || "TODAS".equalsIgnoreCase(estacao)) {
            return pedido;
        }
        Map<String, Object> copy = new LinkedHashMap<>(pedido);
        List<Map<String, Object>> itens = (List<Map<String, Object>>) pedido.getOrDefault("itens", List.of());
        List<Map<String, Object>> filtrados = new ArrayList<>();
        for (Map<String, Object> item : itens) {
            if (estacao.equalsIgnoreCase(String.valueOf(item.get("estacao")))) {
                filtrados.add(item);
            }
        }
        copy.put("itens", filtrados);
        return copy;
    }
}
