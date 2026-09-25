package com.localmarts.pos.inventory;

import com.localmarts.pos.inventory.entity.Inventory;
import com.localmarts.pos.inventory.entity.InventoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory,Long> {

    List<Inventory> findAllByOrderByTimestampDesc();
    List<Inventory> findByProductIdOrderByTimestampDesc(Long productId);
    List<Inventory> findByInventoryTypeOrderByTimestampDesc(InventoryType type);
    List<Inventory> findByActorIdOrderByTimestampDesc(Long actorId);


    @EntityGraph(attributePaths = {"product", "actor"})
    @Query(value = """
    SELECT i FROM Inventory i WHERE (
        :keyword IS NULL
        OR TRIM(:keyword) = ''
            OR LOWER(i.product.name) LIKE LOWER(CONCAT('%',:keyword,'%')) 
                OR LOWER(COALESCE(i.product.barcode,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))
        )
        AND (:type IS NULL OR i.inventoryType=:type)
            AND (:actorId IS NULL OR i.actor.id = :actorId)
                ORDER BY i.timestamp DESC, i.id DESC
    """, countQuery = """
    SELECT COUNT(i) FROM Inventory i WHERE (
        :keyword IS NULL
        OR TRIM(:keyword) = ''
            OR LOWER(i.product.name) LIKE LOWER(CONCAT('%',:keyword,'%'))
                OR LOWER(COALESCE(i.product.barcode,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))
        )
        AND (:type IS NULL OR i.inventoryType=:type)
            AND (:actorId IS NULL OR i.actor.id = :actorId)
    """)
    Page<Inventory> filter(
            @Param("keyword") String keyword,
            @Param("type") InventoryType type,
            @Param("actorId") Long actorId,
            Pageable pageable
    );

    @Query("""
    SELECT COUNT(i) FROM Inventory i WHERE (
        :keyword IS NULL
        OR TRIM(:keyword) = ''
            OR LOWER(i.product.name) LIKE LOWER(CONCAT('%',:keyword,'%'))
                OR LOWER(COALESCE(i.product.barcode,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))
        )
        AND (:selectedType IS NULL OR i.inventoryType=:selectedType)
        AND (:actorId IS NULL OR i.actor.id = :actorId)
        AND i.inventoryType = :countedType
    """)
    long countFilteredByType(
            @Param("keyword") String keyword,
            @Param("selectedType") InventoryType selectedType,
            @Param("actorId") Long actorId,
            @Param("countedType") InventoryType countedType
    );

}
