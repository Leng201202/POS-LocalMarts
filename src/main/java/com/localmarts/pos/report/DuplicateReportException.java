package com.localmarts.pos.report;

public class DuplicateReportException extends IllegalArgumentException {
    private final Long reportId;
    public DuplicateReportException(Long reportId) {
        super("A submitted report already exists for this exact date range. Overlapping, different ranges are allowed.");
        this.reportId = reportId;
    }
    public Long getReportId() { return reportId; }
}
