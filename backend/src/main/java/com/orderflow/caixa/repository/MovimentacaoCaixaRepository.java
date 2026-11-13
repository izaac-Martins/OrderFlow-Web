package com.orderflow.caixa.repository;

import com.orderflow.caixa.domain.MovimentacaoCaixa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovimentacaoCaixaRepository extends JpaRepository<MovimentacaoCaixa, UUID> {

    Optional<MovimentacaoCaixa> findByIdAndLojaId(UUID id, UUID lojaId);

    Optional<MovimentacaoCaixa> findByLojaIdAndIdempotencyKey(UUID lojaId, UUID idempotencyKey);

    List<MovimentacaoCaixa> findByLojaIdAndSessaoCaixaIdOrderByCreatedAtAsc(UUID lojaId, UUID sessaoCaixaId);

    boolean existsByLojaIdAndMovimentacaoEstornadaId(UUID lojaId, UUID movimentacaoEstornadaId);
}
