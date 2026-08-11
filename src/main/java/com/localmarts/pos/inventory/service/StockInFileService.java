package com.localmarts.pos.inventory.service;

import com.localmarts.pos.category.CategoryService;
import com.localmarts.pos.category.entity.Category;
import com.localmarts.pos.inventory.dto.StockInReviewRow;
import com.localmarts.pos.inventory.entity.StockInRowStatus;
import com.localmarts.pos.inventory.exception.StockInUploadException;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.unit.UnitService;
import com.localmarts.pos.unit.entity.Unit;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Parses, validates, and matches an uploaded stock-in workbook against the
 * current Product/Category/Unit tables. Never writes to the database -
 * confirmation happens later, separately, in StockInBatchService.
 */
@Service
@RequiredArgsConstructor
public class StockInFileService {

    private static final String SHEET_NAME = "Stock In";

    private static final String[] EXPECTED_HEADERS = {
            "barcode", "product_name", "category", "unit",
            "size", "quantity", "cost_price", "mfd", "exp"
    };

    private static final int COL_BARCODE = 0;
    private static final int COL_PRODUCT_NAME = 1;
    private static final int COL_CATEGORY = 2;
    private static final int COL_UNIT = 3;
    private static final int COL_SIZE = 4;
    private static final int COL_QUANTITY = 5;
    private static final int COL_COST_PRICE = 6;
    private static final int COL_MFD = 7;
    private static final int COL_EXP = 8;

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final UnitService unitService;

    public List<StockInReviewRow> parse(MultipartFile file) {
        validateFile(file);

        Workbook workbook;
        try {
            workbook = new XSSFWorkbook(file.getInputStream());
        } catch (IOException | RuntimeException e) {
            throw new StockInUploadException(
                    "The uploaded file could not be read. Make sure it is a valid Excel (.xlsx) file.", e);
        }

        try {
            Sheet sheet = resolveSheet(workbook);
            validateHeaders(sheet);
            return parseRows(sheet);
        } finally {
            closeQuietly(workbook);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StockInUploadException("Please choose a file to upload.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new StockInUploadException("Only .xlsx files are supported.");
        }
    }

    private Sheet resolveSheet(Workbook workbook) {
        Sheet sheet = workbook.getSheet(SHEET_NAME);
        if (sheet == null && workbook.getNumberOfSheets() > 0) {
            sheet = workbook.getSheetAt(0);
        }
        if (sheet == null) {
            throw new StockInUploadException("The uploaded file does not contain any sheets.");
        }
        return sheet;
    }

    private void validateHeaders(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new StockInUploadException(
                    "The uploaded file does not match the expected template (missing header row).");
        }

        DataFormatter formatter = new DataFormatter();
        for (int col = 0; col < EXPECTED_HEADERS.length; col++) {
            String actual = getStringValue(headerRow.getCell(col), formatter).toLowerCase(Locale.ROOT);
            if (!actual.equals(EXPECTED_HEADERS[col])) {
                throw new StockInUploadException(
                        "The uploaded file does not match the expected template. Column " + (col + 1)
                                + " should be \"" + EXPECTED_HEADERS[col] + "\" but was \""
                                + actual + "\". Please download the latest template.");
            }
        }
    }

