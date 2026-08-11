package com.localmarts.pos.inventory.exception;

/**
 * Thrown when the uploaded file itself is unusable (wrong extension, empty,
 * unreadable, or missing/renamed template columns). The whole upload is
 * rejected before any row parsing happens.
 */
public class StockInUploadException extends RuntimeException {
    public StockInUploadException(String message) {
        super(message);
    }

    public StockInUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
