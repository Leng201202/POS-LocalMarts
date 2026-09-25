package com.localmarts.pos.inventory;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.category.CategoryService;
import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.inventory.dto.StockInBatchSession;
import com.localmarts.pos.inventory.dto.StockInConfirmRequest;
import com.localmarts.pos.inventory.dto.StockInConfirmResult;
import com.localmarts.pos.inventory.dto.StockInConfirmRow;
import com.localmarts.pos.inventory.dto.StockInReviewRow;
import com.localmarts.pos.inventory.entity.Inventory;
import com.localmarts.pos.inventory.entity.InventoryType;
import com.localmarts.pos.inventory.entity.StockInRowStatus;
import com.localmarts.pos.inventory.exception.StockInConfirmException;
import com.localmarts.pos.inventory.exception.StockInUploadException;
import com.localmarts.pos.inventory.exception.StockInValidationException;
import com.localmarts.pos.inventory.service.InventoryService;
import com.localmarts.pos.inventory.service.StockInBatchService;
import com.localmarts.pos.inventory.service.StockInFileService;
import com.localmarts.pos.inventory.service.StockInSessionStore;
import com.localmarts.pos.inventory.service.StockInTemplateService;
import com.localmarts.pos.unit.UnitService;
import com.localmarts.pos.user.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("inventory")
@RequiredArgsConstructor
public class InventoryController {

