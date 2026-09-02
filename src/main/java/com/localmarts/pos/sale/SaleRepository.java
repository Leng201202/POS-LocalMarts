package com.localmarts.pos.sale;

import com.localmarts.pos.sale.entity.Sale;
import com.localmarts.pos.sale.entity.SaleStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    boolean existsByInvoiceNo(String invoiceNo);

    @Query("""
            select count(s)
            from Sale s
            where s.cashier.id = :cashierId
              and s.saleStatus = :saleStatus
              and s.timestamp >= :start
              and s.timestamp < :end
            """)
    long countCompletedSalesForCashier(
            @Param("cashierId") Long cashierId,
            @Param("saleStatus") SaleStatus saleStatus,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            select coalesce(sum(s.totalAmount), 0)
            from Sale s
            where s.cashier.id = :cashierId
              and s.saleStatus = :saleStatus
              and s.timestamp >= :start
              and s.timestamp < :end
            """)
    BigDecimal sumTotalAmountForCashier(
            @Param("cashierId") Long cashierId,
            @Param("saleStatus") SaleStatus saleStatus,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    List<Sale> findTop10ByCashierIdOrderByTimestampDesc(Long cashierId);

    @EntityGraph(attributePaths = {"customer", "items"})
    List<Sale> findByCashierIdOrderByTimestampDesc(Long cashierId);

    @EntityGraph(attributePaths = {"cashier", "customer", "items", "items.product"})
    @Query("""
            select distinct s
            from Sale s
            left join s.customer c
            where s.saleStatus = :status
              and s.timestamp >= :start
              and s.timestamp <= :end
              and (:invoiceNo is null or upper(s.invoiceNo) = upper(:invoiceNo))
              and (:customerPhone is null or c.phoneNumber = :customerPhone)
              and (:totalAmount is null or s.totalAmount = :totalAmount)
              and (:paymentMethod is null or s.paymentMethod = :paymentMethod)
            order by s.timestamp desc
            """)
    List<Sale> searchRefundEligibleSales(
            @Param("status") SaleStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("invoiceNo") String invoiceNo,
            @Param("customerPhone") String customerPhone,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("paymentMethod") com.localmarts.pos.sale.entity.PaymentMethod paymentMethod
    );

    @EntityGraph(attributePaths = {"cashier", "customer", "items", "items.product"})
    @Query("select distinct s from Sale s where s.id = :id")
    Optional<Sale> findRefundDetailsById(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Sale s
            set s.saleStatus = :newStatus
            where s.id = :saleId
              and s.saleStatus = :expectedStatus
            """)
    int claimRefund(
            @Param("saleId") Long saleId,
            @Param("expectedStatus") SaleStatus expectedStatus,
            @Param("newStatus") SaleStatus newStatus
    );

    @EntityGraph(attributePaths = {"cashier", "items"})
    @Query("select distinct s from Sale s where s.id = :id")
    Optional<Sale> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"cashier", "items"})
    @Query("""
            select distinct s
            from Sale s
            where s.id = :id
              and s.cashier.id = :cashierId
            """)
    Optional<Sale> findDetailedByIdAndCashierId(
            @Param("id") Long id,
            @Param("cashierId") Long cashierId
    );
}
