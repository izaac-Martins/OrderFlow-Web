package com.orderflow.pedido.repository;

import com.orderflow.pedido.domain.Pedido;
import com.orderflow.pedido.domain.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    Optional<Pedido> findByIdAndLojaId(UUID id, UUID lojaId);

    Optional<Pedido> findByLojaIdAndIdempotencyKey(UUID lojaId, UUID idempotencyKey);

    List<Pedido> findByLojaIdAndStatusInOrderByConfirmadoEmAscCreatedAtAsc(UUID lojaId, Collection<StatusPedido> statuses);

    List<Pedido> findByLojaIdOrderByCreatedAtDesc(UUID lojaId);

    @Query("select coalesce(max(p.numeroDiario), 0) from Pedido p where p.lojaId = :lojaId and p.dataOperacional = :data")
    int findMaxNumeroDiario(@Param("lojaId") UUID lojaId, @Param("data") LocalDate data);

    @Query("""
            select count(p) from Pedido p
            where p.lojaId = :lojaId
              and p.saldoDevedorCentavos > 0
              and p.status not in (com.orderflow.pedido.domain.StatusPedido.CANCELADO, com.orderflow.pedido.domain.StatusPedido.ESTORNADO)
            """)
    long countPedidosEmAberto(@Param("lojaId") UUID lojaId);
}
