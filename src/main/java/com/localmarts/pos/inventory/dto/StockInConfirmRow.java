package com.localmarts.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form-backed, per-row edits submitted from the review page. Only these
 * fields are ever taken from the browser on confirm - everything else
 * (barcode, name, dates, existingProductId, status) is re-read from the
 * trusted StockInBatchSession stored server-side.
 *
 * <p>Fields are kept as raw strings (not Integer/BigDecimal/Long) so that
 * malformed or empty submissions are handled as row-level validation
 * errors by StockInBatchService instead of failing Spring's data binding
 * for the whole request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockInConfirmRow {
    private int rowNumber;
    private String quantity;
    private String costPrice;
    private String categoryId;
    private String unitId;
}
