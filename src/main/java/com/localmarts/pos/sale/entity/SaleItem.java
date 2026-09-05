package com.localmarts.pos.sale.entity;

import com.localmarts.pos.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "barcode_snapshot", nullable = false)
    private String barcodeSnapshot;

    @Column(name = "size_snapshot")
    private String sizeSnapshot;

    @Column(name = "unit_snapshot")
    private String unitSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "gross_subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossSubtotal;

    @Column(
            name = "discount_percent_snapshot",
            nullable = false,
            precision = 5,
            scale = 2
    ) 
    private BigDecimal discountPercentSnapshot;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "net_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal netTotal;

    @Column(name = "cost_price_snapshot", nullable = false, precision = 14, scale = 2)
    private BigDecimal costPriceSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_status", nullable = false, length = 30)
    private SaleStatus saleStatus;

    @Column(name = "refund_quantity", nullable = false)
    @Builder.Default
    private Integer refundQuantity = 0;

    @Transient
    public Integer getDiscountedUnitCount() {
        if (discountAmount == null
                || discountAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        if (discountAmount.signum() < 0
                || quantity == null
                || quantity < 1
                || unitPrice == null
                || discountPercentSnapshot == null
                || discountPercentSnapshot.signum() <= 0) {
            return null;
        }

        BigDecimal unitDiscount = unitPrice
                .multiply(discountPercentSnapshot)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        if (unitDiscount.signum() <= 0) {
            return null;
        }

        BigDecimal[] division = discountAmount.divideAndRemainder(unitDiscount);
        if (division[1].compareTo(BigDecimal.ZERO) != 0) {
            return null;
        }

        try {
            int discountedUnits = division[0].intValueExact();
            return discountedUnits <= quantity ? discountedUnits : null;
        } catch (ArithmeticException exception) {
            return null;
        }
    }
}
