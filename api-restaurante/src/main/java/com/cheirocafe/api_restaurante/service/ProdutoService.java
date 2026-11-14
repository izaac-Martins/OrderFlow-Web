package com.cheirocafe.api_restaurante.service;

import com.cheirocafe.api_restaurante.dto.AdicionalDTO;
import com.cheirocafe.api_restaurante.dto.ProdutoRequestDTO;
import com.cheirocafe.api_restaurante.dto.ProdutoResponseDTO;
import com.cheirocafe.api_restaurante.dto.VarianteDTO;
import com.cheirocafe.api_restaurante.entity.Adicional;
import com.cheirocafe.api_restaurante.entity.Produto;
import com.cheirocafe.api_restaurante.entity.Variante;
import com.cheirocafe.api_restaurante.exception.RecursoNaoEncontradoException;
import com.cheirocafe.api_restaurante.repository.AdicionalRepository;
import com.cheirocafe.api_restaurante.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProdutoService {

    private static final String VINCULO_PRODUTO = "PRODUTO";
    private static final String PREFIXO_CATEGORIA = "CATEGORIA_";

    private final ProdutoRepository produtoRepository;
    private final AdicionalRepository adicionalRepository;

    public ProdutoService(ProdutoRepository produtoRepository, AdicionalRepository adicionalRepository) {
        this.produtoRepository = produtoRepository;
        this.adicionalRepository = adicionalRepository;
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponseDTO> listarTodos() {
        List<Produto> produtos = produtoRepository.findAll();
        List<Adicional> adicionaisGlobais = adicionalRepository.findByVinculoStartingWith(PREFIXO_CATEGORIA);

        List<ProdutoResponseDTO> resposta = new ArrayList<>(produtos.size());
        for (Produto produto : produtos) {
            resposta.add(converterParaResponseDTO(produto, adicionaisGlobais));
        }
        return resposta;
    }

    @Transactional(readOnly = true)
    public ProdutoResponseDTO buscarPorId(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com ID: " + id));
        List<Adicional> adicionaisGlobais = adicionalRepository.findByVinculoStartingWith(PREFIXO_CATEGORIA);
        return converterParaResponseDTO(produto, adicionaisGlobais);
    }

    @Transactional
    public ProdutoResponseDTO criar(ProdutoRequestDTO dto) {
        Produto produto = new Produto();
        aplicarDadosBasicosEFilhos(produto, dto);

        Produto salvo = produtoRepository.save(produto);
        salvarOuAtualizarAdicionaisGlobais(dto.getAdicionais());

        List<Adicional> adicionaisGlobais = adicionalRepository.findByVinculoStartingWith(PREFIXO_CATEGORIA);
        return converterParaResponseDTO(salvo, adicionaisGlobais);
    }

    @Transactional
    public ProdutoResponseDTO atualizar(Long id, ProdutoRequestDTO dto) {
        Produto produtoExistente = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado com ID: " + id));

        produtoExistente.limparVariantes();
        produtoExistente.limparAdicionaisEspecificos();
        aplicarDadosBasicosEFilhos(produtoExistente, dto);

        Produto salvo = produtoRepository.save(produtoExistente);
        salvarOuAtualizarAdicionaisGlobais(dto.getAdicionais());

        List<Adicional> adicionaisGlobais = adicionalRepository.findByVinculoStartingWith(PREFIXO_CATEGORIA);
        return converterParaResponseDTO(salvo, adicionaisGlobais);
    }

    @Transactional
    public void deletar(Long id) {
        if (!produtoRepository.existsById(id)) {
            throw new RecursoNaoEncontradoException("Produto não encontrado com ID: " + id);
        }
        produtoRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<AdicionalDTO> listarAdicionaisGlobais() {
        List<Adicional> globais = adicionalRepository.findByVinculoStartingWith(PREFIXO_CATEGORIA);
        List<AdicionalDTO> dtoList = new ArrayList<>(globais.size());
        for (Adicional a : globais) {
            dtoList.add(new AdicionalDTO(a.getId(), a.getNome(), escalarMoeda(a.getPreco()), a.getVinculo()));
        }
        return dtoList;
    }

    @Transactional
    public AdicionalDTO salvarAdicionalGlobal(AdicionalDTO dto) {
        String vinculo = normalizarVinculo(dto.getVinculo()).toUpperCase();
        if (!vinculo.startsWith(PREFIXO_CATEGORIA)) {
            vinculo = PREFIXO_CATEGORIA + vinculo;
        }
        String nomeLimpo = dto.getNome().trim();
        BigDecimal precoEscalado = escalarMoeda(dto.getPreco());
        final String vinculoFinal = vinculo;

        Adicional global = adicionalRepository
                .findFirstByNomeIgnoreCaseAndVinculoIgnoreCaseAndProdutoIsNull(nomeLimpo, vinculoFinal)
                .orElseGet(() -> new Adicional(nomeLimpo, precoEscalado, vinculoFinal));

        global.setNome(nomeLimpo);
        global.setPreco(precoEscalado);
        global.setVinculo(vinculoFinal);
        global.setProduto(null);

        Adicional salvo = adicionalRepository.save(global);
        return new AdicionalDTO(salvo.getId(), salvo.getNome(), escalarMoeda(salvo.getPreco()), salvo.getVinculo());
    }

    @Transactional
    public void deletarAdicionalGlobal(Long id) {
        if (!adicionalRepository.existsById(id)) {
            throw new RecursoNaoEncontradoException("Adicional global não encontrado com ID: " + id);
        }
        adicionalRepository.deleteById(id);
    }

    private void aplicarDadosBasicosEFilhos(Produto produto, ProdutoRequestDTO dto) {
        produto.setNome(dto.getNome().trim());
        produto.setDescricao(dto.getDescricao() != null ? dto.getDescricao().trim() : null);
        produto.setPreco(escalarMoeda(dto.getPreco()));
        produto.setCategoria(dto.getCategoria());
        produto.setSubcategoria(dto.getSubcategoria() != null ? dto.getSubcategoria().trim() : null);

        if (dto.getVariantes() != null) {
            for (VarianteDTO varDto : dto.getVariantes()) {
                Variante variante = new Variante(varDto.getNome().trim(), escalarMoeda(varDto.getPreco()));
                produto.adicionarVariante(variante);
            }
        }

        if (dto.getAdicionais() != null) {
            for (AdicionalDTO adDto : dto.getAdicionais()) {
                String vinculo = normalizarVinculo(adDto.getVinculo());
                if (VINCULO_PRODUTO.equalsIgnoreCase(vinculo)) {
                    Adicional adicional = new Adicional(
                            adDto.getNome().trim(),
                            escalarMoeda(adDto.getPreco()),
                            VINCULO_PRODUTO
                    );
                    produto.adicionarAdicionalEspecifico(adicional);
                }
            }
        }
    }

    private void salvarOuAtualizarAdicionaisGlobais(List<AdicionalDTO> adicionaisDto) {
        if (adicionaisDto == null || adicionaisDto.isEmpty()) {
            return;
        }

        for (AdicionalDTO adDto : adicionaisDto) {
            String vinculo = normalizarVinculo(adDto.getVinculo());
            if (!VINCULO_PRODUTO.equalsIgnoreCase(vinculo)) {
                String nomeLimpo = adDto.getNome().trim();
                String vinculoPadronizado = vinculo.toUpperCase();
                BigDecimal precoEscalado = escalarMoeda(adDto.getPreco());

                Adicional global = adicionalRepository
                        .findFirstByNomeIgnoreCaseAndVinculoIgnoreCaseAndProdutoIsNull(nomeLimpo, vinculoPadronizado)
                        .orElseGet(() -> new Adicional(nomeLimpo, precoEscalado, vinculoPadronizado));

                global.setPreco(precoEscalado);
                global.setProduto(null);
                adicionalRepository.save(global);
            }
        }
    }

    private BigDecimal escalarMoeda(BigDecimal valor) {
        return valor != null
                ? valor.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizarVinculo(String vinculo) {
        return (vinculo == null || vinculo.isBlank()) ? VINCULO_PRODUTO : vinculo.trim();
    }

    private ProdutoResponseDTO converterParaResponseDTO(Produto produto, List<Adicional> todosAdicionaisGlobais) {
        List<VarianteDTO> variantesDto = new ArrayList<>();
        if (produto.getVariantes() != null) {
            for (Variante v : produto.getVariantes()) {
                variantesDto.add(new VarianteDTO(v.getId(), v.getNome(), escalarMoeda(v.getPreco())));
            }
        }

        List<AdicionalDTO> adicionaisUnificadosDto = new ArrayList<>();
        if (produto.getAdicionais() != null) {
            for (Adicional a : produto.getAdicionais()) {
                adicionaisUnificadosDto.add(new AdicionalDTO(a.getId(), a.getNome(), escalarMoeda(a.getPreco()), a.getVinculo()));
            }
        }

        if (produto.getCategoria() != null && todosAdicionaisGlobais != null) {
            String tagEsperada = PREFIXO_CATEGORIA + produto.getCategoria().name();
            for (Adicional global : todosAdicionaisGlobais) {
                if (global.getVinculo() != null && global.getVinculo().equalsIgnoreCase(tagEsperada)) {
                    adicionaisUnificadosDto.add(
                            new AdicionalDTO(global.getId(), global.getNome(), escalarMoeda(global.getPreco()), global.getVinculo())
                    );
                }
            }
        }

        return new ProdutoResponseDTO(
                produto.getId(),
                produto.getNome(),
                produto.getDescricao(),
                escalarMoeda(produto.getPreco()),
                produto.getCategoria(),
                produto.getSubcategoria(),
                variantesDto,
                adicionaisUnificadosDto
        );
    }
}
