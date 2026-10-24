package com.localmarts.pos.report.cashier.repository;

import com.localmarts.pos.report.cashier.config.CashierReportSchema;
import com.localmarts.pos.report.cashier.entity.CashierSaleReport;
import com.localmarts.pos.report.cashier.exception.DuplicateReportException;
import com.localmarts.pos.report.cashier.model.CashierReportSnapshot;
import com.localmarts.pos.report.cashier.model.ReportPeriod;
import com.localmarts.pos.report.cashier.model.ReportType;
import com.localmarts.pos.report.cashier.model.SavedReportView;
import com.localmarts.pos.report.cashier.service.CashierSaleReportCalculator;
import com.localmarts.pos.report.cashier.service.CashierReportSubmissionService;
import com.localmarts.pos.report.cashier.service.ReportSnapshotCodec;
import com.localmarts.pos.audit.AuditRepository;
import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.localmarts.pos.report.cashier.fixture.ReportFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:sqlite:file:cashier-report-phase2-tests?mode=memory&cache=shared")
@Transactional
class CashierReportPersistenceTest {
    @Autowired CashierReportSubmissionService service;
    @Autowired CashierSaleReportRepository repository;
    @Autowired UserRepository users;
    @Autowired AuditRepository audits;
    @Autowired ReportSnapshotCodec codec;
    @Autowired CashierReportSchema schema;
    @Autowired EntityManager em;
    @MockitoSpyBean AuditService auditService;

    User cashier(String suffix) {
        var u=user(2);u.setId(null);u.setUsername("report-"+suffix);u.setFullName("Original name");u.setCreatedBy(0L);
        return users.saveAndFlush(u);
    }
    CashierReportSnapshot preview(User u, ReportType type, LocalDate start, LocalDate end) {
        var p=new ReportPeriod(type,start,end,LocalDateTime.of(2026,10,1,10,0),end.plusDays(1).atStartOfDay());
        return new CashierSaleReportCalculator().calculate(u.getId(),u.getUsername(),u.getFullName(),p,List.of(),List.of());
    }
    CashierReportSnapshot preview(User u) { return preview(u,ReportType.DAILY,LocalDate.of(2026,9,30),LocalDate.of(2026,9,30)); }

