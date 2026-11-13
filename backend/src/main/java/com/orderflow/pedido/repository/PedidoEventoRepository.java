package com.orderflow.pedido.repository;

import com.orderflow.pedido.domain.PedidoEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PedidoEventoRepository extends JpaRepository<PedidoEvento, Long> {

    List<PedidoEvento> findByLojaIdAndSeqGreaterThanOrderBySeqAsc(UUID lojaId, long desdeSeq);

    @Query("select coalesce(max(e.seq), 0) from PedidoEvento e where e.lojaId = :lojaId")
    long findMaxSeqByLojaId(@Param("lojaId") UUID lojaId);

    @Query("select coalesce(min(e.seq), 0) from PedidoEvento e where e.lojaId = :lojaId")
    long findMinSeqByLojaId(@Param("lojaId") UUID lojaId);
}
