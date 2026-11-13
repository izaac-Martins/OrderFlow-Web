package com.orderflow.pedido.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.auth.domain.Loja;
import com.orderflow.auth.repository.LojaRepository;
import com.orderflow.auth.service.AuthService;
import com.orderflow.caixa.domain.MetodoPagamento;
import com.orderflow.caixa.domain.MovimentacaoCaixa;
import com.orderflow.caixa.domain.NaturezaMovimentacao;
import com.orderflow.caixa.domain.Pagamento;
import com.orderflow.caixa.domain.SessaoCaixa;
import com.orderflow.caixa.domain.StatusPagamento;
import com.orderflow.caixa.domain.TipoMovimentacao;
import com.orderflow.caixa.dto.CaixaDtos.EstornoRequest;
import com.orderflow.caixa.dto.CaixaDtos.PagamentoRequest;
import com.orderflow.caixa.dto.CaixaDtos.PagamentoResponse;
import com.orderflow.caixa.repository.MovimentacaoCaixaRepository;
import com.orderflow.caixa.repository.PagamentoRepository;
import com.orderflow.caixa.service.CaixaService;
import com.orderflow.catalogo.domain.Categoria;
import com.orderflow.catalogo.domain.ComboSlot;
import com.orderflow.catalogo.domain.ComboSlotOpcao;
import com.orderflow.catalogo.domain.EstacaoPreparo;
import com.orderflow.catalogo.domain.GrupoModificador;
import com.orderflow.catalogo.domain.Insumo;
import com.orderflow.catalogo.domain.OpcaoModificador;
import com.orderflow.catalogo.domain.Produto;
import com.orderflow.catalogo.domain.ProdutoModificadorGrupo;
import com.orderflow.catalogo.domain.TipoPreco;
import com.orderflow.catalogo.domain.TipoProduto;
import com.orderflow.catalogo.repository.CategoriaRepository;
import com.orderflow.catalogo.repository.ComboSlotOpcaoRepository;
import com.orderflow.catalogo.repository.ComboSlotRepository;
import com.orderflow.catalogo.repository.GrupoModificadorRepository;
import com.orderflow.catalogo.repository.InsumoRepository;
import com.orderflow.catalogo.repository.OpcaoModificadorRepository;
import com.orderflow.catalogo.repository.ProdutoModificadorGrupoRepository;
import com.orderflow.catalogo.repository.ProdutoRepository;
import com.orderflow.pedido.domain.ItemPedido;
import com.orderflow.pedido.domain.ItemPedidoModificador;
import com.orderflow.pedido.domain.ModalidadePagamento;
import com.orderflow.pedido.domain.OrigemPedido;
import com.orderflow.pedido.domain.Pedido;
import com.orderflow.pedido.domain.PedidoEvento;
import com.orderflow.pedido.domain.StatusPedido;
import com.orderflow.pedido.domain.TipoAtendimento;
import com.orderflow.pedido.dto.PedidoDtos.AlterarStatusKdsRequest;
import com.orderflow.pedido.dto.PedidoDtos.CancelarPedidoRequest;
import com.orderflow.pedido.dto.PedidoDtos.CozinhaSnapshotResponse;
import com.orderflow.pedido.dto.PedidoDtos.CriarPedidoRequest;
import com.orderflow.pedido.dto.PedidoDtos.ItemCozinhaDto;
import com.orderflow.pedido.dto.PedidoDtos.ItemPedidoModificadorResponse;
import com.orderflow.pedido.dto.PedidoDtos.ItemPedidoRequest;
import com.orderflow.pedido.dto.PedidoDtos.ItemPedidoResponse;
import com.orderflow.pedido.dto.PedidoDtos.ModificadorSelecionadoRequest;
import com.orderflow.pedido.dto.PedidoDtos.PedidoCozinhaDto;
import com.orderflow.pedido.dto.PedidoDtos.PedidoCozinhaEventoDto;
import com.orderflow.pedido.dto.PedidoDtos.PedidoProntoEventoDto;
import com.orderflow.pedido.dto.PedidoDtos.PedidoResponse;
import com.orderflow.pedido.dto.PedidoDtos.RefazerPedidoRequest;
import com.orderflow.pedido.dto.PedidoDtos.SelecaoComboSlotRequest;
import com.orderflow.pedido.dto.PedidoDtos.VersaoRequest;
import com.orderflow.pedido.repository.ItemPedidoModificadorRepository;
import com.orderflow.pedido.repository.ItemPedidoRepository;
import com.orderflow.pedido.repository.PedidoEventoRepository;
import com.orderflow.pedido.repository.PedidoRepository;
import com.orderflow.shared.error.CodigoErro;
import com.orderflow.shared.error.OrderFlowException;
import com.orderflow.shared.fiscal.EmissorFiscalPort;
import com.orderflow.shared.id.UuidV7Generator;
import com.orderflow.shared.security.AuthenticatedUser;
import com.orderflow.shared.security.TenantContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço central de Pedidos, Pagamentos, Máquina de Estados, Transactional Outbox ({@code pedido_eventos})
 * e Despacho em Tempo Real para o KDS via WebSocket STOMP.
 */
@Service
public class PedidoService {

    private static final List<StatusPedido> STATUS_ATIVOS_KDS = List.of(
            StatusPedido.CONFIRMADO,
            StatusPedido.EM_PREPARO,
            StatusPedido.PRONTO
    );

