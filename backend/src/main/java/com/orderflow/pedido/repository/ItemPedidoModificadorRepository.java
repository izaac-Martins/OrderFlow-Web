package com.orderflow.pedido.repository;

import com.orderflow.pedido.domain.ItemPedidoModificador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemPedidoModificadorRepository extends JpaRepository<ItemPedidoModificador, UUID> {

    List<ItemPedidoModificador> findByLojaIdAndItemPedidoIdIn(UUID lojaId, Collection<UUID> itemPedidoIds);
}
