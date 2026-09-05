package com.localmarts.pos.sale.dto;

public class ReturnBreakdownRequest {

    private Integer quantity = 1;

    private RefundReason reason;

    private ReturnCondition condition;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public RefundReason getReason() {
        return reason;
    }

    public void setReason(RefundReason reason) {
        this.reason = reason;
    }

    public ReturnCondition getCondition() {
        return condition;
    }

    public void setCondition(ReturnCondition condition) {
        this.condition = condition;
    }
}
