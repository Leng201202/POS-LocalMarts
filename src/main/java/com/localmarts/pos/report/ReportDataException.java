package com.localmarts.pos.report;

public class ReportDataException extends IllegalArgumentException {
    public ReportDataException(String message) { super(message); }
    public ReportDataException(String message, Throwable cause) { super(message, cause); }
}
