package com.localmarts.pos.inventory.service;


import com.localmarts.pos.category.CategoryService;
import com.localmarts.pos.category.entity.Category;
import com.localmarts.pos.unit.UnitService;
import com.localmarts.pos.unit.entity.Unit;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockInTemplateService {

    private static final int FIRST_DATA_ROW = 1;
    private static final int LAST_DATA_ROW = 500;

    private final CategoryService categoryService;
    private final UnitService unitService;

    public byte[] generateTemplate() throws IOException {
        List<Category> categories=categoryService.findAll();
        List<Unit> units=unitService.findAll();

        if(categories.isEmpty()){
            throw new IllegalStateException(
                    "At least one active categories should have in system."
            );
        }
        if(units.isEmpty()){
            throw new IllegalStateException(
                    "At least one active unit should have in system."
            );
        }
        try(
                Workbook workbook=new XSSFWorkbook();
                ByteArrayOutputStream outputStream=new ByteArrayOutputStream()
                )
        {
            Sheet stockInSheet=workbook.createSheet("Stock In");
            Sheet categorySheet=workbook.createSheet("Categories");
            Sheet unitSheet=workbook.createSheet("Units");
            Sheet instructionSheet=workbook.createSheet("Instructions");

            creatReferenceSheet(
                    categorySheet,
                    "Available Categories",
                    categories.stream()
                            .map(Category::getName)
                            .toList()
            );

            creatReferenceSheet(
                    unitSheet,
                    "Available Units",
                    units.stream()
                            .map(Unit::getName)
                            .toList()
            );
            createNamedRange(workbook,"CategoryList","Categories",categories.size());
            createNamedRange(workbook,"UnitList","Units",units.size());

            createStockInSheet(workbook, stockInSheet);
            createInstructions(workbook, instructionSheet);
            workbook.setActiveSheet(

                    workbook.getSheetIndex(stockInSheet)

            );

            workbook.write(outputStream);

            return outputStream.toByteArray();
        }
    }


    private void createStockInSheet(Workbook workbook, Sheet stockInSheet) {
        String[] headers={
                "barcode",
                "product_name",
                "category",
                "unit",
                "size",
                "quantity",
                "cost_price",
                "mfd",
                "exp"
        };
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle exampleStyle = createExampleStyle(workbook);
        Row headerRow= stockInSheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell=headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        Row exampleRow= stockInSheet.createRow(1);
        exampleRow.createCell(0).setCellValue("8851234567890");
        exampleRow.createCell(1).setCellValue("Coca-Cola");
        exampleRow.createCell(2).setCellValue("Beverage");
        exampleRow.createCell(3).setCellValue("Bottle");
        exampleRow.createCell(4).setCellValue("500ml");
        exampleRow.createCell(5).setCellValue(24);
        exampleRow.createCell(6).setCellValue(12.50);
        Cell mfdCell = exampleRow.createCell(7);
        mfdCell.setCellValue(LocalDate.of(2026, 8, 1));
        mfdCell.setCellStyle(dateStyle);
        Cell expCell = exampleRow.createCell(8);
        expCell.setCellValue(LocalDate.of(2027, 8, 1));
        expCell.setCellStyle(dateStyle);

        for (Cell cell : exampleRow) {
            if (cell.getColumnIndex() != 7 &&
                    cell.getColumnIndex() != 8) {
                cell.setCellStyle(exampleStyle);
            }
        }
        for (int rowIndex = 2;
             rowIndex <= LAST_DATA_ROW;
             rowIndex++) {
            Row row = stockInSheet.createRow(rowIndex);
            row.createCell(7).setCellStyle(dateStyle);
            row.createCell(8).setCellStyle(dateStyle);
        }
        addDropdownValidation(
                stockInSheet,
                "CategoryList",
                2
        );

        addDropdownValidation(
                stockInSheet,
                "UnitList",
                3
        );

        addQuantityValidation(stockInSheet);
        addCostPriceValidation(stockInSheet);
        addRequiredDateValidation(stockInSheet, 7, "Manufacturing Date");
        addRequiredDateValidation(stockInSheet, 8, "Expiry Date");

        configureStockInColumns(stockInSheet);
        stockInSheet.createFreezePane(0, 1);
        stockInSheet.setAutoFilter(
                new CellRangeAddress(
                        0,
                        0,
                        0,
                        headers.length - 1
                )
        );
    }

    private void configureStockInColumns(Sheet sheet) {
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 30 * 256);
        sheet.setColumnWidth(2, 22 * 256);
        sheet.setColumnWidth(3, 18 * 256);
        sheet.setColumnWidth(4, 16 * 256);
        sheet.setColumnWidth(5, 14 * 256);
        sheet.setColumnWidth(6, 16 * 256);
        sheet.setColumnWidth(7, 16 * 256);
        sheet.setColumnWidth(8, 16 * 256);
    }

    private void addQuantityValidation(Sheet sheet) {
        DataValidationHelper helper= sheet.getDataValidationHelper();

        DataValidationConstraint constraint=helper.createIntegerConstraint(
                DataValidationConstraint.OperatorType.GREATER_THAN,
                "0",null
        );

        CellRangeAddressList addressList=new CellRangeAddressList(
                FIRST_DATA_ROW,
                LAST_DATA_ROW,
                5,
                5
        );
        DataValidation validation=helper.createValidation(
                constraint,addressList
        );
        validation.setEmptyCellAllowed(false);
        validation.setShowErrorBox(true);
        validation.createErrorBox(
                "Invalid quantity",
                "Quantity is required and must be a whole number greater than zero."
        );
        sheet.addValidationData(validation);
    }

    private void addCostPriceValidation(Sheet sheet) {
        DataValidationHelper helper =
                sheet.getDataValidationHelper();
        DataValidationConstraint constraint =
                helper.createDecimalConstraint(
                        DataValidationConstraint.OperatorType.GREATER_THAN,
                        "0",
                        null
                );
        CellRangeAddressList addressList =
                new CellRangeAddressList(
                        FIRST_DATA_ROW,
                        LAST_DATA_ROW,
                        6,
                        6
                );

        DataValidation validation =
                helper.createValidation(
                        constraint,
                        addressList
                );

        validation.setEmptyCellAllowed(false);
        validation.setShowErrorBox(true);
        validation.createErrorBox(
                "Invalid cost price",
                "Cost price is required and must be greater than zero."
        );
        sheet.addValidationData(validation);
    }

    private void addRequiredDateValidation(
            Sheet sheet,
            int columnIndex,
            String fieldName
    ) {
        DataValidationHelper helper =
                sheet.getDataValidationHelper();
        DataValidationConstraint constraint =
                helper.createDateConstraint(
                        DataValidationConstraint.OperatorType.BETWEEN,
                        "DATE(2000,1,1)",
                        "DATE(2100,12,31)",
                        "yyyy-mm-dd"
                );
        CellRangeAddressList addressList =
                new CellRangeAddressList(
                        FIRST_DATA_ROW,
                        LAST_DATA_ROW,
                        columnIndex,
                        columnIndex
                );
        DataValidation validation =
                helper.createValidation(
                        constraint,
                        addressList
                );
        validation.setEmptyCellAllowed(false);
        validation.setShowErrorBox(true);
        validation.createErrorBox(
                "Invalid " + fieldName,
                fieldName +
                        " is required and must use a valid date."
        );
        validation.setShowPromptBox(true);
        validation.createPromptBox(
                fieldName,
                "Enter a date using yyyy-mm-dd."
        );
        sheet.addValidationData(validation);
    }

    private void addDropdownValidation(
            Sheet sheet,
            String namedRange,
            int columnIndex
    ) {
        DataValidationHelper helper =
                sheet.getDataValidationHelper();
        DataValidationConstraint constraint =
                helper.createFormulaListConstraint(namedRange);
        CellRangeAddressList addressList =
                new CellRangeAddressList(
                        FIRST_DATA_ROW,
                        LAST_DATA_ROW,
                        columnIndex,
                        columnIndex
                );
        DataValidation validation =
                helper.createValidation(
                        constraint,
                        addressList
                );
        validation.setEmptyCellAllowed(false);
        validation.setShowErrorBox(true);
        validation.createErrorBox(
                "Invalid value",
                "Please select a value from the dropdown list."
        );
        validation.setShowPromptBox(true);
        validation.createPromptBox(
                "Select a value",
                "Choose an existing value from the list."
        );
        sheet.addValidationData(validation);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(
                IndexedColors.DARK_BLUE.getIndex()
        );
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createExampleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.DARK_RED.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(
                IndexedColors.LIGHT_YELLOW.getIndex()
        );
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(
                dataFormat.getFormat("yyyy-mm-dd")
        );
        return style;
    }

    private void createNamedRange(Workbook workbook, String rangeName, String sheetName, int itemCount) {
        Name name=workbook.createName();
        name.setNameName(rangeName);
        String safeSheetName= WorkbookUtil.createSafeSheetName(sheetName);

        name.setRefersToFormula("'"+safeSheetName+"'!$A$2:$A$" +
                (itemCount + 1));
    }

    private void creatReferenceSheet(Sheet sheet,String title,List<String> value) {
        Row header=sheet.createRow(0);
        header.createCell(0).setCellValue(title);
        for (int i = 0; i < value.size(); i++) {
            Row row=sheet.createRow(i+1);
            row.createCell(0).setCellValue(value.get(i));
        }
        sheet.setColumnWidth(0,35*256);
        sheet.createFreezePane(0,1);
    }

    private void createInstructions(
            Workbook workbook,
            Sheet sheet
    ) {
        String[][] rows = {
                {
                        "Column",
                        "Required",
                        "Description"
                },
                {
                        "barcode",
                        "Recommended",
                        "Used to match an existing product."
                },
                {
                        "product_name",
                        "Yes",
                        "Name of the product."
                },
                {
                        "category",
                        "Yes",
                        "Select one of the current categories."
                },
                {
                        "unit",
                        "Yes",
                        "Select one of the current units."
                },
                {
                        "size",
                        "No",
                        "Example: 500ml, 1kg, M, XL."
                },
                {
                        "quantity",
                        "Yes",
                        "Physical quantity received; must be greater than zero."
                },
                {
                        "cost_price",
                        "Yes",
                        "Supplier cost per unit."
                },
                {
                        "mfd",
                        "Yes",
                        "Manufacturing date using yyyy-mm-dd."
                },
                {
                        "exp",
                        "Yes",
                        "Expiry date using yyyy-mm-dd."
                }
        };

        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int rowIndex = 0;
             rowIndex < rows.length;
             rowIndex++) {
            Row row = sheet.createRow(rowIndex);
            for (int columnIndex = 0;
                 columnIndex < rows[rowIndex].length;
                 columnIndex++) {
                Cell cell = row.createCell(columnIndex);
                cell.setCellValue(
                        rows[rowIndex][columnIndex]
                );
                if (rowIndex == 0) {
                    cell.setCellStyle(headerStyle);
                }
            }
        }
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 70 * 256);
        sheet.createFreezePane(0, 1);
    }
}