package com.cheirocafe.api_restaurante.dto;

import com.cheirocafe.api_restaurante.entity.CategoriaProduto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProdutoResponseDTO {

    private Long id;
    private String nome;
    private String descricao;
    private BigDecimal preco;
    private CategoriaProduto categoria;
    private String subcategoria;
    private List<VarianteDTO> variantes = new ArrayList<>();
    private List<AdicionalDTO> adicionais = new ArrayList<>();

    public ProdutoResponseDTO() {}

    public ProdutoResponseDTO(
            Long id,
            String nome,
            String descricao,
            BigDecimal preco,
            CategoriaProduto categoria,
            String subcategoria,
            List<VarianteDTO> variantes,
            List<AdicionalDTO> adicionais
    ) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.categoria = categoria;
        this.subcategoria = subcategoria;
        this.variantes = variantes != null ? variantes : new ArrayList<>();
        this.adicionais = adicionais != null ? adicionais : new ArrayList<>();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }

    public BigDecimal getPrecoBase() { return preco; }
    public Boolean getAtivo() { return true; }

    public CategoriaProduto getCategoria() { return categoria; }
    public void setCategoria(CategoriaProduto categoria) { this.categoria = categoria; }

    public String getSubcategoria() { return subcategoria; }
    public void setSubcategoria(String subcategoria) { this.subcategoria = subcategoria; }

    public List<VarianteDTO> getVariantes() { return variantes; }
    public void setVariantes(List<VarianteDTO> variantes) { this.variantes = variantes; }

    public List<AdicionalDTO> getAdicionais() { return adicionais; }
    public void setAdicionais(List<AdicionalDTO> adicionais) { this.adicionais = adicionais; }
}
