package com.orderflow.catalogo.domain;

import com.orderflow.shared.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Grupo reutilizável de modificadores/adicionais ({@code grupos_modificadores}).
 */
@Entity
@Table(name = "grupos_modificadores")
public class GrupoModificador extends BaseAuditableEntity {

    @Column(name = "nome", nullable = false, length = 60)
    private String nome;

    @Column(name = "nome_interno", length = 60)
    private String nomeInterno;

    @Column(name = "min_selecao", nullable = false)
    private int minSelecao = 0;

    @Column(name = "max_selecao", nullable = false)
    private int maxSelecao = 1;

    @Column(name = "permite_repeticao", nullable = false)
    private boolean permiteRepeticao = false;

    @Column(name = "ordem", nullable = false)
    private int ordem = 0;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "arquivado_em")
    private Instant arquivadoEm;

    protected GrupoModificador() {
    }

    public GrupoModificador(
            UUID lojaId,
            String nome,
            String nomeInterno,
            int minSelecao,
            int maxSelecao,
            boolean permiteRepeticao,
            int ordem,
            boolean ativo
    ) {
        setLojaId(lojaId);
        this.nome = Objects.requireNonNull(nome, "nome é obrigatório").trim();
        this.nomeInterno = nomeInterno != null ? nomeInterno.trim() : null;
        this.minSelecao = minSelecao;
        this.maxSelecao = maxSelecao;
        this.permiteRepeticao = permiteRepeticao;
        this.ordem = Math.max(0, ordem);
        this.ativo = ativo;
    }

    public boolean isObrigatorio() {
        return this.minSelecao >= 1;
    }

    public boolean isArquivado() {
        return this.arquivadoEm != null;
    }

    public void arquivar(Instant instante) {
        this.arquivadoEm = instante != null ? instante : Instant.now();
        this.ativo = false;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = Objects.requireNonNull(nome).trim();
    }

    public String getNomeInterno() {
        return nomeInterno;
    }

    public void setNomeInterno(String nomeInterno) {
        this.nomeInterno = nomeInterno != null ? nomeInterno.trim() : null;
    }

    public int getMinSelecao() {
        return minSelecao;
    }

    public void setMinSelecao(int minSelecao) {
        this.minSelecao = minSelecao;
    }

    public int getMaxSelecao() {
        return maxSelecao;
    }

    public void setMaxSelecao(int maxSelecao) {
        this.maxSelecao = maxSelecao;
    }

    public boolean isPermiteRepeticao() {
        return permiteRepeticao;
    }

    public void setPermiteRepeticao(boolean permiteRepeticao) {
        this.permiteRepeticao = permiteRepeticao;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = Math.max(0, ordem);
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Instant getArquivadoEm() {
        return arquivadoEm;
    }
}
