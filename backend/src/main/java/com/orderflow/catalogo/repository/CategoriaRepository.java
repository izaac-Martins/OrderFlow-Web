package com.orderflow.catalogo.repository;

import com.orderflow.catalogo.domain.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

    Optional<Categoria> findByIdAndLojaId(UUID id, UUID lojaId);

    List<Categoria> findByLojaIdOrderByOrdemExibicaoAscNomeAsc(UUID lojaId);

    List<Categoria> findByLojaIdAndArquivadoEmIsNullOrderByOrdemExibicaoAscNomeAsc(UUID lojaId);

    boolean existsByLojaIdAndCategoriaPaiIdAndArquivadoEmIsNull(UUID lojaId, UUID categoriaPaiId);

    @Query("""
            select count(c) > 0 from Categoria c
            where c.lojaId = :lojaId
              and lower(c.slug) = lower(:slug)
              and ((:paiId is null and c.categoriaPaiId is null) or c.categoriaPaiId = :paiId)
              and (:excluirId is null or c.id <> :excluirId)
            """)
    boolean existsSiblingWithSlug(
            @Param("lojaId") UUID lojaId,
            @Param("paiId") UUID paiId,
            @Param("slug") String slug,
            @Param("excluirId") UUID excluirId
    );
}
