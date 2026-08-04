package com.localmarts.pos.product.entity;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.localmarts.pos.category.entity.Category;
import com.localmarts.pos.common.BaseEntity;
import com.localmarts.pos.unit.entity.Unit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String barcode;

    private String size;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate mfd;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate exp;

    private BigDecimal sellingPrice;

    private Integer stockQuantity;

    private Integer minimumStock;

    private BigDecimal costPrice;

    @Column(
        name = "discount_percent",
        nullable = false,
        precision = 5,
        scale = 2
    )
    @Builder.Default
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @ManyToOne
    @JoinColumn(name = "category_id") 
    private Category category;

    @ManyToOne
    @JoinColumn(name = "unit_id")
    private Unit unit;
}