    private final TenantContext tenantContext;
    private final AuthService authService;
    private final CaixaService caixaService;
    private final LojaRepository lojaRepository;
    private final CategoriaRepository categoriaRepository;
    private final InsumoRepository insumoRepository;
    private final GrupoModificadorRepository grupoModificadorRepository;
    private final OpcaoModificadorRepository opcaoModificadorRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoModificadorGrupoRepository produtoModificadorGrupoRepository;
    private final ComboSlotRepository comboSlotRepository;
    private final ComboSlotOpcaoRepository comboSlotOpcaoRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ItemPedidoModificadorRepository itemPedidoModificadorRepository;
    private final PedidoEventoRepository pedidoEventoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    private final EmissorFiscalPort emissorFiscalPort;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public PedidoService(
            TenantContext tenantContext,
            AuthService authService,
            CaixaService caixaService,
            LojaRepository lojaRepository,
            CategoriaRepository categoriaRepository,
            InsumoRepository insumoRepository,
            GrupoModificadorRepository grupoModificadorRepository,
            OpcaoModificadorRepository opcaoModificadorRepository,
            ProdutoRepository produtoRepository,
            ProdutoModificadorGrupoRepository produtoModificadorGrupoRepository,
            ComboSlotRepository comboSlotRepository,
            ComboSlotOpcaoRepository comboSlotOpcaoRepository,
            PedidoRepository pedidoRepository,
            ItemPedidoRepository itemPedidoRepository,
            ItemPedidoModificadorRepository itemPedidoModificadorRepository,
            PedidoEventoRepository pedidoEventoRepository,
            PagamentoRepository pagamentoRepository,
            MovimentacaoCaixaRepository movimentacaoCaixaRepository,
            EmissorFiscalPort emissorFiscalPort,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper
    ) {
        this.tenantContext = tenantContext;
        this.authService = authService;
        this.caixaService = caixaService;
        this.lojaRepository = lojaRepository;
        this.categoriaRepository = categoriaRepository;
        this.insumoRepository = insumoRepository;
        this.grupoModificadorRepository = grupoModificadorRepository;
        this.opcaoModificadorRepository = opcaoModificadorRepository;
        this.produtoRepository = produtoRepository;
        this.produtoModificadorGrupoRepository = produtoModificadorGrupoRepository;
        this.comboSlotRepository = comboSlotRepository;
        this.comboSlotOpcaoRepository = comboSlotOpcaoRepository;
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.itemPedidoModificadorRepository = itemPedidoModificadorRepository;
        this.pedidoEventoRepository = pedidoEventoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.movimentacaoCaixaRepository = movimentacaoCaixaRepository;
        this.emissorFiscalPort = emissorFiscalPort;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. CRIAÇÃO DO PEDIDO (Validação de Disponibilidade, Modificadores, Combos e Preço)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public PedidoResponse criarPedido(CriarPedidoRequest req, UUID idempotencyKey) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();
        UUID chaveIdemp = idempotencyKey != null ? idempotencyKey : UuidV7Generator.nextId();

        Optional<Pedido> existente = pedidoRepository.findByLojaIdAndIdempotencyKey(lojaId, chaveIdemp);
        if (existente.isPresent()) {
            return montarPedidoResponseCompleto(lojaId, existente.get());
        }

        Map<UUID, Insumo> insumosMap = insumoRepository.findByLojaIdOrderByNomeAsc(lojaId).stream()
                .collect(Collectors.toMap(Insumo::getId, i -> i));
        Map<UUID, Categoria> categoriasMap = categoriaRepository.findByLojaIdOrderByOrdemExibicaoAscNomeAsc(lojaId).stream()
                .collect(Collectors.toMap(Categoria::getId, c -> c));
        Map<UUID, GrupoModificador> gruposMap = grupoModificadorRepository.findByLojaIdAndArquivadoEmIsNullOrderByOrdemAscNomeAsc(lojaId).stream()
                .collect(Collectors.toMap(GrupoModificador::getId, g -> g));
        Map<UUID, OpcaoModificador> modsMap = opcaoModificadorRepository.findByLojaIdAndArquivadoEmIsNull(lojaId).stream()
                .collect(Collectors.toMap(OpcaoModificador::getId, m -> m));
        Map<UUID, Produto> produtosMap = produtoRepository.findByLojaIdAndArquivadoEmIsNullOrderByOrdemAscNomeAsc(lojaId).stream()
                .collect(Collectors.toMap(Produto::getId, p -> p));
        Map<UUID, List<ProdutoModificadorGrupo>> vinculosPorProduto = produtoModificadorGrupoRepository.findByLojaId(lojaId)
                .stream()
                .collect(Collectors.groupingBy(ProdutoModificadorGrupo::getProdutoId));
        Map<UUID, List<ComboSlot>> slotsPorCombo = comboSlotRepository.findByLojaIdOrderByOrdemAsc(lojaId)
                .stream()
                .collect(Collectors.groupingBy(ComboSlot::getProdutoComboId));
        Map<UUID, List<ComboSlotOpcao>> opcoesPorSlot = comboSlotOpcaoRepository.findByLojaIdOrderByOrdemAsc(lojaId)
                .stream()
                .collect(Collectors.groupingBy(ComboSlotOpcao::getSlotId));

        // 1. Verifica disponibilidade em tempo real de todos os itens e modificadores solicitados
        List<UUID> indisponiveis = new ArrayList<>();
        for (ItemPedidoRequest itemReq : req.itens()) {
            Produto prod = produtosMap.get(itemReq.produtoId());
            if (prod == null || !isProdutoDisponivel(prod, insumosMap)) {
                indisponiveis.add(itemReq.produtoId());
                continue;
            }
            if (itemReq.modificadores() != null) {
                for (ModificadorSelecionadoRequest ms : itemReq.modificadores()) {
                    OpcaoModificador mod = modsMap.get(ms.modificadorId());
                    if (mod == null || !isModificadorDisponivel(mod, insumosMap)) {
                        indisponiveis.add(ms.modificadorId());
                    }
                }
            }
            if (itemReq.selecoesCombo() != null) {
                for (SelecaoComboSlotRequest sel : itemReq.selecoesCombo()) {
                    Produto prodFilho = produtosMap.get(sel.produtoId());
                    if (prodFilho == null || !isProdutoDisponivel(prodFilho, insumosMap)) {
                        indisponiveis.add(sel.produtoId());
                    }
                    if (sel.modificadores() != null) {
                        for (ModificadorSelecionadoRequest ms : sel.modificadores()) {
                            OpcaoModificador mod = modsMap.get(ms.modificadorId());
                            if (mod == null || !isModificadorDisponivel(mod, insumosMap)) {
                                indisponiveis.add(ms.modificadorId());
                            }
                        }
                    }
                }
            }
        }
        if (!indisponiveis.isEmpty()) {
            throw OrderFlowException.itensIndisponiveis(
                    "Um ou mais itens ou adicionais selecionados estão esgotados no momento.",
                    indisponiveis.stream().distinct().toList()
            );
        }

        // 2. Calcula os itens, modificadores e subtotal (centavos inteiros no servidor)
        UUID pedidoIdProvisorio = UuidV7Generator.nextId();
        List<ItemPedido> itensParaSalvar = new ArrayList<>();
        List<ItemPedidoModificador> modsParaSalvar = new ArrayList<>();
        long subtotalPedidoCentavos = 0L;

        for (ItemPedidoRequest itemReq : req.itens()) {
            Produto produto = produtosMap.get(itemReq.produtoId());
            int qtdItem = itemReq.quantidade();
            EstacaoPreparo estacao = resolverEstacaoEfetiva(produto, categoriasMap);

            if (produto.getTipo() == TipoProduto.SIMPLES) {
                UUID itemId = UuidV7Generator.nextId();
                CalculoModificadoresItem calcMods = validarECalcularModificadores(
                        lojaId,
                        itemId,
                        produto,
                        produto.getPrecoBaseCentavos(),
                        itemReq.modificadores(),
                        vinculosPorProduto.getOrDefault(produto.getId(), List.of()),
                        gruposMap,
                        modsMap
                );
                long precoUnitarioFinal = produto.getPrecoBaseCentavos() + calcMods.adicionalUnitarioCentavos();
                if (precoUnitarioFinal < 0) {
                    throw new OrderFlowException(CodigoErro.PRECO_INVALIDO, "O preço unitário final do item não pode ser negativo.");
                }
                long totalItem = precoUnitarioFinal * qtdItem;
                subtotalPedidoCentavos += totalItem;

                itensParaSalvar.add(new ItemPedido(
                        itemId,
                        lojaId,
                        pedidoIdProvisorio,
                        produto.getId(),
                        null,
                        null,
                        null,
                        produto.getNome(),
                        produto.getPrecoBaseCentavos(),
                        qtdItem,
                        precoUnitarioFinal,
                        totalItem,
                        estacao,
                        itemReq.observacao()
                ));
                modsParaSalvar.addAll(calcMods.entidades());
            } else {
                // Produto tipo COMBO
                UUID itemComboPaiId = UuidV7Generator.nextId();
                List<ComboSlot> slotsDoCombo = slotsPorCombo.getOrDefault(produto.getId(), List.of());
                List<SelecaoComboSlotRequest> selecoes = itemReq.selecoesCombo() != null ? itemReq.selecoesCombo() : List.of();

                Map<UUID, List<SelecaoComboSlotRequest>> selecoesPorSlot = selecoes.stream()
                        .collect(Collectors.groupingBy(SelecaoComboSlotRequest::slotId));

                long somaAdicionaisFilhosUnitario = 0L;
                List<ItemPedido> filhosDoCombo = new ArrayList<>();

                for (ComboSlot slot : slotsDoCombo) {
                    List<SelecaoComboSlotRequest> escolhidasNoSlot = selecoesPorSlot.getOrDefault(slot.getId(), List.of());
                    if (escolhidasNoSlot.size() < slot.getMinSelecao() || escolhidasNoSlot.size() > slot.getMaxSelecao()) {
                        throw new OrderFlowException(
                                CodigoErro.COMBO_SLOT_INVALIDO,
                                "Seleção inválida para o slot '" + slot.getNome() + "' do combo '" + produto.getNome() + "'."
                        );
                    }
                    Map<UUID, ComboSlotOpcao> opcoesValidasNoSlot = opcoesPorSlot.getOrDefault(slot.getId(), List.of())
                            .stream()
                            .collect(Collectors.toMap(ComboSlotOpcao::getProdutoId, o -> o));

                    for (SelecaoComboSlotRequest sel : escolhidasNoSlot) {
                        ComboSlotOpcao opcaoSlot = opcoesValidasNoSlot.get(sel.produtoId());
                        if (opcaoSlot == null) {
                            throw new OrderFlowException(
                                    CodigoErro.COMBO_SLOT_INVALIDO,
                                    "Produto selecionado não pertence às opções do slot '" + slot.getNome() + "'."
                            );
                        }
                        Produto prodFilho = produtosMap.get(sel.produtoId());
                        UUID itemFilhoId = UuidV7Generator.nextId();
                        CalculoModificadoresItem calcModsFilho = validarECalcularModificadores(
                                lojaId,
                                itemFilhoId,
                                prodFilho,
                                opcaoSlot.getAcrescimoCentavos(),
                                sel.modificadores(),
                                vinculosPorProduto.getOrDefault(prodFilho.getId(), List.of()),
                                gruposMap,
                                modsMap
                        );
                        long unitarioFilho = opcaoSlot.getAcrescimoCentavos() + calcModsFilho.adicionalUnitarioCentavos();
                        somaAdicionaisFilhosUnitario += unitarioFilho;

                        filhosDoCombo.add(new ItemPedido(
                                itemFilhoId,
                                lojaId,
                                pedidoIdProvisorio,
                                prodFilho.getId(),
                                itemComboPaiId,
                                slot.getId(),
                                slot.getNome(),
                                prodFilho.getNome(),
                                opcaoSlot.getAcrescimoCentavos(),
                                qtdItem,
                                unitarioFilho,
                                unitarioFilho * qtdItem,
                                resolverEstacaoEfetiva(prodFilho, categoriasMap),
                                sel.observacao()
                        ));
                        modsParaSalvar.addAll(calcModsFilho.entidades());
                    }
                }

                long unitarioComboFinal = produto.getPrecoBaseCentavos() + somaAdicionaisFilhosUnitario;
                long totalCombo = unitarioComboFinal * qtdItem;
                subtotalPedidoCentavos += totalCombo;

                itensParaSalvar.add(new ItemPedido(
                        itemComboPaiId,
                        lojaId,
                        pedidoIdProvisorio,
                        produto.getId(),
                        null,
                        null,
                        null,
                        produto.getNome(),
                        produto.getPrecoBaseCentavos(),
                        qtdItem,
                        unitarioComboFinal,
                        totalCombo,
                        EstacaoPreparo.NENHUMA,
                        itemReq.observacao()
                ));
                itensParaSalvar.addAll(filhosDoCombo);
            }
        }

        long desconto = req.descontoCentavos() != null ? Math.max(0L, req.descontoCentavos()) : 0L;
        long totalCalculado = subtotalPedidoCentavos - desconto;
        if (totalCalculado < 0) {
            throw new OrderFlowException(CodigoErro.PRECO_INVALIDO, "O desconto não pode superar o subtotal do pedido.");
        }

        if (req.totalEsperadoCentavos() != null && !req.totalEsperadoCentavos().equals(totalCalculado)) {
            throw new OrderFlowException(
                    CodigoErro.PRECO_DESATUALIZADO,
                    "Total calculado pelo servidor (" + totalCalculado + ") diverge do esperado pelo cliente (" + req.totalEsperadoCentavos() + ")."
            );
        }

        LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
        int proximoNumeroDiario = pedidoRepository.findMaxNumeroDiario(lojaId, hoje) + 1;

        TipoAtendimento tipoAtendimento = req.tipoAtendimento() != null
                ? req.tipoAtendimento()
                : (req.mesa() != null && !req.mesa().isBlank() ? TipoAtendimento.MESA : TipoAtendimento.BALCAO);

        ModalidadePagamento modalidade = req.modalidadePagamento() != null
                ? req.modalidadePagamento()
                : (tipoAtendimento == TipoAtendimento.MESA ? ModalidadePagamento.POS_PAGO : ModalidadePagamento.PRE_PAGO);

        Pedido pedido = new Pedido(
                lojaId,
                req.sessaoCaixaId(),
                req.clienteId(),
                chaveIdemp,
                proximoNumeroDiario,
                hoje,
                req.origem() != null ? req.origem() : OrigemPedido.APP_ATENDENTE,
                tipoAtendimento,
                modalidade,
                req.identificadorCliente(),
                req.mesa(),
                req.observacao(),
                subtotalPedidoCentavos,
                desconto
        );

        Pedido pedidoSalvo = pedidoRepository.saveAndFlush(pedido);

        // Reatribui o ID definitivo do pedido nos itens e persiste
        Map<UUID, UUID> mapaIdItem = new HashMap<>();
        for (ItemPedido item : itensParaSalvar) {
            if (item.getItemPaiId() == null) {
                ItemPedido salvo = itemPedidoRepository.save(new ItemPedido(
                        item.getId(),
                        lojaId,
                        pedidoSalvo.getId(),
                        item.getProdutoId(),
                        null,
                        item.getComboSlotId(),
                        item.getComboSlotNome(),
                        item.getProdutoNome(),
                        item.getPrecoBaseCentavos(),
                        item.getQuantidade(),
                        item.getPrecoUnitarioFinalCentavos(),
                        item.getTotalCentavos(),
                        item.getEstacaoPreparo(),
                        item.getObservacao()
                ));
                mapaIdItem.put(item.getId(), salvo.getId());
            }
        }
        for (ItemPedido item : itensParaSalvar) {
            if (item.getItemPaiId() != null) {
                itemPedidoRepository.save(new ItemPedido(
                        item.getId(),
                        lojaId,
                        pedidoSalvo.getId(),
                        item.getProdutoId(),
                        mapaIdItem.getOrDefault(item.getItemPaiId(), item.getItemPaiId()),
                        item.getComboSlotId(),
                        item.getComboSlotNome(),
                        item.getProdutoNome(),
                        item.getPrecoBaseCentavos(),
                        item.getQuantidade(),
                        item.getPrecoUnitarioFinalCentavos(),
                        item.getTotalCentavos(),
                        item.getEstacaoPreparo(),
                        item.getObservacao()
                ));
            }
        }
        itemPedidoModificadorRepository.saveAll(modsParaSalvar);

        // Se o cliente já enviou o pagamento junto na criação (fluxo pré-pago rápido de balcão)
        if (req.pagamentoInicial() != null) {
            PagamentoRequest pagReq = new PagamentoRequest(
                    pedidoSalvo.getId(),
                    req.pagamentoInicial().sessaoCaixaId() != null ? req.pagamentoInicial().sessaoCaixaId() : req.sessaoCaixaId(),
                    req.pagamentoInicial().metodo(),
                    req.pagamentoInicial().valorCentavos(),
                    req.pagamentoInicial().valorRecebidoCentavos(),
                    req.pagamentoInicial().nsu(),
                    req.pagamentoInicial().autorizacao(),
                    req.pagamentoInicial().txidPix()
            );
            registrarPagamentoInterno(lojaId, user, pedidoSalvo, pagReq, UuidV7Generator.nextId());
        } else if (modalidade == ModalidadePagamento.POS_PAGO) {
            // Pedidos pós-pagos (mesa) já podem entrar direto na fila da cozinha como CONFIRMADO
            confirmarPedidoInterno(lojaId, user.usuarioId(), pedidoSalvo);
        }

        return montarPedidoResponseCompleto(lojaId, pedidoSalvo);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. PAGAMENTOS & ESTORNOS (Com impacto físico no Caixa apenas para DINHEIRO)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public PagamentoResponse registrarPagamento(PagamentoRequest req, UUID idempotencyKey) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();
        UUID chaveIdemp = idempotencyKey != null ? idempotencyKey : UuidV7Generator.nextId();

        Optional<Pagamento> existente = pagamentoRepository.findByLojaIdAndIdempotencyKey(lojaId, chaveIdemp);
        if (existente.isPresent()) {
            Pagamento pag = existente.get();
            Pedido ped = buscarPedidoObrigatorio(lojaId, pag.getPedidoId());
            return toPagamentoResponse(pag, ped);
        }

        Pedido pedido = buscarPedidoObrigatorio(lojaId, req.pedidoId());
        return registrarPagamentoInterno(lojaId, user, pedido, req, chaveIdemp);
    }

