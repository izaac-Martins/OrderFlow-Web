package com.orderflow.caixa.repository;

import com.orderflow.caixa.domain.Caixa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaixaRepository extends JpaRepository<Caixa, UUID> {

    Optional<Caixa> findByIdAndLojaId(UUID id, UUID lojaId);

    List<Caixa> findByLojaIdOrderByNomeAsc(UUID lojaId);

    boolean existsByLojaIdAndNomeIgnoreCase(UUID lojaId, String nome);
}
