package com.orderflow.catalogo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.auth.domain.Loja;
import com.orderflow.auth.domain.Usuario;
import com.orderflow.auth.repository.LojaRepository;
import com.orderflow.auth.repository.UsuarioRepository;
import com.orderflow.catalogo.domain.EstacaoPreparo;
import com.orderflow.catalogo.domain.TipoPreco;
import com.orderflow.catalogo.domain.TipoProduto;
import com.orderflow.catalogo.dto.CatalogoDtos.CatalogoEventoStomp;
import com.orderflow.catalogo.dto.CatalogoDtos.CategoriaRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.CategoriaResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.ComboSlotItemRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.ComboSlotOpcaoRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.ComboSlotsRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.GrupoModificadorRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.GrupoModificadorResponse;
import com.orderflow.catalogo.dto.CatalogoDtos.InsumoDisponibilidadeRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.InsumoRequest;
import com.orderflow.catalogo.dto.CatalogoDtos.InsumoResponse;
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
import com.orderflow.shared.id.UuidV7Generator;
import com.orderflow.shared.security.AuthenticatedUser;
import com.orderflow.shared.security.JwtTokenService;
import com.orderflow.shared.security.Role;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogoIntegrationTest {

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

    private Loja lojaMatriz;
    private Loja lojaFilial;
    private String tokenGerenteMatriz;
    private String tokenCozinhaMatriz;
    private String tokenGerenteFilial;

    @BeforeEach
    void setUp() {
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

        lojaMatriz = lojaRepository.save(new Loja(UuidV7Generator.nextId(), "MATRIZ", "Cafeteria Matriz", "11.111.111/0001-11"));
        lojaFilial = lojaRepository.save(new Loja(UuidV7Generator.nextId(), "FILIAL", "Cafeteria Filial", "22.222.222/0001-22"));

        Usuario gerenteMatriz = usuarioRepository.save(new Usuario(
                lojaMatriz.getId(), "Gerente Matriz", "gerente@matriz.local",
                passwordEncoder.encode("123456"), Role.GERENTE, passwordEncoder.encode("1234")
        ));
        Usuario cozinhaMatriz = usuarioRepository.save(new Usuario(
                lojaMatriz.getId(), "Cozinha Matriz", "cozinha@matriz.local",
                passwordEncoder.encode("123456"), Role.COZINHA, null
        ));
        Usuario gerenteFilial = usuarioRepository.save(new Usuario(
                lojaFilial.getId(), "Gerente Filial", "gerente@filial.local",
                passwordEncoder.encode("123456"), Role.GERENTE, passwordEncoder.encode("5678")
        ));

        tokenGerenteMatriz = emitirToken(gerenteMatriz);
        tokenCozinhaMatriz = emitirToken(cozinhaMatriz);
        tokenGerenteFilial = emitirToken(gerenteFilial);
    }

    @Test
    void deveValidarHierarquiaMaximaDe2NiveisESlugDuplicadoEmCategorias() throws Exception {
        // 1. Cria categoria raiz (Nível 1)
        CategoriaRequest reqRaiz = new CategoriaRequest(null, "Cafés Especiais", "cafes-especiais", 1, true, EstacaoPreparo.BAR, null);
        String raizBody = mockMvc.perform(post("/api/v1/categorias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqRaiz)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("cafes-especiais"))
                .andReturn().getResponse().getContentAsString();
        CategoriaResponse raiz = objectMapper.readValue(raizBody, CategoriaResponse.class);

        // 2. Tenta criar outra categoria raiz irmã com o mesmo slug -> 409 SLUG_DUPLICADO
        mockMvc.perform(post("/api/v1/categorias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqRaiz)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SLUG_DUPLICADO"));

        // 3. Cria subcategoria (Nível 2) -> 201 Created
        CategoriaRequest reqSub = new CategoriaRequest(raiz.id(), "Com Leite Vegetal", "com-leite-vegetal", 1, true, EstacaoPreparo.BAR, null);
        String subBody = mockMvc.perform(post("/api/v1/categorias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqSub)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        CategoriaResponse sub = objectMapper.readValue(subBody, CategoriaResponse.class);

        // 4. Tenta criar sub-subcategoria (Nível 3) -> 422 CATEGORIA_PROFUNDIDADE_EXCEDIDA
        CategoriaRequest reqNivel3 = new CategoriaRequest(sub.id(), "Gelados", "gelados", 1, true, EstacaoPreparo.BAR, null);
        mockMvc.perform(post("/api/v1/categorias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNivel3)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CATEGORIA_PROFUNDIDADE_EXCEDIDA"));
    }

    @Test
    void deveValidarUnicidadeDeSkuPorLojaPrecoNegativoEBloqueioDeArquivamentoDeCategoriaComProdutos() throws Exception {
        CategoriaResponse catMatriz = criarCategoriaHelper(tokenGerenteMatriz, "Bebidas", "bebidas");
        CategoriaResponse catFilial = criarCategoriaHelper(tokenGerenteFilial, "Bebidas", "bebidas");

        // 1. Preço negativo -> 422 PRECO_INVALIDO
        ProdutoRequest prodPrecoNegativo = new ProdutoRequest(
                catMatriz.id(), "LATTE-01", "Latte Tradicional", "Espresso com leite", null,
                TipoProduto.SIMPLES, -500L, EstacaoPreparo.BAR, true, true, 1, null
        );
        mockMvc.perform(post("/api/v1/produtos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodPrecoNegativo)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRECO_INVALIDO"));

        // 2. Cria produto válido na Matriz -> 201
        ProdutoRequest prodValido = new ProdutoRequest(
                catMatriz.id(), "LATTE-01", "Latte Tradicional", "Espresso com leite", null,
                TipoProduto.SIMPLES, 1200L, EstacaoPreparo.BAR, true, true, 1, null
        );
        mockMvc.perform(post("/api/v1/produtos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodValido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("LATTE-01"));

        // 3. Mesmo SKU na MESMA loja -> 409 SKU_DUPLICADO
        mockMvc.perform(post("/api/v1/produtos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodValido)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_DUPLICADO"));

        // 4. Mesmo SKU em OUTRA loja (Filial) -> 201 permitido (isolamento multi-tenant)
        ProdutoRequest prodFilial = new ProdutoRequest(
                catFilial.id(), "LATTE-01", "Latte Filial", null, null,
                TipoProduto.SIMPLES, 1300L, EstacaoPreparo.BAR, true, true, 1, null
        );
        mockMvc.perform(post("/api/v1/produtos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteFilial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodFilial)))
                .andExpect(status().isCreated());

        // 5. Tenta arquivar categoria da Matriz que possui produto ativo -> 409 CATEGORIA_COM_PRODUTOS_ATIVOS
        mockMvc.perform(delete("/api/v1/categorias/" + catMatriz.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORIA_COM_PRODUTOS_ATIVOS"));
    }

    @Test
    void deveImpedirComboAninhadoCom422EValidarCascataDeInsumoComBroadcastStomp() throws Exception {
        CategoriaResponse cat = criarCategoriaHelper(tokenGerenteMatriz, "Combos e Cafés", "combos-e-cafes");

        // 1. Cria insumo "Leite de Aveia"
        InsumoRequest insumoReq = new InsumoRequest("Leite de Aveia", "ML", 2000L, 500L, true, null);
        String insumoBody = mockMvc.perform(post("/api/v1/catalogo/insumos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(insumoReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        InsumoResponse insumoAveia = objectMapper.readValue(insumoBody, InsumoResponse.class);

        // 2. Cria Grupo OBRIGATÓRIO "Tipo de Leite" (min=1, max=1) cujo único modificador depende de "Leite de Aveia"
        GrupoModificadorRequest grupoReq = new GrupoModificadorRequest(
                "Leite Vegetal Exclusivo", "leite-veg", 1, 1, false, 1, true, null,
                List.of(new ModificadorRequest("Aveia Barista", TipoPreco.FIXO, 300L, true, 1, 1, true, true, List.of(insumoAveia.id()), null))
        );
        String grupoBody = mockMvc.perform(post("/api/v1/grupos-modificadores")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grupoReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        GrupoModificadorResponse grupoLeite = objectMapper.readValue(grupoBody, GrupoModificadorResponse.class);

        // 3. Cria Produto SIMPLES "Latte Vegano" e vincula ao grupo obrigatório "Leite Vegetal Exclusivo"
        ProdutoDetalheResponse latteVegano = criarProdutoHelper(tokenGerenteMatriz, cat.id(), "LATTE-VEG", "Latte Vegano", TipoProduto.SIMPLES, 1600L);
        VinculoGruposRequest vinculoReq = new VinculoGruposRequest(
                latteVegano.versao(),
                List.of(new VinculoGrupoItemRequest(grupoLeite.id(), 1, 1, 1))
        );
        mockMvc.perform(put("/api/v1/produtos/" + latteVegano.id() + "/grupos-modificadores")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vinculoReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(true));

        // 4. Cria Combo A e Combo B; tenta colocar Combo B dentro do slot do Combo A -> 422 COMBO_ANINHADO
        ProdutoDetalheResponse comboManha = criarProdutoHelper(tokenGerenteMatriz, cat.id(), "COMBO-MANHA", "Combo Manhã", TipoProduto.COMBO, 2400L);
        ProdutoDetalheResponse outroCombo = criarProdutoHelper(tokenGerenteMatriz, cat.id(), "COMBO-DUPLO", "Combo Duplo", TipoProduto.COMBO, 3900L);

        ComboSlotsRequest slotsAninhados = new ComboSlotsRequest(
                comboManha.versao(),
                List.of(new ComboSlotItemRequest(
                        null, "Escolha sua Bebida", 1, 1, true, 1,
                        List.of(new ComboSlotOpcaoRequest(outroCombo.id(), 0L, true, 1))
                ))
        );
        mockMvc.perform(put("/api/v1/produtos/" + comboManha.id() + "/combo-slots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slotsAninhados)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMBO_ANINHADO"));

        // 5. Vincula o produto SIMPLES "Latte Vegano" como única opção obrigatória do "Combo Manhã" -> 200 OK
        ComboSlotsRequest slotsValidos = new ComboSlotsRequest(
                comboManha.versao(),
                List.of(new ComboSlotItemRequest(
                        null, "Escolha sua Bebida", 1, 1, true, 1,
                        List.of(new ComboSlotOpcaoRequest(latteVegano.id(), 0L, true, 1))
                ))
        );
        mockMvc.perform(put("/api/v1/produtos/" + comboManha.id() + "/combo-slots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slotsValidos)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(true));

        // 6. Perfil COZINHA marca o insumo "Leite de Aveia" como ESGOTADO ->
        //    Deve indisponibilizar em cascata: modificador "Aveia Barista", produto "Latte Vegano" e o "Combo Manhã",
        //    além de disparar mensagem STOMP em /topic/lojas/{lojaId}/disponibilidade.
        InsumoDisponibilidadeRequest esgotarReq = new InsumoDisponibilidadeRequest(false, "Acabou o estoque no bar");
        mockMvc.perform(patch("/api/v1/catalogo/insumos/" + insumoAveia.id() + "/disponibilidade")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCozinhaMatriz)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(esgotarReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insumo.disponivel").value(false))
                .andExpect(jsonPath("$.modificadoresAfetados[0].disponivel").value(false))
                .andExpect(jsonPath("$.produtosAfetados.length()").value(2));

        ArgumentCaptor<CatalogoEventoStomp> stompCaptor = ArgumentCaptor.forClass(CatalogoEventoStomp.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/topic/lojas/" + lojaMatriz.getId() + "/disponibilidade"),
                stompCaptor.capture()
        );
        CatalogoEventoStomp ultimoEvento = stompCaptor.getValue();
        assertThat(ultimoEvento.tipo()).isEqualTo("DISPONIBILIDADE_ALTERADA");
        assertThat(ultimoEvento.disponibilidade()).isNotEmpty();
        assertThat(ultimoEvento.disponibilidade()).allMatch(item -> !item.disponivel());

        // 7. Valida ETag e 304 Not Modified em GET /api/v1/catalogo
        String etag = mockMvc.perform(get("/api/v1/catalogo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCozinhaMatriz))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/v1/catalogo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCozinhaMatriz)
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    private CategoriaResponse criarCategoriaHelper(String token, String nome, String slug) throws Exception {
        CategoriaRequest req = new CategoriaRequest(null, nome, slug, 1, true, EstacaoPreparo.BAR, null);
        String body = mockMvc.perform(post("/api/v1/categorias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, CategoriaResponse.class);
    }

    private ProdutoDetalheResponse criarProdutoHelper(
            String token,
            java.util.UUID categoriaId,
            String sku,
            String nome,
            TipoProduto tipo,
            long precoCentavos
    ) throws Exception {
        ProdutoRequest req = new ProdutoRequest(categoriaId, sku, nome, null, null, tipo, precoCentavos, EstacaoPreparo.BAR, true, true, 1, null);
        String body = mockMvc.perform(post("/api/v1/produtos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ProdutoDetalheResponse.class);
    }

    private String emitirToken(Usuario u) {
        AuthenticatedUser authUser = new AuthenticatedUser(
                u.getId(), u.getLojaId(), u.getNome(), u.getRole(), Set.copyOf(u.getRole().allGrantedAuthorities())
        );
        return jwtTokenService.generateAccessToken(authUser, Instant.now());
    }
}