    private List<StockInReviewRow> parseRows(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();

        Map<String, Category> categoriesByKey = indexByKey(categoryService.findAll(), Category::getName);
        Map<String, Unit> unitsByKey = indexByKey(unitService.findAll(), Unit::getName);

        List<NumberedRow> numberedRows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowBlank(row, formatter)) {
                continue;
            }
            numberedRows.add(new NumberedRow(rowIndex + 1, row));
        }

        Map<String, Long> barcodeOccurrences = numberedRows.stream()
                .map(numberedRow -> getStringValue(numberedRow.row().getCell(COL_BARCODE), formatter))
                .filter(barcode -> !barcode.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<StockInReviewRow> reviewRows = new ArrayList<>();
        for (NumberedRow numberedRow : numberedRows) {
            reviewRows.add(parseRow(numberedRow, formatter, categoriesByKey, unitsByKey, barcodeOccurrences));
        }
        return reviewRows;
    }

    private StockInReviewRow parseRow(
            NumberedRow numberedRow,
            DataFormatter formatter,
            Map<String, Category> categoriesByKey,
            Map<String, Unit> unitsByKey,
            Map<String, Long> barcodeOccurrences
    ) {
        Row row = numberedRow.row();

        // Structural errors relate to fields the review page cannot edit
        // (name, dates, duplicate barcode) - once set they persist as-is.
        // Value errors relate to quantity/cost price, which the review page
        // lets the user correct, so they are recomputed on every reconcile.
        List<String> structuralErrors = new ArrayList<>();
        List<String> valueErrors = new ArrayList<>();

        String barcode = getStringValue(row.getCell(COL_BARCODE), formatter);
        String productName = getStringValue(row.getCell(COL_PRODUCT_NAME), formatter);
        String uploadedCategory = getStringValue(row.getCell(COL_CATEGORY), formatter);
        String uploadedUnit = getStringValue(row.getCell(COL_UNIT), formatter);
        String size = getStringValue(row.getCell(COL_SIZE), formatter);

        Integer quantity = parseQuantity(row.getCell(COL_QUANTITY), formatter, valueErrors);
        BigDecimal costPrice = parseCostPrice(row.getCell(COL_COST_PRICE), formatter, valueErrors);
        LocalDate mfd = parseDate(row.getCell(COL_MFD), formatter, "Manufacturing date", structuralErrors);
        LocalDate exp = parseDate(row.getCell(COL_EXP), formatter, "Expiry date", structuralErrors);

        if (productName.isBlank()) {
            structuralErrors.add("Product name is required.");
        }
        if (quantity != null && quantity <= 0) {
            valueErrors.add("Quantity must be greater than zero.");
        }
        if (costPrice != null && costPrice.signum() <= 0) {
            valueErrors.add("Cost price must be greater than zero.");
        }
        if (mfd != null && exp != null && exp.isBefore(mfd)) {
            structuralErrors.add("Expiry date cannot be before the manufacturing date.");
        }
        if (!barcode.isBlank() && barcodeOccurrences.getOrDefault(barcode, 0L) > 1) {
            structuralErrors.add("Barcode " + barcode + " appears more than once in this file.");
        }

        List<String> errors = new ArrayList<>(structuralErrors);
        errors.addAll(valueErrors);

        StockInReviewRow.StockInReviewRowBuilder builder = StockInReviewRow.builder()
                .rowNumber(numberedRow.rowNumber())
                .barcode(barcode)
                .productName(productName)
                .uploadedCategory(uploadedCategory)
                .uploadedUnit(uploadedUnit)
                .size(size)
                .quantity(quantity)
                .costPrice(costPrice)
                .mfd(mfd)
                .exp(exp)
                .structuralErrors(structuralErrors)
                .errors(errors);

        Optional<Product> existingProduct = barcode.isBlank()
                ? Optional.empty()
                : productRepository.findFirstByBarcodeAndDeletedAtIsNull(barcode);

        if (existingProduct.isPresent()) {
            Product product = existingProduct.get();
            builder.existingProductId(product.getId())
                    .existingProductName(product.getName())
                    .existingCategoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                    .existingUnitName(product.getUnit() != null ? product.getUnit().getName() : null)
                    .existingStockQuantity(product.getStockQuantity());
        } else {
            Category matchedCategory = categoriesByKey.get(normalizeKey(uploadedCategory));
            Unit matchedUnit = unitsByKey.get(normalizeKey(uploadedUnit));

            if (matchedCategory != null) {
                builder.matchedCategoryId(matchedCategory.getId())
                        .matchedCategoryName(matchedCategory.getName())
                        .categoryMatched(true);
            }
            if (matchedUnit != null) {
                builder.matchedUnitId(matchedUnit.getId())
                        .matchedUnitName(matchedUnit.getName())
                        .unitMatched(true);
            }
        }

        StockInReviewRow reviewRow = builder.build();
        reviewRow.setStatus(resolveStatus(reviewRow, existingProduct.isPresent()));
        return reviewRow;
    }

    private StockInRowStatus resolveStatus(StockInReviewRow row, boolean matchedExisting) {
        if (!row.getErrors().isEmpty()) {
            return StockInRowStatus.INVALID;
        }
        if (matchedExisting) {
            return StockInRowStatus.EXISTING_PRODUCT;
        }
        if (row.isCategoryMatched() && row.isUnitMatched()) {
            return StockInRowStatus.NEW_PRODUCT;
        }
        return StockInRowStatus.NEEDS_MAPPING;
    }

    private boolean isRowBlank(Row row, DataFormatter formatter) {
        for (int col = 0; col < EXPECTED_HEADERS.length; col++) {
            if (!getStringValue(row.getCell(col), formatter).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Integer parseQuantity(Cell cell, DataFormatter formatter, List<String> errors) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            return (int) Math.round(cell.getNumericCellValue());
        }
        String raw = getStringValue(cell, formatter);
        if (raw.isBlank()) {
            errors.add("Quantity is required.");
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            errors.add("Quantity must be a whole number.");
            return null;
        }
    }

    private BigDecimal parseCostPrice(Cell cell, DataFormatter formatter, List<String> errors) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String raw = getStringValue(cell, formatter);
        if (raw.isBlank()) {
            errors.add("Cost price is required.");
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            errors.add("Cost price must be a valid number.");
            return null;
        }
    }

    private LocalDate parseDate(Cell cell, DataFormatter formatter, String fieldLabel, List<String> errors) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String raw = getStringValue(cell, formatter);
        if (raw.isBlank()) {
            errors.add(fieldLabel + " is required.");
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            errors.add(fieldLabel + " must use the format yyyy-MM-dd.");
            return null;
        }
    }

    private String getStringValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private <T> Map<String, T> indexByKey(List<T> items, Function<T, String> nameExtractor) {
        Map<String, T> map = new HashMap<>();
        for (T item : items) {
            map.putIfAbsent(normalizeKey(nameExtractor.apply(item)), item);
        }
        return map;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void closeQuietly(Workbook workbook) {
        try {
            workbook.close();
        } catch (IOException ignored) {
            // best effort close, nothing meaningful to recover here
        }
    }

    private record NumberedRow(int rowNumber, Row row) {
    }
}
