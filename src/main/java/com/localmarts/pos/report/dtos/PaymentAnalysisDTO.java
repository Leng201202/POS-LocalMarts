package com.localmarts.pos.report.dtos;

import lombok.Builder;

import java.math.BigDecimal;
@Builder
public record PaymentAnalysisDTO(
        String paymentType,
        BigDecimal amount,
        BigDecimal percentage
) {
}
