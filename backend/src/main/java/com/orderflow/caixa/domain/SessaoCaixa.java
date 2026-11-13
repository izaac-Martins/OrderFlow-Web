package com.orderflow.caixa.domain;

import com.orderflow.shared.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sessão de Caixa ({@code sessoes_caixa}).
 * <ul>
 *   <li>Não armazena saldo dinâmico em coluna durante a operação (sempre calculado pelas movimentações).</li>
 *   <li>Snapshots de esperado/declarado/diferença só são preenchidos uma única vez no fechamento cego.</li>
 *   <li>Restrição estrita de 1 sessão ABERTA por terminal de caixa e por operador.</li>
 * </ul>
 */
@Entity
@Table(name = "sessoes_caixa")
public class SessaoCaixa extends BaseAuditableEntity {

    @Column(name = "caixa_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID caixaId;

    @Column(name = "operador_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID operadorId;

    @Column(name = "protocolo", length = 40)
    private String protocolo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private StatusSessaoCaixa status = StatusSessaoCaixa.ABERTA;

    @Column(name = "fundo_troco_centavos", nullable = false, updatable = false)
    private long fundoTrocoCentavos = 0L;

    @Column(name = "aberta_em", nullable = false, updatable = false)
    private Instant dataAbertura;

    @Column(name = "fechada_em")
    private Instant dataFechamento;

    @Column(name = "conferida_em")
    private Instant conferidaEm;

    @Column(name = "conferida_por", columnDefinition = "BINARY(16)")
    private UUID conferidaPor;

    @Column(name = "esperado_dinheiro_centavos")
    private Long esperadoDinheiroCentavos;

    @Column(name = "declarado_dinheiro_centavos")
    private Long declaradoDinheiroCentavos;

    @Column(name = "diferenca_dinheiro_centavos")
    private Long diferencaDinheiroCentavos;

    @Column(name = "observacao_operador", length = 300)
    private String observacaoOperador;

    @Column(name = "observacao_conferencia", length = 500)
    private String observacaoConferencia;

    protected SessaoCaixa() {
    }

    public SessaoCaixa(
            UUID lojaId,
            UUID caixaId,
            UUID operadorId,
            long fundoTrocoCentavos,
            Instant dataAbertura
    ) {
        setLojaId(lojaId);
        this.caixaId = Objects.requireNonNull(caixaId, "caixaId é obrigatório");
        this.operadorId = Objects.requireNonNull(operadorId, "operadorId é obrigatório");
        this.fundoTrocoCentavos = Math.max(0L, fundoTrocoCentavos);
        this.dataAbertura = dataAbertura != null ? dataAbertura : Instant.now();
        this.status = StatusSessaoCaixa.ABERTA;
    }

    public boolean isAberta() {
        return this.status == StatusSessaoCaixa.ABERTA;
    }

    public void registrarFechamentoCego(
            String protocolo,
            long esperadoDinheiroCentavos,
            long declaradoDinheiroCentavos,
            String observacaoOperador,
            Instant instanteFechamento
    ) {
        this.protocolo = Objects.requireNonNull(protocolo);
        this.esperadoDinheiroCentavos = esperadoDinheiroCentavos;
        this.declaradoDinheiroCentavos = declaradoDinheiroCentavos;
        this.diferencaDinheiroCentavos = declaradoDinheiroCentavos - esperadoDinheiroCentavos;
        this.observacaoOperador = observacaoOperador != null ? observacaoOperador.trim() : null;
        this.dataFechamento = instanteFechamento != null ? instanteFechamento : Instant.now();
        this.status = StatusSessaoCaixa.FECHADA;
    }

    public void registrarConferencia(UUID gerenteId, String observacao, Instant instante) {
        this.conferidaPor = Objects.requireNonNull(gerenteId);
        this.observacaoConferencia = observacao != null ? observacao.trim() : null;
        this.conferidaEm = instante != null ? instante : Instant.now();
        this.status = StatusSessaoCaixa.CONFERIDA;
    }

    public UUID getCaixaId() {
        return caixaId;
    }

    public UUID getOperadorId() {
        return operadorId;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public StatusSessaoCaixa getStatus() {
        return status;
    }

    public long getFundoTrocoCentavos() {
        return fundoTrocoCentavos;
    }

    public Instant getDataAbertura() {
        return dataAbertura;
    }

    public Instant getDataFechamento() {
        return dataFechamento;
    }

    public Instant getConferidaEm() {
        return conferidaEm;
    }

    public UUID getConferidaPor() {
        return conferidaPor;
    }

    public Long getEsperadoDinheiroCentavos() {
        return esperadoDinheiroCentavos;
    }

    public Long getDeclaradoDinheiroCentavos() {
        return declaradoDinheiroCentavos;
    }

    public Long getDiferencaDinheiroCentavos() {
        return diferencaDinheiroCentavos;
    }

    public String getObservacaoOperador() {
        return observacaoOperador;
    }

    public String getObservacaoConferencia() {
        return observacaoConferencia;
    }
}
