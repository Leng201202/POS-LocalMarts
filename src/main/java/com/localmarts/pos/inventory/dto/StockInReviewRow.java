package com.localmarts.pos.inventory.dto;

import com.localmarts.pos.inventory.entity.StockInRowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockInReviewRow implements Serializable {

    private int rowNumber;

    // Values parsed directly from the uploaded file. Trusted server-side truth
    // for everything except quantity/costPrice/categoryId/unitId, which the
    // review page allows the user to adjust before confirmation.
    private String barcode;
    private String productName;
    private String uploadedCategory;
    private String uploadedUnit;
    private String size;
    private Integer quantity;
    private BigDecimal costPrice;
    private LocalDate mfd;
    private LocalDate exp;

    // Populated when the barcode matched an existing, non-deleted product.
    private Long existingProductId;
    private String existingProductName;
    private String existingCategoryName;
    private String existingUnitName;
    private Integer existingStockQuantity;

    // Populated when a new-product category/unit could be auto-matched.
    private Long matchedCategoryId;
    private String matchedCategoryName;
    private boolean categoryMatched;

    private Long matchedUnitId;
    private String matchedUnitName;
    private boolean unitMatched;

    private StockInRowStatus status;

    // Errors tied to fields the review page cannot edit (blank name,
    // missing/malformed mfd or exp, exp before mfd, duplicate barcode in
    // the file). Set once at parse time and never recomputed - a row with
    // structural errors can only be fixed by re-uploading a corrected file.
    @Builder.Default
    private List<String> structuralErrors = new ArrayList<>();

    // Effective, currently-displayed error list: structuralErrors plus
    // whatever quantity/cost/mapping problems apply to the row's current
    // (possibly user-edited) values. Rebuilt every time status is derived.
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public boolean isExistingProduct() {
        return status == StockInRowStatus.EXISTING_PRODUCT;
    }

    public boolean needsMapping() {
        return status == StockInRowStatus.NEEDS_MAPPING;
    }

    public void addError(String message) {
        this.errors.add(message);
    }

    public String getErrorMessage() {
        return String.join(" ", errors);
    }
}
