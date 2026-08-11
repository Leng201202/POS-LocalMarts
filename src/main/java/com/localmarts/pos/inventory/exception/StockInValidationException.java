package com.localmarts.pos.inventory.exception;

import com.localmarts.pos.inventory.dto.StockInReviewRow;
import lombok.Getter;

import java.util.List;

/**
 * Thrown when reconcile() still finds INVALID or unresolved NEEDS_MAPPING
 * rows after merging the user's edits. Nothing has touched the database -
 * the controller re-renders the review page with the refreshed row list.
 */
@Getter
public class StockInValidationException extends RuntimeException {

    private final List<StockInReviewRow> rows;

    public StockInValidationException(String message, List<StockInReviewRow> rows) {
        super(message);
        this.rows = rows;
    }
}
