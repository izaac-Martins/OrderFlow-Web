package com.orderflow.pedido.web;

import com.orderflow.caixa.dto.CaixaDtos.EstornoRequest;
import com.orderflow.caixa.dto.CaixaDtos.PagamentoRequest;
import com.orderflow.caixa.dto.CaixaDtos.PagamentoResponse;
import com.orderflow.catalogo.domain.EstacaoPreparo;
import com.orderflow.pedido.domain.StatusPedido;
import com.orderflow.pedido.dto.PedidoDtos.AlterarStatusKdsRequest;
import com.orderflow.pedido.dto.PedidoDtos.CancelarPedidoRequest;
import com.orderflow.pedido.dto.PedidoDtos.CozinhaSnapshotResponse;
import com.orderflow.pedido.dto.PedidoDtos.CriarPedidoRequest;
import com.orderflow.pedido.dto.PedidoDtos.PedidoCozinhaDto;
import com.orderflow.pedido.dto.PedidoDtos.PedidoCozinhaEventoDto;
import com.orderflow.pedido.dto.PedidoDtos.PedidoResponse;
import com.orderflow.pedido.dto.PedidoDtos.RefazerPedidoRequest;
import com.orderflow.pedido.dto.PedidoDtos.VersaoRequest;
import com.orderflow.pedido.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controlador REST de Pedidos (`/api/v1/pedidos`), Pagamentos (`/api/v1/pagamentos`)
 * e Cozinha / KDS (`/api/v1/cozinha`).
 */
@RestController
@RequestMapping("/api/v1")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    // ─────────────────────────────── PEDIDOS ───────────────────────────────

    @PostMapping("/pedidos")
    public ResponseEntity<PedidoResponse> criarPedido(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody CriarPedidoRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pedidoService.criarPedido(request, idempotencyKey));
    }

    @GetMapping("/pedidos")
    public ResponseEntity<List<PedidoResponse>> listarPedidos(
            @RequestParam(value = "status", required = false) List<StatusPedido> statuses,
            @RequestParam(value = "emAberto", required = false) Boolean emAberto
    ) {
        return ResponseEntity.ok(pedidoService.listarPedidos(statuses, emAberto));
    }

    @GetMapping("/pedidos/{id}")
    public ResponseEntity<PedidoResponse> obterPedido(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(pedidoService.obterPedido(id));
    }

    @PostMapping("/pedidos/{id}/confirmar")
    public ResponseEntity<PedidoResponse> confirmarPedido(
            @PathVariable("id") UUID id,
            @Valid @RequestBody VersaoRequest request
    ) {
        return ResponseEntity.ok(pedidoService.confirmarPedido(id, request));
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public ResponseEntity<PedidoResponse> cancelarPedido(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CancelarPedidoRequest request
    ) {
        return ResponseEntity.ok(pedidoService.cancelarPedido(id, request));
    }

    @PostMapping("/pedidos/{id}/entregar")
    public ResponseEntity<PedidoResponse> entregarPedido(
            @PathVariable("id") UUID id,
            @Valid @RequestBody VersaoRequest request
    ) {
        return ResponseEntity.ok(pedidoService.entregarPedido(id, request));
    }

    // ─────────────────────────────── PAGAMENTOS ───────────────────────────────

    @PostMapping("/pagamentos")
    public ResponseEntity<PagamentoResponse> registrarPagamento(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody PagamentoRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pedidoService.registrarPagamento(request, idempotencyKey));
    }

    @PostMapping("/pagamentos/{id}/estorno")
    public ResponseEntity<PagamentoResponse> estornarPagamento(
            @PathVariable("id") UUID id,
            @Valid @RequestBody EstornoRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pedidoService.estornarPagamento(id, request));
    }

    // ─────────────────────────────── COZINHA / KDS ───────────────────────────────

    @GetMapping("/cozinha/pedidos/ativos")
    public ResponseEntity<CozinhaSnapshotResponse> obterSnapshotCozinha(
            @RequestParam(value = "estacao", required = false) EstacaoPreparo estacao
    ) {
        return ResponseEntity.ok(pedidoService.obterSnapshotCozinha(estacao));
    }

    @GetMapping("/cozinha/eventos")
    public ResponseEntity<List<PedidoCozinhaEventoDto>> replayEventosCozinha(
            @RequestParam("desdeSeq") long desdeSeq,
            @RequestParam(value = "estacao", required = false) EstacaoPreparo estacao
    ) {
        return ResponseEntity.ok(pedidoService.replayEventosCozinha(desdeSeq, estacao));
    }

    @PostMapping("/cozinha/pedidos/{id}/iniciar-preparo")
    public ResponseEntity<PedidoCozinhaDto> iniciarPreparo(
            @PathVariable("id") UUID id,
            @Valid @RequestBody VersaoRequest request
    ) {
        return ResponseEntity.ok(pedidoService.iniciarPreparo(id, request));
    }

    @PostMapping("/cozinha/pedidos/{id}/marcar-pronto")
    public ResponseEntity<PedidoCozinhaDto> marcarPronto(
            @PathVariable("id") UUID id,
            @Valid @RequestBody VersaoRequest request
    ) {
        return ResponseEntity.ok(pedidoService.marcarPronto(id, request));
    }

    @PostMapping("/cozinha/pedidos/{id}/refazer")
    public ResponseEntity<PedidoCozinhaDto> refazerPedido(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RefazerPedidoRequest request
    ) {
        return ResponseEntity.ok(pedidoService.refazerPedido(id, request));
    }

    @PostMapping("/cozinha/pedidos/{id}/entregar")
    public ResponseEntity<PedidoCozinhaDto> entregarPedidoCozinha(
            @PathVariable("id") UUID id,
            @Valid @RequestBody VersaoRequest request
    ) {
        return ResponseEntity.ok(pedidoService.entregarPedidoCozinha(id, request));
    }

    @PatchMapping("/cozinha/pedidos/{id}/status")
    public ResponseEntity<PedidoCozinhaDto> alterarStatusGenericoKds(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AlterarStatusKdsRequest request
    ) {
        return ResponseEntity.ok(pedidoService.alterarStatusGenericoKds(id, request));
    }
}