    @Test void persistsAndReadsImmutableSnapshotAndExactlyOneSuccessfulAudit() {
        var u=cashier("save");var p=preview(u);long before=audits.count();
        Long id=service.submit(u.getId(),p,money("12.34"));em.flush();em.clear();
        var row=repository.findByIdAndCashierId(id,u.getId()).orElseThrow();
        assertThat(row.getSnapshotVersion()).isEqualTo(1);
        assertThat(row.getRealWorldCash()).isEqualByComparingTo("12.34");
        assertThat(row.getCashDifference()).isEqualByComparingTo("12.34");
        assertThat(row.getGeneratedAt()).isEqualTo(p.period().generatedAt());
        assertThat(row.getSubmittedAt()).isAfter(p.period().generatedAt());
        assertThat(audits.count()).isEqualTo(before+1);
        var audit=audits.findAll().stream().filter(a->"CashierSaleReport".equals(a.getEntityName()) && id.equals(a.getRowId())).findFirst().orElseThrow();
        assertThat(audit.getActor()).isEqualTo(u.getId());assertThat(audit.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(audit.getDetails()).hasSize(9);
        em.createNativeQuery("update users set full_name='Changed live name', username='changed-live-username' where id=:id")
                .setParameter("id",u.getId()).executeUpdate();em.clear();
        var view=service.view(u.getId(),id).orElseThrow();
        assertThat(view.snapshot()).isEqualTo(p);
        assertThat(view.snapshot().cashierName()).isEqualTo("Original name");
        assertThat(view.snapshot().cashierUsername()).isEqualTo("report-save");
    }
    @Test void exactRangeRejectsDifferentTypeButOverlappingPeriodsAndOtherCashiersAreAllowed() {
        var u=cashier("ranges");var other=cashier("other");var p=preview(u);
        Long first=service.submit(u.getId(),p,money("0"));
        var custom=preview(u,ReportType.CUSTOM,p.period().startDate(),p.period().endDate());
        assertThatThrownBy(()->service.submit(u.getId(),custom,money("0"))).isInstanceOf(DuplicateReportException.class);
        var overlap=preview(u,ReportType.WEEKLY,LocalDate.of(2026,9,24),LocalDate.of(2026,9,30));
        Long second=service.submit(u.getId(),overlap,money("0"));
        Long otherReport=service.submit(other.getId(),preview(other),money("0"));
        assertThat(service.list(u.getId())).extracting(SavedReportView::id).containsExactly(second,first);
        assertThat(service.list(other.getId())).extracting(SavedReportView::id).containsExactly(otherReport);
        assertThat(service.view(other.getId(),first)).isEmpty();
        assertThat(service.view(other.getId(),Long.MAX_VALUE)).isEmpty();
    }
    @Test void databaseConstraintRejectsDuplicateEvenWhenApplicationPrecheckIsBypassed() {
        var u=cashier("constraint");var p=preview(u);
        service.submit(u.getId(),p,money("0"));
        // Represents the losing insert after two requests both observed no existing range.
        var duplicate=new CashierSaleReport(u,p,LocalDateTime.now(),money("1"),money("1"),codec.encode(p));
        assertThatThrownBy(()->repository.saveAndFlush(duplicate)).isInstanceOf(DataAccessException.class).hasMessageContaining("SQLITE_CONSTRAINT_UNIQUE");
    }
    @Test void sqliteSchemaHasIntegerIdentityForeignKeyAndIdempotentUniqueIndex() {
        schema.afterSingletonsInstantiated();schema.afterSingletonsInstantiated();
        var columns=em.createNativeQuery("pragma table_info(cashier_sale_reports)").getResultList();
        assertThat(columns).anySatisfy(value -> {
            Object[] row=(Object[])value;
            assertThat(row[1]).isEqualTo("id");assertThat(row[2].toString()).isEqualToIgnoringCase("INTEGER");
            assertThat(((Number)row[5]).intValue()).isEqualTo(1);
        });
        var index=em.createNativeQuery("pragma index_info(uk_cashier_report_period)").getResultList();
        assertThat(index).extracting(value->((Object[])value)[2]).containsExactly("cashier_id","start_date","end_date");
        var fks=em.createNativeQuery("pragma foreign_key_list(cashier_sale_reports)").getResultList();
        assertThat(fks).anySatisfy(value->{
            Object[] row=(Object[])value;assertThat(row[2]).isEqualTo("users");assertThat(row[3]).isEqualTo("cashier_id");
        });
    }
    @Test void unsupportedStoredVersionFailsClearly() {
        var u=cashier("version");Long id=service.submit(u.getId(),preview(u),money("0"));
        em.createNativeQuery("update cashier_sale_reports set snapshot_version=2 where id=:id").setParameter("id",id).executeUpdate();em.clear();
        assertThatThrownBy(()->service.view(u.getId(),id)).hasMessageContaining("Unsupported saved report snapshot version");
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void reportAndAuditRollbackTogetherIfAuditCannotBeSaved() {
        var u=cashier("rollback");long before=repository.count(), auditsBefore=audits.count();
        doThrow(new IllegalStateException("audit write failed")).when(auditService)
                .logSuccess(eq(u.getId()),eq(AuditAction.CREATE),eq("CashierSaleReport"),any(),anyString(),anyList());
        try {
            assertThatThrownBy(()->service.submit(u.getId(),preview(u),money("0"))).hasMessageContaining("audit write failed");
            assertThat(repository.count()).isEqualTo(before);assertThat(audits.count()).isEqualTo(auditsBefore);
        } finally { reset(auditService); }
    }
}
