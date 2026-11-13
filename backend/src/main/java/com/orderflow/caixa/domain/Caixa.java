package com.orderflow.caixa.domain;

import com.orderflow.shared.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Terminal físico de caixa ({@code caixas}) acoplado opcionalmente a impressora térmica ESC/POS + gaveta RJ11.
 */
@Entity
@Table(name = "caixas")
public class Caixa extends BaseAuditableEntity {

    @Column(name = "nome", nullable = false, length = 60)
    private String nome;

    @Column(name = "impressora_ip", length = 45)
    private String impressoraIp;

    @Column(name = "impressora_porta", nullable = false)
    private int impressoraPorta = 9100;

    @Column(name = "possui_gaveta", nullable = false)
    private boolean possuiGaveta = true;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    protected Caixa() {
    }

    public Caixa(UUID lojaId, String nome, String impressoraIp, int impressoraPorta, boolean possuiGaveta, boolean ativo) {
        setLojaId(lojaId);
        this.nome = Objects.requireNonNull(nome, "nome é obrigatório").trim();
        this.impressoraIp = impressoraIp;
        this.impressoraPorta = impressoraPorta > 0 ? impressoraPorta : 9100;
        this.possuiGaveta = possuiGaveta;
        this.ativo = ativo;
    }

    public String getNome() {
        return nome;
    }

    public String getImpressoraIp() {
        return impressoraIp;
    }

    public int getImpressoraPorta() {
        return impressoraPorta;
    }

    public boolean isPossuiGaveta() {
        return possuiGaveta;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
