package com.localmarts.pos.sale;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.inventory.service.InventoryService;
import com.localmarts.pos.loyalty.service.LoyaltyService;
import com.localmarts.pos.sale.dto.RefundItemRequest;
import com.localmarts.pos.sale.dto.RefundRequest;
import com.localmarts.pos.sale.dto.ReturnCondition;
import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.Sale;
import com.localmarts.pos.sale.entity.SaleItem;
import com.localmarts.pos.sale.entity.SaleStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RefundService {

    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Bangkok");
    private static final int REFUND_PERIOD_DAYS = 7;
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final SaleRepository saleRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;
    private final LoyaltyService loyaltyService;
    private final Clock clock;

    @Autowired
    public RefundService(
            SaleRepository saleRepository,
            InventoryService inventoryService,
            AuditService auditService,
            LoyaltyService loyaltyService
    ) {
        this(
                saleRepository,
                inventoryService,
                auditService,
                loyaltyService,
                Clock.system(STORE_ZONE)
        );
    }

    RefundService(
            SaleRepository saleRepository,
            InventoryService inventoryService,
            AuditService auditService,
            LoyaltyService loyaltyService,
            Clock clock
    ) {
        this.saleRepository = saleRepository;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
        this.loyaltyService = loyaltyService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Sale> searchEligibleSales(
            String invoiceNo,
            String customerPhone,
            LocalDate dateFrom,
            LocalDate dateTo,
            BigDecimal totalAmount,
            PaymentMethod paymentMethod
    ) {
        LocalDateTime now = now();
        LocalDateTime eligibleSince = now.minusDays(REFUND_PERIOD_DAYS);
        LocalDateTime start = eligibleSince;
        LocalDateTime end = now;

        if (dateFrom != null) {
            LocalDateTime requestedStart = dateFrom.atStartOfDay();
            if (requestedStart.isAfter(start)) {
                start = requestedStart;
            }
        }

        if (dateTo != null) {
            LocalDateTime requestedEnd = dateTo.plusDays(1).atStartOfDay().minusNanos(1);
            if (requestedEnd.isBefore(end)) {
                end = requestedEnd;
            }
        }

        if (start.isAfter(end)) {
            return List.of();
        }

        if (totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total amount cannot be negative");
        }

        return saleRepository.searchRefundEligibleSales(
                        SaleStatus.COMPLETED,
                        start,
                        end,
                        normalize(invoiceNo),
                        normalize(customerPhone),
                        totalAmount,
                        paymentMethod
                )
                .stream()
                .filter(this::hasNoRecordedRefundQuantity)
                .filter(this::supportsRefundWithoutRedemption)
                .toList();
    }

    @Transactional(readOnly = true)
    public Sale getRefundableSale(Long saleId) {
        Sale sale = loadRefundSale(saleId);
        validateSaleEligibility(sale);
        return sale;
    }

    public RefundRequest createRequestFor(Sale sale) {
        RefundRequest request = new RefundRequest();
        List<RefundItemRequest> items = sale.getItems()
                .stream()
                .map(item -> {
                    RefundItemRequest itemRequest = new RefundItemRequest();
                    itemRequest.setSaleItemId(item.getId());
                    itemRequest.setQuantity(1);
                    return itemRequest;
                })
                .toList();
        request.setItems(new ArrayList<>(items));
        return request;
    }

    @Transactional(readOnly = true)
    public BigDecimal previewRefund(Long saleId, RefundRequest request) {
        Sale sale = loadRefundSale(saleId);
        validateSaleEligibility(sale);
        return prepareRefund(sale, request).totalRefundAmount();
    }

    @Transactional
    public RefundOutcome processRefund(
            Long saleId,
            RefundRequest request,
            Long actorId
    ) {
        Sale sale = loadRefundSale(saleId);
        validateSaleEligibility(sale);

        PreparedRefund prepared = prepareRefund(sale, request);
        SaleStatus oldSaleStatus = sale.getSaleStatus();

        int claimed = saleRepository.claimRefund(
                sale.getId(),
                SaleStatus.COMPLETED,
                prepared.finalSaleStatus()
        );

        if (claimed != 1) {
            throw new IllegalStateException(
                    "This Sale has already been refunded by another user"
            );
        }

        List<AuditDetails> auditDetails = new ArrayList<>();
        addDetail(
                auditDetails,
                "refundTotal",
                ZERO_MONEY,
                prepared.totalRefundAmount()
        );
        addDetail(auditDetails, "refundReason", null, request.getReason());
        addDetail(auditDetails, "refundNote", null, normalize(request.getNote()));
        addDetail(
                auditDetails,
                "saleStatus",
                oldSaleStatus,
                prepared.finalSaleStatus()
        );

        int returnedUnitCount = 0;

        for (PreparedRefundLine line : prepared.lines()) {
            SaleItem item = line.saleItem();
            Integer oldRefundQuantity = item.getRefundQuantity();
            SaleStatus oldItemStatus = item.getSaleStatus();

            item.setRefundQuantity(line.quantity());
            item.setSaleStatus(line.finalStatus());
            returnedUnitCount += line.quantity();

            String inventoryReason = "Customer refund "
                    + sale.getInvoiceNo()
                    + ", sale item " + item.getId()
                    + ", returned quantity " + line.quantity()
                    + ", reason " + request.getReason();

            applyInventory(
                    item,
                    line.quantity(),
                    line.condition(),
                    inventoryReason,
                    actorId
            );

            String prefix = "item." + item.getId() + ".";
            addDetail(
                    auditDetails,
                    prefix + "saleItemId",
                    null,
                    item.getId()
            );
            addDetail(
                    auditDetails,
                    prefix + "product",
                    null,
                    item.getProductNameSnapshot()
            );
            addDetail(
                    auditDetails,
                    prefix + "refundQuantity",
                    oldRefundQuantity,
                    line.quantity()
            );
            addDetail(
                    auditDetails,
                    prefix + "saleStatus",
                    oldItemStatus,
                    line.finalStatus()
            );
            addDetail(
                    auditDetails,
                    prefix + "returnCondition",
                    null,
                    line.condition()
            );
            addDetail(
                    auditDetails,
                    prefix + "lineRefundAmount",
                    ZERO_MONEY,
                    line.refundAmount()
            );
        }

        sale.setSaleStatus(prepared.finalSaleStatus());
        saleRepository.save(sale);

        if (sale.getCustomer() != null
                && sale.getPointEarned() != null
                && sale.getPointEarned() > 0) {
            BigDecimal remainingEligibleAmount = money(
                    sale.getTotalAmount()
                            .subtract(prepared.totalRefundAmount())
                            .max(BigDecimal.ZERO)
            );
            LoyaltyService.LoyaltyRefundImpact loyaltyImpact =
                    loyaltyService.reverseEarnedPointsForRefund(
                            sale.getCustomer(),
                            sale,
                            remainingEligibleAmount
                    );
            addLoyaltyAuditDetails(
                    auditDetails,
                    remainingEligibleAmount,
                    loyaltyImpact
            );
        }

        String description = "Processed refund for sale "
                + sale.getInvoiceNo()
                + " for " + prepared.totalRefundAmount().toPlainString()
                + " using original payment method " + sale.getPaymentMethod()
                + ". Reason: " + request.getReason()
                + paymentRecordingNote(sale.getPaymentMethod());

        auditService.logSuccess(
                actorId,
                AuditAction.UPDATE,
                "Sale",
                sale.getId(),
                description,
                auditDetails
        );

        return new RefundOutcome(
                sale.getId(),
                sale.getInvoiceNo(),
                prepared.totalRefundAmount(),
                prepared.finalSaleStatus(),
                sale.getPaymentMethod(),
                returnedUnitCount
        );
    }

    public Set<Long> refundableSaleIds(List<Sale> sales) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Sale sale : sales) {
            if (isRefundable(sale)) {
                ids.add(sale.getId());
            }
        }
        return ids;
    }

    public Set<Long> expiredRefundSaleIds(List<Sale> sales) {
        Set<Long> ids = new LinkedHashSet<>();
        LocalDateTime currentTime = now();
        for (Sale sale : sales) {
            if (sale.getSaleStatus() == SaleStatus.COMPLETED
                    && sale.getTimestamp() != null
                    && currentTime.isAfter(
                            sale.getTimestamp().plusDays(REFUND_PERIOD_DAYS)
                    )) {
                ids.add(sale.getId());
            }
        }
        return ids;
    }

    private Sale loadRefundSale(Long saleId) {
        return saleRepository.findRefundDetailsById(saleId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Sale not found")
                );
    }

    private void validateSaleEligibility(Sale sale) {
        if (sale.getSaleStatus() == SaleStatus.VOIDED) {
            throw new IllegalArgumentException("VOIDED Sales cannot be refunded");
        }

        if (sale.getSaleStatus() != SaleStatus.COMPLETED) {
            throw new IllegalArgumentException(
                    "This Sale has already used its one refund operation"
            );
        }

        if (!hasNoRecordedRefundQuantity(sale)) {
            throw new IllegalArgumentException(
                    "This Sale already contains refunded quantities"
            );
        }

        if (sale.getTimestamp() == null
                || now().isAfter(
                        sale.getTimestamp().plusDays(REFUND_PERIOD_DAYS)
                )) {
            throw new IllegalArgumentException(
                    "Refund period expired. Sales may be refunded within 7 days of purchase"
            );
        }

        if (!supportsRefundWithoutRedemption(sale)) {
            throw new IllegalArgumentException(
                    "Refunds for Sales that redeemed loyalty points are not supported until Refund Phase 3"
            );
        }
    }

    private PreparedRefund prepareRefund(Sale sale, RefundRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Select at least one item to refund");
        }

        if (!request.isPhysicalReturnConfirmed()) {
            throw new IllegalArgumentException(
                    "Confirm that the customer physically returned the selected items"
            );
        }

        if (request.getReason() == null) {
            throw new IllegalArgumentException("Refund reason is required");
        }

        Map<Long, SaleItem> saleItemsById = new HashMap<>();
        for (SaleItem item : sale.getItems()) {
            saleItemsById.put(item.getId(), item);
        }

        Set<Long> submittedIds = new HashSet<>();
        List<PreparedRefundLine> lines = new ArrayList<>();
        BigDecimal totalRefund = ZERO_MONEY;

        for (RefundItemRequest itemRequest : request.getItems()) {
            if (itemRequest == null || itemRequest.getSaleItemId() == null) {
                throw new IllegalArgumentException("Every refund item must identify a SaleItem");
            }

            if (!submittedIds.add(itemRequest.getSaleItemId())) {
                throw new IllegalArgumentException("Duplicate SaleItem IDs are not allowed");
            }

            SaleItem saleItem = saleItemsById.get(itemRequest.getSaleItemId());
            if (saleItem == null) {
                throw new IllegalArgumentException(
                        "A selected SaleItem does not belong to this Sale"
                );
            }

            if (!itemRequest.isSelected()) {
                continue;
            }

            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new IllegalArgumentException("Refund quantity must be greater than zero");
            }

            if (itemRequest.getQuantity() > saleItem.getQuantity()) {
                throw new IllegalArgumentException(
                        "Refund quantity exceeds original quantity for "
                                + saleItem.getProductNameSnapshot()
                );
            }

            if (itemRequest.getCondition() == null) {
                throw new IllegalArgumentException(
                        "Return condition is required for "
                                + saleItem.getProductNameSnapshot()
                );
            }

            BigDecimal lineRefund = allocateRefund(
                    saleItem.getNetTotal(),
                    saleItem.getQuantity(),
                    itemRequest.getQuantity()
            );
            SaleStatus lineStatus = itemRequest.getQuantity().equals(saleItem.getQuantity())
                    ? SaleStatus.REFUNDED
                    : SaleStatus.PARTIALLY_REFUNDED;

            lines.add(new PreparedRefundLine(
                    saleItem,
                    itemRequest.getQuantity(),
                    itemRequest.getCondition(),
                    lineRefund,
                    lineStatus
            ));
            totalRefund = totalRefund.add(lineRefund);
        }

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Select at least one item to refund");
        }

        Set<Long> fullyRefundedIds = new HashSet<>();
        for (PreparedRefundLine line : lines) {
            if (line.finalStatus() == SaleStatus.REFUNDED) {
                fullyRefundedIds.add(line.saleItem().getId());
            }
        }

        boolean everyItemFullyRefunded = sale.getItems()
                .stream()
                .allMatch(item -> fullyRefundedIds.contains(item.getId()));

        SaleStatus finalSaleStatus = everyItemFullyRefunded
                ? SaleStatus.REFUNDED
                : SaleStatus.PARTIALLY_REFUNDED;

        return new PreparedRefund(
                List.copyOf(lines),
                money(totalRefund),
                finalSaleStatus
        );
    }

    private BigDecimal allocateRefund(
            BigDecimal netTotal,
            Integer originalQuantity,
            Integer refundQuantity
    ) {
        if (refundQuantity.equals(originalQuantity)) {
            return money(netTotal);
        }

        return netTotal
                .multiply(BigDecimal.valueOf(refundQuantity))
                .divide(
                        BigDecimal.valueOf(originalQuantity),
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private void applyInventory(
            SaleItem item,
            int quantity,
            ReturnCondition condition,
            String reason,
            Long actorId
    ) {
        switch (condition) {
            case SELLABLE -> inventoryService.refund(
                    item.getProduct().getId(),
                    quantity,
                    reason,
                    actorId
            );
            case DAMAGED -> inventoryService.recordReturnedDamaged(
                    item.getProduct().getId(),
                    quantity,
                    reason,
                    actorId
            );
            case EXPIRED -> inventoryService.recordReturnedExpired(
                    item.getProduct().getId(),
                    quantity,
                    reason,
                    actorId
            );
        }
    }

    private boolean isRefundable(Sale sale) {
        return sale.getSaleStatus() == SaleStatus.COMPLETED
                && sale.getTimestamp() != null
                && !now().isAfter(
                        sale.getTimestamp().plusDays(REFUND_PERIOD_DAYS)
                )
                && hasNoRecordedRefundQuantity(sale)
                && supportsRefundWithoutRedemption(sale);
    }

    private boolean hasNoRecordedRefundQuantity(Sale sale) {
        return sale.getItems()
                .stream()
                .allMatch(item -> item.getRefundQuantity() == null
                        || item.getRefundQuantity() == 0);
    }

    private boolean supportsRefundWithoutRedemption(Sale sale) {
        boolean redeemedPoints = sale.getPointRedeemed() != null
                && sale.getPointRedeemed() > 0;
        boolean pointDiscount = sale.getPointDiscount() != null
                && sale.getPointDiscount().compareTo(BigDecimal.ZERO) > 0;
        return !redeemedPoints && !pointDiscount;
    }

    private void addLoyaltyAuditDetails(
            List<AuditDetails> auditDetails,
            BigDecimal remainingEligibleAmount,
            LoyaltyService.LoyaltyRefundImpact loyaltyImpact
    ) {
        addDetail(
                auditDetails,
                "loyalty.remainingEligibleAmount",
                null,
                remainingEligibleAmount
        );
        addDetail(
                auditDetails,
                "loyalty.originalEarnedPoints",
                null,
                loyaltyImpact.originalEarnedPoints()
        );
        addDetail(
                auditDetails,
                "loyalty.remainingAllowedPoints",
                null,
                loyaltyImpact.remainingAllowedPoints()
        );
        addDetail(
                auditDetails,
                "loyalty.reversedPoints",
                null,
                loyaltyImpact.reversedPoints()
        );
        addDetail(
                auditDetails,
                "loyalty.beforeBalance",
                null,
                loyaltyImpact.beforeBalance()
        );
        addDetail(
                auditDetails,
                "loyalty.afterBalance",
                loyaltyImpact.beforeBalance(),
                loyaltyImpact.afterBalance()
        );
    }

    private String paymentRecordingNote(PaymentMethod paymentMethod) {
        if (paymentMethod == PaymentMethod.CASH) {
            return ". Cash refund recorded in POS; cashier returns cash physically.";
        }
        return ". Refund recorded in POS only; no external bank, card, or QR reversal was performed by the application.";
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);  
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void addDetail(
            List<AuditDetails> details,
            String fieldName,
            Object oldValue,
            Object newValue
    ) {
        details.add(AuditDetails.builder()
                .fieldName(fieldName)
                .oldData(oldValue == null ? null : String.valueOf(oldValue))
                .newData(newValue == null ? null : String.valueOf(newValue))
                .build());
    }

    private record PreparedRefundLine(
            SaleItem saleItem,
            int quantity,
            ReturnCondition condition,
            BigDecimal refundAmount,
            SaleStatus finalStatus
    ) {
    }

    private record PreparedRefund(
            List<PreparedRefundLine> lines,
            BigDecimal totalRefundAmount,
            SaleStatus finalSaleStatus
    ) {
    }

    public record RefundOutcome(
            Long saleId,
            String invoiceNo,
            BigDecimal totalRefundAmount,
            SaleStatus saleStatus,
            PaymentMethod paymentMethod,
            int returnedUnitCount
    ) {
    }
}
