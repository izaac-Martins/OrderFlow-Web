package com.orderflow.catalogo.repository;

import com.orderflow.catalogo.domain.ProdutoModificadorGrupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProdutoModificadorGrupoRepository extends JpaRepository<ProdutoModificadorGrupo, ProdutoModificadorGrupo.Id> {

    List<ProdutoModificadorGrupo> findByLojaIdAndIdProdutoIdOrderByOrdemAsc(UUID lojaId, UUID produtoId);

    List<ProdutoModificadorGrupo> findByLojaIdAndIdProdutoIdInOrderByOrdemAsc(UUID lojaId, Collection<UUID> produtoIds);

    List<ProdutoModificadorGrupo> findByLojaId(UUID lojaId);

    @Query("""
            select count(v) from ProdutoModificadorGrupo v, Produto p
            where v.lojaId = :lojaId
              and v.id.grupoId = :grupoId
              and p.id = v.id.produtoId
              and p.arquivadoEm is null
              and p.ativo = true
            """)
    long countActiveProductsUsingGrupo(@Param("lojaId") UUID lojaId, @Param("grupoId") UUID grupoId);

    @Modifying
    @Query("delete from ProdutoModificadorGrupo v where v.lojaId = :lojaId and v.id.produtoId = :produtoId")
    void deleteByLojaIdAndProdutoId(@Param("lojaId") UUID lojaId, @Param("produtoId") UUID produtoId);
}
