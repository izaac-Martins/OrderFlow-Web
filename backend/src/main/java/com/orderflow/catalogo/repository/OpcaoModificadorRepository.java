package com.orderflow.catalogo.repository;

import com.orderflow.catalogo.domain.OpcaoModificador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OpcaoModificadorRepository extends JpaRepository<OpcaoModificador, UUID> {

    Optional<OpcaoModificador> findByIdAndLojaId(UUID id, UUID lojaId);

    List<OpcaoModificador> findByLojaIdAndGrupoIdAndArquivadoEmIsNullOrderByOrdemAscNomeAsc(UUID lojaId, UUID grupoId);

    List<OpcaoModificador> findByLojaIdAndGrupoIdInAndArquivadoEmIsNullOrderByOrdemAscNomeAsc(UUID lojaId, Collection<UUID> grupoIds);

    List<OpcaoModificador> findByLojaIdAndArquivadoEmIsNull(UUID lojaId);

    boolean existsByGrupoIdAndNomeIgnoreCaseAndArquivadoEmIsNull(UUID grupoId, String nome);

    boolean existsByGrupoIdAndNomeIgnoreCaseAndIdNotAndArquivadoEmIsNull(UUID grupoId, String nome, UUID id);
}
