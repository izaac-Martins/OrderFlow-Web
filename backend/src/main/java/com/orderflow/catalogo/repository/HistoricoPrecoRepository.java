package com.orderflow.catalogo.repository;

import com.orderflow.catalogo.domain.HistoricoPreco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoricoPrecoRepository extends JpaRepository<HistoricoPreco, UUID> {

    List<HistoricoPreco> findByLojaIdAndEntidadeTipoAndEntidadeIdOrderByVigenteDesdeDesc(
            UUID lojaId,
            String entidadeTipo,
            UUID entidadeId
    );
}
