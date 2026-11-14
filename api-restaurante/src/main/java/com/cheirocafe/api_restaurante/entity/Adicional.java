package com.cheirocafe.api_restaurante.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "adicionais")
public class Adicional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false, length = 50)
    private String vinculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = true)
    private Produto produto;

    public Adicional() {}

    public Adicional(String nome, BigDecimal preco, String vinculo) {
        this.nome = nome;
        this.preco = preco != null ? preco.setScale(2, java.math.RoundingMode.HALF_UP) : null;
        this.vinculo = vinculo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) {
        this.preco = preco != null ? preco.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }

    public String getVinculo() { return vinculo; }
    public void setVinculo(String vinculo) { this.vinculo = vinculo; }

    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }
}
