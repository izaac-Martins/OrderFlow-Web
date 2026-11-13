package com.orderflow.pedido.repository;

import com.orderflow.pedido.domain.ItemPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemPedidoRepository extends JpaRepository<ItemPedido, UUID> {

    List<ItemPedido> findByLojaIdAndPedidoIdOrderByCreatedAtAsc(UUID lojaId, UUID pedidoId);

    List<ItemPedido> findByLojaIdAndPedidoIdInOrderByCreatedAtAsc(UUID lojaId, Collection<UUID> pedidoIds);
}
