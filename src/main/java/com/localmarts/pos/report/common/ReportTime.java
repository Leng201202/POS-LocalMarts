package com.localmarts.pos.report.common;

import java.time.ZoneId;

/** Shared business timezone for all reports. */
public final class ReportTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    private ReportTime() {
    }
}
