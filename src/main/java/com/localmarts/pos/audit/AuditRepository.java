package com.localmarts.pos.audit;

import com.localmarts.pos.audit.entity.Audit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

import java.util.List;

public interface AuditRepository extends JpaRepository<Audit, Long> {
    // Successful Sale UPDATEs are the existing refund event convention. Include malformed
    // ownership/time links conservatively so incomplete financial history is not silently omitted.
    @EntityGraph(attributePaths = "details")
    @Query("""
            select distinct a from Audit a
            where a.entityName = 'Sale'
              and a.action = com.localmarts.pos.audit.utils.AuditAction.UPDATE
              and a.status = com.localmarts.pos.audit.utils.AuditStatus.SUCCESS
              and (a.rowId in :saleIds
                or ((a.actor = :actorId or a.actor is null or a.rowId is null)
                    and (a.timestamp is null or (a.timestamp >= :start and a.timestamp < :cutoff))))
            order by a.timestamp, a.id
            """)
    List<Audit> findCashierReportRefundCandidates(
            @Param("actorId") Long actorId,
            @Param("saleIds") List<Long> saleIds,
            @Param("start") LocalDateTime start,
            @Param("cutoff") LocalDateTime cutoff);

    List<Audit> findAllByOrderByTimestampDesc();
    Page<Audit> findAllByOrderByTimestampDescIdDesc(Pageable pageable);
}
