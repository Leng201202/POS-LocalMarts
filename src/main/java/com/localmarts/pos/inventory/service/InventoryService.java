package com.localmarts.pos.inventory.service;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.audit.utils.AuditChangeHelper;
import com.localmarts.pos.inventory.dto.AdjustmentProduct;
import com.localmarts.pos.user.entity.Role;
import org.springframework.security.access.AccessDeniedException;

import com.localmarts.pos.inventory.InventoryRepository;
import com.localmarts.pos.inventory.entity.Inventory;
import com.localmarts.pos.inventory.entity.InventoryType;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AdjustmentProduct> searchAdjustmentProducts(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return productRepository.searchActiveByNameOrBarcode(keyword.trim()).stream()
                .limit(25)
                .map(p -> new AdjustmentProduct(
                        p.getId(), p.getName(), p.getBarcode(), p.getSize(),
                        p.getUnit() == null ? null : p.getUnit().getName(), p.getStockQuantity()))
                .toList();
    }

    @Transactional
    public Inventory manualAdjustment(Long productId, InventoryType type, Integer amount,
                                      String reason, Long actorId) {
        User actor = getActor(actorId);
        boolean admin = actor.getRole() == Role.ADMIN;
        boolean cashier = actor.getRole() == Role.CASHIER;
        if ((!admin && !cashier) || (!admin && type != InventoryType.DAMAGE && type != InventoryType.EXPIRED)) {
            throw new AccessDeniedException("This adjustment type requires an administrator.");
        }
        if (type != InventoryType.DAMAGE && type != InventoryType.EXPIRED
                && type != InventoryType.MISSING && type != InventoryType.ADJUSTMENT) {
            throw new IllegalArgumentException("Select a valid adjustment type.");
        }
        if (productId == null) throw new IllegalArgumentException("Product is required.");
        if (reason == null || reason.isBlank() || reason.trim().length() > 255) {
            throw new IllegalArgumentException("A reason of 1 to 255 characters is required.");
        }
        if (amount == null || amount < (type == InventoryType.ADJUSTMENT ? 0 : 1)) {
            throw new IllegalArgumentException(type == InventoryType.ADJUSTMENT
                    ? "Actual physical stock must be zero or greater." : "Quantity must be a positive whole number.");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product was not found."));
        if (product.getDeletedAt() != null || !Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalArgumentException("Product is inactive or deleted.");
        }
        if (product.getStockQuantity() == null || product.getStockQuantity() < 0) {
            throw new IllegalArgumentException("Product has invalid current stock.");
        }
        String note = reason.trim();
        Inventory movement = switch (type) {
            case DAMAGE -> damaged(productId, amount, note, actorId);
            case EXPIRED -> expired(productId, amount, note, actorId);
            case MISSING -> missing(productId, amount, note, actorId);
            case ADJUSTMENT -> adjustment(productId, amount, note, actorId);
            default -> throw new IllegalArgumentException("Invalid adjustment type.");
        };
        auditService.logSuccess(actorId, AuditAction.CREATE,
                "Inventory", movement.getId(), "Manual stock adjustment: " + type,
                new AuditChangeHelper()
                        .addChange("productId", null, productId)
                        .addChange("product", null, product.getName())
                        .addChange("adjustmentType", null, type)
                        .addChange("stock", movement.getBeforeStock(), movement.getAfterStock())
                        .addChange("delta", null, movement.getQuantity())
                        .addChange("reason", null, note).build());
        return movement;
    }


    public List<Inventory> findAll(){
        return inventoryRepository.findAllByOrderByTimestampDesc();
    }

    public List<Inventory> filter(String keyword, InventoryType type,Long actorId){
        return inventoryRepository.filter(
                keyword,
                type,
                actorId,
                Pageable.unpaged()
        ).getContent();
    }

    @Transactional(readOnly = true)
    public Page<Inventory> filter(
            String keyword,
            InventoryType type,
            Long actorId,
            Pageable pageable
    ) {
        return inventoryRepository.filter(normalize(keyword), type, actorId, pageable);
    }

    @Transactional(readOnly = true)
    public long countFilteredByType(
            String keyword,
            InventoryType selectedType,
            Long actorId,
            InventoryType countedType
    ) {
        return inventoryRepository.countFilteredByType(
                normalize(keyword),
                selectedType,
                actorId,
                countedType
        );
    }

    @Transactional(readOnly = true)
    public long countFilteredAdjustments(String keyword, InventoryType selectedType, Long actorId) {
        return inventoryRepository.countFilteredAdjustments(normalize(keyword), selectedType, actorId);
    }

    private String normalize(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    @Transactional
    public Inventory sale(
            Long productId,
            Integer quantity,
            String reason,
            Long actorId
    ){
        return applyMovement(productId,-quantity,InventoryType.SALE,reason,actorId);
    }

    @Transactional
    public Inventory stockIn(
            Long productId,
            Integer quantity,
            String reason,
            Long actorId
    ){
        return applyMovement(productId,quantity,InventoryType.STOCK_IN,reason,actorId);
    }

    @Transactional
    public Inventory refund(
            Long productId,
            Integer quantity,
            String reason,
            Long actorId
    ){
        return applyMovement(productId,quantity,InventoryType.REFUND,reason,actorId);
    }

    @Transactional
    public Inventory recordReturnedDamaged(
            Long productId,
            Integer returnedQuantity,
            String reason,
            Long actorId
    ) {
        return recordZeroStockImpactReturn(
                productId,
                returnedQuantity,
                InventoryType.DAMAGE,
                reason,
                actorId
        );
    }

    @Transactional
    public Inventory recordReturnedExpired(
            Long productId,
            Integer returnedQuantity,
            String reason,
            Long actorId
    ) {
        return recordZeroStockImpactReturn(
                productId,
                returnedQuantity,
                InventoryType.EXPIRED,
                reason,
                actorId
        );
    }

    @Transactional
    public Inventory missing(
            Long productId,
            Integer quantity,
            String reason,
            Long actorId
    ){
        return applyMovement(productId,-quantity,InventoryType.MISSING,reason,actorId);
    }

    @Transactional
    public Inventory expired(
            Long productId,
            Integer quantity,
            String reason,
            Long actorId
    ){
        return applyMovement(productId,-quantity,InventoryType.EXPIRED,reason,actorId);
    }


    @Transactional
    public Inventory damaged(
            Long productId,
            Integer quantity,
            String reason,
            Long actorId
    ){
        return applyMovement(productId,-quantity,InventoryType.DAMAGE,reason,actorId);
    }

    @Transactional
    public Inventory adjustment(
            Long productId,
            Integer newStock,
            String reason,
            Long actorId
    ){
        if(newStock==null||newStock<0) throw new IllegalArgumentException("New stock quantity cannot be negative");

        Product product=getProduct(productId);
        User actor=getActor(actorId);

        int beforeStock=product.getStockQuantity();
        int quantityChange=newStock-beforeStock;
        if(quantityChange==0) throw new IllegalArgumentException("No stock change detected");

        product.setStockQuantity(newStock);
        productRepository.save(product);

        Inventory inventory=Inventory.builder()
                .product(product)
                .inventoryType(InventoryType.ADJUSTMENT)
                .quantity(quantityChange)
                .beforeStock(beforeStock)
                .afterStock(newStock)
                .reason(reason)
                .actor(actor)
                .build();
        return inventoryRepository.save(inventory);
    }


    private Inventory applyMovement(
            Long productId,
            Integer quantityChange,
            InventoryType type,
            String reason,
            Long actorId
    ){
        if(quantityChange ==null || quantityChange ==0) throw new IllegalArgumentException("Quantity can't be null or zero");

        Product product=getProduct(productId);
        User actor=getActor(actorId);

        int beforeStock=product.getStockQuantity();
        int afterStock=beforeStock+quantityChange;

        if(afterStock<0) throw new IllegalArgumentException("Insufficient stock. Available stock: " + beforeStock);

        product.setStockQuantity(afterStock);
        productRepository.save(product);
        Inventory inventory=Inventory.builder()
                .product(product)
                .inventoryType(type)
                .quantity(quantityChange)
                .beforeStock(beforeStock)
                .afterStock(afterStock)
                .reason(reason)
                .actor(actor)
                .build();

        return inventoryRepository.save(inventory);

    }

    private Inventory recordZeroStockImpactReturn(
            Long productId,
            Integer returnedQuantity,
            InventoryType type,
            String reason,
            Long actorId
    ) {
        if (returnedQuantity == null || returnedQuantity < 1) {
            throw new IllegalArgumentException(
                    "Returned quantity must be at least 1"
            );
        }

        Product product = getProduct(productId);
        User actor = getActor(actorId);
        int currentStock = product.getStockQuantity();

        Inventory inventory = Inventory.builder()
                .product(product)
                .inventoryType(type)
                .quantity(0)
                .beforeStock(currentStock)
                .afterStock(currentStock)
                .reason(reason)
                .actor(actor)
                .build();

        return inventoryRepository.save(inventory);
    }

    private Product getProduct(Long id){
        return productRepository.findById(id)
                .orElseThrow(()->
                        new RuntimeException("Product is not found"));
    }

    private User getActor(Long id){
        return userRepository.findById(id)
                .orElseThrow(()->new RuntimeException("Actor is not found"));
    }








}
