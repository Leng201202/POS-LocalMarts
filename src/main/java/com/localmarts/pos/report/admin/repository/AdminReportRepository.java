package com.localmarts.pos.report.admin.repository;

import com.localmarts.pos.sale.entity.Sale;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AdminReportRepository {
    // Admin financial reports currently recognize only paid, fully completed sales.
    // Partially refunded, refunded, voided, pending, and failed sales are intentionally excluded.
    private static final String COMPLETED_FILTER =
            " s.paymentStatus = com.localmarts.pos.sale.entity.PaymentStatus.PAID "
            + "and s.saleStatus = com.localmarts.pos.sale.entity.SaleStatus.COMPLETED ";

    private final EntityManager entityManager;

    public Object[] findSalesBetween(LocalDateTime start, LocalDateTime end) {
        return entityManager.createQuery("""
                        select count(s), coalesce(sum(s.totalAmount), 0)
                        from Sale s
                        where s.timestamp >= :start and s.timestamp < :end
                          and """ + COMPLETED_FILTER, Object[].class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();
    }

    public List<Object[]> findCashierSales(LocalDateTime start, LocalDateTime end) {
        return entityManager.createQuery("""
                        select s.cashier.id, s.cashier.fullName, count(s), coalesce(sum(s.totalAmount), 0)
                        from Sale s
                        where s.timestamp >= :start and s.timestamp < :end
                          and """ + COMPLETED_FILTER + """
                        group by s.cashier.id, s.cashier.fullName
                        order by sum(s.totalAmount) desc, s.cashier.fullName asc
                        """, Object[].class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    public Object[] findProfitSummary(LocalDateTime start, LocalDateTime end) {
        return entityManager.createQuery("""
                        select coalesce(sum(si.netTotal), 0),
                               coalesce(sum(si.costPriceSnapshot * si.quantity), 0)
                        from SaleItem si join si.sale s
                        where s.timestamp >= :start and s.timestamp < :end
                          and """ + COMPLETED_FILTER, Object[].class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();
    }

    public List<Object[]> findProfitRows(LocalDateTime start, LocalDateTime end) {
        return entityManager.createQuery("""
                        select s.timestamp, si.netTotal, si.costPriceSnapshot, si.quantity
                        from SaleItem si join si.sale s
                        where s.timestamp >= :start and s.timestamp < :end
                          and """ + COMPLETED_FILTER + """
                        order by s.timestamp asc, si.id asc
                        """, Object[].class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    public List<Object[]> findProductPerformance(LocalDateTime start, LocalDateTime end) {
        return entityManager.createQuery("""
                        select si.productNameSnapshot,
                               coalesce(c.name, 'Uncategorized'),
                               coalesce(si.unitSnapshot, ''),
                               sum(si.quantity),
                               coalesce(sum(si.netTotal), 0),
                               coalesce(sum(si.costPriceSnapshot * si.quantity), 0)
                        from SaleItem si join si.sale s join si.product p left join p.category c
                        where s.timestamp >= :start and s.timestamp < :end
                          and """ + COMPLETED_FILTER + """
                        group by p.id, si.productNameSnapshot, c.name, si.unitSnapshot
                        """, Object[].class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    public List<Object[]> findPaymentSummary(LocalDateTime start, LocalDateTime end) {
        return entityManager.createQuery("""
                        select s.paymentMethod, coalesce(sum(s.totalAmount), 0)
                        from Sale s
                        where s.timestamp >= :start and s.timestamp < :end
                          and """ + COMPLETED_FILTER + """
                        group by s.paymentMethod
                        order by sum(s.totalAmount) desc
                        """, Object[].class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    public List<Sale> findSalesForExport(LocalDateTime start, LocalDateTime end, Long cashierId) {
        return entityManager.createQuery("""
                        select s
                        from Sale s
                        join fetch s.cashier
                        left join fetch s.customer
                        where s.timestamp >= :start and s.timestamp < :end
                          and (:cashierId is null or s.cashier.id = :cashierId)
                          and """ + COMPLETED_FILTER + """
                        order by s.timestamp asc, s.id asc
                        """, Sale.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("cashierId", cashierId)
                .getResultList();
    }
}
