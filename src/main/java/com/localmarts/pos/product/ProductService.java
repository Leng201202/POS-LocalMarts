package com.localmarts.pos.product;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.audit.utils.AuditChangeHelper;
import com.localmarts.pos.product.entity.Product;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final AuditService auditService;

    public ProductService(
            ProductRepository productRepository,
            AuditService auditService) {

        this.productRepository = productRepository;
        this.auditService = auditService;
    }

    public List<Product> findAll() {
        return productRepository.findByDeletedAtIsNullOrderByNameAsc();
    }

    public Product findById(Long id) {
        return productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public void create(Product product, Long createdBy) {

        try {
            if (productRepository.existsByBarcodeAndDeletedAtIsNull(
                    product.getBarcode())) {

                throw new RuntimeException("Barcode already exists");
            }

            product.setActive(true);
            product.setCreatedBy(createdBy);

            if (product.getStockQuantity() == null) {
                product.setStockQuantity(0);
            }

            Product savedProduct = productRepository.save(product);

            auditService.logSuccess(
                    createdBy,
                    AuditAction.CREATE,
                    "Product",
                    savedProduct.getId(),
                    "Created product: " + savedProduct.getName(),
                    null
            );

        } catch (RuntimeException e) {

            auditService.logFailed(
                    createdBy,
                    AuditAction.CREATE,
                    "Product",
                    product.getId(),
                    "Failed to create product: " + e.getMessage()
            );

            throw e;
        }
    }
    public void softDelete(Long id, Long deletedBy) {

        try {

            Product product = findById(id);
            product.setActive(false);
            product.setDeletedBy(deletedBy);
            product.setDeletedAt(LocalDateTime.now());

            Product deletedProduct = productRepository.save(product);

            auditService.logSuccess(
                    deletedBy,
                    AuditAction.DELETE,
                    "Product",
                    deletedProduct.getId(),
                    "Deleted product: " + deletedProduct.getName()
            );

        } catch (RuntimeException e) {

            auditService.logFailed(
                    deletedBy,
                    AuditAction.DELETE,
                    "Product",
                    id,
                    "Failed to delete product: " + e.getMessage()
            );

            throw e;
        }
    }

 

    public void update(Long id, Product formProduct, Long updatedBy) {

        Product existingProduct = findById(id);

        try {
            boolean barcodeChanged =
                    !java.util.Objects.equals(
                            existingProduct.getBarcode(),
                            formProduct.getBarcode()
                    );

            if (barcodeChanged
                    && productRepository.existsByBarcodeAndDeletedAtIsNull(
                            formProduct.getBarcode())) {

                throw new RuntimeException("Barcode already exists");
            }

            List<AuditDetails> changes = new AuditChangeHelper()
                    .addChange(
                            "name",
                            existingProduct.getName(),
                            formProduct.getName()
                    )
                    .addChange(
                            "barcode",
                            existingProduct.getBarcode(),
                            formProduct.getBarcode()
                    )
                    .addChange(
                            "size",
                            existingProduct.getSize(),
                            formProduct.getSize()
                    )
                    .addChange(
                            "manufacturingDate",
                            existingProduct.getMfd(),
                            formProduct.getMfd()
                    )
                    .addChange(
                            "expiryDate",
                            existingProduct.getExp(),
                            formProduct.getExp()
                    )
                    .addChange(
                            "costPrice",
                            existingProduct.getCostPrice(),
                            formProduct.getCostPrice()
                    )
                    .addChange(
                            "sellingPrice",
                            existingProduct.getSellingPrice(),
                            formProduct.getSellingPrice()
                    )
                    .addChange(
                            "minimumStock",
                            existingProduct.getMinimumStock(),
                            formProduct.getMinimumStock()
                    )
                    .addChange(
                            "category",
                            getCategoryName(existingProduct),
                            getCategoryName(formProduct)
                    )
                    .addChange(
                            "unit",
                            getUnitName(existingProduct),
                            getUnitName(formProduct)
                    )
                    .build();

            existingProduct.setName(formProduct.getName());
            existingProduct.setBarcode(formProduct.getBarcode());
            existingProduct.setSize(formProduct.getSize());
            existingProduct.setMfd(formProduct.getMfd());
            existingProduct.setExp(formProduct.getExp());
            existingProduct.setCostPrice(formProduct.getCostPrice());
            existingProduct.setSellingPrice(formProduct.getSellingPrice());
            existingProduct.setMinimumStock(formProduct.getMinimumStock());
            existingProduct.setCategory(formProduct.getCategory());
            existingProduct.setUnit(formProduct.getUnit());
            existingProduct.setUpdatedBy(updatedBy);

            Product savedProduct = productRepository.save(existingProduct);

            auditService.logSuccess(
                    updatedBy,
                    AuditAction.UPDATE,
                    "Product",
                    savedProduct.getId(),
                    "Updated product: " + savedProduct.getName(),
                    changes
            );

        } catch (RuntimeException e) {

            auditService.logFailed(
                    updatedBy,
                    AuditAction.UPDATE,
                    "Product",
                    id,
                    "Failed to update product: " + e.getMessage()
            );

            throw e;
        }
    }

    private String getCategoryName(Product product) {
        if (product.getCategory() == null) {
            return null;
        }

        return product.getCategory().getName();
    }

    private String getUnitName(Product product) {
        if (product.getUnit() == null) {
            return null;
        }

        return product.getUnit().getName();
    }

}