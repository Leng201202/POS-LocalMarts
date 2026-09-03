package com.localmarts.pos.sale;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.customer.CustomerService;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.inventory.service.InventoryService;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.service.LoyaltyService;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.product.ProductService;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.sale.dto.CreateSaleRequest;
import com.localmarts.pos.sale.dto.SaleItemRequest;
import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.PaymentStatus;
import com.localmarts.pos.sale.entity.Sale;
import com.localmarts.pos.sale.entity.SaleItem;
import com.localmarts.pos.sale.entity.SaleStatus;
import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SaleService {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter INVOICE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final CustomerService customerService;
    private final LoyaltyService loyaltyService;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public SaleService(
            SaleRepository saleRepository,
            ProductRepository productRepository,
            ProductService productService,
            CustomerService customerService,
            LoyaltyService loyaltyService,
            InventoryService inventoryService,
            UserRepository userRepository,
            AuditService auditService) {

        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.customerService = customerService;
        this.loyaltyService = loyaltyService;
        this.inventoryService = inventoryService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Sale checkout(CreateSaleRequest request, Long actorId) {
        if (request == null
                || request.getItems() == null
                || request.getItems().isEmpty()) {

            throw new IllegalArgumentException(
                    "Cart must contain at least one product"
            );
        }

        User cashier = userRepository.findById(actorId)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Active cashier account not found"
                        )
                );

        Customer customer = resolveMemberCustomer(request.getCustomerId());
        LoyaltyConfig loyaltyConfig = customer == null
                ? null
                : loyaltyService.getCurrentConfig();

        Map<Long, Integer> requestedQuantities =
                combineRequestedQuantities(request.getItems());

        Sale sale = Sale.builder()
                .invoiceNo(generateInvoiceNo())
                .cashier(cashier)
                .customer(customer)
                .subtotal(ZERO_MONEY)
                .totalDiscount(ZERO_MONEY)
                .pointDiscount(ZERO_MONEY)
                .totalAmount(ZERO_MONEY)
                .paidAmount(ZERO_MONEY)
                .changeAmount(ZERO_MONEY)
                .pointEarned(0)
                .pointRedeemed(0)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .saleStatus(SaleStatus.COMPLETED)
                .note(normalizeNote(request.getNote()))
                .timestamp(LocalDateTime.now())
                .build();

        BigDecimal subtotal = ZERO_MONEY;
        BigDecimal pointDiscount = ZERO_MONEY;
        long totalRequiredPoints = 0L;

        for (Map.Entry<Long, Integer> entry : requestedQuantities.entrySet()) {
            Product product = productRepository
                    .findByIdAndDeletedAtIsNull(entry.getKey())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Product not found: " + entry.getKey()
                            )
                    );

            validateProductForSale(product, entry.getValue());

            SaleItem item = createSaleItem(
                    product,
                    entry.getValue()
            );

            sale.addItem(item);
            subtotal = subtotal.add(item.getGrossSubtotal());

            if (request.isUseLoyaltyDiscount()) {
                LoyaltyLineDiscount loyaltyLineDiscount =
                        calculateLoyaltyLineDiscount(item, loyaltyConfig);
                item.setDiscountAmount(loyaltyLineDiscount.discountAmount());
                item.setNetTotal(money(
                        item.getGrossSubtotal().subtract(
                                loyaltyLineDiscount.discountAmount()
                        )
                ));
                pointDiscount = pointDiscount.add(
                        loyaltyLineDiscount.discountAmount()
                );
                try {
                    totalRequiredPoints = Math.addExact(
                            totalRequiredPoints,
                            loyaltyLineDiscount.requiredPoints()
                    );
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException(
                            "Required loyalty points exceed the supported limit"
                    );
                }
            }

            inventoryService.sale(
                    product.getId(),
                    entry.getValue(),
                    "Sale checkout " + sale.getInvoiceNo(),
                    actorId
            );
        }

        if (request.isUseLoyaltyDiscount()) {
            validateLoyaltyRedemption(
                    customer,
                    loyaltyConfig,
                    totalRequiredPoints,
                    pointDiscount
            );
        }

        BigDecimal totalDiscount = money(pointDiscount);
        BigDecimal totalAmount = money(subtotal.subtract(totalDiscount));
        BigDecimal paidAmount = validatePaidAmount(
                request.getPaidAmount()
        );

        validatePayment(
                request.getPaymentMethod(),
                paidAmount,
                totalAmount
        );

        BigDecimal changeAmount =
                request.getPaymentMethod() == PaymentMethod.CASH
                        ? money(paidAmount.subtract(totalAmount))
                        : ZERO_MONEY;

        sale.setSubtotal(money(subtotal));
        sale.setTotalDiscount(totalDiscount);
        sale.setPointDiscount(totalDiscount);
        sale.setPointRedeemed(toPointInteger(totalRequiredPoints));
        sale.setTotalAmount(totalAmount);
        sale.setPaidAmount(paidAmount);
        sale.setChangeAmount(changeAmount);
        sale.setPaymentStatus(PaymentStatus.PAID);

        Sale savedSale = saleRepository.save(sale);

        if (customer != null) {
            if (request.isUseLoyaltyDiscount()) {
                loyaltyService.redeemPoints(
                        customer,
                        savedSale,
                        totalRequiredPoints
                );
            }

            long earnedPoints = loyaltyService.earnPoint(
                    customer,
                    savedSale,
                    totalAmount,
                    loyaltyConfig
            );

            if (earnedPoints > 0) {
                savedSale.setPointEarned((int) earnedPoints);
                savedSale = saleRepository.save(savedSale);
            }
        }

        auditService.logSuccess(
                actorId,
                AuditAction.CREATE,
                "Sale",
                savedSale.getId(),
                "Completed sale " + savedSale.getInvoiceNo()
        );

        return savedSale;
    }

    @Transactional(readOnly = true)
    public List<Product> searchProducts(String query) {
        return productService.searchActive(query);
    }

    @Transactional(readOnly = true)
    public long getTodayCompletedSaleCount(Long cashierId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        return saleRepository
                .countCompletedSalesForCashier(
                        cashierId,
                        SaleStatus.COMPLETED,
                        start,
                        end
                );
    }

    @Transactional(readOnly = true)
    public BigDecimal getTodayRevenue(Long cashierId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        BigDecimal revenue = saleRepository.sumTotalAmountForCashier(
                cashierId,
                SaleStatus.COMPLETED,
                start,
                end
        );

        return revenue == null ? ZERO_MONEY : money(revenue);
    }

    @Transactional(readOnly = true)
    public List<Sale> getRecentSales(Long cashierId) {
        return saleRepository
                .findTop10ByCashierIdOrderByTimestampDesc(cashierId);
    }

    @Transactional(readOnly = true)
    public List<Sale> getSalesHistory(Long cashierId) {
        return saleRepository
                .findByCashierIdOrderByTimestampDesc(cashierId);
    }

    @Transactional(readOnly = true)
    public Sale findReceipt(
            Long saleId,
            Long actorId,
            boolean isAdmin) {

        if (isAdmin) {
            return saleRepository.findDetailedById(saleId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Sale receipt not found"
                            )
                    );
        }

        return saleRepository
                .findDetailedByIdAndCashierId(saleId, actorId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Sale receipt not found"
                        )
                );
    }

    private Customer resolveMemberCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }

        return customerService.findActiveById(customerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Selected member customer was not found"
                        )
                );
    }

    private Map<Long, Integer> combineRequestedQuantities(
            List<SaleItemRequest> requestedItems) {

        Map<Long, Integer> quantities = new LinkedHashMap<>();

        for (SaleItemRequest item : requestedItems) {
            if (item == null || item.getProductId() == null) {
                throw new IllegalArgumentException(
                        "Every cart item must identify a product"
                );
            }

            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new IllegalArgumentException(
                        "Quantity must be at least 1"
                );
            }

            try {
                quantities.merge(
                        item.getProductId(),
                        item.getQuantity(),
                        Math::addExact
                );
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "Requested quantity is too large"
                );
            }
        }

        return quantities;
    }

    private void validateProductForSale(
            Product product,
            Integer quantity) {

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalArgumentException(
                    "Product is inactive: " + product.getName()
            );
        }

        if (product.getStockQuantity() == null
                || quantity > product.getStockQuantity()) {

            throw new IllegalArgumentException(
                    "Insufficient stock for " + product.getName()
            );
        }

        if (product.getSellingPrice() == null) {
            throw new IllegalArgumentException(
                    "Selling price is missing for " + product.getName()
            );
        }

        BigDecimal discountPercent = product.getDiscountPercent();

        if (discountPercent != null
                && (discountPercent.compareTo(BigDecimal.ZERO) < 0
                || discountPercent.compareTo(ONE_HUNDRED) > 0)) {

            throw new IllegalArgumentException(
                    "Invalid discount for " + product.getName()
            );
        }
    }

    private SaleItem createSaleItem(
            Product product,
            Integer quantity) {

        BigDecimal unitPrice = money(product.getSellingPrice());
        BigDecimal discountPercent =
                product.getDiscountPercent() == null
                        ? BigDecimal.ZERO
                        : product.getDiscountPercent();

        BigDecimal grossSubtotal = money(
                unitPrice.multiply(BigDecimal.valueOf(quantity))
        );
        String unitSnapshot = product.getUnit() == null
                ? null
                : product.getUnit().getName();

        BigDecimal costPriceSnapshot =
                product.getCostPrice() == null
                        ? ZERO_MONEY
                        : money(product.getCostPrice());

        return SaleItem.builder()
                .product(product)
                .productNameSnapshot(product.getName())
                .barcodeSnapshot(product.getBarcode())
                .sizeSnapshot(product.getSize())
                .unitSnapshot(unitSnapshot)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .grossSubtotal(grossSubtotal)
                .discountPercentSnapshot(discountPercent)
                .discountAmount(ZERO_MONEY)
                .netTotal(grossSubtotal)
                .costPriceSnapshot(costPriceSnapshot)
                .saleStatus(SaleStatus.COMPLETED)
                .refundQuantity(0)
                .build();
    }

    private LoyaltyLineDiscount calculateLoyaltyLineDiscount(
            SaleItem item,
            LoyaltyConfig config
    ) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "An active member is required to use loyalty discounts"
            );
        }

        BigDecimal discountPercent = item.getDiscountPercentSnapshot();
        if (discountPercent == null
                || discountPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return new LoyaltyLineDiscount(ZERO_MONEY, 0L);
        }

        validateRedemptionConversion(config);

        BigDecimal discountAmount = item.getGrossSubtotal()
                .multiply(discountPercent)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new LoyaltyLineDiscount(ZERO_MONEY, 0L);
        }

        long requiredPoints;
        try {
            requiredPoints = discountAmount
                    .multiply(BigDecimal.valueOf(config.getRedeemPoints()))
                    .divide(config.getRedeemValue(), 0, RoundingMode.CEILING)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Loyalty point requirement exceeds the supported limit"
            );
        }

        return new LoyaltyLineDiscount(money(discountAmount), requiredPoints);
    }

    private void validateLoyaltyRedemption(
            Customer customer,
            LoyaltyConfig config,
            long totalRequiredPoints,
            BigDecimal pointDiscount
    ) {
        if (customer == null) {
            throw new IllegalArgumentException(
                    "An active member is required to use loyalty discounts"
            );
        }
        if (config == null || !config.isRedemptionEnabled()) {
            throw new IllegalArgumentException(
                    "Loyalty redemption is currently unavailable"
            );
        }
        validateRedemptionConversion(config);
        if (totalRequiredPoints <= 0
                || pointDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "The cart has no products eligible for a loyalty discount"
            );
        }
        if (config.getMinimumRedeemPoints() == null
                || config.getMinimumRedeemPoints() <= 0) {
            throw new IllegalStateException(
                    "Minimum loyalty points per sale must be greater than zero"
            );
        }
        if (totalRequiredPoints < config.getMinimumRedeemPoints()) {
            throw new IllegalArgumentException(
                    "This cart requires " + totalRequiredPoints
                            + " points, below the minimum of "
                            + config.getMinimumRedeemPoints()
            );
        }
        long pointBalance = customer.getPointBalance() == null
                ? 0L
                : customer.getPointBalance();
        if (pointBalance < totalRequiredPoints) {
            throw new IllegalArgumentException(
                    "The member needs " + totalRequiredPoints
                            + " points to use the complete loyalty discount"
            );
        }
    }

    private void validateRedemptionConversion(LoyaltyConfig config) {
        if (config.getRedeemPoints() == null
                || config.getRedeemPoints() <= 0
                || config.getRedeemValue() == null
                || config.getRedeemValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Loyalty redemption conversion must be greater than zero"
            );
        }
    }

    private int toPointInteger(long points) {
        try {
            return Math.toIntExact(points);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Required loyalty points exceed the Sale limit"
            );
        }
    }

    private BigDecimal validatePaidAmount(BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Paid amount cannot be negative"
            );
        }

        try {
            return paidAmount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Paid amount may contain at most 2 decimal places"
            );
        }
    }

    private void validatePayment(
            PaymentMethod paymentMethod,
            BigDecimal paidAmount,
            BigDecimal totalAmount) {

        if (paymentMethod == null) {
            throw new IllegalArgumentException(
                    "Payment method is required"
            );
        }

        if (paymentMethod == PaymentMethod.CASH
                && paidAmount.compareTo(totalAmount) < 0) {

            throw new IllegalArgumentException(
                    "Cash paid must cover the final total"
            );
        }

        if (paymentMethod != PaymentMethod.CASH
                && paidAmount.compareTo(totalAmount) != 0) {

            throw new IllegalArgumentException(
                    "Card and QR payments must equal the final total"
            );
        }
    }

    private String generateInvoiceNo() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String invoiceNo = "INV-"
                    + LocalDateTime.now().format(INVOICE_TIME_FORMAT)
                    + "-"
                    + UUID.randomUUID()
                    .toString()
                    .substring(0, 6)
                    .toUpperCase();

            if (!saleRepository.existsByInvoiceNo(invoiceNo)) {
                return invoiceNo;
            }
        }

        throw new IllegalStateException(
                "Unable to generate a unique invoice number"
        );
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }

        return note.trim();
    }

    private record LoyaltyLineDiscount(
            BigDecimal discountAmount,
            long requiredPoints
    ) {
    }
}
