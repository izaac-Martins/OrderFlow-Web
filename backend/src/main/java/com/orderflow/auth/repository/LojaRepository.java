package com.orderflow.auth.repository;

import com.orderflow.auth.domain.Loja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LojaRepository extends JpaRepository<Loja, UUID> {

    Optional<Loja> findByIdAndAtivoTrue(UUID id);

    Optional<Loja> findByCodigoIgnoreCaseAndAtivoTrue(String codigo);
}