    private PagamentoResponse registrarPagamentoInterno(
            UUID lojaId,
            AuthenticatedUser user,
            Pedido pedido,
            PagamentoRequest req,
            UUID chaveIdemp
    ) {
        if (pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatus() == StatusPedido.ESTORNADO) {
            throw new OrderFlowException(CodigoErro.TRANSICAO_INVALIDA, "Não é possível registrar pagamento para pedido cancelado/estornado.");
        }

        SessaoCaixa sessao = caixaService.resolverSessaoAbertaObrigatoria(lojaId, req.sessaoCaixaId(), user.usuarioId());

        long valor = req.valorCentavos();
        if (valor > pedido.getSaldoDevedorCentavos()) {
            throw new OrderFlowException(
                    CodigoErro.VALOR_EXCEDE_SALDO_DEVEDOR,
                    "O valor do pagamento (" + valor + ") excede o saldo devedor do pedido (" + pedido.getSaldoDevedorCentavos() + ")."
            );
        }

        Long recebido = req.valorRecebidoCentavos();
        Long troco = null;
        if (req.metodo() == MetodoPagamento.DINHEIRO) {
            long efetivoRecebido = recebido != null ? recebido : valor;
            if (efetivoRecebido < valor) {
                throw new OrderFlowException(
                        CodigoErro.RECEBIDO_INSUFICIENTE,
                        "O valor recebido em dinheiro (" + efetivoRecebido + ") é menor que o valor do pagamento (" + valor + ")."
                );
            }
            recebido = efetivoRecebido;
            troco = efetivoRecebido - valor;
        } else if (recebido != null && recebido > valor) {
            throw new OrderFlowException(
                    CodigoErro.TROCO_APENAS_DINHEIRO,
                    "Troco só é permitido para pagamentos em espécie (DINHEIRO)."
            );
        }

        Pagamento pagamento = pagamentoRepository.save(new Pagamento(
                lojaId,
                pedido.getId(),
                sessao.getId(),
                chaveIdemp,
                req.metodo(),
                valor,
                recebido,
                troco,
                req.nsu(),
                req.autorizacao(),
                req.txidPix()
        ));

        // Regra contábil de gaveta: apenas DINHEIRO gera movimentação física na gaveta (pelo valor líquido = recebido - troco)
        if (req.metodo() == MetodoPagamento.DINHEIRO) {
            movimentacaoCaixaRepository.save(new MovimentacaoCaixa(
                    lojaId,
                    sessao.getId(),
                    chaveIdemp,
                    TipoMovimentacao.VENDA_DINHEIRO,
                    NaturezaMovimentacao.ENTRADA,
                    valor,
                    "Recebimento em espécie ref. pedido #" + pedido.getNumeroDiario(),
                    pagamento.getId(),
                    null,
                    null,
                    null
            ));
        }

        pedido.registrarPagamentoConfirmado(valor, sessao.getId());
        pedidoRepository.saveAndFlush(pedido);

        if (pedido.isQuitado()) {
            emissorFiscalPort.solicitarEmissaoPedidoQuitado(lojaId, pedido.getId());
            Loja loja = lojaRepository.findById(lojaId).orElse(null);
            boolean autoConfirmar = loja == null || loja.isConfirmarPrePagoAutomatico();
            if (autoConfirmar && (pedido.getStatus() == StatusPedido.CRIADO || pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO)) {
                confirmarPedidoInterno(lojaId, user.usuarioId(), pedido);
            }
        }

        return toPagamentoResponse(pagamento, pedido);
    }

