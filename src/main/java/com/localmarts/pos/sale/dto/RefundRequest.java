package com.localmarts.pos.sale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class RefundRequest {

    @NotEmpty(message = "Refund items are required")
    @Valid
    private List<RefundItemRequest> items = new ArrayList<>();

    @NotNull(message = "Refund reason is required")
    private RefundReason reason;

    @Size(max = 500, message = "Refund note must be 500 characters or fewer")
    private String note;

    @AssertTrue(message = "Confirm that the customer physically returned the selected items")
    private boolean physicalReturnConfirmed;

    public List<RefundItemRequest> getItems() {
        return items;
    }

    public void setItems(List<RefundItemRequest> items) {
        this.items = items;
    }

    public RefundReason getReason() {
        return reason;
    }

    public void setReason(RefundReason reason) {
        this.reason = reason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isPhysicalReturnConfirmed() {
        return physicalReturnConfirmed;
    }

    public void setPhysicalReturnConfirmed(boolean physicalReturnConfirmed) {
        this.physicalReturnConfirmed = physicalReturnConfirmed;
    }
}
