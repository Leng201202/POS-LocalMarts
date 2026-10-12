package com.localmarts.pos.report;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReportRepository {
    private final EntityManager entityManager;

    public Object[] findAllSalesSummary() {
        return entityManager.createQuery(
                """
            SELECT COUNT(s.id),SUM (s.totalAmount)
"""
        )
    }

    public Object[] findSalesBetween(
            LocalDateTime start,
            LocalDateTime end
    ) {
        return entityManager.createQuery(
                        """
                        SELECT
                            COUNT(s.id),
                            SUM(s.totalAmount)
                        FROM Sale s
                        WHERE s.timestamp >= :start
                          AND s.timestamp < :end
                          AND s.saleStatus = 'COMPLETED'
                        """,
                        Object[].class
                )
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();
    }

    public Object[] findProfitSummary(LocalDateTime start, LocalDateTime end) {
        return entityManager.createQuery(
                """
            SELECT SUM (si.netTotal),SUM(si.costPriceSnapshot*si.quantity)
            FROM SaleItem si 
            JOIN si.sale s 
            WHERE s.timestamp >= :start AND s.timestamp< :end
            AND s.saleStatus='COMPLETED'
"""         ,Object[].class
        )
                .setParameter("start",start)
                .setParameter("end",end)
                .getSingleResult();
    }

    public List<Object[]> findPaymentSummary(LocalDateTime start, LocalDateTime end) {
        return entityManager.createQuery("""
          SELECT 
          s.paymentMethod,
          SUM (s.totalAmount)
          FROM Sale s 
          WHERE s.timestamp >= :start AND s.timestamp< :end
          AND s.saleStatus='COMPLETED'
          GROUP BY s.paymentMethod
          ORDER BY SUM(s.totalAmount) DESC 
""",
                Object[].class)
                .setParameter("start",start)
                .setParameter("end",end)
                .getResultList();
    }
}