    private static final int PAGE_SIZE = 25;

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );;
    private final InventoryService inventoryService;
    private final UserService userService;
    private final StockInTemplateService templateService;
    private final StockInFileService stockInFileService;
    private final StockInBatchService stockInBatchService;
    private final StockInSessionStore stockInSessionStore;
    private final CategoryService categoryService;
    private final UnitService unitService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @GetMapping
    public String index(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InventoryType type,
            @RequestParam(required = false) Long actorId,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ){
        Page<Inventory> inventoryPage = inventoryService.filter(
                keyword,
                type,
                actorId,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE)
        );
        model.addAttribute(
                "inventories", inventoryPage.getContent()
        );
        model.addAttribute("page", inventoryPage);

        // Filter options
        model.addAttribute("users", userService.findAllActive());
        model.addAttribute("inventoryTypes", InventoryType.values());
        // Keep selected filter values
        model.addAttribute("selectedKeyword", keyword);
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedActorId", actorId);

        // Add dash data
        model.addAttribute("stockInCount",
                inventoryService.countFilteredByType(
                        keyword, type, actorId, InventoryType.STOCK_IN
                )
                );

        model.addAttribute("saleCount",
                inventoryService.countFilteredByType(
                        keyword, type, actorId, InventoryType.SALE
                )
                );
        model.addAttribute("adjustmentCount",
                inventoryService.countFilteredByType(
                        keyword, type, actorId, InventoryType.ADJUSTMENT
                )
                );
        return "common/inventory/index";
    }



    @GetMapping("/adjustment")
    public String adjustmentForm(Model model) {
        //model.addAttribute("products", productService.findAll());
        return "common/inventory/adjustment";
    }

    @GetMapping("/stock-in")
    public String stockInUploadPage() {
        return "common/inventory/stock-in";
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {

        byte[] fileContent;

        try {
            fileContent = templateService.generateTemplate();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to generate stock-in template",
                    e
            );
        }

        String filename =
                "localmart-stock-in-template-" +
                        LocalDate.now() +
                        ".xlsx";

        ContentDisposition contentDisposition =
                ContentDisposition.attachment()
                        .filename(
                                filename,
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition.toString()
                )
                .contentLength(fileContent.length)
                .body(fileContent);
    }

    @PostMapping("/stock-in/upload")
    public String uploadStockFile(
            @RequestParam("file") MultipartFile file,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        List<StockInReviewRow> reviewRows;
        try {
            reviewRows = stockInFileService.parse(file);
        } catch (StockInUploadException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/inventory/stock-in";
        }

        StockInBatchSession batch = StockInBatchSession.builder()
                .batchToken(UUID.randomUUID().toString())
                .fileName(file.getOriginalFilename())
                .uploadedAt(LocalDateTime.now())
                .rows(reviewRows)
                .build();

        stockInSessionStore.save(session, batch);

        addReviewModel(model, batch);
        return "common/inventory/stock-in/review";
    }

    @PostMapping("/stock-in/confirm")
    public String confirmStockIn(
            @ModelAttribute StockInConfirmRequest confirmRequest,
            Authentication authentication,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Optional<StockInBatchSession> batchOpt = stockInSessionStore.get(session);
        if (batchOpt.isEmpty()
                || confirmRequest.getBatchToken() == null
                || !batchOpt.get().getBatchToken().equals(confirmRequest.getBatchToken())) {

            stockInSessionStore.clear(session);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Your review session has expired. Please upload the file again."
            );
            return "redirect:/inventory/stock-in";
        }

        StockInBatchSession batch = batchOpt.get();

        List<StockInReviewRow> rows;
        try {
            rows = stockInBatchService.reconcile(batch, confirmRequest);
        } catch (StockInValidationException e) {
            model.addAttribute("errorMessage", e.getMessage());
            addReviewModel(model, batch);
            return "common/inventory/stock-in/review";
        }

        Long actorId = currentUserService.getCurrentUserId(authentication);

        StockInConfirmResult result;
        try {
            result = stockInBatchService.applyBatch(rows, batch.getFileName(), actorId);
        } catch (StockInConfirmException e) {
            auditService.logFailed(
                    actorId,
                    AuditAction.CREATE,
                    "Inventory",
                    null,
                    "Stock-in batch failed: " + e.getMessage()
            );
            stockInSessionStore.clear(session);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Stock-in could not be completed: " + e.getMessage()
                            + " Please review and re-upload the file."
            );
            return "redirect:/inventory/stock-in";
        }

        stockInSessionStore.clear(session);

        StringBuilder successMessage = new StringBuilder(
                "Stock-in batch confirmed: " + result.totalCount() + " product(s) updated."
        );
        if (result.newProductCount() > 0) {
            successMessage.append(" ")
                    .append(result.newProductCount())
                    .append(" new product(s) were created with selling price set to cost price - "
                            + "update pricing on the Products page.");
        }
        redirectAttributes.addFlashAttribute("successMessage", successMessage.toString());
        return "redirect:/inventory";
    }

    @PostMapping("/stock-in/cancel")
    public String cancelStockIn(HttpSession session) {
        stockInSessionStore.clear(session);
        return "redirect:/inventory";
    }

    private void addReviewModel(Model model, StockInBatchSession batch) {
        List<StockInReviewRow> rows = batch.getRows();

        model.addAttribute("reviewRows", rows);
        model.addAttribute("fileName", batch.getFileName());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("units", unitService.findAll());

        model.addAttribute("confirmRequest", toConfirmRequest(batch));

        model.addAttribute("totalCount", rows.size());
        model.addAttribute(
                "existingCount",
                rows.stream().filter(StockInReviewRow::isExistingProduct).count()
        );
        model.addAttribute(
                "newCount",
                rows.stream().filter(row -> row.getStatus() == StockInRowStatus.NEW_PRODUCT).count()
        );
        model.addAttribute(
                "needsMappingCount",
                rows.stream().filter(StockInReviewRow::needsMapping).count()
        );
        model.addAttribute(
                "invalidCount",
                rows.stream().filter(row -> row.getStatus() == StockInRowStatus.INVALID).count()
        );
    }

    private StockInConfirmRequest toConfirmRequest(StockInBatchSession batch) {
        StockInConfirmRequest confirmRequest = new StockInConfirmRequest();
        confirmRequest.setBatchToken(batch.getBatchToken());

        List<StockInConfirmRow> confirmRows = new ArrayList<>();
        for (StockInReviewRow row : batch.getRows()) {
            StockInConfirmRow confirmRow = new StockInConfirmRow();
            confirmRow.setRowNumber(row.getRowNumber());
            confirmRow.setQuantity(row.getQuantity() != null ? row.getQuantity().toString() : null);
            confirmRow.setCostPrice(row.getCostPrice() != null ? row.getCostPrice().toPlainString() : null);
            confirmRow.setCategoryId(row.getMatchedCategoryId() != null ? row.getMatchedCategoryId().toString() : null);
            confirmRow.setUnitId(row.getMatchedUnitId() != null ? row.getMatchedUnitId().toString() : null);
            confirmRows.add(confirmRow);
        }
        confirmRequest.setRows(confirmRows);
        return confirmRequest;
    }
}
