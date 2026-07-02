package com.localmarts.pos.common;


import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {
    @Column(nullable = false)
    private Boolean active=true;

    @Column(nullable = false)
    private Boolean deleted=false;

    @CreationTimestamp
    @Column(nullable = false,updatable = false)
    private LocalDateTime created_at;

    @UpdateTimestamp
    private LocalDateTime updated_at;

    private LocalDateTime deleted_at;

    @Column(nullable = false,updatable = false)
    private Long created_by;

    private Long updatedBy;

    private Long deletedBy;
}
