package com.cheirocafe.api_restaurante.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "produtos")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategoriaProduto categoria;

    @Column(length = 50)
    private String subcategoria;

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @BatchSize(size = 50)
    private List<Variante> variantes = new ArrayList<>();

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @BatchSize(size = 50)
    private List<Adicional> adicionais = new ArrayList<>();

    public Produto() {}

    public void adicionarVariante(Variante variante) {
        variante.setProduto(this);
        this.variantes.add(variante);
    }

    public void limparVariantes() {
        for (Variante v : this.variantes) {
            v.setProduto(null);
        }
        this.variantes.clear();
    }

    public void adicionarAdicionalEspecifico(Adicional adicional) {
        adicional.setProduto(this);
        this.adicionais.add(adicional);
    }

    public void limparAdicionaisEspecificos() {
        for (Adicional a : this.adicionais) {
            a.setProduto(null);
        }
        this.adicionais.clear();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) {
        this.preco = preco != null ? preco.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public CategoriaProduto getCategoria() { return categoria; }
    public void setCategoria(CategoriaProduto categoria) { this.categoria = categoria; }

    public String getSubcategoria() { return subcategoria; }
    public void setSubcategoria(String subcategoria) { this.subcategoria = subcategoria; }

    public List<Variante> getVariantes() { return variantes; }
    public void setVariantes(List<Variante> variantes) { this.variantes = variantes; }

    public List<Adicional> getAdicionais() { return adicionais; }
    public void setAdicionais(List<Adicional> adicionais) { this.adicionais = adicionais; }
}
