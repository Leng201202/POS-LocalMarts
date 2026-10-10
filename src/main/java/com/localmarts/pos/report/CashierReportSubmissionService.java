package com.localmarts.pos.report;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CashierReportSubmissionService {
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");
    private final CashierSaleReportRepository repository;
    private final UserRepository users;
    private final AuditService audits;
    private final ReportSnapshotCodec codec;
    private final Clock clock;
    @Autowired
    public CashierReportSubmissionService(CashierSaleReportRepository repository, UserRepository users,
            AuditService audits, ReportSnapshotCodec codec) {
        this(repository, users, audits, codec, Clock.system(ReportPeriod.ZONE));
    }
    CashierReportSubmissionService(CashierSaleReportRepository repository, UserRepository users,
            AuditService audits, ReportSnapshotCodec codec, Clock clock) {
        this.repository = repository; this.users = users; this.audits = audits; this.codec = codec; this.clock = clock;
    }
    @Transactional
    public Long submit(Long actorId, CashierReportSnapshot preview, BigDecimal realWorldCash) {
        if (preview == null || !preview.ownerActorId().equals(actorId))
            throw new IllegalArgumentException("This preview is unavailable for your account. Generate a new preview.");
        if (realWorldCash == null || realWorldCash.signum() < 0 || realWorldCash.scale() > 2 || realWorldCash.compareTo(MAX_MONEY) > 0)
            throw new IllegalArgumentException("Enter Real-World Cash between 0 and 999999999999.99 with at most two decimals.");
        realWorldCash = realWorldCash.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal difference = realWorldCash.subtract(preview.systemCash());
        if (preview.systemCash().abs().compareTo(MAX_MONEY) > 0 || difference.abs().compareTo(MAX_MONEY) > 0)
            throw new IllegalArgumentException("The report cash totals exceed the supported monetary range.");
        existing(actorId, preview).ifPresent(id -> { throw new DuplicateReportException(id); });
        // Reference only: identity/display values were already frozen at preview generation.
        var report = new CashierSaleReport(users.getReferenceById(actorId), preview,
                LocalDateTime.now(clock.withZone(ReportPeriod.ZONE)), realWorldCash, difference, codec.encode(preview));
        report = repository.saveAndFlush(report); // Enforce the DB uniqueness rule before emitting success.
        audits.logSuccess(actorId, AuditAction.CREATE, "CashierSaleReport", report.getId(), "Submitted cashier sale report",
                List.of(detail("reportType", preview.period().type()), detail("startDate", preview.period().startDate()),
                        detail("endDate", preview.period().endDate()), detail("generatedAt", preview.period().generatedAt()),
                        detail("cutoff", preview.period().cutoff()), detail("systemCash", preview.systemCash()),
                        detail("realWorldCash", realWorldCash), detail("cashDifference", difference), detail("snapshotVersion", 1)));
        return report.getId();
    }
    @Transactional(readOnly = true)
    public Optional<Long> existing(Long actorId, CashierReportSnapshot preview) {
        return repository.findByCashierIdAndStartDateAndEndDate(actorId, preview.period().startDate(), preview.period().endDate())
                .map(CashierSaleReport::getId);
    }
    @Transactional(readOnly = true)
    public List<SavedReportView> list(Long actorId) {
        return repository.findByCashierIdOrderBySubmittedAtDescIdDesc(actorId).stream()
                .map(row -> SavedReportView.from(row, null)).toList();
    }
    @Transactional(readOnly = true)
    public Optional<SavedReportView> view(Long actorId, Long id) {
        return repository.findByIdAndCashierId(id, actorId)
                .map(row -> SavedReportView.from(row, codec.decode(row.getSnapshotVersion(), row.getSnapshotJson())));
    }
    private AuditDetails detail(String field, Object value) {
        return AuditDetails.builder().fieldName(field).newData(value.toString()).build();
    }
}
