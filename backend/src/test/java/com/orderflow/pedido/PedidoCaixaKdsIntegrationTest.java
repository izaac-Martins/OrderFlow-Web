package com.orderflow.pedido;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.auth.domain.Loja;
import com.orderflow.auth.domain.Usuario;
import com.orderflow.auth.repository.LojaRepository;
import com.orderflow.auth.repository.UsuarioRepository;
import com.orderflow.caixa.domain.MetodoPagamento;
import com.orderflow.caixa.domain.SituacaoConferencia;
import com.orderflow.caixa.domain.TipoMovimentacao;
import com.orderflow.caixa.dto.CaixaDtos.AbrirSessaoRequest;
import com.orderflow.caixa.dto.CaixaDtos.CaixaResponse;
import com.orderflow.caixa.dto.CaixaDtos.ConferenciaResponse;
import com.orderflow.caixa.dto.CaixaDtos.CriarTerminalCaixaRequest;
import com.orderflow.caixa.dto.CaixaDtos.DeclaracaoMetodoRequest;
import com.orderflow.caixa.dto.CaixaDtos.FechamentoCegoRequest;
import com.orderflow.caixa.dto.CaixaDtos.MovimentacaoCaixaRequest;
import com.orderflow.caixa.dto.CaixaDtos.PagamentoInicialRequest;
import com.orderflow.caixa.dto.CaixaDtos.SessaoCaixaOperadorResponse;
import com.orderflow.caixa.repository.CaixaRepository;
import com.orderflow.caixa.repository.FechamentoDeclaracaoRepository;
import com.orderflow.caixa.repository.MovimentacaoCaixaRepository;
import com.orderflow.caixa.repository.PagamentoRepository;
import com.orderflow.caixa.repository.SessaoCaixaRepository;
import com.orderflow.caixa.service.CaixaService;
import com.orderflow.catalogo.domain.EstacaoPreparo;
import com.orderflow.catalogo.domain.TipoPreco;
import com.orderflow.catalogo.domain.TipoProduto;
import com.orderflow.catalogo.dto.CatalogoDtos.CategoriaRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.CategoriaResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.GrupoModificadorRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.GrupoModificadorResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ModificadorRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.ProdutoDetalheResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ProdutoRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.VinculoGrupoItemRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.VinculoGruposRequest;
import com.orderflow.catalogo.repository.CatalogoVersaoRepository;
import com.orderflow.catalogo.repository.CategoriaRepository;
import com.orderflow.catalogo.repository.ComboSlotOpcaoRepository;
import com.orderflow.catalogo.repository.ComboSlotRepository;
import com.orderflow.catalogo.repository.GrupoModificadorRepository;
import com.orderflow.catalogo.repository.HistoricoPrecoRepository;
import com.orderflow.catalogo.repository.InsumoRepository;
import com.orderflow.catalogo.repository.OpcaoModificadorRepository;
import com.orderflow.catalogo.repository.ProdutoModificadorGrupoRepository;
import com.orderflow.catalogo.repository.ProdutoRepository;
import com.orderflow.pedido.domain.ModalidadePagamento;
import com.orderflow.pedido.domain.OrigemPedido;
import com.orderflow.pedido.domain.StatusPedido;
import com.orderflow.pedido.domain.TipoAtendimento;
import com.orderflow.pedido.dto.PedidoDtos.AlterarStatusKdsRequest;
import com.orderflow.pedido.dto.PedidoDtos.CancelarPedidoRequest;
import com.orderflow.pedido.dto.PedidoDtos.CriarPedidoRequest;
import com.orderflow.pedido.dto.PedidoDtos.ItemPedidoRequest;
import com.orderflow.pedido.dto.PedidoDtos.ModificadorSelecionadoRequest;
import com.orderflow.pedido.dto.PedidoDtos.PedidoCozinhaDto;
import com.orderflow.pedido.dto.PedidoDtos.PedidoCozinhaEventoDto;
import com.orderflow.pedido.dto.PedidoDtos.PedidoResponse;
import com.orderflow.pedido.dto.PedidoDtos.VersaoRequest;
import com.orderflow.pedido.repository.ItemPedidoModificadorRepository;
import com.orderflow.pedido.repository.ItemPedidoRepository;
import com.orderflow.pedido.repository.PedidoEventoRepository;
import com.orderflow.pedido.repository.PedidoRepository;
import com.orderflow.shared.id.UuidV7Generator;
import com.orderflow.shared.security.AuthenticatedUser;
import com.orderflow.shared.security.JwtTokenService;
import com.orderflow.shared.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PedidoCaixaKdsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LojaRepository lojaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CaixaRepository caixaRepository;

    @Autowired
    private SessaoCaixaRepository sessaoCaixaRepository;

    @Autowired
    private MovimentacaoCaixaRepository movimentacaoCaixaRepository;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private FechamentoDeclaracaoRepository fechamentoDeclaracaoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ItemPedidoRepository itemPedidoRepository;

    @Autowired
    private ItemPedidoModificadorRepository itemPedidoModificadorRepository;

    @Autowired
    private PedidoEventoRepository pedidoEventoRepository;

    @Autowired
    private CatalogoVersaoRepository catalogoVersaoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private InsumoRepository insumoRepository;

    @Autowired
    private GrupoModificadorRepository grupoModificadorRepository;

    @Autowired
    private OpcaoModificadorRepository opcaoModificadorRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ProdutoModificadorGrupoRepository produtoModificadorGrupoRepository;

    @Autowired
    private ComboSlotRepository comboSlotRepository;

    @Autowired
    private ComboSlotOpcaoRepository comboSlotOpcaoRepository;

    @Autowired
    private HistoricoPrecoRepository historicoPrecoRepository;

    @SpyBean
    private SimpMessagingTemplate messagingTemplate;

    private Loja loja;
    private Usuario atendente;
    private Usuario outroAtendente;
    private Usuario cozinha;
    private Usuario gerente;

    private String tokenAtendente;
    private String tokenOutroAtendente;
    private String tokenCozinha;
    private String tokenGerente;

    @BeforeEach
    void setUp() {
        limparTudo();

        loja = lojaRepository.save(new Loja(UuidV7Generator.nextId(), "MATRIZ-01", "Cafeteria Matriz", "11.111.111/0001-11"));

        atendente = usuarioRepository.save(new Usuario(
                loja.getId(), "Lucas Atendente", "lucas@orderflow.local",
                passwordEncoder.encode("123456"), Role.ATENDENTE, null
        ));
        outroAtendente = usuarioRepository.save(new Usuario(
                loja.getId(), "Marina Atendente", "marina@orderflow.local",
                passwordEncoder.encode("123456"), Role.ATENDENTE, null
        ));
        cozinha = usuarioRepository.save(new Usuario(
                loja.getId(), "Barista KDS", "kds@orderflow.local",
                passwordEncoder.encode("123456"), Role.COZINHA, null
        ));
        gerente = usuarioRepository.save(new Usuario(
                loja.getId(), "Roberto Gerente", "gerente@orderflow.local",
                passwordEncoder.encode("123456"), Role.GERENTE, passwordEncoder.encode("4321")
        ));

        tokenAtendente = emitirToken(atendente);
        tokenOutroAtendente = emitirToken(outroAtendente);
        tokenCozinha = emitirToken(cozinha);
        tokenGerente = emitirToken(gerente);
    }

    @AfterEach
    void tearDown() {
        limparTudo();
    }

    private void limparTudo() {
        fechamentoDeclaracaoRepository.deleteAll();
        movimentacaoCaixaRepository.deleteAll();
        pagamentoRepository.deleteAll();
        pedidoEventoRepository.deleteAll();
        itemPedidoModificadorRepository.deleteAll();
        itemPedidoRepository.deleteAll();
        pedidoRepository.deleteAll();
        sessaoCaixaRepository.deleteAll();
        caixaRepository.deleteAll();
        comboSlotOpcaoRepository.deleteAll();
        comboSlotRepository.deleteAll();
        produtoModificadorGrupoRepository.deleteAll();
        historicoPrecoRepository.deleteAll();
        opcaoModificadorRepository.deleteAll();
        grupoModificadorRepository.deleteAll();
        produtoRepository.deleteAll();
        insumoRepository.deleteAll();
        categoriaRepository.deleteAll();
        catalogoVersaoRepository.deleteAll();
        usuarioRepository.deleteAll();
        lojaRepository.deleteAll();
    }

    @Test
    void deveAbrirSessaoDeCaixaEBloquearSegundaSessaoAbertaSimultaneaNoMesmoTerminalOuOperador() throws Exception {
        CaixaResponse terminal1 = criarTerminalHelper("PDV Balcão 01");
        CaixaResponse terminal2 = criarTerminalHelper("PDV Balcão 02");

        // 1. Abre sessão no Terminal 1 com fundo de troco de R$ 150,00 (15000 centavos)
        AbrirSessaoRequest reqAbrir = new AbrirSessaoRequest(terminal1.id(), 15_000L);
        mockMvc.perform(post("/api/v1/caixa/abertura")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAtendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAbrir)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caixaId").value(terminal1.id().toString()))
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.fundoTrocoCentavos").value(15_000));

        // 2. Outro atendente tenta abrir segunda sessão no MESMO Terminal 1 -> 409 CAIXA_JA_ABERTO
        mockMvc.perform(post("/api/v1/caixa/abertura")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutroAtendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAbrir)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CAIXA_JA_ABERTO"));

        // 3. Mesmo atendente tenta abrir outra sessão simultânea no Terminal 2 -> 409 OPERADOR_COM_SESSAO
        AbrirSessaoRequest reqTerminal2 = new AbrirSessaoRequest(terminal2.id(), 10_000L);
        mockMvc.perform(post("/api/v1/caixa/sessoes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAtendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqTerminal2)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("OPERADOR_COM_SESSAO"));
    }

    @Test
    void deveValidarSangriaAcimaDoLimiteComPinDeAlcadaESinalizarSaldoEsperadoNegativoSemExporAoOperador() throws Exception {
        CaixaResponse terminal = criarTerminalHelper("PDV Principal");

        // Abre sessão com fundo de troco de R$ 200,00 (20.000 centavos)
        // Limite padrão da loja para sangria sem aprovação = R$ 500,00 (50.000 centavos)
        SessaoCaixaOperadorResponse sessao = abrirSessaoHelper(tokenAtendente, terminal.id(), 20_000L);

        // 1. Atendente tenta sangria de R$ 600,00 (60.000 centavos > limite de 50.000) SEM PIN -> 403 APROVACAO_NECESSARIA
        MovimentacaoCaixaRequest sangriaSemPin = new MovimentacaoCaixaRequest(
                sessao.id(), TipoMovimentacao.SANGRIA, 60_000L, "Recolhimento para cofre central", null
        );
        mockMvc.perform(post("/api/v1/caixa/movimentacoes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAtendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sangriaSemPin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APROVACAO_NECESSARIA"));

        // 2. Atendente envia a mesma sangria de R$ 600,00 COM PIN INCORRETO -> 403 PIN_APROVADOR_INVALIDO
        MovimentacaoCaixaRequest sangriaPinInvalido = new MovimentacaoCaixaRequest(
                sessao.id(), TipoMovimentacao.SANGRIA, 60_000L, "Recolhimento para cofre central", "9999"
        );
        mockMvc.perform(post("/api/v1/caixa/movimentacoes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAtendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sangriaPinInvalido)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PIN_APROVADOR_INVALIDO"));

        // 3. Atendente envia sangria de R$ 600,00 COM PIN VÁLIDO DO GERENTE ("4321"):
        //    Como 60.000 > saldo esperado na gaveta (20.000), a operação é ACEITA (201) sem vazar o saldo ao operador,
        //    mas fica gravada internamente com sinalização SALDO_ESPERADO_NEGATIVO para auditoria do gerente.
        MovimentacaoCaixaRequest sangriaComPinValido = new MovimentacaoCaixaRequest(
                sessao.id(), TipoMovimentacao.SANGRIA, 60_000L, "Recolhimento autorizado pelo gerente", "4321"
        );
        mockMvc.perform(post("/api/v1/caixa/movimentacoes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAtendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sangriaComPinValido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("SANGRIA"))
                .andExpect(jsonPath("$.valorCentavos").value(60_000))
                .andExpect(jsonPath("$.usuarioAprovadorId").value(gerente.getId().toString()))
                // Garante que a resposta do operador NÃO expõe a sinalização nem o saldo esperado
                .andExpect(jsonPath("$.sinalizacao").doesNotExist());

        // 4. Gerente consulta as movimentações da sessão e vê a sinalização SALDO_ESPERADO_NEGATIVO
        mockMvc.perform(get("/api/v1/caixa/sessoes/" + sessao.id() + "/movimentacoes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].tipo").value("SANGRIA"))
                .andExpect(jsonPath("$[1].sinalizacao").value(CaixaService.SINALIZACAO_SALDO_NEGATIVO));
    }

    @Test
    void deveExecutarFluxoCompletoDePedidoPagamentoMaquinaDeEstadosKdsConcorrenciaFechamentoCegoEBroadcastStomp() throws Exception {
        // 1. Prepara terminal e abre sessão de caixa com R$ 100,00 (10.000 centavos)
        CaixaResponse terminal = criarTerminalHelper("PDV Balcão Principal");
        SessaoCaixaOperadorResponse sessao = abrirSessaoHelper(tokenAtendente, terminal.id(), 10_000L);

        // 2. Cria Categoria (BAR), Grupo de Modificadores ("Extras Espresso") e Produto ("Cappuccino Italiano" = R$ 14,00)
        CategoriaRequest catReq = new CategoriaRequest(null, "Cafés Quentes", "cafes-quentes", 1, true, EstacaoPreparo.BAR, null);
        CategoriaResponse categoria = objectMapper.readValue(
                mockMvc.perform(post("/api/v1/categorias")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerente)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(catReq)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                CategoriaResponse.class
        );

        GrupoModificadorRequest grupoReq = new GrupoModificadorRequest(
                "Adicionais de Café", "adicionais-cafe", 0, 2, true, 1, true, null,
                List.of(new ModificadorRequest("Dose Extra Espresso", TipoPreco.FIXO, 400L, false, 2, 1, true, true, List.of(), null))
        );
        GrupoModificadorResponse grupo = objectMapper.readValue(
                mockMvc.perform(post("/api/v1/grupos-modificadores")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerente)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(grupoReq)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                GrupoModificadorResponse.class
        );
        UUID modDoseExtraId = grupo.modificadores().get(0).id();

        ProdutoRequest prodReq = new ProdutoRequest(
                categoria.id(), "CAP-ITA", "Cappuccino Italiano", "Cappuccino cremoso", null,
                TipoProduto.SIMPLES, 1_400L, EstacaoPreparo.BAR, true, true, 1, null
        );
        ProdutoDetalheResponse cappuccino = objectMapper.readValue(
                mockMvc.perform(post("/api/v1/produtos")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerente)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(prodReq)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                ProdutoDetalheResponse.class
        );

        VinculoGruposRequest vinculoReq = new VinculoGruposRequest(
                cappuccino.versao(),
                List.of(new VinculoGrupoItemRequest(grupo.id(), 1, 0, 2))
        );
        mockMvc.perform(put("/api/v1/produtos/" + cappuccino.id() + "/grupos-modificadores")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vinculoReq)))
                .andExpect(status().isOk());

        // 3. Cria Pedido PRÉ-PAGO de Balcão:
        //    2x Cappuccino Italiano (1.400) + 1x Dose Extra Espresso (400) por unidade = 1.800 unitário * 2 = 3.600 centavos (R$ 36,00)
        //    Pago em DINHEIRO com nota de R$ 50,00 (5.000 centavos) -> troco de R$ 14,00 (1.400 centavos)
        CriarPedidoRequest criarPedidoReq = new CriarPedidoRequest(
                OrigemPedido.MASTER_BALCAO,
                TipoAtendimento.BALCAO,
                ModalidadePagamento.PRE_PAGO,
                sessao.id(),
                null,
                "Cliente Ana",
                null,
                "Bem quente",
                0L,
                null,
                3_600L,
                new PagamentoInicialRequest(sessao.id(), MetodoPagamento.DINHEIRO, 3_600L, 5_000L, null, null, null),
                List.of(new ItemPedidoRequest(
                        cappuccino.id(),
                        2,
                        "Sem canela",
                        List.of(new ModificadorSelecionadoRequest(modDoseExtraId, 1)),
                        null
                ))
        );

        String pedidoCriadoBody = mockMvc.perform(post("/api/v1/pedidos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAtendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criarPedidoReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorTotalCentavos").value(3_600))
                .andExpect(jsonPath("$.totalPagoCentavos").value(3_600))
                .andExpect(jsonPath("$.saldoDevedorCentavos").value(0))
                // Quitação integral pré-paga já confirma automaticamente e despacha para o KDS
                .andExpect(jsonPath("$.status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.pagamentos[0].trocoCentavos").value(1_400))
                .andReturn().getResponse().getContentAsString();

        PedidoResponse pedidoConfirmado = objectMapper.readValue(pedidoCriadoBody, PedidoResponse.class);

        // 4. Verifica que o pedido aparece no Snapshot de pedidos ativos do KDS
        mockMvc.perform(get("/api/v1/cozinha/pedidos/ativos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCozinha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos.length()").value(1))
                .andExpect(jsonPath("$.pedidos[0].pedidoId").value(pedidoConfirmado.id().toString()))
                .andExpect(jsonPath("$.pedidos[0].status").value("CONFIRMADO"));

        // 5. KDS avança o pedido de CONFIRMADO -> EM_PREPARO
        String emPreparoBody = mockMvc.perform(post("/api/v1/cozinha/pedidos/" + pedidoConfirmado.id() + "/iniciar-preparo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCozinha)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VersaoRequest(pedidoConfirmado.versao()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_PREPARO"))
                .andReturn().getResponse().getContentAsString();
        PedidoCozinhaDto pedidoEmPreparo = objectMapper.readValue(emPreparoBody, PedidoCozinhaDto.class);

        // 6. Teste de Concorrência Otimista (@Version):
        //    Atendente tenta cancelar enviando a versão antiga (stale) -> 409 TRANSICAO_CONCORRENTE
        CancelarPedidoRequest cancelarVersaoAntiga = new CancelarPedidoRequest(
                pedidoConfirmado.versao(), "Cliente desistiu", false, sessao.id(), "4321"
        );
        mockMvc.perform(post("/api/v1/pedidos/" + pedidoConfirmado.id() + "/cancelar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAtendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelarVersaoAntiga)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSICAO_CONCORRENTE"))
                .andExpect(jsonPath("$.estadoAtual.status").value("EM_PREPARO"))
                .andExpect(jsonPath("$.estadoAtual.versao").value(pedidoEmPreparo.versao()));

        // 7. Atendente tenta cancelar pedido já EM_PREPARO com a versão atual, mas SEM PIN de gerente -> 403 CANCELAMENTO_REQUER_GERENTE
        CancelarPedidoRequest cancelarSemPin = new CancelarPedidoRequest(
                pedidoEmPreparo.versao(), "Cliente desistiu", false, sessao.id(), null
        );
        mockMvc.perform(post("/api/v1/pedidos/" + pedidoConfirmado.id() + "/cancelar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAtendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelarSemPin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CANCELAMENTO_REQUER_GERENTE"));

        // 8. KDS avança EM_PREPARO -> PRONTO (via PATCH /api/v1/cozinha/pedidos/{id}/status)
        AlterarStatusKdsRequest reqPronto = new AlterarStatusKdsRequest(pedidoEmPreparo.versao(), StatusPedido.PRONTO, null);
        String prontoBody = mockMvc.perform(patch("/api/v1/cozinha/pedidos/" + pedidoConfirmado.id() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCozinha)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqPronto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRONTO"))
                .andReturn().getResponse().getContentAsString();
        PedidoCozinhaDto pedidoPronto = objectMapper.readValue(prontoBody, PedidoCozinhaDto.class);

        // 9. Entrega o pedido ao cliente (PRONTO -> ENTREGUE)
        mockMvc.perform(post("/api/v1/pedidos/" + pedidoConfirmado.id() + "/entregar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAtendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VersaoRequest(pedidoPronto.versao()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENTREGUE"));

        // 10. Verifica disparo dos eventos STOMP em /topic/lojas/{lojaId}/kds e /topic/lojas/{lojaId}/cozinha/pedidos
        ArgumentCaptor<PedidoCozinhaEventoDto> kdsCaptor = ArgumentCaptor.forClass(PedidoCozinhaEventoDto.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/topic/lojas/" + loja.getId() + "/kds"),
                kdsCaptor.capture()
        );
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/topic/lojas/" + loja.getId() + "/cozinha/pedidos"),
                kdsCaptor.capture()
        );
        assertThat(kdsCaptor.getAllValues())
                .extracting(PedidoCozinhaEventoDto::tipo)
                .contains("PEDIDO_CONFIRMADO", "PREPARO_INICIADO", "PEDIDO_PRONTO", "PEDIDO_ENTREGUE");

        // 11. Verifica Replay da fila de eventos monotônicos do KDS (GET /api/v1/cozinha/eventos?desdeSeq=0)
        mockMvc.perform(get("/api/v1/cozinha/eventos")
                        .param("desdeSeq", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCozinha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].tipo").value("PEDIDO_CONFIRMADO"))
                .andExpect(jsonPath("$[3].tipo").value("PEDIDO_ENTREGUE"));

        // 12. Fechamento Cego pelo Atendente:
        //     Esperado na gaveta = 10.000 (abertura) + 3.600 (venda em dinheiro líquida) = 13.600 centavos.
        //     Atendente declara 13.500 centavos (diferença de -100 centavos = QUEBRA).
        //     O retorno do fechamento cego devolve apenas o protocolo (sem expor esperado nem diferença).
        FechamentoCegoRequest fechamentoReq = new FechamentoCegoRequest(
                sessao.id(),
                sessao.versao(),
                "Fechamento do turno da manhã",
                List.of(new DeclaracaoMetodoRequest(MetodoPagamento.DINHEIRO, 13_500L, null))
        );
        mockMvc.perform(post("/api/v1/caixa/fechamento")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAtendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fechamentoReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.protocolo").exists())
                .andExpect(jsonPath("$.esperadoDinheiroCentavos").doesNotExist())
                .andExpect(jsonPath("$.diferencaDinheiroCentavos").doesNotExist());

        // 13. Conferência Gerencial pelo Gerente: exibe composição completa, esperado (13.600), declarado (13.500) e diferença (-100 = QUEBRA)
        String confBody = mockMvc.perform(get("/api/v1/caixa/sessoes/" + sessao.id() + "/conferencia")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.composicaoDinheiro.aberturaCentavos").value(10_000))
                .andExpect(jsonPath("$.composicaoDinheiro.vendasCentavos").value(3_600))
                .andExpect(jsonPath("$.composicaoDinheiro.esperadoCentavos").value(13_600))
                .andReturn().getResponse().getContentAsString();

        ConferenciaResponse conferencia = objectMapper.readValue(confBody, ConferenciaResponse.class);
        assertThat(conferencia.porMetodo()).hasSize(1);
        assertThat(conferencia.porMetodo().get(0).diferencaCentavos()).isEqualTo(-100L);
        assertThat(conferencia.porMetodo().get(0).situacao()).isEqualTo(SituacaoConferencia.QUEBRA);
    }

    private CaixaResponse criarTerminalHelper(String nome) throws Exception {
        CriarTerminalCaixaRequest req = new CriarTerminalCaixaRequest(nome, "192.168.1.50", 9100, true);
        String body = mockMvc.perform(post("/api/v1/caixas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, CaixaResponse.class);
    }

    private SessaoCaixaOperadorResponse abrirSessaoHelper(String token, UUID caixaId, long fundoTrocoCentavos) throws Exception {
        AbrirSessaoRequest req = new AbrirSessaoRequest(caixaId, fundoTrocoCentavos);
        String body = mockMvc.perform(post("/api/v1/caixa/abertura")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SessaoCaixaOperadorResponse.class);
    }

    private String emitirToken(Usuario u) {
        AuthenticatedUser authUser = new AuthenticatedUser(
                u.getId(), u.getLojaId(), u.getNome(), u.getRole(), Set.copyOf(u.getRole().allGrantedAuthorities())
        );
        return jwtTokenService.generateAccessToken(authUser, Instant.now());
    }
}
