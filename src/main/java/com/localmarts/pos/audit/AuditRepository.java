package com.localmarts.pos.audit;

import com.localmarts.pos.audit.entity.Audit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRepository extends JpaRepository<Audit, Long> {
    List<Audit> findAllByOrderByTimestampDesc();
}
