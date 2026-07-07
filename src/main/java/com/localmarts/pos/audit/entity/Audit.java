package com.localmarts.pos.audit.entity;

import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.audit.utils.AuditStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Audit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long actor;

    @Column(nullable = false,length = 20)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AuditStatus status;

    @Column(nullable = false,length = 100)
    private String entityName;

    @Column()
    private Long rowId;

    @OneToMany(mappedBy = "audit",cascade = CascadeType.ALL,orphanRemoval = true)
    @Builder.Default
    private List<AuditDetails> details=new ArrayList<>();


    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp()
    private LocalDateTime timestamp;


}
