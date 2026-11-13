package com.orderflow.caixa.repository;

import com.orderflow.caixa.domain.FechamentoDeclaracao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FechamentoDeclaracaoRepository extends JpaRepository<FechamentoDeclaracao, UUID> {

    List<FechamentoDeclaracao> findByLojaIdAndSessaoCaixaIdOrderByMetodoAsc(UUID lojaId, UUID sessaoCaixaId);
}
