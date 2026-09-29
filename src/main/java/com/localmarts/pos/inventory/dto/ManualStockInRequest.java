package com.localmarts.pos.inventory.dto;

import lombok.Getter;
import lombok.Setter;

/** Only receiving inputs; product metadata and actor identity are never bound. */
@Getter
@Setter
public class ManualStockInRequest {
    private Long productId;
    private Integer quantity;
    private String reason;
}
