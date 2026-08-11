package com.localmarts.pos.inventory.dto;

public record StockInConfirmResult(
        int totalCount,
        int newProductCount,
        int existingProductCount
) {
}
