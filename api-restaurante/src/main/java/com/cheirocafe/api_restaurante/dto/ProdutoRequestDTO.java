package com.cheirocafe.api_restaurante.dto;

import com.cheirocafe.api_restaurante.entity.CategoriaProduto;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProdutoRequestDTO {

    @NotBlank(message = "O nome do produto é obrigatório")
    private String nome;

    private String descricao;

    @NotNull(message = "O preço do produto é obrigatório")
    @PositiveOrZero(message = "O preço do produto não pode ser negativo")
    @JsonAlias({"precoBase", "preco"})
    private BigDecimal preco;

    @NotNull(message = "A categoria do produto é obrigatória")
    private CategoriaProduto categoria;

    private String subcategoria;

    @Valid
    private List<VarianteDTO> variantes = new ArrayList<>();

    @Valid
    private List<AdicionalDTO> adicionais = new ArrayList<>();

    public ProdutoRequestDTO() {}

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }

    public CategoriaProduto getCategoria() { return categoria; }
    public void setCategoria(CategoriaProduto categoria) { this.categoria = categoria; }

    public String getSubcategoria() { return subcategoria; }
    public void setSubcategoria(String subcategoria) { this.subcategoria = subcategoria; }

    public List<VarianteDTO> getVariantes() { return variantes; }
    public void setVariantes(List<VarianteDTO> variantes) {
        this.variantes = variantes != null ? variantes : new ArrayList<>();
    }

    public List<AdicionalDTO> getAdicionais() { return adicionais; }
    public void setAdicionais(List<AdicionalDTO> adicionais) {
        this.adicionais = adicionais != null ? adicionais : new ArrayList<>();
    }
}
