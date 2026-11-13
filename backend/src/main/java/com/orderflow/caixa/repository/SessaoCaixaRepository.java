package com.orderflow.caixa.repository;

import com.orderflow.caixa.domain.SessaoCaixa;
import com.orderflow.caixa.domain.StatusSessaoCaixa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessaoCaixaRepository extends JpaRepository<SessaoCaixa, UUID> {

    Optional<SessaoCaixa> findByIdAndLojaId(UUID id, UUID lojaId);

    Optional<SessaoCaixa> findByLojaIdAndCaixaIdAndStatus(UUID lojaId, UUID caixaId, StatusSessaoCaixa status);

    Optional<SessaoCaixa> findByLojaIdAndOperadorIdAndStatus(UUID lojaId, UUID operadorId, StatusSessaoCaixa status);

    Optional<SessaoCaixa> findFirstByLojaIdAndStatusOrderByDataAberturaDesc(UUID lojaId, StatusSessaoCaixa status);

    List<SessaoCaixa> findByLojaIdOrderByDataAberturaDesc(UUID lojaId);
}
