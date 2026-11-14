package com.cheirocafe.api_restaurante.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public class AdicionalDTO {

    private Long id;

    @NotBlank(message = "O nome do adicional é obrigatório")
    private String nome;

    @NotNull(message = "O preço do adicional é obrigatório")
    @PositiveOrZero(message = "O preço do adicional não pode ser negativo")
    private BigDecimal preco;

    @JsonAlias({"escopo", "vinculo"})
    private String vinculo = "PRODUTO";

    public AdicionalDTO() {}

    public AdicionalDTO(Long id, String nome, BigDecimal preco, String vinculo) {
        this.id = id;
        this.nome = nome;
        this.preco = preco;
        this.vinculo = (vinculo == null || vinculo.isBlank()) ? "PRODUTO" : vinculo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }

    public String getVinculo() { return vinculo; }
    public String getEscopo() { return vinculo; }
    public void setVinculo(String vinculo) {
        this.vinculo = (vinculo == null || vinculo.isBlank()) ? "PRODUTO" : vinculo;
    }
}
