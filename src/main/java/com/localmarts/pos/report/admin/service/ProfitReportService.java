package com.localmarts.pos.report.admin.service;

import com.localmarts.pos.report.admin.model.AdminReportPeriod;
import com.localmarts.pos.report.common.ReportTime;
import com.localmarts.pos.report.admin.repository.AdminReportRepository;
import com.localmarts.pos.report.admin.dto.ProductProfitDTO;
import com.localmarts.pos.report.admin.dto.ProfitSummaryDTO;
import com.localmarts.pos.report.admin.dto.ProfitTrendDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProfitReportService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final AdminReportRepository repository;
    private final Clock clock;

    @Autowired
    public ProfitReportService(AdminReportRepository repository) {
        this(repository, Clock.system(ReportTime.ZONE));
    }

    ProfitReportService(AdminReportRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfitSummaryDTO getProfitSummary(LocalDate start, LocalDate end) {
        AdminReportPeriod period = AdminReportPeriod.resolve(start, end, clock);
        Object[] data = repository.findProfitSummary(period.start(), period.cutoff());
        BigDecimal revenue = money(data[0]);
        BigDecimal cost = money(data[1]);
        BigDecimal profit = money(revenue.subtract(cost));
        // There is no operating-expense model yet, so net profit intentionally equals gross profit.
        return ProfitSummaryDTO.builder()
                .totalRevenue(revenue)
                .costOfGoodsSold(cost)
                .grossProfit(profit)
                .netProfit(profit)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ProfitTrendDTO> getProfitTrend(LocalDate start, LocalDate end) {
        AdminReportPeriod period = AdminReportPeriod.resolve(start, end, clock);
        TrendBuckets buckets = TrendBuckets.forPeriod(period);
        for (Object[] row : repository.findProfitRows(period.start(), period.cutoff())) {
            LocalDate date = ((LocalDateTime) row[0]).toLocalDate();
            BigDecimal revenue = money(row[1]);
            BigDecimal cost = money(row[2]).multiply(BigDecimal.valueOf(number(row[3]).longValue()));
            buckets.add(date, revenue.subtract(cost));
        }
        return buckets.values();
    }

    @Transactional(readOnly = true)
    public List<ProductProfitDTO> getProductPerformance(LocalDate start, LocalDate end, String sort) {
        AdminReportPeriod period = AdminReportPeriod.resolve(start, end, clock);
        List<ProductProfitDTO> products = repository.findProductPerformance(period.start(), period.cutoff()).stream()
                .map(row -> {
                    BigDecimal revenue = money(row[4]);
                    BigDecimal cost = money(row[5]);
                    return new ProductProfitDTO(
                            String.valueOf(row[0]),
                            String.valueOf(row[1]),
                            String.valueOf(row[2]),
                            number(row[3]).longValue(),
                            revenue,
                            cost,
                            money(revenue.subtract(cost))
                    );
                })
                .toList();

        Comparator<ProductProfitDTO> comparator = "units".equalsIgnoreCase(sort)
                ? Comparator.comparingLong(ProductProfitDTO::quantitySold).reversed()
                    .thenComparing(ProductProfitDTO::profit, Comparator.reverseOrder())
                : Comparator.comparing(ProductProfitDTO::profit, Comparator.reverseOrder())
                    .thenComparing(Comparator.comparingLong(ProductProfitDTO::quantitySold).reversed());
        return products.stream().sorted(comparator.thenComparing(ProductProfitDTO::productName)).toList();
    }

    public String trendGranularity(LocalDate start, LocalDate end) {
        long days = AdminReportPeriod.resolve(start, end, clock).daysInclusive();
        return days <= 7 ? "Daily" : days <= 31 ? "Weekly" : "Monthly";
    }

    private static Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private static BigDecimal money(Object value) {
        if (value == null) return ZERO;
        if (value instanceof BigDecimal decimal) return decimal.setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private static final class TrendBuckets {
        private final AdminReportPeriod period;
        private final Granularity granularity;
        private final LinkedHashMap<LocalDate, BigDecimal> totals = new LinkedHashMap<>();

        private TrendBuckets(AdminReportPeriod period, Granularity granularity) {
            this.period = period;
            this.granularity = granularity;
            if (granularity == Granularity.MONTHLY) {
                YearMonth month = YearMonth.from(period.startDate());
                YearMonth last = YearMonth.from(period.endDate());
                while (!month.isAfter(last)) {
                    totals.put(month.atDay(1), ZERO);
                    month = month.plusMonths(1);
                }
            } else {
                int step = granularity == Granularity.DAILY ? 1 : 7;
                for (LocalDate cursor = period.startDate(); !cursor.isAfter(period.endDate()); cursor = cursor.plusDays(step)) {
                    totals.put(cursor, ZERO);
                }
            }
        }

        static TrendBuckets forPeriod(AdminReportPeriod period) {
            // Up to 7 days is daily, a calendar-month-sized range is weekly, and longer ranges are monthly.
            Granularity granularity = period.daysInclusive() <= 7
                    ? Granularity.DAILY
                    : period.daysInclusive() <= 31 ? Granularity.WEEKLY : Granularity.MONTHLY;
            return new TrendBuckets(period, granularity);
        }

        void add(LocalDate date, BigDecimal value) {
            LocalDate key;
            if (granularity == Granularity.MONTHLY) {
                key = YearMonth.from(date).atDay(1);
            } else if (granularity == Granularity.WEEKLY) {
                long offset = java.time.temporal.ChronoUnit.DAYS.between(period.startDate(), date);
                key = period.startDate().plusDays((offset / 7) * 7);
            } else {
                key = date;
            }
            totals.computeIfPresent(key, (ignored, total) -> total.add(value));
        }

        List<ProfitTrendDTO> values() {
            List<ProfitTrendDTO> result = new ArrayList<>();
            for (Map.Entry<LocalDate, BigDecimal> entry : totals.entrySet()) {
                String label;
                if (granularity == Granularity.MONTHLY) {
                    label = entry.getKey().format(MONTH_LABEL);
                } else if (granularity == Granularity.WEEKLY) {
                    LocalDate bucketEnd = entry.getKey().plusDays(6);
                    if (bucketEnd.isAfter(period.endDate())) bucketEnd = period.endDate();
                    label = entry.getKey().format(DAY_LABEL) + " – " + bucketEnd.format(DAY_LABEL);
                } else {
                    label = entry.getKey().format(DAY_LABEL);
                }
                result.add(new ProfitTrendDTO(label, money(entry.getValue())));
            }
            return List.copyOf(result);
        }
    }

    private enum Granularity { DAILY, WEEKLY, MONTHLY }
}
