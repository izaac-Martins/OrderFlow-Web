package com.orderflow.caixa.web;

import com.orderflow.caixa.dto.CaixaDtos.AbrirSessaoRequest;
import com.orderflow.caixa.dto.CaixaDtos.CaixaResponse;
import com.orderflow.caixa.dto.CaixaDtos.ConferenciaResponse;
import com.orderflow.caixa.dto.CaixaDtos.ConferirSessaoRequest;
import com.orderflow.caixa.dto.CaixaDtos.CriarTerminalCaixaRequest;
import com.orderflow.caixa.dto.CaixaDtos.EstornoRequest;
import com.orderflow.caixa.dto.CaixaDtos.FechamentoCegoRequest;
import com.orderflow.caixa.dto.CaixaDtos.FechamentoProtocoloResponse;
import com.orderflow.caixa.dto.CaixaDtos.MovimentacaoCaixaRequest;
import com.orderflow.caixa.dto.CaixaDtos.MovimentacaoGerencialResponse;
import com.orderflow.caixa.dto.CaixaDtos.MovimentacaoOperadorResponse;
import com.orderflow.caixa.dto.CaixaDtos.SaldoSessaoResponse;
import com.orderflow.caixa.dto.CaixaDtos.SessaoCaixaGerencialResponse;
import com.orderflow.caixa.dto.CaixaDtos.SessaoCaixaOperadorResponse;
import com.orderflow.caixa.service.CaixaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controlador REST do Módulo de Caixa Rigoroso (`/api/v1/caixas` e `/api/v1/caixa`).
 */
@RestController
@RequestMapping("/api/v1")
public class CaixaController {

    private final CaixaService caixaService;

    public CaixaController(CaixaService caixaService) {
        this.caixaService = caixaService;
    }

    // ─────────────────────────────── TERMINAIS DE CAIXA ───────────────────────────────

    @GetMapping("/caixas")
    public ResponseEntity<List<CaixaResponse>> listarTerminais() {
        return ResponseEntity.ok(caixaService.listarTerminais());
    }

    @PostMapping("/caixas")
    public ResponseEntity<CaixaResponse> criarTerminal(@Valid @RequestBody CriarTerminalCaixaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caixaService.criarTerminal(request));
    }

    // ─────────────────────────────── ABERTURA & CONSULTA DE SESSÃO ───────────────────────────────

    @PostMapping({"/caixa/sessoes", "/caixa/abertura"})
    public ResponseEntity<SessaoCaixaOperadorResponse> abrirSessao(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody AbrirSessaoRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(caixaService.abrirSessao(request, idempotencyKey));
    }

    @GetMapping("/caixa/sessoes/atual")
    public ResponseEntity<SessaoCaixaOperadorResponse> obterSessaoAtual(@RequestParam("caixaId") UUID caixaId) {
        return ResponseEntity.ok(caixaService.obterSessaoAbertaPorCaixa(caixaId));
    }

    @GetMapping("/caixa/sessoes")
    public ResponseEntity<List<SessaoCaixaGerencialResponse>> listarSessoesGerenciais() {
        return ResponseEntity.ok(caixaService.listarSessoesGerenciais());
    }

    // ─────────────────────────────── SANGRIA, SUPRIMENTO E MOVIMENTAÇÕES ───────────────────────────────

    @PostMapping("/caixa/sessoes/{id}/sangrias")
    public ResponseEntity<MovimentacaoOperadorResponse> registrarSangria(
            @PathVariable("id") UUID sessaoId,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody MovimentacaoCaixaRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(caixaService.registrarSangria(sessaoId, request, idempotencyKey));
    }

    @PostMapping("/caixa/sessoes/{id}/suprimentos")
    public ResponseEntity<MovimentacaoOperadorResponse> registrarSuprimento(
            @PathVariable("id") UUID sessaoId,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody MovimentacaoCaixaRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(caixaService.registrarSuprimento(sessaoId, request, idempotencyKey));
    }

    @PostMapping("/caixa/movimentacoes")
    public ResponseEntity<MovimentacaoOperadorResponse> registrarMovimentacaoManual(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody MovimentacaoCaixaRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(caixaService.registrarMovimentacaoManual(null, request, idempotencyKey));
    }

    @PostMapping("/caixa/movimentacoes/{id}/estorno")
    public ResponseEntity<MovimentacaoGerencialResponse> estornarMovimentacao(
            @PathVariable("id") UUID movimentacaoId,
            @Valid @RequestBody EstornoRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(caixaService.estornarMovimentacao(movimentacaoId, request));
    }

    @GetMapping("/caixa/sessoes/{id}/movimentacoes")
    public ResponseEntity<List<MovimentacaoGerencialResponse>> listarMovimentacoesSessao(
            @PathVariable("id") UUID sessaoId
    ) {
        return ResponseEntity.ok(caixaService.listarMovimentacoesSessao(sessaoId));
    }

    // ─────────────────────────────── FECHAMENTO CEGO & CONFERÊNCIA GERENCIAL ───────────────────────────────

    @PostMapping("/caixa/sessoes/{id}/fechamento")
    public ResponseEntity<FechamentoProtocoloResponse> fecharSessaoCegoPorId(
            @PathVariable("id") UUID sessaoId,
            @Valid @RequestBody FechamentoCegoRequest request
    ) {
        return ResponseEntity.ok(caixaService.fecharSessaoCego(sessaoId, request));
    }

    @PostMapping("/caixa/fechamento")
    public ResponseEntity<FechamentoProtocoloResponse> fecharSessaoCego(
            @Valid @RequestBody FechamentoCegoRequest request
    ) {
        return ResponseEntity.ok(caixaService.fecharSessaoCego(null, request));
    }

    @GetMapping("/caixa/sessoes/{id}/conferencia")
    public ResponseEntity<ConferenciaResponse> obterConferencia(@PathVariable("id") UUID sessaoId) {
        return ResponseEntity.ok(caixaService.obterConferencia(sessaoId));
    }

    @PostMapping("/caixa/sessoes/{id}/conferir")
    public ResponseEntity<ConferenciaResponse> conferirSessao(
            @PathVariable("id") UUID sessaoId,
            @Valid @RequestBody ConferirSessaoRequest request
    ) {
        return ResponseEntity.ok(caixaService.conferirSessao(sessaoId, request));
    }

    @GetMapping("/caixa/sessoes/{id}/saldo")
    public ResponseEntity<SaldoSessaoResponse> obterSaldoSessaoGerencial(@PathVariable("id") UUID sessaoId) {
        return ResponseEntity.ok(caixaService.obterSaldoSessaoGerencial(sessaoId));
    }
}
