package com.localmarts.pos.unit.entity;

import com.localmarts.pos.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Unit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String abbreviation;

}
