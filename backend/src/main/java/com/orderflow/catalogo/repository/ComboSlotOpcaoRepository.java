package com.orderflow.catalogo.repository;

import com.orderflow.catalogo.domain.ComboSlotOpcao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ComboSlotOpcaoRepository extends JpaRepository<ComboSlotOpcao, ComboSlotOpcao.Id> {

    List<ComboSlotOpcao> findByLojaIdAndIdSlotIdInOrderByOrdemAsc(UUID lojaId, Collection<UUID> slotIds);

    List<ComboSlotOpcao> findByLojaIdOrderByOrdemAsc(UUID lojaId);

    @Modifying
    @Query("delete from ComboSlotOpcao o where o.lojaId = :lojaId and o.id.slotId in :slotIds")
    void deleteByLojaIdAndSlotIds(@Param("lojaId") UUID lojaId, @Param("slotIds") Collection<UUID> slotIds);
}
