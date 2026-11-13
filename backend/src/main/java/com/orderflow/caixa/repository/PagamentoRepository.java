package com.orderflow.caixa.repository;

import com.orderflow.caixa.domain.Pagamento;
import com.orderflow.caixa.domain.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    Optional<Pagamento> findByIdAndLojaId(UUID id, UUID lojaId);

    Optional<Pagamento> findByLojaIdAndIdempotencyKey(UUID lojaId, UUID idempotencyKey);

    List<Pagamento> findByLojaIdAndPedidoIdOrderByCreatedAtAsc(UUID lojaId, UUID pedidoId);

    List<Pagamento> findByLojaIdAndSessaoCaixaIdAndStatus(UUID lojaId, UUID sessaoCaixaId, StatusPagamento status);

    long countByLojaIdAndSessaoCaixaIdAndStatus(UUID lojaId, UUID sessaoCaixaId, StatusPagamento status);
}
