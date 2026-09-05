package com.localmarts.pos.sale;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.inventory.service.InventoryService;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.service.LoyaltyConfigService;
import com.localmarts.pos.loyalty.service.LoyaltyService;
import com.localmarts.pos.sale.dto.RefundItemRequest;
import com.localmarts.pos.sale.dto.RefundReason;
import com.localmarts.pos.sale.dto.RefundRequest;
import com.localmarts.pos.sale.dto.ReturnBreakdownRequest;
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
import java.util.LinkedHashMap;
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
    private final LoyaltyConfigService loyaltyConfigService;
    private final Clock clock;

    @Autowired
    public RefundService(
            SaleRepository saleRepository,
            InventoryService inventoryService,
            AuditService auditService,
            LoyaltyService loyaltyService,
            LoyaltyConfigService loyaltyConfigService
    ) {
        this(
                saleRepository,
                inventoryService,
                auditService,
                loyaltyService,
                loyaltyConfigService,
                Clock.system(STORE_ZONE)
        );
    }

    RefundService(
            SaleRepository saleRepository,
            InventoryService inventoryService,
            AuditService auditService,
            LoyaltyService loyaltyService,
            LoyaltyConfigService loyaltyConfigService,
            Clock clock
    ) {
        this.saleRepository = saleRepository;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
        this.loyaltyService = loyaltyService;
        this.loyaltyConfigService = loyaltyConfigService;
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
                .filter(this::hasSafeLoyaltyHistory)
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
                    ReturnBreakdownRequest breakdown = new ReturnBreakdownRequest();
                    breakdown.setQuantity(1);
                    itemRequest.setBreakdowns(new ArrayList<>(List.of(breakdown)));
                    return itemRequest;
                })
                .toList();
        request.setItems(new ArrayList<>(items));
        return request;
    }

    @Transactional(readOnly = true)
    public Map<Long, RefundLineUiData> getRefundLineUiData(Sale sale) {
        LoyaltyRefundContext context = validateHistoricalLoyalty(sale);
        Map<Long, RefundLineUiData> result = new LinkedHashMap<>();
        for (SaleItem item : sale.getItems()) {
            int discountedUnits = safeDiscountedUnitCount(item);
            BigDecimal unitDiscount = ZERO_MONEY;
            long pointsPerDiscountedUnit = 0L;
            if (discountedUnits > 0) {
                unitDiscount = historicalUnitDiscount(item);
                pointsPerDiscountedUnit = pointsPerDiscountedUnit(
                        unitDiscount,
                        context.historicalConfig()
                );
            }
            result.put(item.getId(), new RefundLineUiData(
                    item.getQuantity(),
                    discountedUnits,
                    item.getUnitPrice(),
                    unitDiscount,
                    item.getNetTotal(),
                    pointsPerDiscountedUnit
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public RefundPreview previewRefund(Long saleId, RefundRequest request) {
        Sale sale = loadRefundSale(saleId);
        LoyaltyRefundContext loyaltyContext = validateSaleEligibility(sale);
        PreparedRefund prepared = prepareRefund(sale, request, loyaltyContext);
        return new RefundPreview(
                prepared.totalRefundAmount(),
                prepared.restoredRedeemedPoints(),
                prepared.lines().stream()
                        .map(line -> new RefundLinePreview(
                                line.saleItem().getId(),
                                line.regularUnitsReturned(),
                                line.discountedUnitsReturned(),
                                line.refundAmount(),
                                line.restoredRedeemedPoints()
                        ))
                        .toList()
        );
    }

    @Transactional
    public RefundOutcome processRefund(
            Long saleId,
            RefundRequest request,
            Long actorId
    ) {
        Sale sale = loadRefundSale(saleId);
        LoyaltyRefundContext loyaltyContext = validateSaleEligibility(sale);

        PreparedRefund prepared = prepareRefund(sale, request, loyaltyContext);
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

            for (PreparedReturnBreakdown breakdown : line.breakdowns()) {
                String inventoryReason = "Customer refund "
                        + sale.getInvoiceNo()
                        + ", sale item " + item.getId()
                        + ", returned quantity " + breakdown.quantity()
                        + ", reason " + breakdown.reason()
                        + ", condition " + breakdown.condition();

                applyInventory(
                        item,
                        breakdown.quantity(),
                        breakdown.condition(),
                        inventoryReason,
                        actorId
                );
            }

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
            for (int index = 0; index < line.breakdowns().size(); index++) {
                PreparedReturnBreakdown breakdown = line.breakdowns().get(index);
                String breakdownPrefix = prefix + "breakdown." + index + ".";
                addDetail(
                        auditDetails,
                        breakdownPrefix + "quantity",
                        null,
                        breakdown.quantity()
                );
                addDetail(
                        auditDetails,
                        breakdownPrefix + "refundReason",
                        null,
                        breakdown.reason()
                );
                addDetail(
                        auditDetails,
                        breakdownPrefix + "returnCondition",
                        null,
                        breakdown.condition()
                );
            }
            addDetail(
                    auditDetails,
                    prefix + "discountedUnitsOriginal",
                    null,
                    line.discountedUnitsOriginal()
            );
            addDetail(
                    auditDetails,
                    prefix + "regularUnitsReturned",
                    null,
                    line.regularUnitsReturned()
            );
            addDetail(
                    auditDetails,
                    prefix + "discountedUnitsReturned",
                    null,
                    line.discountedUnitsReturned()
            );
            addDetail(
                    auditDetails,
                    prefix + "lineRefundAmount",
                    ZERO_MONEY,
                    line.refundAmount()
            );
            addDetail(
                    auditDetails,
                    prefix + "cashRefundAmount",
                    ZERO_MONEY,
                    line.refundAmount()
            );
            addDetail(
                    auditDetails,
                    prefix + "restoredRedeemedPoints",
                    0,
                    line.restoredRedeemedPoints()
            );
        }

        sale.setSaleStatus(prepared.finalSaleStatus());
        saleRepository.save(sale);

        long balanceBeforeLoyalty = currentPointBalance(sale);
        LoyaltyService.LoyaltyRefundImpact loyaltyImpact =
                new LoyaltyService.LoyaltyRefundImpact(
                        sale.getPointEarned() == null ? 0L : sale.getPointEarned(),
                        sale.getPointEarned() == null ? 0L : sale.getPointEarned(),
                        0L,
                        balanceBeforeLoyalty,
                        balanceBeforeLoyalty
                );

        if (sale.getCustomer() != null
                && sale.getPointEarned() != null
                && sale.getPointEarned() > 0) {
            BigDecimal remainingEligibleAmount = money(
                    sale.getTotalAmount()
                            .subtract(prepared.totalRefundAmount())
                            .max(BigDecimal.ZERO)
            );
            loyaltyImpact = loyaltyService.reverseEarnedPointsForRefund(
                            sale.getCustomer(),
                            sale,
                            remainingEligibleAmount
                    );
        }

        LoyaltyService.LoyaltyRestoreImpact restoreImpact;
        if (prepared.restoredRedeemedPoints() > 0) {
            restoreImpact = loyaltyService.restoreRedeemedPointsForRefund(
                    sale.getCustomer(),
                    sale,
                    prepared.restoredRedeemedPoints()
            );
        } else {
            long currentBalance = currentPointBalance(sale);
            restoreImpact = LoyaltyService.LoyaltyRestoreImpact.none(
                    currentBalance
            );
        }
        BigDecimal remainingEligibleAmount = money(
                sale.getTotalAmount()
                        .subtract(prepared.totalRefundAmount())
                        .max(BigDecimal.ZERO)
        );
        addLoyaltyAuditDetails(
                auditDetails,
                remainingEligibleAmount,
                sale,
                loyaltyImpact,
                restoreImpact
        );

        String description = "Processed refund for sale "
                + sale.getInvoiceNo()
                + " for " + prepared.totalRefundAmount().toPlainString()
                + " using original payment method " + sale.getPaymentMethod()
                + ". Item reasons: " + refundReasonSummary(prepared.lines())
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
                returnedUnitCount,
                restoreImpact.restoredPoints(),
                loyaltyImpact.reversedPoints()
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

    private LoyaltyRefundContext validateSaleEligibility(Sale sale) {
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

        return validateHistoricalLoyalty(sale);
    }

    private PreparedRefund prepareRefund(
            Sale sale,
            RefundRequest request,
            LoyaltyRefundContext loyaltyContext
    ) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Select at least one item to refund");
        }

        if (!request.isPhysicalReturnConfirmed()) {
            throw new IllegalArgumentException(
                    "Confirm that the customer physically returned the selected items"
            );
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

            List<PreparedReturnBreakdown> breakdowns = prepareBreakdowns(
                    itemRequest,
                    saleItem
            );

            RefundLineValue lineValue = calculateRefundLine(
                    saleItem,
                    itemRequest.getQuantity(),
                    loyaltyContext
            );
            SaleStatus lineStatus = itemRequest.getQuantity().equals(saleItem.getQuantity())
                    ? SaleStatus.REFUNDED
                    : SaleStatus.PARTIALLY_REFUNDED;

            lines.add(new PreparedRefundLine(
                    saleItem,
                    itemRequest.getQuantity(),
                    breakdowns,
                    lineValue.refundAmount(),
                    lineValue.discountedUnitsOriginal(),
                    lineValue.regularUnitsReturned(),
                    lineValue.discountedUnitsReturned(),
                    lineValue.restoredRedeemedPoints(),
                    lineStatus
            ));
            totalRefund = totalRefund.add(lineValue.refundAmount());
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

        long restoredRedeemedPoints = 0L;
        try {
            for (PreparedRefundLine line : lines) {
                restoredRedeemedPoints = Math.addExact(
                        restoredRedeemedPoints,
                        line.restoredRedeemedPoints()
                );
            }
        } catch (ArithmeticException exception) {
            throw manualReview(
                    "Historical redeemed points exceed the supported limit"
            );
        }
        BigDecimal normalizedTotalRefund = money(totalRefund);

        if (finalSaleStatus == SaleStatus.REFUNDED) {
            if (normalizedTotalRefund.compareTo(money(sale.getTotalAmount())) != 0) {
                throw manualReview(
                        "Historical Sale totals do not match the reconstructed full refund"
                );
            }
            if (restoredRedeemedPoints != loyaltyContext.originalRedeemedPoints()) {
                throw manualReview(
                        "Historical redeemed points do not match the reconstructed full refund"
                );
            }
        }

        return new PreparedRefund(
                List.copyOf(lines),
                normalizedTotalRefund,
                restoredRedeemedPoints,
                finalSaleStatus
        );
    }

    private RefundLineValue calculateRefundLine(
            SaleItem item,
            int refundQuantity,
            LoyaltyRefundContext context
    ) {
        int discountedUnits = safeDiscountedUnitCount(item);
        int regularUnits = item.getQuantity() - discountedUnits;
        int regularReturned = Math.min(refundQuantity, regularUnits);
        int discountedReturned = Math.min(
                discountedUnits,
                Math.max(0, refundQuantity - regularUnits)
        );

        if (discountedUnits == 0) {
            return new RefundLineValue(
                    allocateRefund(
                            item.getNetTotal(),
                            item.getQuantity(),
                            refundQuantity
                    ),
                    0,
                    refundQuantity,
                    0,
                    0L
            );
        }

        BigDecimal unitDiscount = historicalUnitDiscount(item);
        BigDecimal refundAmount;
        if (refundQuantity == item.getQuantity()) {
            refundAmount = money(item.getNetTotal());
        } else {
            BigDecimal discountedUnitPrice = item.getUnitPrice()
                    .subtract(unitDiscount);
            refundAmount = money(
                    item.getUnitPrice()
                            .multiply(BigDecimal.valueOf(regularReturned))
                            .add(discountedUnitPrice.multiply(
                                    BigDecimal.valueOf(discountedReturned)
                            ))
            );
        }

        long restoredPoints = Math.multiplyExact(
                discountedReturned,
                pointsPerDiscountedUnit(unitDiscount, context.historicalConfig())
        );
        return new RefundLineValue(
                refundAmount,
                discountedUnits,
                regularReturned,
                discountedReturned,
                restoredPoints
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

    private List<PreparedReturnBreakdown> prepareBreakdowns(
            RefundItemRequest itemRequest,
            SaleItem saleItem
    ) {
        if (itemRequest.getBreakdowns() == null
                || itemRequest.getBreakdowns().isEmpty()) {
            throw new IllegalArgumentException(
                    "Return breakdown is required for "
                            + saleItem.getProductNameSnapshot()
            );
        }

        List<PreparedReturnBreakdown> breakdowns = new ArrayList<>();
        int breakdownTotal = 0;
        for (ReturnBreakdownRequest breakdown : itemRequest.getBreakdowns()) {
            if (breakdown == null
                    || breakdown.getQuantity() == null
                    || breakdown.getQuantity() < 1) {
                throw new IllegalArgumentException(
                        "Return breakdown quantity must be at least one for "
                                + saleItem.getProductNameSnapshot()
                );
            }
            if (breakdown.getReason() == null) {
                throw new IllegalArgumentException(
                        "Refund reason is required for every return breakdown for "
                                + saleItem.getProductNameSnapshot()
                );
            }
            ReturnCondition condition = resolveReturnCondition(
                    breakdown,
                    saleItem
            );
            try {
                breakdownTotal = Math.addExact(
                        breakdownTotal,
                        breakdown.getQuantity()
                );
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "Return breakdown quantities are too large"
                );
            }
            breakdowns.add(new PreparedReturnBreakdown(
                    breakdown.getQuantity(),
                    breakdown.getReason(),
                    condition
            ));
        }

        if (breakdownTotal != itemRequest.getQuantity()) {
            throw new IllegalArgumentException(
                    "Return breakdown quantities must equal the selected refund quantity."
            );
        }
        return List.copyOf(breakdowns);
    }

    private ReturnCondition resolveReturnCondition(
            ReturnBreakdownRequest breakdown,
            SaleItem saleItem
    ) {
        return switch (breakdown.getReason()) {
            case WRONG_ITEM, CUSTOMER_CHANGED_MIND -> ReturnCondition.SELLABLE;
            case DEFECTIVE -> ReturnCondition.DAMAGED;
            case EXPIRED -> ReturnCondition.EXPIRED;
            case OTHER -> {
                if (breakdown.getCondition() == null) {
                    throw new IllegalArgumentException(
                            "Return condition is required for OTHER returns for "
                                    + saleItem.getProductNameSnapshot()
                    );
                }
                yield breakdown.getCondition();
            }
        };
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
                && hasSafeLoyaltyHistory(sale);
    }

    private boolean hasNoRecordedRefundQuantity(Sale sale) {
        return sale.getItems()
                .stream()
                .allMatch(item -> item.getRefundQuantity() == null
                        || item.getRefundQuantity() == 0);
    }

    private boolean hasSafeLoyaltyHistory(Sale sale) {
        try {
            validateHistoricalLoyalty(sale);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private LoyaltyRefundContext validateHistoricalLoyalty(Sale sale) {
        int redeemedPoints = sale.getPointRedeemed() == null
                ? 0
                : sale.getPointRedeemed();
        BigDecimal pointDiscount = money(
                sale.getPointDiscount() == null
                        ? BigDecimal.ZERO
                        : sale.getPointDiscount()
        );
        if (redeemedPoints < 0 || pointDiscount.signum() < 0) {
            throw manualReview("Historical loyalty totals are invalid");
        }

        boolean hasRedeemedPoints = redeemedPoints > 0;
        boolean hasPointDiscount = pointDiscount.signum() > 0;
        if (hasRedeemedPoints != hasPointDiscount) {
            throw manualReview(
                    "Historical redeemed points and loyalty discount are inconsistent"
            );
        }
        if (!hasRedeemedPoints) {
            return LoyaltyRefundContext.none();
        }
        if (sale.getCustomer() == null) {
            throw manualReview(
                    "A Sale with redeemed loyalty points has no Customer"
            );
        }

        BigDecimal summedDiscount = ZERO_MONEY;
        boolean discountedLineFound = false;
        for (SaleItem item : sale.getItems()) {
            BigDecimal itemDiscount = item.getDiscountAmount() == null
                    ? BigDecimal.ZERO
                    : item.getDiscountAmount();
            if (itemDiscount.signum() < 0) {
                throw manualReview("A SaleItem has an invalid loyalty discount");
            }
            if (itemDiscount.signum() > 0) {
                discountedLineFound = true;
                safeDiscountedUnitCount(item);
            }
            summedDiscount = summedDiscount.add(itemDiscount);
        }
        if (!discountedLineFound) {
            throw manualReview(
                    "Redeemed points exist but no SaleItem has a loyalty discount"
            );
        }

        BigDecimal normalizedSummedDiscount = money(summedDiscount);
        if (normalizedSummedDiscount.compareTo(pointDiscount) != 0
                || sale.getTotalDiscount() == null
                || money(sale.getTotalDiscount()).compareTo(pointDiscount) != 0) {
            throw manualReview(
                    "Historical Sale and SaleItem loyalty discounts are inconsistent"
            );
        }

        LoyaltyConfig historicalConfig = loyaltyConfigService.getSaleConfig(
                sale.getTimestamp()
        );
        validateHistoricalRedemptionConfig(historicalConfig);

        long reconstructedPoints = 0L;
        try {
            for (SaleItem item : sale.getItems()) {
                int discountedUnits = safeDiscountedUnitCount(item);
                if (discountedUnits == 0) {
                    continue;
                }
                long linePoints = Math.multiplyExact(
                        discountedUnits,
                        pointsPerDiscountedUnit(
                                historicalUnitDiscount(item),
                                historicalConfig
                        )
                );
                reconstructedPoints = Math.addExact(
                        reconstructedPoints,
                        linePoints
                );
            }
        } catch (ArithmeticException exception) {
            throw manualReview(
                    "Historical redeemed points exceed the supported limit"
            );
        }
        if (reconstructedPoints != redeemedPoints) {
            throw manualReview(
                    "Historical redeemed points cannot be reconstructed safely"
            );
        }

        return new LoyaltyRefundContext(
                historicalConfig,
                redeemedPoints
        );
    }

    private int safeDiscountedUnitCount(SaleItem item) {
        Integer discountedUnits = item.getDiscountedUnitCount();
        if (discountedUnits == null) {
            throw manualReview(
                    "Historical loyalty allocation for this SaleItem cannot be reconstructed safely"
            );
        }
        return discountedUnits;
    }

    private BigDecimal historicalUnitDiscount(SaleItem item) {
        BigDecimal unitDiscount = money(item.getUnitPrice()
                .multiply(item.getDiscountPercentSnapshot())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        if (unitDiscount.signum() <= 0
                || unitDiscount.compareTo(item.getUnitPrice()) > 0) {
            throw manualReview(
                    "Historical per-unit loyalty discount is invalid"
            );
        }
        return unitDiscount;
    }

    private long pointsPerDiscountedUnit(
            BigDecimal unitDiscount,
            LoyaltyConfig config
    ) {
        validateHistoricalRedemptionConfig(config);
        try {
            long points = unitDiscount
                    .multiply(BigDecimal.valueOf(config.getRedeemPoints()))
                    .divide(config.getRedeemValue(), 0, RoundingMode.CEILING)
                    .longValueExact();
            if (points <= 0) {
                throw manualReview(
                        "Historical points per discounted unit are invalid"
                );
            }
            return points;
        } catch (ArithmeticException exception) {
            throw manualReview(
                    "Historical redeemed points exceed the supported limit"
            );
        }
    }

    private void validateHistoricalRedemptionConfig(LoyaltyConfig config) {
        if (config == null
                || config.getRedeemPoints() == null
                || config.getRedeemPoints() <= 0
                || config.getRedeemValue() == null
                || config.getRedeemValue().signum() <= 0) {
            throw manualReview(
                    "Historical loyalty conversion is unavailable or invalid"
            );
        }
    }

    private IllegalArgumentException manualReview(String reason) {
        return new IllegalArgumentException(
                reason + ". Manual review is required."
        );
    }

    private long currentPointBalance(Sale sale) {
        return sale.getCustomer() == null
                || sale.getCustomer().getPointBalance() == null
                ? 0L
                : sale.getCustomer().getPointBalance();
    }

    private void addLoyaltyAuditDetails(
            List<AuditDetails> auditDetails,
            BigDecimal remainingEligibleAmount,
            Sale sale,
            LoyaltyService.LoyaltyRefundImpact loyaltyImpact,
            LoyaltyService.LoyaltyRestoreImpact restoreImpact
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
                "loyalty.reversedEarnedPoints",
                0,
                loyaltyImpact.reversedPoints()
        );
        addDetail(
                auditDetails,
                "loyalty.originalRedeemedPoints",
                null,
                sale.getPointRedeemed() == null ? 0 : sale.getPointRedeemed()
        );
        addDetail(
                auditDetails,
                "loyalty.restoredRedeemedPoints",
                0,
                restoreImpact.restoredPoints()
        );
        addDetail(
                auditDetails,
                "loyalty.beforeBalance",
                null,
                loyaltyImpact.beforeBalance()
        );
        addDetail(
                auditDetails,
                "loyalty.afterEarnReversalBalance",
                loyaltyImpact.beforeBalance(),
                loyaltyImpact.afterBalance()
        );
        addDetail(
                auditDetails,
                "loyalty.afterRedeemRestoreBalance",
                restoreImpact.beforeBalance(),
                restoreImpact.afterBalance()
        );
        addDetail(
                auditDetails,
                "loyalty.afterBalance",
                loyaltyImpact.beforeBalance(),
                restoreImpact.afterBalance()
        );
    }

    private String paymentRecordingNote(PaymentMethod paymentMethod) {
        if (paymentMethod == PaymentMethod.CASH) {
            return ". Cash refund recorded in POS; cashier returns cash physically.";
        }
        return ". Refund recorded in POS only; no external bank, card, or QR reversal was performed by the application.";
    }

    private String refundReasonSummary(List<PreparedRefundLine> lines) {
        return lines.stream()
                .flatMap(line -> line.breakdowns().stream()
                        .map(breakdown -> "item " + line.saleItem().getId()
                                + "=" + breakdown.reason()
                                + "/" + breakdown.condition()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
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
            List<PreparedReturnBreakdown> breakdowns,
            BigDecimal refundAmount,
            int discountedUnitsOriginal,
            int regularUnitsReturned,
            int discountedUnitsReturned,
            long restoredRedeemedPoints,
            SaleStatus finalStatus
    ) {
    }

    private record PreparedReturnBreakdown(
            int quantity,
            RefundReason reason,
            ReturnCondition condition
    ) {
    }

    private record PreparedRefund(
            List<PreparedRefundLine> lines,
            BigDecimal totalRefundAmount,
            long restoredRedeemedPoints,
            SaleStatus finalSaleStatus
    ) {
    }

    public record RefundOutcome(
            Long saleId,
            String invoiceNo,
            BigDecimal totalRefundAmount,
            SaleStatus saleStatus,
            PaymentMethod paymentMethod,
            int returnedUnitCount,
            long restoredRedeemedPoints,
            long reversedEarnedPoints
    ) {
    }

    public record RefundPreview(
            BigDecimal totalRefundAmount,
            long restoredRedeemedPoints,
            List<RefundLinePreview> lines
    ) {
    }

    public record RefundLinePreview(
            Long saleItemId,
            int regularUnitsReturned,
            int discountedUnitsReturned,
            BigDecimal refundAmount,
            long restoredRedeemedPoints
    ) {
    }

    public record RefundLineUiData(
            int originalQuantity,
            int discountedUnitsOriginal,
            BigDecimal unitPrice,
            BigDecimal unitDiscount,
            BigDecimal netTotal,
            long pointsPerDiscountedUnit
    ) {
    }

    private record RefundLineValue(
            BigDecimal refundAmount,
            int discountedUnitsOriginal,
            int regularUnitsReturned,
            int discountedUnitsReturned,
            long restoredRedeemedPoints
    ) {
    }

    private record LoyaltyRefundContext(
            LoyaltyConfig historicalConfig,
            long originalRedeemedPoints
    ) {
        private static LoyaltyRefundContext none() {
            return new LoyaltyRefundContext(null, 0L);
        }
    }
}
