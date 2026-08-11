package com.localmarts.pos.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.localmarts.pos.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
    
    List<Product> findByDeletedAtIsNullOrderByNameAsc();

    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByBarcodeAndDeletedAtIsNull(String barcode);

    Optional<Product> findFirstByBarcodeAndActiveTrueAndDeletedAtIsNull(
            String barcode
    );

    Optional<Product> findFirstByBarcodeAndDeletedAtIsNull(
            String barcode
    );

    List<Product> findTop30ByNameContainingIgnoreCaseAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(
            String name
    );
}
