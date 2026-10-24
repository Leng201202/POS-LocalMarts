package com.localmarts.pos.report.admin.model;

import com.localmarts.pos.report.common.ReportTime;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Inclusive calendar-date period used by all admin reports. */
public record AdminReportPeriod(LocalDate startDate, LocalDate endDate) {

    public static AdminReportPeriod resolve(LocalDate start, LocalDate end, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(ReportTime.ZONE));
        if (start == null && end == null) {
            // Opening an admin report without dates defaults to month-to-date in Bangkok time.
            return new AdminReportPeriod(today.withDayOfMonth(1), today);
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("Choose both a start date and an end date.");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be on or before end date.");
        }
        return new AdminReportPeriod(start, end);
    }

    public LocalDateTime start() {
        return startDate.atStartOfDay();
    }

    public LocalDateTime cutoff() {
        return endDate.plusDays(1).atStartOfDay();
    }

    public long daysInclusive() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
