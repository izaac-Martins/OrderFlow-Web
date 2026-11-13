package com.orderflow.auth.domain;

import com.orderflow.shared.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade raiz de tenant ({@code lojas}) do Ecossistema OrderFlow.
 */
@Entity
@Table(name = "lojas")
public class Loja {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "cnpj", length = 18)
    private String cnpj;

    @Column(name = "fuso_horario", nullable = false, length = 50)
    private String fusoHorario = "America/Sao_Paulo";

    @Column(name = "limite_sangria_sem_aprovacao_centavos", nullable = false)
    private Long limiteSangriaSemAprovacaoCentavos = 50_000L;

    @Column(name = "confirmar_pre_pago_automatico", nullable = false)
    private boolean confirmarPrePagoAutomatico = true;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Version
    @Column(name = "versao", nullable = false)
    private Integer versao = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Loja() {
    }

    public Loja(UUID id, String codigo, String nome, String cnpj) {
        this.id = id != null ? id : UuidV7Generator.nextId();
        this.codigo = Objects.requireNonNull(codigo, "codigo é obrigatório").trim();
        this.nome = Objects.requireNonNull(nome, "nome é obrigatório").trim();
        this.cnpj = cnpj;
    }

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UuidV7Generator.nextId();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getFusoHorario() {
        return fusoHorario;
    }

    public Long getLimiteSangriaSemAprovacaoCentavos() {
        return limiteSangriaSemAprovacaoCentavos;
    }

    public void setLimiteSangriaSemAprovacaoCentavos(Long limiteSangriaSemAprovacaoCentavos) {
        this.limiteSangriaSemAprovacaoCentavos = limiteSangriaSemAprovacaoCentavos;
    }

    public boolean isConfirmarPrePagoAutomatico() {
        return confirmarPrePagoAutomatico;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
