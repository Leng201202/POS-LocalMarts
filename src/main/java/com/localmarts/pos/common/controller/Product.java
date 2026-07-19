package com.localmarts.pos.common.controller;

import com.localmarts.pos.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {
    @Id
    private Long id;

    private String name;

    private Integer stockQuantity;

    private String barcode;

    private String size;

}
