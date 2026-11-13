package com.orderflow.catalogo.repository;

import com.orderflow.catalogo.domain.GrupoModificador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrupoModificadorRepository extends JpaRepository<GrupoModificador, UUID> {

    Optional<GrupoModificador> findByIdAndLojaId(UUID id, UUID lojaId);

    List<GrupoModificador> findByLojaIdAndArquivadoEmIsNullOrderByOrdemAscNomeAsc(UUID lojaId);

    List<GrupoModificador> findByLojaIdAndIdIn(UUID lojaId, Collection<UUID> ids);

    boolean existsByLojaIdAndNomeIgnoreCaseAndArquivadoEmIsNull(UUID lojaId, String nome);

    boolean existsByLojaIdAndNomeIgnoreCaseAndIdNotAndArquivadoEmIsNull(UUID lojaId, String nome, UUID id);
}
