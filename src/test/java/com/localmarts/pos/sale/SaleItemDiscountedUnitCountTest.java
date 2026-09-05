package com.localmarts.pos.sale;

import com.localmarts.pos.sale.entity.SaleItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SaleItemDiscountedUnitCountTest {

    @Test
    void reconstructsWholeDiscountedUnitsFromHistoricalAmounts() {
        SaleItem item = item("25.00", "10.00", "5.00", 5);

        assertThat(item.getDiscountedUnitCount()).isEqualTo(2);
    }

    @Test
    void zeroActualDiscountMeansZeroDiscountedUnits() {
        SaleItem item = item("25.00", "10.00", "0.00", 5);

        assertThat(item.getDiscountedUnitCount()).isZero();
    }

    @Test
    void ambiguousHistoricalAmountDoesNotInventFractionalUnits() {
        SaleItem item = item("25.00", "10.00", "3.00", 5);

        assertThat(item.getDiscountedUnitCount()).isNull();
    }

    private SaleItem item(
            String unitPrice,
            String discountPercent,
            String discountAmount,
            int quantity
    ) {
        return SaleItem.builder()
                .unitPrice(new BigDecimal(unitPrice))
                .discountPercentSnapshot(new BigDecimal(discountPercent))
                .discountAmount(new BigDecimal(discountAmount))
                .quantity(quantity)
                .build();
    }
}
