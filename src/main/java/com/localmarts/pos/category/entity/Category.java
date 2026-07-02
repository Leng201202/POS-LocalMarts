package com.localmarts.pos.category.entity;

import com.localmarts.pos.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories",uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;


}
