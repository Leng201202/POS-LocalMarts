package com.localmarts.pos.audit.entity;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audit_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,length = 100)
    private String fieldName;

    @Column(columnDefinition = "TEXT")
    private String oldData;

    @Column(columnDefinition = "TEXT")
    private String newData;

    @ManyToOne
    @JoinColumn(name = "audit_id", nullable = false)
    private Audit audit;
}
