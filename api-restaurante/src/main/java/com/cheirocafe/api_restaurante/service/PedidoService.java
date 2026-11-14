package com.cheirocafe.api_restaurante.service;

import com.cheirocafe.api_restaurante.dto.AtualizarStatusPedidoDTO;
import com.cheirocafe.api_restaurante.dto.ItemPedidoDTO;
import com.cheirocafe.api_restaurante.dto.PedidoRequestDTO;
import com.cheirocafe.api_restaurante.dto.PedidoResponseDTO;
import com.cheirocafe.api_restaurante.entity.ItemPedido;
import com.cheirocafe.api_restaurante.entity.Pedido;
import com.cheirocafe.api_restaurante.entity.StatusPedido;
import com.cheirocafe.api_restaurante.exception.RecursoNaoEncontradoException;
import com.cheirocafe.api_restaurante.exception.TransicaoStatusInvalidaException;
import com.cheirocafe.api_restaurante.repository.PedidoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;

    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos() {
        List<Pedido> pedidos = pedidoRepository.findAll(Sort.by(Sort.Direction.DESC, "criadoEm", "id"));
        List<PedidoResponseDTO> resposta = new ArrayList<>(pedidos.size());
        for (Pedido pedido : pedidos) {
            resposta.add(converterParaResponseDTO(pedido));
        }
        return resposta;
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPorId(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado com ID: " + id));
        return converterParaResponseDTO(pedido);
    }

    @Transactional
    public PedidoResponseDTO criar(PedidoRequestDTO dto) {
        Pedido pedido = new Pedido();
        pedido.setNumeroMesa(dto.getNumeroMesa());
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setCriadoEm(Instant.now());

        BigDecimal somaCalculada = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (ItemPedidoDTO itemDto : dto.getItens()) {
            ItemPedido item = new ItemPedido();
            item.setProdutoId(itemDto.getProdutoId());
            item.setNomeProduto(itemDto.getNomeProduto().trim());
            item.setTamanhoEscolhido(itemDto.getTamanhoEscolhido());
            item.setTipoGraoEscolhido(itemDto.getTipoGraoEscolhido());
            item.setAdicionaisEscolhidos(itemDto.getAdicionaisEscolhidos());
            item.setQuantidade(itemDto.getQuantidade());

            BigDecimal precoItemEscala = itemDto.getPrecoTotalItem().setScale(2, RoundingMode.HALF_UP);
            item.setPrecoTotalItem(precoItemEscala);
            item.setPagadorItem(
                    (itemDto.getPagadorItem() != null && !itemDto.getPagadorItem().isBlank())
                            ? itemDto.getPagadorItem().trim()
                            : "Mesa Total"
            );

            somaCalculada = somaCalculada.add(precoItemEscala).setScale(2, RoundingMode.HALF_UP);
            pedido.adicionarItem(item);
        }

        pedido.setValorTotal(somaCalculada);

        Pedido salvo = pedidoRepository.save(pedido);
        return converterParaResponseDTO(salvo);
    }

    @Transactional
    public PedidoResponseDTO atualizarStatus(Long id, AtualizarStatusPedidoDTO dto) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado com ID: " + id));

        StatusPedido statusAtual = pedido.getStatus();
        StatusPedido novoStatus = dto.getStatus();

        if (!statusAtual.podeTransicionarPara(novoStatus)) {
            throw new TransicaoStatusInvalidaException(
                    String.format(
                            "Transição de status inválida para o Pedido #%d: não é permitido mudar de %s para %s.",
                            id,
                            statusAtual,
                            novoStatus
                    )
            );
        }

        pedido.setStatus(novoStatus);
        Pedido salvo = pedidoRepository.saveAndFlush(pedido);
        return converterParaResponseDTO(salvo);
    }

    private PedidoResponseDTO converterParaResponseDTO(Pedido pedido) {
        List<ItemPedidoDTO> itensDto = new ArrayList<>();
        if (pedido.getItens() != null) {
            for (ItemPedido item : pedido.getItens()) {
                BigDecimal precoItem = item.getPrecoTotalItem() != null
                        ? item.getPrecoTotalItem().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                itensDto.add(new ItemPedidoDTO(
                        item.getId(),
                        item.getProdutoId(),
                        item.getNomeProduto(),
                        item.getTamanhoEscolhido(),
                        item.getTipoGraoEscolhido(),
                        item.getAdicionaisEscolhidos(),
                        item.getQuantidade(),
                        precoItem,
                        item.getPagadorItem()
                ));
            }
        }

        BigDecimal totalPedido = pedido.getValorTotal() != null
                ? pedido.getValorTotal().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new PedidoResponseDTO(
                pedido.getId(),
                pedido.getVersao(),
                pedido.getNumeroMesa(),
                pedido.getStatus(),
                totalPedido,
                pedido.getCriadoEm(),
                itensDto
        );
    }
}
