package com.localmarts.pos.inventory.service;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.category.CategoryService;
import com.localmarts.pos.category.entity.Category;
import com.localmarts.pos.inventory.dto.StockInBatchSession;
import com.localmarts.pos.inventory.dto.StockInConfirmRequest;
import com.localmarts.pos.inventory.dto.StockInConfirmResult;
import com.localmarts.pos.inventory.dto.StockInConfirmRow;
import com.localmarts.pos.inventory.dto.StockInReviewRow;
import com.localmarts.pos.inventory.entity.StockInRowStatus;
import com.localmarts.pos.inventory.exception.StockInConfirmException;
import com.localmarts.pos.inventory.exception.StockInValidationException;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.unit.UnitService;
import com.localmarts.pos.unit.entity.Unit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Confirms a reviewed stock-in batch. Split into two phases on purpose:
 *
 * <p>{@link #reconcile} merges the user's edits onto the trusted,
 * session-side rows and re-validates them. It does no database writes and
 * is NOT @Transactional.
 *
 * <p>{@link #applyBatch} performs the actual Product/Inventory writes and
 * is @Transactional so the whole batch commits or rolls back together. It
 * must always be invoked directly by the controller (never via
 * reconcile/this) so Spring's transactional proxy actually applies.
 */
@Service
@RequiredArgsConstructor
public class StockInBatchService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final UnitService unitService;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public List<StockInReviewRow> reconcile(StockInBatchSession batch, StockInConfirmRequest request) {
        Map<Integer, StockInConfirmRow> editsByRow = request.getRows().stream()
                .collect(Collectors.toMap(
                        StockInConfirmRow::getRowNumber,
                        Function.identity(),
                        (first, second) -> first
                ));

        List<StockInReviewRow> rows = batch.getRows();
        boolean blocked = false;

        for (StockInReviewRow row : rows) {
            revalidateRow(row, editsByRow.get(row.getRowNumber()));
            if (row.getStatus() == StockInRowStatus.INVALID
                    || row.getStatus() == StockInRowStatus.NEEDS_MAPPING) {
                blocked = true;
            }
        }

        if (blocked) {
            throw new StockInValidationException(
                    "Some rows still need attention before this batch can be confirmed.", rows);
        }
        return rows;
    }

    private void revalidateRow(StockInReviewRow row, StockInConfirmRow edit) {
        List<String> errors = new ArrayList<>(row.getStructuralErrors());

        Integer quantity = parseQuantity(edit != null ? edit.getQuantity() : null, errors);
        BigDecimal costPrice = parseCostPrice(edit != null ? edit.getCostPrice() : null, errors);
        row.setQuantity(quantity);
        row.setCostPrice(costPrice);

        if (row.getExistingProductId() != null) {
            row.setErrors(errors);
            row.setStatus(errors.isEmpty() ? StockInRowStatus.EXISTING_PRODUCT : StockInRowStatus.INVALID);
            return;
        }

        Long categoryId = parseId(edit != null ? edit.getCategoryId() : null);
        Long unitId = parseId(edit != null ? edit.getUnitId() : null);

        Category category = lookupCategory(categoryId, errors);
        Unit unit = lookupUnit(unitId, errors);

        row.setMatchedCategoryId(category != null ? category.getId() : null);
        row.setMatchedCategoryName(category != null ? category.getName() : null);
        row.setCategoryMatched(category != null);

        row.setMatchedUnitId(unit != null ? unit.getId() : null);
        row.setMatchedUnitName(unit != null ? unit.getName() : null);
        row.setUnitMatched(unit != null);

        row.setErrors(errors);

        if (!errors.isEmpty()) {
            row.setStatus(StockInRowStatus.INVALID);
        } else if (category != null && unit != null) {
            row.setStatus(StockInRowStatus.NEW_PRODUCT);
        } else {
            row.setStatus(StockInRowStatus.NEEDS_MAPPING);
        }
    }

    private Category lookupCategory(Long categoryId, List<String> errors) {
        if (categoryId == null) {
            return null;
        }
        try {
            return categoryService.findById(categoryId);
        } catch (RuntimeException e) {
            errors.add("Selected category is invalid or inactive.");
            return null;
        }
    }

    private Unit lookupUnit(Long unitId, List<String> errors) {
        if (unitId == null) {
            return null;
        }
        try {
            return unitService.findById(unitId);
        } catch (RuntimeException e) {
            errors.add("Selected unit is invalid or inactive.");
            return null;
        }
    }

    @Transactional
    public StockInConfirmResult applyBatch(List<StockInReviewRow> rows, String fileName, Long actorId) {
        String reason = "Batch stock-in: " + fileName;
        int newCount = 0;
        int existingCount = 0;

        for (StockInReviewRow row : rows) {
            if (row.getExistingProductId() != null) {
                applyExistingProduct(row, reason, actorId);
                existingCount++;
            } else {
                applyNewProduct(row, reason, actorId);
                newCount++;
            }
        }

        int total = newCount + existingCount;
        auditService.logSuccess(
                actorId,
                AuditAction.CREATE,
                "Inventory",
                null,
                "Stock-in batch confirmed: " + total + " products ("
                        + newCount + " new, " + existingCount + " existing)"
        );

        return new StockInConfirmResult(total, newCount, existingCount);
    }

    private void applyExistingProduct(StockInReviewRow row, String reason, Long actorId) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(row.getExistingProductId())
                .orElseThrow(() -> new StockInConfirmException(
                        "Row " + row.getRowNumber() + ": product no longer exists."));

        // Single-lot schema (no batch/lot tracking) - the most recently
        // received shipment's cost/dates become the product's current
        // cost/dates. Category/unit are intentionally left untouched.
        product.setCostPrice(row.getCostPrice());
        product.setMfd(row.getMfd());
        product.setExp(row.getExp());

        inventoryService.stockIn(product.getId(), row.getQuantity(), reason, actorId);
    }

    private void applyNewProduct(StockInReviewRow row, String reason, Long actorId) {
        String barcode = (row.getBarcode() == null || row.getBarcode().isBlank()) ? null : row.getBarcode();

        if (barcode != null && productRepository.existsByBarcodeAndDeletedAtIsNull(barcode)) {
            throw new StockInConfirmException(
                    "Row " + row.getRowNumber() + ": barcode " + barcode + " was taken by another product.");
        }

        Category category;
        Unit unit;
        try {
            category = categoryService.findById(row.getMatchedCategoryId());
            unit = unitService.findById(row.getMatchedUnitId());
        } catch (RuntimeException e) {
            throw new StockInConfirmException("Row " + row.getRowNumber() + ": " + e.getMessage());
        }

        Product product = Product.builder()
                .name(row.getProductName())
                .barcode(barcode)
                .size(row.getSize())
                .mfd(row.getMfd())
                .exp(row.getExp())
                .costPrice(row.getCostPrice())
                // No selling-price column in the template - default to cost
                // price (0% margin) so the product is never sellable at
                // zero. Flagged to the user in the confirmation summary.
                .sellingPrice(row.getCostPrice())
                .stockQuantity(0)
                .minimumStock(0)
                .category(category)
                .unit(unit)
                .build();
        product.setActive(true);
        product.setCreatedBy(actorId);

        Product savedProduct = productRepository.save(product);

        auditService.logSuccess(
                actorId,
                AuditAction.CREATE,
                "Product",
                savedProduct.getId(),
                "Created product via batch stock-in: " + savedProduct.getName()
        );

        inventoryService.stockIn(savedProduct.getId(), row.getQuantity(), reason, actorId);
    }

    private Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseQuantity(String raw, List<String> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add("Quantity is required.");
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value <= 0) {
                errors.add("Quantity must be greater than zero.");
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add("Quantity must be a whole number.");
            return null;
        }
    }

    private BigDecimal parseCostPrice(String raw, List<String> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add("Cost price is required.");
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() <= 0) {
                errors.add("Cost price must be greater than zero.");
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add("Cost price must be a valid number.");
            return null;
        }
    }
}
