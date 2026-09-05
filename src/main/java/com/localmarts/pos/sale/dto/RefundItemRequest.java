package com.localmarts.pos.sale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RefundItemRequest {

    @NotNull(message = "Sale item is required")
    private Long saleItemId;

    private boolean selected;

    private Integer quantity = 0;

    @Valid
    private List<ReturnBreakdownRequest> breakdowns = new ArrayList<>();

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

    public List<ReturnBreakdownRequest> getBreakdowns() {
        return breakdowns;
    }

    public void setBreakdowns(List<ReturnBreakdownRequest> breakdowns) {
        this.breakdowns = breakdowns;
    }
}
