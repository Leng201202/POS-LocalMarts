package com.localmarts.pos.loyalty.dtos;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record LoyaltyConfigRequest(
        @NotNull(message = "Minimum purchase amount is required")
        @PositiveOrZero(message = "Minimum purchase amount cannot be negative")
        @Digits(integer = 13, fraction = 2, message = "Minimum purchase amount has too many digits")
        BigDecimal minimumPurchaseAmount,

        @NotNull(message = "Spending amount is required")
        @Positive(message = "Spending amount must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "Spending amount has too many digits")
        BigDecimal spendingAmount,

        @NotNull(message = "Earned points is required")
        @Positive(message = "Earned points must be greater than zero")
        Long earnedPoints,

        @NotNull(message = "Redeem points is required")
        @Positive(message = "Redeem points must be greater than zero")
        Long redeemPoints,

        @NotNull(message = "Redeem value is required")
        @Positive(message = "Redeem value must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "Redeem value has too many digits")
        BigDecimal redeemValue,

        @NotNull(message = "Minimum redeem points is required")
        @Positive(message = "Minimum redeem points must be greater than zero")
        Long minimumRedeemPoints,

        boolean earningEnabled,

        boolean redemptionEnabled
) {
}
