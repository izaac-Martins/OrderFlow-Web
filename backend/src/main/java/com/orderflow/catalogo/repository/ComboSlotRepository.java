package com.orderflow.catalogo.repository;

import com.orderflow.catalogo.domain.ComboSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ComboSlotRepository extends JpaRepository<ComboSlot, UUID> {

    List<ComboSlot> findByLojaIdAndProdutoComboIdOrderByOrdemAsc(UUID lojaId, UUID produtoComboId);

    List<ComboSlot> findByLojaIdAndProdutoComboIdInOrderByOrdemAsc(UUID lojaId, Collection<UUID> produtoComboIds);

    List<ComboSlot> findByLojaIdOrderByOrdemAsc(UUID lojaId);

    @Modifying
    @Query("delete from ComboSlot s where s.lojaId = :lojaId and s.produtoComboId = :produtoComboId")
    void deleteByLojaIdAndProdutoComboId(@Param("lojaId") UUID lojaId, @Param("produtoComboId") UUID produtoComboId);
}
