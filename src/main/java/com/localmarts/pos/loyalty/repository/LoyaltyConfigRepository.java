package com.localmarts.pos.loyalty.repository;

import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoyaltyConfigRepository extends JpaRepository<LoyaltyConfig,Long> {
    Optional<LoyaltyConfig> findFirstByOrderByIdAsc();
}
