package com.cheirocafe.api_restaurante.controller;

import com.cheirocafe.api_restaurante.dto.AdicionalDTO;
import com.cheirocafe.api_restaurante.dto.ProdutoRequestDTO;
import com.cheirocafe.api_restaurante.dto.ProdutoResponseDTO;
import com.cheirocafe.api_restaurante.service.ProdutoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/produtos", "/api/v1/produtos"})
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(produtoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> criar(@Valid @RequestBody ProdutoRequestDTO dto) {
        ProdutoResponseDTO criado = produtoService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProdutoRequestDTO dto
    ) {
        ProdutoResponseDTO atualizado = produtoService.atualizar(id, dto);
        return ResponseEntity.ok(atualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        produtoService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/adicionais-globais")
    public ResponseEntity<List<AdicionalDTO>> listarAdicionaisGlobais() {
        return ResponseEntity.ok(produtoService.listarAdicionaisGlobais());
    }

    @PostMapping("/adicionais-globais")
    public ResponseEntity<AdicionalDTO> salvarAdicionalGlobal(@Valid @RequestBody AdicionalDTO dto) {
        AdicionalDTO salvo = produtoService.salvarAdicionalGlobal(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @DeleteMapping("/adicionais-globais/{id}")
    public ResponseEntity<Void> deletarAdicionalGlobal(@PathVariable Long id) {
        produtoService.deletarAdicionalGlobal(id);
        return ResponseEntity.noContent().build();
    }
}
