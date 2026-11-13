package com.orderflow.caixa.service;

import com.orderflow.auth.domain.Loja;
import com.orderflow.auth.domain.Usuario;
import com.orderflow.auth.dto.ValidarPinResponse;
import com.orderflow.auth.repository.LojaRepository;
import com.orderflow.auth.repository.UsuarioRepository;
import com.orderflow.auth.service.AuthService;
import com.orderflow.caixa.domain.Caixa;
import com.orderflow.caixa.domain.FechamentoDeclaracao;
import com.orderflow.caixa.domain.MetodoPagamento;
import com.orderflow.caixa.domain.MovimentacaoCaixa;
import com.orderflow.caixa.domain.NaturezaMovimentacao;
import com.orderflow.caixa.domain.Pagamento;
import com.orderflow.caixa.domain.SessaoCaixa;
import com.orderflow.caixa.domain.SituacaoConferencia;
import com.orderflow.caixa.domain.StatusPagamento;
import com.orderflow.caixa.domain.StatusSessaoCaixa;
import com.orderflow.caixa.domain.TipoMovimentacao;
import com.orderflow.caixa.dto.CaixaDtos.AbrirSessaoRequest;
import com.orderflow.caixa.dto.CaixaDtos.CaixaResponse;
import com.orderflow.caixa.dto.CaixaDtos.ComposicaoDinheiroDto;
import com.orderflow.caixa.dto.CaixaDtos.ConferenciaMetodoItem;
import com.orderflow.caixa.dto.CaixaDtos.ConferenciaResponse;
import com.orderflow.caixa.dto.CaixaDtos.ConferirSessaoRequest;
import com.orderflow.caixa.dto.CaixaDtos.CriarTerminalCaixaRequest;
import com.orderflow.caixa.dto.CaixaDtos.DeclaracaoMetodoRequest;
import com.orderflow.caixa.dto.CaixaDtos.EstornoRequest;
import com.orderflow.caixa.dto.CaixaDtos.FechamentoCegoRequest;
import com.orderflow.caixa.dto.CaixaDtos.FechamentoProtocoloResponse;
import com.orderflow.caixa.dto.CaixaDtos.MovimentacaoCaixaRequest;
import com.orderflow.caixa.dto.CaixaDtos.MovimentacaoGerencialResponse;
import com.orderflow.caixa.dto.CaixaDtos.MovimentacaoOperadorResponse;
import com.orderflow.caixa.dto.CaixaDtos.SaldoSessaoResponse;
import com.orderflow.caixa.dto.CaixaDtos.SessaoCaixaGerencialResponse;
import com.orderflow.caixa.dto.CaixaDtos.SessaoCaixaOperadorResponse;
import com.orderflow.caixa.dto.CaixaDtos.TotalPorMetodoDto;
import com.orderflow.caixa.dto.CaixaDtos.UsuarioRefDto;
import com.orderflow.caixa.repository.CaixaRepository;
import com.orderflow.caixa.repository.FechamentoDeclaracaoRepository;
import com.orderflow.caixa.repository.MovimentacaoCaixaRepository;
import com.orderflow.caixa.repository.PagamentoRepository;
import com.orderflow.caixa.repository.SessaoCaixaRepository;
import com.orderflow.pedido.repository.PedidoRepository;
import com.orderflow.shared.error.CodigoErro;
import com.orderflow.shared.error.OrderFlowException;
import com.orderflow.shared.id.UuidV7Generator;
import com.orderflow.shared.security.AuthenticatedUser;
import com.orderflow.shared.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Serviço de domínio e aplicação do Módulo de Caixa Rigoroso:
 * Abertura com fundo de troco, Sangria (com aprovação por PIN de alçada acima do limite e sinalização de saldo negativo),
 * Suprimento, Fechamento Cego, Conferência Gerencial e Lançamentos Reversos Imutáveis.
 */
@Service
public class CaixaService {

    public static final String SINALIZACAO_SALDO_NEGATIVO = "SALDO_ESPERADO_NEGATIVO";

    private final TenantContext tenantContext;
    private final AuthService authService;
    private final LojaRepository lojaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CaixaRepository caixaRepository;
    private final SessaoCaixaRepository sessaoCaixaRepository;
    private final MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    private final PagamentoRepository pagamentoRepository;
    private final FechamentoDeclaracaoRepository fechamentoDeclaracaoRepository;
    private final PedidoRepository pedidoRepository;

