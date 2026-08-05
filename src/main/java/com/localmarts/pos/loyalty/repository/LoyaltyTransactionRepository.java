package com.localmarts.pos.loyalty.repository;

import com.localmarts.pos.loyalty.entity.LoyaltyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction,Long> {
    List<LoyaltyTransaction> findByCustomerIdOrderByTimestamp(Long customerId);
    List<LoyaltyTransaction> findBySaleIdOrderByTimestamp(Long saleId);

}
