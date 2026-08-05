package com.localmarts.pos.inventory;

import com.localmarts.pos.inventory.entity.Inventory;
import com.localmarts.pos.inventory.entity.InventoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory,Long> {

    List<Inventory> findAllByOrderByTimestampDesc();
    List<Inventory> findByProductIdOrderByTimestampDesc(Long productId);
    List<Inventory> findByInventoryTypeOrderByTimestampDesc(InventoryType type);
    List<Inventory> findByActorIdOrderByTimestampDesc(Long actorId);


    @Query("""
    SELECT i FROM Inventory i WHERE (
        :keyword IS NULL
        OR :keyword = ' '
            OR LOWER(i.product.name) LIKE LOWER(CONCAT('%',:keyword,'%')) 
                OR LOWER(COALESCE(i.product.barcode,'')) LIKE LOWER(CONCAT('%',:keyword,'%'))
        )
        AND (:type IS NULL OR i.inventoryType=:type)
            AND (:actorId IS NULL OR i.actor.id = :actorId)
                ORDER BY i.timestamp DESC
    """)
    List<Inventory> filter(
            @Param("keyword") String keyword,
            @Param("type") InventoryType type,
            @Param("actorId") Long actorId
    );

}
