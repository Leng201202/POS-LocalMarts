package com.localmarts.pos.audit;

import com.localmarts.pos.audit.entity.Audit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuditRepository extends JpaRepository<Audit, Long> {
    List<Audit> findAllByOrderByTimestampDesc();
    Page<Audit> findAllByOrderByTimestampDescIdDesc(Pageable pageable);
}
