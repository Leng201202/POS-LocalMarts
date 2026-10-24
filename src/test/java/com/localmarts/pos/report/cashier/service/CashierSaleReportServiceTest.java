package com.localmarts.pos.report.cashier.service;

import com.localmarts.pos.report.cashier.dto.ReportPeriodRequest;
import com.localmarts.pos.report.cashier.model.ReportType;
import com.localmarts.pos.audit.AuditRepository;
import com.localmarts.pos.sale.SaleRepository;
import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.user.UserRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static com.localmarts.pos.report.cashier.fixture.ReportFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CashierSaleReportServiceTest {
    final SaleRepository sales=mock(SaleRepository.class);
    final AuditRepository audits=mock(AuditRepository.class);
    final UserRepository users=mock(UserRepository.class);
    final CashierSaleReportService service=new CashierSaleReportService(sales,audits,users,new RefundAuditReader(),new CashierSaleReportCalculator(),CLOCK);
    ReportPeriodRequest request() {
        ReportPeriodRequest r=new ReportPeriodRequest();r.setType(ReportType.CUSTOM);
        r.setStartDate(LocalDate.of(2026,9,3));r.setEndDate(LocalDate.of(2026,9,4));return r;
    }
    @Test void ownerQueriesAndSnapshotAreReadOnlyAndLegacyIsAllowed() {
        var sale=sale(1,2,PaymentMethod.CASH);sale.getItems().getFirst().setRefundQuantity(3);sale.setSaleStatus(com.localmarts.pos.sale.entity.SaleStatus.PARTIALLY_REFUNDED);
        when(users.findById(2L)).thenReturn(Optional.of(user(2)));
        when(sales.findCashierReportSales(eq(2L),any(),any())).thenReturn(List.of(sale));
        when(audits.findCashierReportRefundCandidates(eq(2L),eq(List.of(1L)),any(),any())).thenReturn(List.of(audit(49,sale,2,true)));
        var result=service.preview(2L,request());
        assertThat(result.ownerActorId()).isEqualTo(2L);assertThat(result.systemCash()).isEqualByComparingTo("22.50");
        assertThat(result.legacyUnitSplitsUnavailable()).isTrue();
        verify(sales,never()).save(any());verify(audits,never()).save(any());
        sale.setTotalAmount(money("1"));sale.getCashier().setFullName("Changed");
        assertThat(result.summary().salesAmount()).isEqualByComparingTo("95");assertThat(result.cashierName()).isEqualTo("Cashier 2");
        assertThatThrownBy(() -> result.sales().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void historicalPreviewUsesAuditTimingRatherThanTodaysRefundQuantity() {
        var sale=sale(1,2,PaymentMethod.CASH);sale.getItems().getFirst().setRefundQuantity(3);sale.setSaleStatus(com.localmarts.pos.sale.entity.SaleStatus.PARTIALLY_REFUNDED);
        when(users.findById(2L)).thenReturn(Optional.of(user(2)));
        when(sales.findCashierReportSales(eq(2L),any(),any())).thenReturn(List.of(sale));
        when(audits.findCashierReportRefundCandidates(eq(2L),anyList(),any(),any())).thenReturn(List.of(audit(49,sale,2,false)));
        var r=request();r.setEndDate(LocalDate.of(2026,9,3));
        assertThat(service.preview(2L,r).sales().getFirst().returnedQuantity()).isZero();
    }
    @Test void missingRefundHistoryFailsInsteadOfReportingUnrefundedSale() {
        var sale=sale(1,2,PaymentMethod.CASH);sale.getItems().getFirst().setRefundQuantity(3);sale.setSaleStatus(com.localmarts.pos.sale.entity.SaleStatus.PARTIALLY_REFUNDED);
        when(users.findById(2L)).thenReturn(Optional.of(user(2)));
        when(sales.findCashierReportSales(eq(2L),any(),any())).thenReturn(List.of(sale));
        when(audits.findCashierReportRefundCandidates(eq(2L),anyList(),any(),any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.preview(2L,request())).hasMessageContaining("missing or inconsistent structured refund history");
    }
    @Test void refundedStatusWithoutStructuredHistoryFails() {
        var sale=sale(1,2,PaymentMethod.CASH);sale.setSaleStatus(com.localmarts.pos.sale.entity.SaleStatus.REFUNDED);
        when(users.findById(2L)).thenReturn(Optional.of(user(2)));
        when(sales.findCashierReportSales(eq(2L),any(),any())).thenReturn(List.of(sale));
        when(audits.findCashierReportRefundCandidates(eq(2L),anyList(),any(),any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.preview(2L,request())).hasMessageContaining("status disagrees");
    }
    @Test void processesCrossCashierRefundWithoutAddingOriginalSaleToOwnSales() {
        var sale=sale(1,3,PaymentMethod.CASH);
        when(users.findById(2L)).thenReturn(Optional.of(user(2)));
        when(sales.findCashierReportSales(eq(2L),any(),any())).thenReturn(List.of());
        when(audits.findCashierReportRefundCandidates(eq(2L),eq(List.of(-1L)),any(),any())).thenReturn(List.of(audit(49,sale,2,false)));
        when(sales.findRefundDetailsById(1L)).thenReturn(Optional.of(sale));
        var r=service.preview(2L,request());assertThat(r.sales()).isEmpty();assertThat(r.systemCash()).isEqualByComparingTo("-72.50");
    }
    @Test void missingLinkedSaleAndNonexistentActorFail() {
        var sale=sale(1,2,PaymentMethod.CASH);
        when(users.findById(2L)).thenReturn(Optional.of(user(2)));
        when(sales.findCashierReportSales(eq(2L),any(),any())).thenReturn(List.of());
        when(audits.findCashierReportRefundCandidates(eq(2L),anyList(),any(),any())).thenReturn(List.of(audit(49,sale,3,false)));
        assertThatThrownBy(() -> service.preview(2L,request())).hasMessageContaining("linked Sale");
        when(sales.findRefundDetailsById(1L)).thenReturn(Optional.of(sale));
        assertThatThrownBy(() -> service.preview(2L,request())).hasMessageContaining("unavailable processing actor");
    }
}
