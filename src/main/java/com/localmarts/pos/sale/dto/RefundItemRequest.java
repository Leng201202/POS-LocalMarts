package com.localmarts.pos.sale.dto;

import jakarta.validation.constraints.NotNull;

public class RefundItemRequest {

    @NotNull(message = "Sale item is required")
    private Long saleItemId;

    private boolean selected;

    private Integer quantity = 0;

    private ReturnCondition condition;

    public Long getSaleItemId() {
        return saleItemId;
    }

    public void setSaleItemId(Long saleItemId) {
        this.saleItemId = saleItemId;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public ReturnCondition getCondition() {
        return condition;
    }

    public void setCondition(ReturnCondition condition) {
        this.condition = condition;
    }
}