    @Transactional
    public PagamentoResponse estornarPagamento(UUID pagamentoId, EstornoRequest req) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();

        Pagamento pagamento = pagamentoRepository.findByIdAndLojaId(pagamentoId, lojaId)
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Pagamento não encontrado."));

        if (!pagamento.isConfirmado()) {
            throw new OrderFlowException(CodigoErro.PAGAMENTO_JA_ESTORNADO, "Este pagamento já foi estornado.");
        }

        Pedido pedido = buscarPedidoObrigatorio(lojaId, pagamento.getPedidoId());
        Instant agora = Instant.now();

        if (pagamento.getMetodo() == MetodoPagamento.DINHEIRO) {
            SessaoCaixa sessaoAberta = caixaService.resolverSessaoAbertaObrigatoria(lojaId, null, user.usuarioId());
            movimentacaoCaixaRepository.save(new MovimentacaoCaixa(
                    lojaId,
                    sessaoAberta.getId(),
                    UuidV7Generator.nextId(),
                    TipoMovimentacao.ESTORNO_VENDA_DINHEIRO,
                    NaturezaMovimentacao.SAIDA,
                    pagamento.getValorCentavos(),
                    req.motivo(),
                    pagamento.getId(),
                    null,
                    user.usuarioId(),
                    null
            ));
        }

        pagamento.estornar(user.usuarioId(), req.motivo(), agora);
        pagamentoRepository.save(pagamento);

        pedido.reverterPagamentoEstornado(pagamento.getValorCentavos());
        pedidoRepository.saveAndFlush(pedido);

        return toPagamentoResponse(pagamento, pedido);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. MÁQUINA DE ESTADOS DO PEDIDO & TRANSIÇÕES CONCORRENTES (KDS / BALCÃO)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public PedidoResponse obterPedido(UUID id) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Pedido pedido = buscarPedidoObrigatorio(lojaId, id);
        return montarPedidoResponseCompleto(lojaId, pedido);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPedidos(List<StatusPedido> statuses, Boolean emAberto) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        List<Pedido> base = (statuses != null && !statuses.isEmpty())
                ? pedidoRepository.findByLojaIdAndStatusInOrderByConfirmadoEmAscCreatedAtAsc(lojaId, statuses)
                : pedidoRepository.findByLojaIdOrderByCreatedAtDesc(lojaId);

