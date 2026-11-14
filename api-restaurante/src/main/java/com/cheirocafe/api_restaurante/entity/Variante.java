package com.cheirocafe.api_restaurante.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "variantes")
public class Variante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    public Variante() {}

    public Variante(String nome, BigDecimal preco) {
        this.nome = nome;
        this.preco = preco != null ? preco.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) {
        this.preco = preco != null ? preco.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }

    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }
}
