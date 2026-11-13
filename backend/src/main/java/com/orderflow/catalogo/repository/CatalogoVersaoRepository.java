package com.orderflow.catalogo.repository;

import com.orderflow.catalogo.domain.CatalogoVersao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CatalogoVersaoRepository extends JpaRepository<CatalogoVersao, UUID> {
}
