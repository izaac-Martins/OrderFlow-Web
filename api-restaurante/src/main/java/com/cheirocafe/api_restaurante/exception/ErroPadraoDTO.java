package com.cheirocafe.api_restaurante.exception;

import java.time.Instant;
import java.util.Map;

public class ErroPadraoDTO {

    private Instant timestamp;
    private int status;
    private String erro;
    private String mensagem;
    private String caminho;
    private Map<String, String> errosValidacao;

    public ErroPadraoDTO() {}

    public ErroPadraoDTO(
            Instant timestamp,
            int status,
            String erro,
            String mensagem,
            String caminho,
            Map<String, String> errosValidacao
    ) {
        this.timestamp = timestamp;
        this.status = status;
        this.erro = erro;
        this.mensagem = mensagem;
        this.caminho = caminho;
        this.errosValidacao = errosValidacao;
    }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getErro() { return erro; }
    public void setErro(String erro) { this.erro = erro; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public String getCaminho() { return caminho; }
    public void setCaminho(String caminho) { this.caminho = caminho; }

    public Map<String, String> getErrosValidacao() { return errosValidacao; }
    public void setErrosValidacao(Map<String, String> errosValidacao) { this.errosValidacao = errosValidacao; }
}
