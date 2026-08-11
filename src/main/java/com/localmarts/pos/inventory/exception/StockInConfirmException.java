package com.localmarts.pos.inventory.exception;

/**
 * Thrown from inside StockInBatchService#applyBatch (the @Transactional
 * step) when the database no longer matches what the review page showed -
 * e.g. a product was deleted or a barcode was taken concurrently. Rolls
 * back the whole batch; the review snapshot is discarded and the caller
 * must re-upload.
 */
public class StockInConfirmException extends RuntimeException {
    public StockInConfirmException(String message) {
        super(message);
    }
}
