package com.cheirocafe.api_restaurante.repository;

import com.cheirocafe.api_restaurante.entity.Adicional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdicionalRepository extends JpaRepository<Adicional, Long> {

    List<Adicional> findByVinculoStartingWith(String prefixo);

    Optional<Adicional> findFirstByNomeIgnoreCaseAndVinculoIgnoreCaseAndProdutoIsNull(String nome, String vinculo);
}
