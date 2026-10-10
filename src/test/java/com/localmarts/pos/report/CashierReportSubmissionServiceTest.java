package com.localmarts.pos.report;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.localmarts.pos.report.ReportFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CashierReportSubmissionServiceTest {
    final CashierSaleReportRepository repository = mock(CashierSaleReportRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final AuditService audits = mock(AuditService.class);
    final ReportSnapshotCodec codec = new ReportSnapshotCodec(JsonMapper.builder().build());
    final Clock submittedClock = Clock.fixed(Instant.parse("2026-09-05T05:00:00Z"), ZoneOffset.UTC);
    final CashierReportSubmissionService service = new CashierReportSubmissionService(repository, users, audits, codec, submittedClock);

    CashierReportSnapshot preview() {
        var sale = sale(1,2,com.localmarts.pos.sale.entity.PaymentMethod.CASH);
        return new CashierSaleReportCalculator().calculate(2L,"cashier2","Original Name",all(),List.of(sale),List.of());
    }
    @ParameterizedTest @CsvSource({"100.00,5.00,OVER", "90.00,-5.00,SHORT", "95.00,0.00,BALANCED"})
    void savesExactPreviewAndServerDifferenceAndOneStructuredAudit(String actual, String difference, String status) {
        var preview = preview();
        var actor = user(2);
        when(users.getReferenceById(2L)).thenReturn(actor);
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service.submit(2L,preview,money(actual));
        var capture = ArgumentCaptor.forClass(CashierSaleReport.class);
        verify(repository).saveAndFlush(capture.capture());
        var row = capture.getValue();
        assertThat(row.getCashier()).isSameAs(actor);
        assertThat(row.getReportType()).isEqualTo(ReportType.CUSTOM);
        assertThat(row.getStartDate()).isEqualTo(preview.period().startDate());
        assertThat(row.getEndDate()).isEqualTo(preview.period().endDate());
        assertThat(row.getGeneratedAt()).isEqualTo(preview.period().generatedAt());
        assertThat(row.getSubmittedAt()).isEqualTo(LocalDateTime.of(2026,9,5,12,0));
        assertThat(row.getSystemCash()).isEqualByComparingTo("95.00");
        assertThat(row.getRealWorldCash()).isEqualByComparingTo(actual);
        assertThat(row.getCashDifference()).isEqualByComparingTo(difference);
        assertThat(SavedReportView.from(row,preview).status()).isEqualTo(status);
        assertThat(row.getSnapshotVersion()).isEqualTo(1);
        assertThat(codec.decode(1,row.getSnapshotJson())).isEqualTo(preview);
        verify(audits).logSuccess(eq(2L),eq(AuditAction.CREATE),eq("CashierSaleReport"),isNull(),anyString(),argThat(details -> details.size() == 9));
        verifyNoMoreInteractions(audits);
        verify(users,never()).findById(any());
    }
    @Test void negativeSystemCashIsValid() {
        var sale = sale(1,3,com.localmarts.pos.sale.entity.PaymentMethod.CASH);
        var event = new RefundAuditReader().read(audit(49,sale,2,false),sale);
        var preview = new CashierSaleReportCalculator().calculate(2L,"cashier2","Cashier",all(),List.of(),List.of(event));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service.submit(2L,preview,money("0"));
        verify(repository).saveAndFlush(argThat(row -> row.getSystemCash().compareTo(money("-72.50")) == 0
                && row.getCashDifference().compareTo(money("72.50")) == 0));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"-1", "1.001", "1000000000000.00"})
    void invalidCashRejected(String amount) {
        BigDecimal value = amount == null || amount.isEmpty() ? null : money(amount);
        assertThatThrownBy(() -> service.submit(2L,preview(),value)).hasMessageContaining("Real-World Cash");
        verifyNoInteractions(repository,users,audits);
    }
    @Test void wrongOwnerAndMissingPreviewRejected() {
        assertThatThrownBy(() -> service.submit(3L,preview(),money("1"))).hasMessageContaining("unavailable");
        assertThatThrownBy(() -> service.submit(2L,null,money("1"))).hasMessageContaining("unavailable");
        verifyNoInteractions(repository,users,audits);
    }
    @Test void duplicateRangeRejectedRegardlessOfType() {
        var p = preview();
        var existing = new CashierSaleReport(user(2),p,LocalDateTime.now(),money("95"),money("0"),codec.encode(p));
        org.springframework.test.util.ReflectionTestUtils.setField(existing,"id",8L);
        when(repository.findByCashierIdAndStartDateAndEndDate(2L,p.period().startDate(),p.period().endDate())).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.submit(2L,p,money("1"))).isInstanceOf(DuplicateReportException.class);
        verify(repository,never()).saveAndFlush(any()); verifyNoInteractions(audits);
    }
    @Test void viewAndListUseOwnerQueriesOnlyAndNeverReloadUserNames() {
        var p = preview();
        var row = new CashierSaleReport(user(2),p,LocalDateTime.now(),money("95"),money("0"),codec.encode(p));
        when(repository.findByIdAndCashierId(5L,2L)).thenReturn(Optional.of(row));
        when(repository.findByCashierIdOrderBySubmittedAtDescIdDesc(2L)).thenReturn(List.of(row));
        row.getCashier().setFullName("Changed current name");
        assertThat(service.view(2L,5L).orElseThrow().snapshot().cashierName()).isEqualTo("Original Name");
        assertThat(service.list(2L)).hasSize(1);
        assertThat(service.view(3L,5L)).isEmpty();
        assertThat(service.view(2L,999L)).isEmpty();
        verify(repository,never()).findById(any());verifyNoInteractions(users,audits);
    }
}
