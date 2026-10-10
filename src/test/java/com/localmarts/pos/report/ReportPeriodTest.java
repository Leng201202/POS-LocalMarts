package com.localmarts.pos.report;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static com.localmarts.pos.report.ReportFixtures.*;

class ReportPeriodTest {
    @Test void dailyUsesBangkokAndExclusiveNextMidnight() {
        ReportPeriodRequest r = new ReportPeriodRequest(); r.setDate(LocalDate.of(2026,9,3));
        ReportPeriod p = ReportPeriod.resolve(r, CLOCK);
        assertThat(p.startDate()).isEqualTo(p.endDate());
        assertThat(p.start()).isEqualTo(LocalDateTime.of(2026,9,3,0,0));
        assertThat(p.cutoff()).isEqualTo(LocalDateTime.of(2026,9,4,0,0));
        assertThat(p.contains(p.start())).isTrue(); assertThat(p.contains(p.cutoff())).isFalse();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "2026-09-30,2026-09-24", "2026-10-02,2026-09-26",
            "2026-01-02,2025-12-27", "2024-03-02,2024-02-25"
    })
    void weeklyUsesSelectedEndAndExactlySevenInclusiveDates(String end, String start) {
        ReportPeriodRequest r = new ReportPeriodRequest(); r.setType(ReportType.WEEKLY);
        r.setDate(LocalDate.parse(end));
        r.setStartDate(LocalDate.of(2000,1,1)); // Forged Custom fields cannot control Weekly.
        r.setEndDate(LocalDate.of(2099,1,1));
        Clock afterPeriod = Clock.fixed(Instant.parse("2026-10-03T08:00:00Z"), ZoneOffset.UTC);
        ReportPeriod p = ReportPeriod.resolve(r, afterPeriod);
        assertThat(p.startDate()).isEqualTo(LocalDate.parse(start));
        assertThat(p.endDate()).isEqualTo(LocalDate.parse(end));
        assertThat(java.time.temporal.ChronoUnit.DAYS.between(p.startDate(), p.endDate()) + 1).isEqualTo(7);
        assertThat(p.start()).isEqualTo(LocalDate.parse(start).atStartOfDay());
        assertThat(p.cutoff()).isEqualTo(LocalDate.parse(end).plusDays(1).atStartOfDay());
    }
    @Test void weeklyEndingTodayRetainsActiveBangkokCutoff() {
        ReportPeriodRequest r = new ReportPeriodRequest(); r.setType(ReportType.WEEKLY);
        r.setDate(LocalDate.of(2026,9,4));
        ReportPeriod p = ReportPeriod.resolve(r, CLOCK);
        assertThat(p.startDate()).isEqualTo(LocalDate.of(2026,8,29));
        assertThat(p.cutoff()).isEqualTo(LocalDateTime.of(2026,9,4,15,0));
    }
    @Test void futureWeeklyEndFailsEvenWhenStartIsInThePast() {
        ReportPeriodRequest r = new ReportPeriodRequest(); r.setType(ReportType.WEEKLY);
        r.setDate(LocalDate.of(2026,9,5));
        assertThatThrownBy(() -> ReportPeriod.resolve(r, CLOCK)).hasMessageContaining("end date must not be in the future");
    }
    @Test void customIncludesEndDateAndCapsAtGenerationTime() {
        ReportPeriod p = all();
        assertThat(p.startDate()).isEqualTo(LocalDate.of(2026,9,3));
        assertThat(p.endDate()).isEqualTo(LocalDate.of(2026,9,4));
        assertThat(p.cutoff()).isEqualTo(p.generatedAt());
        assertThat(p.generatedAt()).isEqualTo(LocalDateTime.of(2026,9,4,15,0));
    }
    @Test void invalidMissingAndFutureOnlyRangesFail() {
        ReportPeriodRequest r = new ReportPeriodRequest();
        assertThatThrownBy(() -> ReportPeriod.resolve(r,CLOCK)).hasMessageContaining("required dates");
        r.setDate(LocalDate.of(2026,9,5));
        assertThatThrownBy(() -> ReportPeriod.resolve(r,CLOCK)).hasMessageContaining("Future-only");
        r.setType(ReportType.CUSTOM); r.setStartDate(LocalDate.of(2026,9,4));r.setEndDate(LocalDate.of(2026,9,3));
        assertThatThrownBy(() -> ReportPeriod.resolve(r,CLOCK)).hasMessageContaining("Start date");
    }
}
