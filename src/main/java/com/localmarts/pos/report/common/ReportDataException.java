package com.localmarts.pos.report.common;

public class ReportDataException extends IllegalArgumentException {
    public ReportDataException(String message) { super(message); }
    public ReportDataException(String message, Throwable cause) { super(message, cause); }
}
