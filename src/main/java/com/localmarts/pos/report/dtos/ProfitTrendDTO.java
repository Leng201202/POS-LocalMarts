package com.localmarts.pos.report.dtos;

import java.math.BigDecimal;

public record ProfitTrendDTO (
        String label,
        BigDecimal profit
){
}