    public CaixaService(
            TenantContext tenantContext,
            AuthService authService,
            LojaRepository lojaRepository,
            UsuarioRepository usuarioRepository,
            CaixaRepository caixaRepository,
            SessaoCaixaRepository sessaoCaixaRepository,
            MovimentacaoCaixaRepository movimentacaoCaixaRepository,
            PagamentoRepository pagamentoRepository,
            FechamentoDeclaracaoRepository fechamentoDeclaracaoRepository,
            PedidoRepository pedidoRepository
    ) {
        this.tenantContext = tenantContext;
        this.authService = authService;
        this.lojaRepository = lojaRepository;
        this.usuarioRepository = usuarioRepository;
        this.caixaRepository = caixaRepository;
        this.sessaoCaixaRepository = sessaoCaixaRepository;
        this.movimentacaoCaixaRepository = movimentacaoCaixaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.fechamentoDeclaracaoRepository = fechamentoDeclaracaoRepository;
        this.pedidoRepository = pedidoRepository;
    }

    // ─────────────────────────────── TERMINAIS ───────────────────────────────

    @Transactional(readOnly = true)
    public List<CaixaResponse> listarTerminais() {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        return caixaRepository.findByLojaIdOrderByNomeAsc(lojaId).stream()
                .map(c -> {
                    Optional<SessaoCaixa> aberta = sessaoCaixaRepository.findByLojaIdAndCaixaIdAndStatus(
                            lojaId, c.getId(), StatusSessaoCaixa.ABERTA
                    );
                    UsuarioRefDto opRef = aberta.flatMap(s -> buscarUsuarioRef(s.getOperadorId())).orElse(null);
                    return new CaixaResponse(
                            c.getId(),
                            c.getNome(),
                            c.isPossuiGaveta(),
                            c.isAtivo(),
                            aberta.map(SessaoCaixa::getId).orElse(null),
                            opRef
                    );
                })
                .toList();
    }

