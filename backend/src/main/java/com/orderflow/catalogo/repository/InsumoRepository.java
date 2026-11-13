package com.orderflow.catalogo.repository;

import com.orderflow.catalogo.domain.Insumo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsumoRepository extends JpaRepository<Insumo, UUID> {

    Optional<Insumo> findByIdAndLojaId(UUID id, UUID lojaId);

    List<Insumo> findByLojaIdOrderByNomeAsc(UUID lojaId);

    List<Insumo> findByLojaIdAndDisponivelOrderByNomeAsc(UUID lojaId, boolean disponivel);

    List<Insumo> findByLojaIdAndIdIn(UUID lojaId, Collection<UUID> ids);

    boolean existsByLojaIdAndNomeIgnoreCase(UUID lojaId, String nome);

    boolean existsByLojaIdAndNomeIgnoreCaseAndIdNot(UUID lojaId, String nome, UUID id);
}
