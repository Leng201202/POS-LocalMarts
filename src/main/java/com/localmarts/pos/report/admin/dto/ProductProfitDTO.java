package com.localmarts.pos.report.admin.dto;

import java.math.BigDecimal;

public record ProductProfitDTO(
        String productName,
        String categoryName,
        String unitName,
        Long quantitySold,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit
) {
}
