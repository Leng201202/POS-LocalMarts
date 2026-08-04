package com.localmarts.pos.loyalty.entity;


import com.localmarts.pos.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "loyalty_config")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoyaltyConfig extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,precision = 15,scale = 2)
    private BigDecimal minimumPurchaseAmount;

    @Column(nullable = false,precision = 15,scale = 2)
    private BigDecimal spendingAmount;

    @Column(nullable = false)
    private Long earnedPoints;

    @Column(nullable = false)
    private Long redeemPoints;

    @Column(nullable = false,precision = 15,scale = 2)
    private BigDecimal redeemValue;

    @Column(nullable = false)
    private Long minimumRedeemPoints;

    @Column(nullable = false)
    @Builder.Default
    private boolean earningEnabled=true;

    @Column(nullable = false)
    @Builder.Default
    private boolean redemptionEnabled=true;


}
