package com.localmarts.pos.report.cashier.service;

import com.localmarts.pos.report.cashier.dto.ReportPeriodRequest;
import com.localmarts.pos.report.cashier.model.CashierReportSnapshot;
import com.localmarts.pos.report.cashier.model.ReportType;
import com.localmarts.pos.audit.AuditRepository;
import com.localmarts.pos.audit.entity.Audit;
import com.localmarts.pos.audit.utils.AuditStatus;
import com.localmarts.pos.sale.SaleRepository;
import com.localmarts.pos.sale.entity.*;
import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import static com.localmarts.pos.report.cashier.fixture.ReportFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** Uses only the test profile's in-memory SQLite database. */
@SpringBootTest
@Transactional
class CashierSaleReportIntegrationTest {
    @Autowired EntityManager em;
    @Autowired SaleRepository sales;
    @Autowired AuditRepository audits;
    @Autowired UserRepository users;
    User persistUser(long label) {
        User user=user(label);user.setId(null);user.setCreatedBy(0L);em.persist(user);return user;
    }
    Sale persistSale(long label,User cashier,PaymentMethod method) {
        Sale sale=sale(label,cashier.getId(),method);sale.setId(null);sale.setCashier(cashier);
        var item=sale.getItems().getFirst();item.setId(null);var product=item.getProduct();
        product.setId(null);product.setCreatedBy(cashier.getId());em.persist(product);em.persist(sale);em.flush();
        // Match existing SQLite integration fixtures: the test dialect generates untyped
        // identity columns for these entities. Populate IDs only in this in-memory fixture.
        for (String table : List.of("products", "sales", "sale_items"))
            em.createNativeQuery("update " + table + " set id = rowid where id is null").executeUpdate();
        return sale;
    }
    Audit persistRefund(long label,Sale sale,long actor,boolean legacy) {
        Audit audit=audit(label,sale,actor,legacy);audit.setId(null);em.persist(audit);em.flush();
        setRefundTime(audit);return audit;
    }
    void setRefundTime(Audit audit) {
        // @CreationTimestamp is insert-generated and immutable to ORM updates.
        em.createNativeQuery("update audit_log set timestamp = :timestamp where id = :id")
                .setParameter("timestamp", java.sql.Timestamp.valueOf(REFUND_TIME).getTime())
                .setParameter("id", audit.getId()).executeUpdate();
    }
    CashierSaleReportService service() {
        return new CashierSaleReportService(sales,audits,users,new RefundAuditReader(),new CashierSaleReportCalculator(),CLOCK);
    }
    ReportPeriodRequest request(LocalDate start, LocalDate end) {
        var r=new ReportPeriodRequest();r.setType(ReportType.CUSTOM);r.setStartDate(start);r.setEndDate(end);return r;
    }
    @Test void databaseQueriesIncludeRefundedSalesAndSeparateCrossCashierActivityWithoutWrites() {
        User seller=persistUser(21),processor=persistUser(22);
        Sale sale=persistSale(101,seller,PaymentMethod.CASH);
        sale.setSaleStatus(SaleStatus.PARTIALLY_REFUNDED);sale.getItems().getFirst().setRefundQuantity(3);
        Audit audit=persistRefund(49,sale,processor.getId(),true);
        Long sellerId=seller.getId(),processorId=processor.getId();
        em.flush();em.clear();
        long saleCount=sales.count(),auditCount=audits.count();
        var request=request(LocalDate.of(2026,9,3),LocalDate.of(2026,9,4));
        var sellerReport=service().preview(sellerId,request);
        assertThat(sellerReport.summary().transactions()).isEqualTo(1);
        assertThat(sellerReport.summary().refundCount()).isEqualTo(1);
        assertThat(sellerReport.summary().refundAmount()).isEqualByComparingTo("72.50");
        assertThat(sellerReport.systemCash()).isEqualByComparingTo("95.00");
        assertThat(sellerReport.legacyUnitSplitsUnavailable()).isTrue();
        var processorReport=service().preview(processorId,request);
        assertThat(processorReport.summary().transactions()).isZero();
        assertThat(processorReport.refundsProcessed()).extracting(CashierReportSnapshot.RefundReportLine::auditId).containsExactly(audit.getId());
        assertThat(processorReport.systemCash()).isEqualByComparingTo("-72.50");
        var historical=service().preview(sellerId,request(LocalDate.of(2026,9,3),LocalDate.of(2026,9,3)));
        assertThat(historical.sales().getFirst().status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(historical.sales().getFirst().returnedQuantity()).isZero();
        assertThat(sales.count()).isEqualTo(saleCount);assertThat(audits.count()).isEqualTo(auditCount);
    }
    @Test void midnightBoundsPaymentStatusOwnershipAndAuditDistinctAreEnforcedByQueries() {
        User cashier=persistUser(31),other=persistUser(32);
        Sale included=persistSale(201,cashier,PaymentMethod.QR);included.setTimestamp(LocalDate.of(2026,9,3).atStartOfDay());
        Sale atEnd=persistSale(202,cashier,PaymentMethod.CASH);atEnd.setTimestamp(LocalDate.of(2026,9,4).atStartOfDay());
        Sale voided=persistSale(203,cashier,PaymentMethod.CARD);voided.setSaleStatus(SaleStatus.VOIDED);
        Sale unpaid=persistSale(204,cashier,PaymentMethod.CARD);unpaid.setPaymentStatus(PaymentStatus.FAILED);
        persistSale(205,other,PaymentMethod.CASH);
        Audit audit=persistRefund(49,included,cashier.getId(),false);
        Audit failed=persistRefund(50,included,cashier.getId(),false);failed.setStatus(AuditStatus.FAILED);
        em.flush();em.clear();
        var found=sales.findCashierReportSales(cashier.getId(),LocalDate.of(2026,9,3).atStartOfDay(),LocalDate.of(2026,9,4).atStartOfDay());
        assertThat(found).extracting(Sale::getId).containsExactly(included.getId());
        var events=audits.findCashierReportRefundCandidates(cashier.getId(),List.of(included.getId()),all().start(),all().cutoff());
        assertThat(events).extracting(Audit::getId).containsExactly(audit.getId());
        assertThat(events.getFirst().getDetails()).hasSize(8);
    }
    @Test void malformedRefundTotalIsNotExcludedByQueryAndFailsPreview() {
        User cashier=persistUser(41);Sale sale=persistSale(301,cashier,PaymentMethod.CASH);
        sale.getItems().getFirst().setRefundQuantity(3);
        Audit audit=audit(49,sale,cashier.getId(),true);audit.setId(null);
        audit.getDetails().removeIf(d -> d.getFieldName().equals("refundTotal"));
        em.persist(audit);em.flush();setRefundTime(audit);em.clear();
        assertThatThrownBy(() -> service().preview(cashier.getId(),request(LocalDate.of(2026,9,3),LocalDate.of(2026,9,4))))
                .hasMessageContaining("missing refundTotal");
    }
}
