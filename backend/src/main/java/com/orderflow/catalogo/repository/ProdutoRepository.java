package com.orderflow.catalogo.repository;

import com.orderflow.catalogo.domain.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProdutoRepository extends JpaRepository<Produto, UUID> {

    Optional<Produto> findByIdAndLojaId(UUID id, UUID lojaId);

    List<Produto> findByLojaIdAndArquivadoEmIsNullOrderByOrdemAscNomeAsc(UUID lojaId);

    List<Produto> findByLojaIdAndIdIn(UUID lojaId, Collection<UUID> ids);

    boolean existsByLojaIdAndSkuIgnoreCase(UUID lojaId, String sku);

    boolean existsByLojaIdAndSkuIgnoreCaseAndIdNot(UUID lojaId, String sku, UUID id);

    boolean existsByLojaIdAndCategoriaIdAndAtivoTrueAndArquivadoEmIsNull(UUID lojaId, UUID categoriaId);
}
