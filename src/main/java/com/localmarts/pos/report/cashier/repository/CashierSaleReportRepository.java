package com.localmarts.pos.report.cashier.repository;

import com.localmarts.pos.report.cashier.entity.CashierSaleReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CashierSaleReportRepository extends JpaRepository<CashierSaleReport, Long> {
    List<CashierSaleReport> findByCashierIdOrderBySubmittedAtDescIdDesc(Long actorId);
    Optional<CashierSaleReport> findByIdAndCashierId(Long id, Long actorId);
    Optional<CashierSaleReport> findByCashierIdAndStartDateAndEndDate(Long actorId, LocalDate start, LocalDate end);
}
