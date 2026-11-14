package com.cheirocafe.api_restaurante.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public class VarianteDTO {

    private Long id;

    @NotBlank(message = "O nome da variante é obrigatório")
    @JsonAlias({"tamanho", "nome"})
    private String nome;

    @NotNull(message = "O preço da variante é obrigatório")
    @PositiveOrZero(message = "O preço da variante não pode ser negativo")
    private BigDecimal preco;

    public VarianteDTO() {}

    public VarianteDTO(Long id, String nome, BigDecimal preco) {
        this.id = id;
        this.nome = nome;
        this.preco = preco;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTamanho() { return nome; }

    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }
}
