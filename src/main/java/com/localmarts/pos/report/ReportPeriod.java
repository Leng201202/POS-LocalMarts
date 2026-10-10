package com.localmarts.pos.report;

import java.time.*;

public record ReportPeriod(ReportType type, LocalDate startDate, LocalDate endDate,
                           LocalDateTime generatedAt, LocalDateTime cutoff) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    public static ReportPeriod resolve(ReportPeriodRequest request, Clock clock) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZONE));
        if (request.getType() == null) throw new IllegalArgumentException("Choose a report type.");
        if (request.getDate() != null && (request.getDate().getYear() < 1 || request.getDate().getYear() > 9999))
            throw new IllegalArgumentException("Choose valid calendar dates.");
        LocalDate start;
        LocalDate end;
        switch (request.getType()) {
            case DAILY -> {
                start = request.getDate();
                end = start;
            }
            case WEEKLY -> {
                if (request.getDate() == null) throw new IllegalArgumentException("Choose a date.");
                end = request.getDate();
                if (end.isAfter(now.toLocalDate()))
                    throw new IllegalArgumentException("Weekly report end date must not be in the future.");
                start = end.minusDays(6);
            }
            case CUSTOM -> {
                start = request.getStartDate();
                end = request.getEndDate();
            }
            default -> throw new IllegalArgumentException("Choose a report type.");
        }
        if (start == null || end == null) throw new IllegalArgumentException("Choose the required dates.");
        if (start.isAfter(end)) throw new IllegalArgumentException("Start date must not be after end date.");
        if (start.isAfter(now.toLocalDate())) throw new IllegalArgumentException("Future-only periods are not allowed.");
        if (start.getYear() < 1 || end.getYear() > 9999) throw new IllegalArgumentException("Choose valid calendar dates.");
        LocalDateTime exclusiveEnd = end.plusDays(1).atStartOfDay();
        return new ReportPeriod(request.getType(), start, end, now,
                exclusiveEnd.isBefore(now) ? exclusiveEnd : now);
    }

    public LocalDateTime start() { return startDate.atStartOfDay(); }
    public boolean contains(LocalDateTime timestamp) {
        return timestamp != null && !timestamp.isBefore(start()) && timestamp.isBefore(cutoff);
    }
}
