package com.localmarts.pos.report.admin.dto;

import java.math.BigDecimal;

public record ProfitTrendDTO (
        String label,
        BigDecimal profit
){
}
