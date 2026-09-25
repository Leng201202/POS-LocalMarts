package com.localmarts.pos.loyalty.repository;

import com.localmarts.pos.loyalty.entity.LoyaltyTransaction;
import com.localmarts.pos.loyalty.entity.TransactionType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction,Long> {
    List<LoyaltyTransaction> findByCustomerIdOrderByTimestampDesc(Long customerId);
    @EntityGraph(attributePaths = "sale")
    List<LoyaltyTransaction> findTop20ByCustomerIdOrderByTimestampDesc(Long customerId);
    List<LoyaltyTransaction> findBySaleIdOrderByTimestampAsc(Long saleId);
    List<LoyaltyTransaction> findAllByOrderByTimestampDesc();
    @Query("""
    SELECT l
    FROM LoyaltyTransaction l
    WHERE (:type IS NULL OR l.type = :type)
    ORDER BY l.timestamp DESC
    """)
    List<LoyaltyTransaction> filter(
            @Param("type") TransactionType type
    );

    @EntityGraph(attributePaths = {"customer", "sale"})
    @Query(
            value = """
                    SELECT l
                    FROM LoyaltyTransaction l
                    WHERE (:type IS NULL OR l.type = :type)
                    ORDER BY l.timestamp DESC, l.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(l)
                    FROM LoyaltyTransaction l
                    WHERE (:type IS NULL OR l.type = :type)
                    """
    )
    Page<LoyaltyTransaction> filter(
            @Param("type") TransactionType type,
            Pageable pageable
    );

}
