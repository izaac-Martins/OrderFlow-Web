package com.cheirocafe.api_restaurante.repository;

import com.cheirocafe.api_restaurante.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
}
