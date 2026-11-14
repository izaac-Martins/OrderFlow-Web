package com.cheirocafe.api_restaurante.dto;

import com.cheirocafe.api_restaurante.entity.StatusPedido;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PedidoResponseDTO {

    private Long id;
    private Long versao;
    private Integer numeroMesa;
    private StatusPedido status;
    private BigDecimal valorTotal;
    private Instant criadoEm;
    private List<ItemPedidoDTO> itens = new ArrayList<>();

    public PedidoResponseDTO() {}

    public PedidoResponseDTO(
            Long id,
            Integer numeroMesa,
            StatusPedido status,
            BigDecimal valorTotal,
            Instant criadoEm,
            List<ItemPedidoDTO> itens
    ) {
        this(id, 0L, numeroMesa, status, valorTotal, criadoEm, itens);
    }

    public PedidoResponseDTO(
            Long id,
            Long versao,
            Integer numeroMesa,
            StatusPedido status,
            BigDecimal valorTotal,
            Instant criadoEm,
            List<ItemPedidoDTO> itens
    ) {
        this.id = id;
        this.versao = versao;
        this.numeroMesa = numeroMesa;
        this.status = status;
        this.valorTotal = valorTotal != null ? valorTotal.setScale(2, RoundingMode.HALF_UP) : null;
        this.criadoEm = criadoEm;
        this.itens = itens != null ? itens : new ArrayList<>();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersao() { return versao; }
    public void setVersao(Long versao) { this.versao = versao; }

    public Integer getNumeroMesa() { return numeroMesa; }
    public void setNumeroMesa(Integer numeroMesa) { this.numeroMesa = numeroMesa; }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal != null ? valorTotal.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
    public Instant getDataHora() { return criadoEm; }

    public List<ItemPedidoDTO> getItens() { return itens; }
    public void setItens(List<ItemPedidoDTO> itens) { this.itens = itens; }
}
