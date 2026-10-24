package com.localmarts.pos.report.admin.dto;

import lombok.Builder;

import java.math.BigDecimal;
@Builder
public record PaymentAnalysisDTO(
        String paymentType,
        BigDecimal amount,
        BigDecimal percentage
) {
}
