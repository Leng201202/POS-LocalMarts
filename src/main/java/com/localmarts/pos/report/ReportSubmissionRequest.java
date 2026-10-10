package com.localmarts.pos.report;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter
public class ReportSubmissionRequest {
    @NotNull
    private UUID previewToken;
    @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2)
    private BigDecimal realWorldCash;
}
