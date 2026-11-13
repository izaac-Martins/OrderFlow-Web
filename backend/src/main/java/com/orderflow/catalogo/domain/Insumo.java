package com.orderflow.catalogo.domain;

import com.orderflow.shared.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Insumo operacional ({@code insumos}) cuja disponibilidade controla em cascata
 * produtos e opções de modificadores dependentes.
 */
@Entity
@Table(name = "insumos")
public class Insumo extends BaseAuditableEntity {

    @Column(name = "nome", nullable = false, length = 60)
    private String nome;

    @Column(name = "unidade", length = 10)
    private String unidadeMedida;

    @Column(name = "quantidade_atual", nullable = false)
    private long quantidadeAtual = 0L;

    @Column(name = "estoque_minimo", nullable = false)
    private long estoqueMinimo = 0L;

    @Column(name = "disponivel", nullable = false)
    private boolean disponivel = true;

    @Column(name = "motivo_indisponibilidade", length = 200)
    private String motivoIndisponibilidade;

    protected Insumo() {
    }

    public Insumo(
            UUID lojaId,
            String nome,
            String unidadeMedida,
            long quantidadeAtual,
            long estoqueMinimo,
            boolean disponivel
    ) {
        setLojaId(lojaId);
        this.nome = Objects.requireNonNull(nome, "nome é obrigatório").trim();
        this.unidadeMedida = unidadeMedida != null ? unidadeMedida.trim() : null;
        this.quantidadeAtual = quantidadeAtual;
        this.estoqueMinimo = estoqueMinimo;
        this.disponivel = disponivel;
    }

    public void alterarDisponibilidade(boolean disponivel, String motivo) {
        this.disponivel = disponivel;
        this.motivoIndisponibilidade = disponivel ? null : (motivo != null ? motivo.trim() : null);
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = Objects.requireNonNull(nome).trim();
    }

    public String getUnidadeMedida() {
        return unidadeMedida;
    }

    public void setUnidadeMedida(String unidadeMedida) {
        this.unidadeMedida = unidadeMedida != null ? unidadeMedida.trim() : null;
    }

    public long getQuantidadeAtual() {
        return quantidadeAtual;
    }

    public void setQuantidadeAtual(long quantidadeAtual) {
        this.quantidadeAtual = quantidadeAtual;
    }

    public long getEstoqueMinimo() {
        return estoqueMinimo;
    }

    public void setEstoqueMinimo(long estoqueMinimo) {
        this.estoqueMinimo = estoqueMinimo;
    }

    public boolean isDisponivel() {
        return disponivel;
    }

    public void setDisponivel(boolean disponivel) {
        this.disponivel = disponivel;
    }

    public String getMotivoIndisponibilidade() {
        return motivoIndisponibilidade;
    }

    public void setMotivoIndisponibilidade(String motivoIndisponibilidade) {
        this.motivoIndisponibilidade = motivoIndisponibilidade;
    }
}