        return base.stream()
                .filter(p -> emAberto == null || (emAberto ? p.getSaldoDevedorCentavos() > 0 : p.getSaldoDevedorCentavos() == 0))
                .map(p -> montarPedidoResponseCompleto(lojaId, p))
                .toList();
    }

    @Transactional
    public PedidoResponse confirmarPedido(UUID id, VersaoRequest req) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();
        Pedido pedido = buscarPedidoObrigatorio(lojaId, id);

        if (pedido.getStatus() == StatusPedido.CONFIRMADO) {
            return montarPedidoResponseCompleto(lojaId, pedido);
        }
        validarVersaoConcorrentePedido(pedido, req.versao());

        if (pedido.getStatus() != StatusPedido.CRIADO && pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new OrderFlowException(
                    CodigoErro.TRANSICAO_INVALIDA,
                    "Apenas pedidos em status CRIADO ou AGUARDANDO_PAGAMENTO podem ser confirmados."
            );
        }

        if (pedido.getModalidadePagamento() == ModalidadePagamento.PRE_PAGO && !pedido.isQuitado()) {
            throw new OrderFlowException(
                    CodigoErro.PEDIDO_NAO_QUITADO,
                    "Pedido pré-pago exige quitação integral antes de ser enviado para a cozinha."
            );
        }

        confirmarPedidoInterno(lojaId, user.usuarioId(), pedido);
        return montarPedidoResponseCompleto(lojaId, pedido);
    }

    @Transactional
    public PedidoCozinhaDto iniciarPreparo(UUID id, VersaoRequest req) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();
        Pedido pedido = buscarPedidoObrigatorio(lojaId, id);

        if (pedido.getStatus() == StatusPedido.EM_PREPARO) {
            return montarPedidoCozinhaDto(lojaId, pedido, null);
        }
        validarVersaoConcorrentePedido(pedido, req.versao());

        if (pedido.getStatus() != StatusPedido.CONFIRMADO) {
            throw OrderFlowException.conflitoEstado(
                    CodigoErro.TRANSICAO_CONCORRENTE,
                    "Não foi possível iniciar o preparo: o pedido está em " + pedido.getStatus(),
                    pedido.getStatus().name(),
                    pedido.getVersao()
            );
        }

        StatusPedido anterior = pedido.getStatus();
        pedido.marcarEmPreparo(Instant.now());
        Pedido salvo = pedidoRepository.saveAndFlush(pedido);

        registrarOutboxEPublicarKds(lojaId, salvo, "PREPARO_INICIADO", anterior, null, user.usuarioId());
        return montarPedidoCozinhaDto(lojaId, salvo, null);
    }

    @Transactional
    public PedidoCozinhaDto marcarPronto(UUID id, VersaoRequest req) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();
        Pedido pedido = buscarPedidoObrigatorio(lojaId, id);

        if (pedido.getStatus() == StatusPedido.PRONTO) {
            return montarPedidoCozinhaDto(lojaId, pedido, null);
        }
        validarVersaoConcorrentePedido(pedido, req.versao());

        if (pedido.getStatus() != StatusPedido.EM_PREPARO) {
            throw OrderFlowException.conflitoEstado(
                    CodigoErro.TRANSICAO_CONCORRENTE,
                    "Apenas pedidos EM_PREPARO podem ser marcados como PRONTO.",
                    pedido.getStatus().name(),
                    pedido.getVersao()
            );
        }

        StatusPedido anterior = pedido.getStatus();
        pedido.marcarPronto(Instant.now());
        Pedido salvo = pedidoRepository.saveAndFlush(pedido);

        registrarOutboxEPublicarKds(lojaId, salvo, "PEDIDO_PRONTO", anterior, null, user.usuarioId());

        // Notifica também os atendentes / painel de senhas em /topic/lojas/{lojaId}/pedidos/prontos
        messagingTemplate.convertAndSend(
                "/topic/lojas/" + lojaId + "/pedidos/prontos",
                new PedidoProntoEventoDto(
                        salvo.getId(),
                        salvo.getNumeroDiario(),
                        salvo.getIdentificadorCliente(),
                        salvo.getMesa()
                )
        );

        return montarPedidoCozinhaDto(lojaId, salvo, null);
    }

    @Transactional
    public PedidoCozinhaDto refazerPedido(UUID id, RefazerPedidoRequest req) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();
        Pedido pedido = buscarPedidoObrigatorio(lojaId, id);

        validarVersaoConcorrentePedido(pedido, req.versao());
        if (pedido.getStatus() != StatusPedido.PRONTO) {
            throw new OrderFlowException(
                    CodigoErro.TRANSICAO_INVALIDA,
                    "Apenas pedidos em status PRONTO podem retornar para EM_PREPARO (refazer)."
            );
        }

        StatusPedido anterior = pedido.getStatus();
        pedido.marcarRefazer(Instant.now());
        Pedido salvo = pedidoRepository.saveAndFlush(pedido);

        registrarOutboxEPublicarKds(lojaId, salvo, "PEDIDO_REFEITO", anterior, req.motivo(), user.usuarioId());
        return montarPedidoCozinhaDto(lojaId, salvo, null);
    }

    @Transactional
    public PedidoResponse entregarPedido(UUID id, VersaoRequest req) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();
        Pedido pedido = buscarPedidoObrigatorio(lojaId, id);

        if (pedido.getStatus() == StatusPedido.ENTREGUE) {
            return montarPedidoResponseCompleto(lojaId, pedido);
        }
        validarVersaoConcorrentePedido(pedido, req.versao());

        if (pedido.getStatus() != StatusPedido.PRONTO) {
            throw OrderFlowException.conflitoEstado(
                    CodigoErro.TRANSICAO_CONCORRENTE,
                    "Apenas pedidos em status PRONTO podem ser entregues.",
                    pedido.getStatus().name(),
                    pedido.getVersao()
            );
        }

        StatusPedido anterior = pedido.getStatus();
        pedido.marcarEntregue(Instant.now());
        Pedido salvo = pedidoRepository.saveAndFlush(pedido);

        registrarOutboxEPublicarKds(lojaId, salvo, "PEDIDO_ENTREGUE", anterior, null, user.usuarioId());
        return montarPedidoResponseCompleto(lojaId, salvo);
    }

    @Transactional
    public PedidoCozinhaDto entregarPedidoCozinha(UUID id, VersaoRequest req) {
        entregarPedido(id, req);
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Pedido salvo = buscarPedidoObrigatorio(lojaId, id);
        return montarPedidoCozinhaDto(lojaId, salvo, null);
    }

    @Transactional
    public PedidoCozinhaDto alterarStatusGenericoKds(UUID id, AlterarStatusKdsRequest req) {
        return switch (req.status()) {
            case EM_PREPARO -> iniciarPreparo(id, new VersaoRequest(req.versao()));
            case PRONTO -> marcarPronto(id, new VersaoRequest(req.versao()));
            case ENTREGUE -> entregarPedidoCozinha(id, new VersaoRequest(req.versao()));
            default -> throw new OrderFlowException(
                    CodigoErro.TRANSICAO_INVALIDA,
                    "Status de destino não suportado diretamente pelo KDS: " + req.status()
            );
        };
    }

    @Transactional
    public PedidoResponse cancelarPedido(UUID id, CancelarPedidoRequest req) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();
        Pedido pedido = buscarPedidoObrigatorio(lojaId, id);

        if (pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatus() == StatusPedido.ESTORNADO) {
            return montarPedidoResponseCompleto(lojaId, pedido);
        }
        validarVersaoConcorrentePedido(pedido, req.versao());

        UUID usuarioAutorizador = user.usuarioId();
        if (!pedido.getStatus().isInicialParaCancelamentoPorAtendente()) {
            if (user.isGerenteOrAdmin()) {
                usuarioAutorizador = user.usuarioId();
            } else if (req.pinAprovador() != null && !req.pinAprovador().isBlank()) {
                usuarioAutorizador = authService.validarPinAlcadaNaLoja(lojaId, req.pinAprovador()).aprovadorId();
            } else {
                throw new OrderFlowException(
                        CodigoErro.CANCELAMENTO_REQUER_GERENTE,
                        "O pedido já está em " + pedido.getStatus() + "; o cancelamento exige permissão ou PIN de GERENTE."
                );
            }
        }

        // Se houver pagamentos confirmados, reverte todos atomicamente (-> ESTORNADO)
        List<Pagamento> pagamentos = pagamentoRepository.findByLojaIdAndPedidoIdOrderByCreatedAtAsc(lojaId, pedido.getId());
        List<Pagamento> confirmados = pagamentos.stream().filter(Pagamento::isConfirmado).toList();
        boolean houvePagamento = !confirmados.isEmpty();

        if (houvePagamento) {
            boolean temDinheiro = confirmados.stream().anyMatch(p -> p.getMetodo() == MetodoPagamento.DINHEIRO);
            SessaoCaixa sessaoDevolucao = temDinheiro
                    ? caixaService.resolverSessaoAbertaObrigatoria(lojaId, req.sessaoCaixaId(), user.usuarioId())
                    : null;

            Instant agora = Instant.now();
            for (Pagamento pag : confirmados) {
                if (pag.getMetodo() == MetodoPagamento.DINHEIRO && sessaoDevolucao != null) {
                    movimentacaoCaixaRepository.save(new MovimentacaoCaixa(
                            lojaId,
                            sessaoDevolucao.getId(),
                            UuidV7Generator.nextId(),
                            TipoMovimentacao.ESTORNO_VENDA_DINHEIRO,
                            NaturezaMovimentacao.SAIDA,
                            pag.getValorCentavos(),
                            req.motivo(),
                            pag.getId(),
                            null,
                            usuarioAutorizador,
                            null
                    ));
                }
                pag.estornar(usuarioAutorizador, req.motivo(), agora);
                pagamentoRepository.save(pag);
                pedido.reverterPagamentoEstornado(pag.getValorCentavos());
            }
        }

        StatusPedido anterior = pedido.getStatus();
        pedido.marcarCanceladoOuEstornado(
                houvePagamento,
                usuarioAutorizador,
                req.motivo(),
                Boolean.TRUE.equals(req.registrarPerda())
        );
        Pedido salvo = pedidoRepository.saveAndFlush(pedido);

        registrarOutboxEPublicarKds(lojaId, salvo, "PEDIDO_CANCELADO", anterior, req.motivo(), usuarioAutorizador);
        return montarPedidoResponseCompleto(lojaId, salvo);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. SNAPSHOT & REPLAY DE EVENTOS DA COZINHA (KDS)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public CozinhaSnapshotResponse obterSnapshotCozinha(EstacaoPreparo estacao) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        long seqAtual = pedidoEventoRepository.findMaxSeqByLojaId(lojaId);

        List<Pedido> ativos = pedidoRepository.findByLojaIdAndStatusInOrderByConfirmadoEmAscCreatedAtAsc(
                lojaId, STATUS_ATIVOS_KDS
        );

        List<PedidoCozinhaDto> dtos = ativos.stream()
                .map(p -> montarPedidoCozinhaDto(lojaId, p, estacao))
                .filter(dto -> !dto.itens().isEmpty())
                .toList();

        return new CozinhaSnapshotResponse(seqAtual, Instant.now(), dtos);
    }

    @Transactional(readOnly = true)
    public List<PedidoCozinhaEventoDto> replayEventosCozinha(long desdeSeq, EstacaoPreparo estacao) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        long minSeq = pedidoEventoRepository.findMinSeqByLojaId(lojaId);
        if (desdeSeq > 0 && minSeq > 0 && desdeSeq < minSeq - 1) {
            throw new OrderFlowException(
                    CodigoErro.EVENTOS_EXPIRADOS,
                    "A sequência solicitada (" + desdeSeq + ") já expirou. Solicite o snapshot completo."
            );
        }

        return pedidoEventoRepository.findByLojaIdAndSeqGreaterThanOrderBySeqAsc(lojaId, desdeSeq)
                .stream()
                .map(ev -> deserializarEventoKds(lojaId, ev, estacao))
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. OUTBOX + BROADCAST STOMP E AUXILIARES DE CÁLCULO
    // ═══════════════════════════════════════════════════════════════════════════

    private void confirmarPedidoInterno(UUID lojaId, UUID usuarioId, Pedido pedido) {
        StatusPedido anterior = pedido.getStatus();
        pedido.marcarConfirmado(Instant.now());
        Pedido salvo = pedidoRepository.saveAndFlush(pedido);
        registrarOutboxEPublicarKds(lojaId, salvo, "PEDIDO_CONFIRMADO", anterior, null, usuarioId);
    }

    private void registrarOutboxEPublicarKds(
            UUID lojaId,
            Pedido pedido,
            String tipoEvento,
            StatusPedido statusAnterior,
            String motivo,
            UUID usuarioId
    ) {
        PedidoCozinhaDto snapshotKds = montarPedidoCozinhaDto(lojaId, pedido, null);
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(snapshotKds);
        } catch (JsonProcessingException ex) {
            jsonPayload = "{}";
        }

        PedidoEvento eventoSalvo = pedidoEventoRepository.saveAndFlush(new PedidoEvento(
                lojaId,
                pedido.getId(),
                tipoEvento,
                statusAnterior,
                pedido.getStatus(),
                pedido.getVersao(),
                motivo,
                jsonPayload,
                usuarioId
        ));

        PedidoCozinhaEventoDto dtoGeral = new PedidoCozinhaEventoDto(
                eventoSalvo.getSeq(),
                tipoEvento,
                pedido.getId(),
                pedido.getVersao(),
                pedido.getStatus(),
                eventoSalvo.getCreatedAt(),
                motivo,
                snapshotKds
        );

        // Publica tanto no tópico do contrato OpenAPI (/cozinha/pedidos) quanto no alias (/kds)
        messagingTemplate.convertAndSend("/topic/lojas/" + lojaId + "/cozinha/pedidos", dtoGeral);
        messagingTemplate.convertAndSend("/topic/lojas/" + lojaId + "/kds", dtoGeral);

        // Publica também nos tópicos filtrados por estação (BAR / COZINHA)
        for (EstacaoPreparo estacao : List.of(EstacaoPreparo.BAR, EstacaoPreparo.COZINHA)) {
            PedidoCozinhaDto filtrado = montarPedidoCozinhaDto(lojaId, pedido, estacao);
            if (!filtrado.itens().isEmpty()) {
                PedidoCozinhaEventoDto dtoEstacao = new PedidoCozinhaEventoDto(
                        eventoSalvo.getSeq(),
                        tipoEvento,
                        pedido.getId(),
                        pedido.getVersao(),
                        pedido.getStatus(),
                        eventoSalvo.getCreatedAt(),
                        motivo,
                        filtrado
                );
                messagingTemplate.convertAndSend("/topic/lojas/" + lojaId + "/cozinha/estacoes/" + estacao.name(), dtoEstacao);
            }
        }
    }

    private void validarVersaoConcorrentePedido(Pedido pedido, Integer versaoCliente) {
        if (versaoCliente == null) {
            throw new OrderFlowException(CodigoErro.VERSAO_AUSENTE, "Campo 'versao' é obrigatório.");
        }
        if (!versaoCliente.equals(pedido.getVersao())) {
            throw OrderFlowException.conflitoEstado(
                    CodigoErro.TRANSICAO_CONCORRENTE,
                    "O pedido #" + pedido.getNumeroDiario() + " já mudou para " + pedido.getStatus()
                            + " (versão " + pedido.getVersao() + ") antes desta requisição.",
                    pedido.getStatus().name(),
                    pedido.getVersao()
            );
        }
    }

    private CalculoModificadoresItem validarECalcularModificadores(
            UUID lojaId,
            UUID itemPedidoId,
            Produto produto,
            long baseParaPercentualCentavos,
            List<ModificadorSelecionadoRequest> selecionadosReq,
            List<ProdutoModificadorGrupo> vinculosDoProduto,
            Map<UUID, GrupoModificador> gruposMap,
            Map<UUID, OpcaoModificador> modsMap
    ) {
        List<ModificadorSelecionadoRequest> selecionados = selecionadosReq != null ? selecionadosReq : List.of();
        Map<UUID, ProdutoModificadorGrupo> vinculoPorGrupo = vinculosDoProduto.stream()
                .collect(Collectors.toMap(ProdutoModificadorGrupo::getGrupoId, v -> v));

        Map<UUID, Integer> qtdTotalPorGrupo = new HashMap<>();
        Set<UUID> modsVistosNoItem = new HashSet<>();
        long somaFixosCentavos = 0L;
        long somaPercentuaisCentavos = 0L;
        List<ItemPedidoModificador> entidades = new ArrayList<>();

        for (ModificadorSelecionadoRequest sel : selecionados) {
            OpcaoModificador mod = modsMap.get(sel.modificadorId());
            if (mod == null) {
                throw new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Modificador não encontrado: " + sel.modificadorId());
            }
            ProdutoModificadorGrupo vinculo = vinculoPorGrupo.get(mod.getGrupoId());
            if (vinculo == null) {
                throw new OrderFlowException(
                        CodigoErro.MODIFICADOR_NAO_PERTENCE_PRODUTO,
                        "O modificador '" + mod.getNome() + "' não pertence a nenhum grupo vinculado ao produto '" + produto.getNome() + "'."
                );
            }
            GrupoModificador grupo = gruposMap.get(mod.getGrupoId());
            int qtd = sel.resolveQuantidade();

            if (!modsVistosNoItem.add(mod.getId()) || qtd > 1) {
                if (!grupo.isPermiteRepeticao()) {
                    throw new OrderFlowException(
                            CodigoErro.MODIFICADOR_REPETICAO_NAO_PERMITIDA,
                            "O grupo '" + grupo.getNome() + "' não permite repetição da mesma opção."
                    );
                }
            }
            if (qtd > mod.getMaxQuantidade()) {
                throw new OrderFlowException(
                        CodigoErro.MODIFICADOR_LIMITE,
                        "A quantidade de '" + mod.getNome() + "' excede o máximo permitido (" + mod.getMaxQuantidade() + ")."
                );
            }

            qtdTotalPorGrupo.merge(grupo.getId(), qtd, Integer::sum);

            long valorCalculado;
            if (mod.getTipoPreco() == TipoPreco.FIXO) {
                valorCalculado = mod.getValorPreco() * qtd;
                somaFixosCentavos += valorCalculado;
            } else if (mod.getTipoPreco() == TipoPreco.PERCENTUAL) {
                // Regra §2.1: percentual aplica-se sobre o preço base com arredondamento HALF_EVEN no centavo
                long unitarioPct = BigDecimal.valueOf(baseParaPercentualCentavos)
                        .multiply(BigDecimal.valueOf(mod.getValorPreco()))
                        .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.HALF_EVEN)
                        .longValue();
                valorCalculado = unitarioPct * qtd;
                somaPercentuaisCentavos += valorCalculado;
            } else {
                valorCalculado = 0L;
            }

            entidades.add(new ItemPedidoModificador(
                    lojaId,
                    itemPedidoId,
                    mod.getId(),
                    grupo.getNome(),
                    mod.getNome(),
                    mod.getTipoPreco(),
                    mod.getValorPreco(),
                    qtd,
                    valorCalculado
            ));
        }

        // Valida min e max de cada grupo vinculado ao produto
        for (ProdutoModificadorGrupo vinculo : vinculosDoProduto) {
            GrupoModificador grupo = gruposMap.get(vinculo.getGrupoId());
            if (grupo == null || !grupo.isAtivo() || grupo.isArquivado()) {
                continue;
            }
            int minEfetivo = vinculo.calcularMinSelecaoEfetiva(grupo);
            int maxEfetivo = vinculo.calcularMaxSelecaoEfetiva(grupo);
            int selecionadoNoGrupo = qtdTotalPorGrupo.getOrDefault(grupo.getId(), 0);

            if (selecionadoNoGrupo < minEfetivo) {
                throw new OrderFlowException(
                        CodigoErro.MODIFICADOR_OBRIGATORIO,
                        "O grupo obrigatório '" + grupo.getNome() + "' exige pelo menos " + minEfetivo + " seleção(ões)."
                );
            }
            if (selecionadoNoGrupo > maxEfetivo) {
                throw new OrderFlowException(
                        CodigoErro.MODIFICADOR_LIMITE,
                        "O grupo '" + grupo.getNome() + "' permite no máximo " + maxEfetivo + " seleção(ões)."
                );
            }
        }

        return new CalculoModificadoresItem(somaFixosCentavos + somaPercentuaisCentavos, entidades);
    }

    private record CalculoModificadoresItem(
            long adicionalUnitarioCentavos,
            List<ItemPedidoModificador> entidades
    ) {
    }

    private boolean isProdutoDisponivel(Produto p, Map<UUID, Insumo> insumosMap) {
        if (!p.isDisponivelProprio()) {
            return false;
        }
        return p.getInsumoIds().stream()
                .map(insumosMap::get)
                .allMatch(i -> i != null && i.isDisponivel());
    }

    private boolean isModificadorDisponivel(OpcaoModificador m, Map<UUID, Insumo> insumosMap) {
        if (!m.isDisponivelProprio()) {
            return false;
        }
        return m.getInsumoIds().stream()
                .map(insumosMap::get)
                .allMatch(i -> i != null && i.isDisponivel());
    }

    private EstacaoPreparo resolverEstacaoEfetiva(Produto p, Map<UUID, Categoria> categoriasMap) {
        if (p.getEstacaoPreparo() != null) {
            return p.getEstacaoPreparo();
        }
        Categoria cat = categoriasMap.get(p.getCategoriaId());
        return cat != null ? cat.getEstacaoPreparoPadrao() : EstacaoPreparo.COZINHA;
    }

    private Pedido buscarPedidoObrigatorio(UUID lojaId, UUID pedidoId) {
        return pedidoRepository.findByIdAndLojaId(pedidoId, lojaId)
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Pedido não encontrado: " + pedidoId));
    }

    private PedidoResponse montarPedidoResponseCompleto(UUID lojaId, Pedido p) {
        List<ItemPedido> todosItens = itemPedidoRepository.findByLojaIdAndPedidoIdOrderByCreatedAtAsc(lojaId, p.getId());
        List<UUID> itemIds = todosItens.stream().map(ItemPedido::getId).toList();
        Map<UUID, List<ItemPedidoModificador>> modsPorItem = itemIds.isEmpty()
                ? Map.of()
                : itemPedidoModificadorRepository.findByLojaIdAndItemPedidoIdIn(lojaId, itemIds)
                        .stream()
                        .collect(Collectors.groupingBy(ItemPedidoModificador::getItemPedidoId));

        Map<UUID, List<ItemPedido>> filhosPorPai = todosItens.stream()
                .filter(i -> i.getItemPaiId() != null)
                .collect(Collectors.groupingBy(ItemPedido::getItemPaiId, LinkedHashMap::new, Collectors.toList()));

        List<ItemPedidoResponse> itensRaiz = todosItens.stream()
                .filter(i -> i.getItemPaiId() == null)
                .map(raiz -> toItemPedidoResponse(raiz, filhosPorPai.getOrDefault(raiz.getId(), List.of()), modsPorItem))
                .toList();

        List<PagamentoResponse> pagamentos = pagamentoRepository.findByLojaIdAndPedidoIdOrderByCreatedAtAsc(lojaId, p.getId())
                .stream()
                .map(pag -> toPagamentoResponse(pag, p))
                .toList();

        return new PedidoResponse(
                p.getId(),
                p.getSessaoCaixaId(),
                p.getClienteId(),
                p.getNumeroDiario(),
                p.getDataOperacional(),
                p.getOrigem(),
                p.getTipoAtendimento(),
                p.getModalidadePagamento(),
                p.getIdentificadorCliente(),
                p.getMesa(),
                p.getObservacao(),
                p.getStatus(),
                p.getVersao(),
                itensRaiz,
                p.getSubtotalCentavos(),
                p.getDescontoCentavos(),
                p.getValorTotalCentavos(),
                p.getValorTotalCentavos(),
                p.getTotalPagoCentavos(),
                p.getSaldoDevedorCentavos(),
                pagamentos,
                p.getMotivoCancelamento(),
                p.getFiscalStatus(),
                p.getConfirmadoEm(),
                p.getPreparoIniciadoEm(),
                p.getProntoEm(),
                p.getEntregueEm(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private ItemPedidoResponse toItemPedidoResponse(
            ItemPedido item,
            List<ItemPedido> filhos,
            Map<UUID, List<ItemPedidoModificador>> modsPorItem
    ) {
        List<ItemPedidoModificadorResponse> mods = modsPorItem.getOrDefault(item.getId(), List.of())
                .stream()
                .map(m -> new ItemPedidoModificadorResponse(
                        m.getModificadorId(),
                        m.getGrupoNome(),
                        m.getModificadorNome(),
                        m.getTipoPreco(),
                        m.getValorPreco(),
                        m.getQuantidade(),
                        m.getValorCalculadoCentavos()
                ))
                .toList();

        List<ItemPedidoResponse> filhosDto = filhos.stream()
                .map(f -> toItemPedidoResponse(f, List.of(), modsPorItem))
                .toList();

        return new ItemPedidoResponse(
                item.getId(),
                item.getProdutoId(),
                item.getProdutoNome(),
                item.getQuantidade(),
                item.getPrecoBaseCentavos(),
                item.getPrecoUnitarioFinalCentavos(),
                item.getTotalCentavos(),
                item.getEstacaoPreparo(),
                item.getObservacao(),
                item.getComboSlotNome(),
                mods,
                filhosDto
        );
    }

    private PedidoCozinhaDto montarPedidoCozinhaDto(UUID lojaId, Pedido p, EstacaoPreparo filtroEstacao) {
        List<ItemPedido> todosItens = itemPedidoRepository.findByLojaIdAndPedidoIdOrderByCreatedAtAsc(lojaId, p.getId());
        Map<UUID, ItemPedido> itensPorId = todosItens.stream().collect(Collectors.toMap(ItemPedido::getId, i -> i));
        List<UUID> itemIds = todosItens.stream().map(ItemPedido::getId).toList();
        Map<UUID, List<ItemPedidoModificador>> modsPorItem = itemIds.isEmpty()
                ? Map.of()
                : itemPedidoModificadorRepository.findByLojaIdAndItemPedidoIdIn(lojaId, itemIds)
                        .stream()
                        .collect(Collectors.groupingBy(ItemPedidoModificador::getItemPedidoId));

        Set<UUID> paisDeComboIds = todosItens.stream()
                .map(ItemPedido::getItemPaiId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ItemCozinhaDto> itensKds = todosItens.stream()
                // Para combos, exibe no KDS diretamente os itens filhos com suas respectivas estações (BAR / COZINHA)
                .filter(i -> !paisDeComboIds.contains(i.getId()))
                .filter(i -> filtroEstacao == null || i.getEstacaoPreparo() == filtroEstacao)
                .map(i -> {
                    List<String> rotulosMods = modsPorItem.getOrDefault(i.getId(), List.of())
                            .stream()
                            .map(ItemPedidoModificador::formatarRotuloKds)
                            .toList();
                    String nomeComboPai = i.getItemPaiId() != null && itensPorId.containsKey(i.getItemPaiId())
                            ? itensPorId.get(i.getItemPaiId()).getProdutoNome()
                            : null;
                    return new ItemCozinhaDto(
                            i.getId(),
                            i.getProdutoNome(),
                            i.getQuantidade(),
                            i.getEstacaoPreparo(),
                            rotulosMods,
                            i.getObservacao(),
                            nomeComboPai
                    );
                })
                .toList();

        return new PedidoCozinhaDto(
                p.getId(),
                p.getNumeroDiario(),
                p.getIdentificadorCliente(),
                p.getMesa(),
                p.getTipoAtendimento(),
                p.getStatus(),
                p.getVersao(),
                p.getObservacao(),
                p.getConfirmadoEm() != null ? p.getConfirmadoEm() : p.getCreatedAt(),
                p.getPreparoIniciadoEm(),
                p.getProntoEm(),
                itensKds
        );
    }

    private PedidoCozinhaEventoDto deserializarEventoKds(UUID lojaId, PedidoEvento ev, EstacaoPreparo estacao) {
        PedidoCozinhaDto dto = null;
        try {
            dto = objectMapper.readValue(ev.getPayloadJson(), PedidoCozinhaDto.class);
            if (estacao != null && dto != null && dto.itens() != null) {
                List<ItemCozinhaDto> filtrados = dto.itens().stream()
                        .filter(i -> i.estacao() == estacao)
                        .toList();
                dto = new PedidoCozinhaDto(
                        dto.pedidoId(),
                        dto.numeroDiario(),
                        dto.identificadorCliente(),
                        dto.mesa(),
                        dto.tipoAtendimento(),
                        dto.status(),
                        dto.versao(),
                        dto.observacao(),
                        dto.confirmadoEm(),
                        dto.preparoIniciadoEm(),
                        dto.prontoEm(),
                        filtrados
                );
            }
        } catch (Exception ignored) {
        }
        return new PedidoCozinhaEventoDto(
                ev.getSeq(),
                ev.getTipo(),
                ev.getPedidoId(),
                ev.getVersaoPedido(),
                ev.getStatusNovo(),
                ev.getCreatedAt(),
                ev.getMotivo(),
                dto
        );
    }

    private PagamentoResponse toPagamentoResponse(Pagamento pag, Pedido pedido) {
        return new PagamentoResponse(
                pag.getId(),
                pag.getPedidoId(),
                pag.getSessaoCaixaId(),
                pag.getMetodo(),
                pag.getValorCentavos(),
                pag.getValorRecebidoCentavos(),
                pag.getTrocoCentavos(),
                pag.getStatus(),
                pag.getNsu(),
                pag.getAutorizacao(),
                pag.getTxidPix(),
                pedido.getStatus(),
                pedido.getSaldoDevedorCentavos(),
                pag.getMotivoEstorno(),
                pag.getEstornadoEm(),
                pag.getCreatedAt()
        );
    }
}