    @Transactional
    public CaixaResponse criarTerminal(CriarTerminalCaixaRequest req) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        if (caixaRepository.existsByLojaIdAndNomeIgnoreCase(lojaId, req.nome().trim())) {
            throw new OrderFlowException(CodigoErro.NOME_DUPLICADO, "Já existe um terminal de caixa com este nome na loja.");
        }
        Caixa salvo = caixaRepository.save(new Caixa(
                lojaId,
                req.nome(),
                req.impressoraIp(),
                req.impressoraPorta() != null ? req.impressoraPorta() : 9100,
                req.possuiGaveta() == null || req.possuiGaveta(),
                true
        ));
        return new CaixaResponse(salvo.getId(), salvo.getNome(), salvo.isPossuiGaveta(), salvo.isAtivo(), null, null);
    }

    // ──────────────────────────── ABERTURA DE CAIXA ────────────────────────────

    @Transactional
    public SessaoCaixaOperadorResponse abrirSessao(AbrirSessaoRequest req, UUID idempotencyKey) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();
        UUID chaveIdemp = idempotencyKey != null ? idempotencyKey : UuidV7Generator.nextId();

        Caixa caixa = caixaRepository.findByIdAndLojaId(req.caixaId(), lojaId)
                .filter(Caixa::isAtivo)
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Terminal de caixa não encontrado ou inativo."));

        if (sessaoCaixaRepository.findByLojaIdAndCaixaIdAndStatus(lojaId, caixa.getId(), StatusSessaoCaixa.ABERTA).isPresent()) {
            throw new OrderFlowException(CodigoErro.CAIXA_JA_ABERTO, "Este terminal de caixa já possui uma sessão ABERTA.");
        }

        if (sessaoCaixaRepository.findByLojaIdAndOperadorIdAndStatus(lojaId, user.usuarioId(), StatusSessaoCaixa.ABERTA).isPresent()) {
            throw new OrderFlowException(CodigoErro.OPERADOR_COM_SESSAO, "Operador já possui uma sessão de caixa aberta em outro terminal.");
        }

        Instant agora = Instant.now();
        SessaoCaixa sessao = sessaoCaixaRepository.save(new SessaoCaixa(
                lojaId,
                caixa.getId(),
                user.usuarioId(),
                req.fundoTrocoCentavos(),
                agora
        ));

        // Registra a movimentação imutável de ABERTURA (fundo de troco)
        movimentacaoCaixaRepository.save(new MovimentacaoCaixa(
                lojaId,
                sessao.getId(),
                chaveIdemp,
                TipoMovimentacao.ABERTURA,
                NaturezaMovimentacao.ENTRADA,
                req.fundoTrocoCentavos(),
                "Fundo de troco inicial na abertura de caixa",
                null,
                null,
                null,
                null
        ));

        return toSessaoOperadorResponse(sessao, caixa.getNome());
    }

    @Transactional(readOnly = true)
    public SessaoCaixaOperadorResponse obterSessaoAbertaPorCaixa(UUID caixaId) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        Caixa caixa = caixaRepository.findByIdAndLojaId(caixaId, lojaId)
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Terminal de caixa não encontrado."));
        SessaoCaixa sessao = sessaoCaixaRepository.findByLojaIdAndCaixaIdAndStatus(lojaId, caixaId, StatusSessaoCaixa.ABERTA)
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Nenhuma sessão aberta para este terminal."));
        return toSessaoOperadorResponse(sessao, caixa.getNome());
    }

    @Transactional(readOnly = true)
    public List<SessaoCaixaGerencialResponse> listarSessoesGerenciais() {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        return sessaoCaixaRepository.findByLojaIdOrderByDataAberturaDesc(lojaId).stream()
                .map(this::toSessaoGerencialResponse)
                .toList();
    }

    // ─────────────────────── SANGRIA, SUPRIMENTO E MOVIMENTAÇÕES ───────────────────────

    @Transactional
    public MovimentacaoOperadorResponse registrarSangria(
            UUID sessaoCaixaId,
            MovimentacaoCaixaRequest req,
            UUID idempotencyKey
    ) {
        MovimentacaoCaixaRequest sangriaReq = new MovimentacaoCaixaRequest(
                sessaoCaixaId,
                TipoMovimentacao.SANGRIA,
                req.valorCentavos(),
                req.motivo(),
                req.pinAprovador()
        );
        return registrarMovimentacaoManual(sessaoCaixaId, sangriaReq, idempotencyKey);
    }

    @Transactional
    public MovimentacaoOperadorResponse registrarSuprimento(
            UUID sessaoCaixaId,
            MovimentacaoCaixaRequest req,
            UUID idempotencyKey
    ) {
        MovimentacaoCaixaRequest suprimentoReq = new MovimentacaoCaixaRequest(
                sessaoCaixaId,
                TipoMovimentacao.SUPRIMENTO,
                req.valorCentavos(),
                req.motivo(),
                req.pinAprovador()
        );
        return registrarMovimentacaoManual(sessaoCaixaId, suprimentoReq, idempotencyKey);
    }

    @Transactional
    public MovimentacaoOperadorResponse registrarMovimentacaoManual(
            UUID sessaoIdFromPath,
            MovimentacaoCaixaRequest req,
            UUID idempotencyKey
    ) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();
        UUID chaveIdemp = idempotencyKey != null ? idempotencyKey : UuidV7Generator.nextId();

        // Idempotência: se já processou com a mesma chave, devolve a movimentação existente
        Optional<MovimentacaoCaixa> existente = movimentacaoCaixaRepository.findByLojaIdAndIdempotencyKey(lojaId, chaveIdemp);
        if (existente.isPresent()) {
            return toMovimentacaoOperadorResponse(existente.get());
        }

        UUID sessaoId = sessaoIdFromPath != null ? sessaoIdFromPath : req.sessaoCaixaId();
        SessaoCaixa sessao = resolverSessaoAbertaObrigatoria(lojaId, sessaoId, user.usuarioId());

        TipoMovimentacao tipo = req.tipo() != null ? req.tipo() : TipoMovimentacao.SANGRIA;
        if (tipo != TipoMovimentacao.SANGRIA && tipo != TipoMovimentacao.SUPRIMENTO) {
            throw new OrderFlowException(
                    CodigoErro.VALIDACAO_CAMPOS,
                    "Apenas lançamentos do tipo SANGRIA ou SUPRIMENTO são permitidos manualmente."
            );
        }

        UUID aprovadorId = null;
        String sinalizacao = null;

        if (tipo == TipoMovimentacao.SANGRIA) {
            Loja loja = lojaRepository.findById(lojaId)
                    .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Loja não encontrada."));
            long limiteSemAprovacao = loja.getLimiteSangriaSemAprovacaoCentavos();

            if (req.valorCentavos() > limiteSemAprovacao) {
                if (user.isGerenteOrAdmin() && (req.pinAprovador() == null || req.pinAprovador().isBlank())) {
                    aprovadorId = user.usuarioId();
                } else {
                    if (req.pinAprovador() == null || req.pinAprovador().isBlank()) {
                        throw new OrderFlowException(
                                CodigoErro.APROVACAO_NECESSARIA,
                                "Sangria acima do limite da loja (" + limiteSemAprovacao + " centavos) exige o PIN de alçada de um GERENTE."
                        );
                    }
                    ValidarPinResponse validacao = authService.validarPinAlcadaNaLoja(lojaId, req.pinAprovador());
                    aprovadorId = validacao.aprovadorId();
                }
            } else if (req.pinAprovador() != null && !req.pinAprovador().isBlank()) {
                ValidarPinResponse validacao = authService.validarPinAlcadaNaLoja(lojaId, req.pinAprovador());
                aprovadorId = validacao.aprovadorId();
            }

            // Decisão D3: Sangria acima do saldo esperado em dinheiro é ACEITA (não vaza saldo ao operador)
            // e gravada com sinalização SALDO_ESPERADO_NEGATIVO para auditoria gerencial.
            long saldoAtualDinheiro = calcularSaldoDinheiroEsperado(lojaId, sessao.getId());
            if (req.valorCentavos() > saldoAtualDinheiro) {
                sinalizacao = SINALIZACAO_SALDO_NEGATIVO;
            }
        }

        MovimentacaoCaixa nova = movimentacaoCaixaRepository.save(new MovimentacaoCaixa(
                lojaId,
                sessao.getId(),
                chaveIdemp,
                tipo,
                tipo.getNaturezaPadrao(),
                req.valorCentavos(),
                req.motivo(),
                null,
                null,
                aprovadorId,
                sinalizacao
        ));

        return toMovimentacaoOperadorResponse(nova);
    }

    @Transactional
    public MovimentacaoGerencialResponse estornarMovimentacao(UUID movimentacaoId, EstornoRequest req) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();

        MovimentacaoCaixa original = movimentacaoCaixaRepository.findByIdAndLojaId(movimentacaoId, lojaId)
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Movimentação de caixa não encontrada."));

        if (original.getTipo() != TipoMovimentacao.SANGRIA && original.getTipo() != TipoMovimentacao.SUPRIMENTO) {
            throw new OrderFlowException(
                    CodigoErro.MOVIMENTACAO_NAO_ESTORNAVEL,
                    "Apenas movimentações de SANGRIA ou SUPRIMENTO podem ser estornadas diretamente."
            );
        }

        if (movimentacaoCaixaRepository.existsByLojaIdAndMovimentacaoEstornadaId(lojaId, original.getId())) {
            throw new OrderFlowException(CodigoErro.MOVIMENTACAO_JA_ESTORNADA, "Esta movimentação já possui lançamento reverso.");
        }

        SessaoCaixa sessao = buscarSessaoObrigatoria(lojaId, original.getSessaoCaixaId());
        if (!sessao.isAberta()) {
            throw new OrderFlowException(CodigoErro.SESSAO_CAIXA_FECHADA, "Não é possível estornar movimentação de uma sessão já fechada.");
        }

        NaturezaMovimentacao naturezaReversa = original.getNatureza() == NaturezaMovimentacao.ENTRADA
                ? NaturezaMovimentacao.SAIDA
                : NaturezaMovimentacao.ENTRADA;

        MovimentacaoCaixa reversa = movimentacaoCaixaRepository.save(new MovimentacaoCaixa(
                lojaId,
                sessao.getId(),
                UuidV7Generator.nextId(),
                TipoMovimentacao.ESTORNO_MOVIMENTACAO,
                naturezaReversa,
                original.getValorCentavos(),
                req.motivo(),
                null,
                original.getId(),
                user.usuarioId(),
                null
        ));

        return toMovimentacaoGerencialResponse(reversa);
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoGerencialResponse> listarMovimentacoesSessao(UUID sessaoId) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        buscarSessaoObrigatoria(lojaId, sessaoId);
        return movimentacaoCaixaRepository.findByLojaIdAndSessaoCaixaIdOrderByCreatedAtAsc(lojaId, sessaoId)
                .stream()
                .map(this::toMovimentacaoGerencialResponse)
                .toList();
    }

    // ─────────────────────────── FECHAMENTO CEGO & CONFERÊNCIA ───────────────────────────

    @Transactional
    public FechamentoProtocoloResponse fecharSessaoCego(UUID sessaoIdFromPath, FechamentoCegoRequest req) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();

        UUID sessaoId = sessaoIdFromPath != null ? sessaoIdFromPath : req.sessaoCaixaId();
        SessaoCaixa sessao = resolverSessaoParaFechamento(lojaId, sessaoId, user);

        if (!sessao.isAberta()) {
            throw OrderFlowException.conflitoEstado(
                    CodigoErro.SESSAO_CAIXA_FECHADA,
                    "Esta sessão de caixa já foi fechada anteriormente.",
                    sessao.getStatus().name(),
                    sessao.getVersao()
            );
        }

        if (req.versao() != null && !req.versao().equals(sessao.getVersao())) {
            throw OrderFlowException.conflitoEstado(
                    CodigoErro.VERSAO_CONFLITANTE,
                    "Versão da sessão de caixa desatualizada.",
                    sessao.getStatus().name(),
                    sessao.getVersao()
            );
        }

        if (!user.isGerenteOrAdmin() && !sessao.getOperadorId().equals(user.usuarioId())) {
            throw new OrderFlowException(
                    CodigoErro.SESSAO_DE_OUTRO_OPERADOR,
                    "Apenas o operador titular da sessão ou um GERENTE pode realizar o fechamento cego."
            );
        }

        Set<MetodoPagamento> metodosVistos = new HashSet<>();
        DeclaracaoMetodoRequest declDinheiro = null;
        for (DeclaracaoMetodoRequest d : req.declaracoes()) {
            if (!metodosVistos.add(d.metodo())) {
                throw new OrderFlowException(
                        CodigoErro.METODO_DECLARADO_DUPLICADO,
                        "Método de pagamento informado mais de uma vez: " + d.metodo()
                );
            }
            if (d.metodo() == MetodoPagamento.DINHEIRO) {
                declDinheiro = d;
            }
        }
        if (declDinheiro == null) {
            throw new OrderFlowException(
                    CodigoErro.DECLARACAO_DINHEIRO_OBRIGATORIA,
                    "A declaração do valor contado em DINHEIRO é obrigatória no fechamento cego."
            );
        }

        long esperadoDinheiro = calcularSaldoDinheiroEsperado(lojaId, sessao.getId());
        Map<MetodoPagamento, Long> esperadoPorMetodo = calcularTotaisEsperadosPorMetodo(lojaId, sessao.getId(), esperadoDinheiro);

        // Persiste declarações imutáveis para todos os métodos relevantes
        Set<MetodoPagamento> todosMetodos = new HashSet<>(metodosVistos);
        todosMetodos.addAll(esperadoPorMetodo.keySet());
        Map<MetodoPagamento, DeclaracaoMetodoRequest> declaradasMap = new EnumMap<>(MetodoPagamento.class);
        req.declaracoes().forEach(d -> declaradasMap.put(d.metodo(), d));

        for (MetodoPagamento metodo : todosMetodos) {
            DeclaracaoMetodoRequest decl = declaradasMap.get(metodo);
            long valorDeclarado = decl != null ? decl.valorDeclaradoCentavos() : 0L;
            long valorEsperado = esperadoPorMetodo.getOrDefault(metodo, 0L);
            Integer qtdComprovantes = decl != null ? decl.quantidadeComprovantes() : null;

            fechamentoDeclaracaoRepository.save(new FechamentoDeclaracao(
                    lojaId,
                    sessao.getId(),
                    metodo,
                    valorDeclarado,
                    valorEsperado,
                    qtdComprovantes
            ));
        }

        Instant agora = Instant.now();
        String protocolo = "CX-" + DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(agora)
                + "-" + sessao.getId().toString().substring(0, 8).toUpperCase();

        sessao.registrarFechamentoCego(
                protocolo,
                esperadoDinheiro,
                declDinheiro.valorDeclaradoCentavos(),
                req.observacao(),
                agora
        );
        sessaoCaixaRepository.saveAndFlush(sessao);

        List<String> alertas = new ArrayList<>();
        long emAberto = pedidoRepository.countPedidosEmAberto(lojaId);
        if (emAberto > 0) {
            alertas.add("Existem " + emAberto + " pedido(s) pós-pago(s) ainda não quitados na loja.");
        }

        // Retorno estrito do fechamento cego: apenas protocolo e confirmação (sem esperado nem diferença)
        return new FechamentoProtocoloResponse(
                sessao.getId(),
                protocolo,
                sessao.getStatus(),
                sessao.getDataFechamento(),
                alertas
        );
    }

    @Transactional(readOnly = true)
    public ConferenciaResponse obterConferencia(UUID sessaoId) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        SessaoCaixa sessao = buscarSessaoObrigatoria(lojaId, sessaoId);
        if (sessao.isAberta()) {
            throw new OrderFlowException(CodigoErro.SESSAO_NAO_FECHADA, "A conferência só está disponível após o fechamento cego da sessão.");
        }

        List<FechamentoDeclaracao> declaracoes = fechamentoDeclaracaoRepository.findByLojaIdAndSessaoCaixaIdOrderByMetodoAsc(lojaId, sessaoId);
        List<Pagamento> pagamentosConfirmados = pagamentoRepository.findByLojaIdAndSessaoCaixaIdAndStatus(
                lojaId, sessaoId, StatusPagamento.CONFIRMADO
        );

        List<ConferenciaMetodoItem> porMetodo = declaracoes.stream()
                .map(d -> {
                    long qtdPagamentos = pagamentosConfirmados.stream()
                            .filter(p -> p.getMetodo() == d.getMetodo())
                            .count();
                    return new ConferenciaMetodoItem(
                            d.getMetodo(),
                            d.getValorEsperadoCentavos(),
                            d.getValorDeclaradoCentavos(),
                            d.getDiferencaCentavos(),
                            d.calcularSituacao(),
                            d.getQuantidadeComprovantes(),
                            qtdPagamentos
                    );
                })
                .toList();

        List<MovimentacaoCaixa> movs = movimentacaoCaixaRepository.findByLojaIdAndSessaoCaixaIdOrderByCreatedAtAsc(lojaId, sessaoId);
        ComposicaoDinheiroDto composicao = calcularComposicaoDinheiro(movs);

        List<MovimentacaoGerencialResponse> sinalizacoes = movs.stream()
                .filter(m -> m.getSinalizacao() != null)
                .map(this::toMovimentacaoGerencialResponse)
                .toList();

        return new ConferenciaResponse(
                sessao.getId(),
                sessao.getStatus(),
                buscarUsuarioRef(sessao.getOperadorId()).orElse(null),
                sessao.getDataAbertura(),
                sessao.getDataFechamento(),
                porMetodo,
                composicao,
                sinalizacoes,
                sessao.getObservacaoOperador(),
                sessao.getObservacaoConferencia()
        );
    }

    @Transactional
    public ConferenciaResponse conferirSessao(UUID sessaoId, ConferirSessaoRequest req) {
        AuthenticatedUser user = tenantContext.requireCurrentUser();
        UUID lojaId = user.lojaId();
        SessaoCaixa sessao = buscarSessaoObrigatoria(lojaId, sessaoId);

        if (sessao.isAberta()) {
            throw new OrderFlowException(CodigoErro.SESSAO_NAO_FECHADA, "A sessão ainda está aberta e não pode ser conferida.");
        }
        if (req.versao() != null && !req.versao().equals(sessao.getVersao())) {
            throw OrderFlowException.conflitoEstado(
                    CodigoErro.VERSAO_CONFLITANTE,
                    "Versão da sessão de caixa desatualizada.",
                    sessao.getStatus().name(),
                    sessao.getVersao()
            );
        }

        sessao.registrarConferencia(user.usuarioId(), req.observacao(), Instant.now());
        sessaoCaixaRepository.saveAndFlush(sessao);
        return obterConferencia(sessaoId);
    }

    @Transactional(readOnly = true)
    public SaldoSessaoResponse obterSaldoSessaoGerencial(UUID sessaoId) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        SessaoCaixa sessao = buscarSessaoObrigatoria(lojaId, sessaoId);
        long esperadoDinheiro = calcularSaldoDinheiroEsperado(lojaId, sessao.getId());
        Map<MetodoPagamento, Long> totais = calcularTotaisEsperadosPorMetodo(lojaId, sessao.getId(), esperadoDinheiro);

        List<TotalPorMetodoDto> lista = totais.entrySet().stream()
                .map(e -> new TotalPorMetodoDto(e.getKey(), e.getValue()))
                .toList();
        return new SaldoSessaoResponse(sessao.getId(), esperadoDinheiro, lista, Instant.now());
    }

    // ─────────────────────────────── CÁLCULOS INTERNOS ───────────────────────────────

    public long calcularSaldoDinheiroEsperado(UUID lojaId, UUID sessaoCaixaId) {
        return movimentacaoCaixaRepository.findByLojaIdAndSessaoCaixaIdOrderByCreatedAtAsc(lojaId, sessaoCaixaId)
                .stream()
                .mapToLong(MovimentacaoCaixa::valorComSinal)
                .sum();
    }

    private Map<MetodoPagamento, Long> calcularTotaisEsperadosPorMetodo(
            UUID lojaId,
            UUID sessaoCaixaId,
            long esperadoDinheiro
    ) {
        Map<MetodoPagamento, Long> mapa = new EnumMap<>(MetodoPagamento.class);
        mapa.put(MetodoPagamento.DINHEIRO, esperadoDinheiro);

        List<Pagamento> confirmados = pagamentoRepository.findByLojaIdAndSessaoCaixaIdAndStatus(
                lojaId, sessaoCaixaId, StatusPagamento.CONFIRMADO
        );
        for (Pagamento p : confirmados) {
            if (p.getMetodo() != MetodoPagamento.DINHEIRO) {
                mapa.merge(p.getMetodo(), p.getValorCentavos(), Long::sum);
            }
        }
        return mapa;
    }

    private ComposicaoDinheiroDto calcularComposicaoDinheiro(List<MovimentacaoCaixa> movs) {
        long abertura = 0L;
        long vendas = 0L;
        long suprimentos = 0L;
        long sangrias = 0L;
        long estornos = 0L;

        for (MovimentacaoCaixa m : movs) {
            switch (m.getTipo()) {
                case ABERTURA -> abertura += m.getValorCentavos();
                case VENDA, VENDA_DINHEIRO -> vendas += m.getValorCentavos();
                case SUPRIMENTO -> suprimentos += m.getValorCentavos();
                case SANGRIA -> sangrias += m.getValorCentavos();
                case ESTORNO, ESTORNO_VENDA_DINHEIRO, ESTORNO_MOVIMENTACAO -> {
                    if (m.getNatureza() == NaturezaMovimentacao.SAIDA) {
                        estornos += m.getValorCentavos();
                    } else {
                        estornos -= m.getValorCentavos();
                    }
                }
            }
        }
        long esperado = abertura + vendas + suprimentos - sangrias - estornos;
        return new ComposicaoDinheiroDto(abertura, vendas, suprimentos, sangrias, estornos, esperado);
    }

    public SessaoCaixa resolverSessaoAbertaObrigatoria(UUID lojaId, UUID sessaoIdInformada, UUID operadorId) {
        if (sessaoIdInformada != null) {
            SessaoCaixa sessao = buscarSessaoObrigatoria(lojaId, sessaoIdInformada);
            if (!sessao.isAberta()) {
                throw new OrderFlowException(CodigoErro.SESSAO_CAIXA_FECHADA, "A sessão de caixa informada já está fechada.");
            }
            return sessao;
        }
        return sessaoCaixaRepository.findByLojaIdAndOperadorIdAndStatus(lojaId, operadorId, StatusSessaoCaixa.ABERTA)
                .or(() -> sessaoCaixaRepository.findFirstByLojaIdAndStatusOrderByDataAberturaDesc(lojaId, StatusSessaoCaixa.ABERTA))
                .orElseThrow(() -> new OrderFlowException(
                        CodigoErro.SESSAO_CAIXA_INEXISTENTE,
                        "Não há sessão de caixa aberta para registrar esta operação."
                ));
    }

    private SessaoCaixa resolverSessaoParaFechamento(UUID lojaId, UUID sessaoIdInformada, AuthenticatedUser user) {
        if (sessaoIdInformada != null) {
            return buscarSessaoObrigatoria(lojaId, sessaoIdInformada);
        }
        return sessaoCaixaRepository.findByLojaIdAndOperadorIdAndStatus(lojaId, user.usuarioId(), StatusSessaoCaixa.ABERTA)
                .or(() -> sessaoCaixaRepository.findFirstByLojaIdAndStatusOrderByDataAberturaDesc(lojaId, StatusSessaoCaixa.ABERTA))
                .orElseThrow(() -> new OrderFlowException(CodigoErro.SESSAO_CAIXA_INEXISTENTE, "Nenhuma sessão de caixa aberta encontrada."));
    }

    public SessaoCaixa buscarSessaoObrigatoria(UUID lojaId, UUID sessaoId) {
        return sessaoCaixaRepository.findByIdAndLojaId(sessaoId, lojaId)
                .orElseThrow(() -> new OrderFlowException(CodigoErro.RECURSO_NAO_ENCONTRADO, "Sessão de caixa não encontrada: " + sessaoId));
    }

    private Optional<UsuarioRefDto> buscarUsuarioRef(UUID usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }
        return usuarioRepository.findById(usuarioId).map(u -> new UsuarioRefDto(u.getId(), u.getNome()));
    }

    private SessaoCaixaOperadorResponse toSessaoOperadorResponse(SessaoCaixa s, String caixaNome) {
        long qtdPagamentos = pagamentoRepository.countByLojaIdAndSessaoCaixaIdAndStatus(
                s.getLojaId(), s.getId(), StatusPagamento.CONFIRMADO
        );
        long posPagoAberto = pedidoRepository.countPedidosEmAberto(s.getLojaId());
        return new SessaoCaixaOperadorResponse(
                s.getId(),
                s.getCaixaId(),
                caixaNome,
                buscarUsuarioRef(s.getOperadorId()).orElse(new UsuarioRefDto(s.getOperadorId(), "Operador")),
                s.getStatus(),
                s.getDataAbertura(),
                s.getDataAbertura(),
                s.getFundoTrocoCentavos(),
                qtdPagamentos,
                posPagoAberto,
                s.getVersao()
        );
    }

    private SessaoCaixaGerencialResponse toSessaoGerencialResponse(SessaoCaixa s) {
        String caixaNome = caixaRepository.findById(s.getCaixaId()).map(Caixa::getNome).orElse("Caixa");
        boolean possuiSinalizacoes = movimentacaoCaixaRepository
                .findByLojaIdAndSessaoCaixaIdOrderByCreatedAtAsc(s.getLojaId(), s.getId())
                .stream()
                .anyMatch(m -> m.getSinalizacao() != null);

        return new SessaoCaixaGerencialResponse(
                s.getId(),
                s.getCaixaId(),
                caixaNome,
                buscarUsuarioRef(s.getOperadorId()).orElse(new UsuarioRefDto(s.getOperadorId(), "Operador")),
                s.getStatus(),
                s.getDataAbertura(),
                s.getDataFechamento(),
                s.getConferidaEm(),
                buscarUsuarioRef(s.getConferidaPor()).orElse(null),
                s.getFundoTrocoCentavos(),
                s.getEsperadoDinheiroCentavos(),
                s.getDeclaradoDinheiroCentavos(),
                s.getDiferencaDinheiroCentavos(),
                possuiSinalizacoes,
                s.getVersao()
        );
    }

    private MovimentacaoOperadorResponse toMovimentacaoOperadorResponse(MovimentacaoCaixa m) {
        UsuarioRefDto aprovador = buscarUsuarioRef(m.getUsuarioAprovadorId()).orElse(null);
        UsuarioRefDto criador = buscarUsuarioRef(m.getCreatedBy()).orElse(null);
        return new MovimentacaoOperadorResponse(
                m.getId(),
                m.getSessaoCaixaId(),
                m.getTipo(),
                m.getNatureza(),
                m.getValorCentavos(),
                m.getMotivo(),
                m.getUsuarioAprovadorId(),
                aprovador,
                m.getCreatedAt(),
                criador
        );
    }

    private MovimentacaoGerencialResponse toMovimentacaoGerencialResponse(MovimentacaoCaixa m) {
        UsuarioRefDto aprovador = buscarUsuarioRef(m.getUsuarioAprovadorId()).orElse(null);
        UsuarioRefDto criador = buscarUsuarioRef(m.getCreatedBy()).orElse(null);
        return new MovimentacaoGerencialResponse(
                m.getId(),
                m.getSessaoCaixaId(),
                m.getTipo(),
                m.getNatureza(),
                m.getValorCentavos(),
                m.getMotivo(),
                m.getPagamentoId(),
                m.getMovimentacaoEstornadaId(),
                m.getUsuarioAprovadorId(),
                aprovador,
                m.getSinalizacao(),
                m.getCreatedAt(),
                criador
        );
    }
}
