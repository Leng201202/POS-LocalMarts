package com.localmarts.pos.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.localmarts.pos.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByDeletedAtIsNullOrderByNameAsc();

    Page<Product> findByDeletedAtIsNullOrderByNameAsc(Pageable pageable);

    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByBarcodeAndDeletedAtIsNull(String barcode);

    Optional<Product> findFirstByBarcodeAndDeletedAtIsNull(
            String barcode
    );

    Optional<Product> findFirstByBarcodeAndActiveTrueAndDeletedAtIsNull(
            String barcode
    );

    List<Product> findAllByActiveTrueAndDeletedAtIsNullOrderByNameAsc();

    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true
              AND p.deletedAt IS NULL
              AND (
                  LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR p.barcode LIKE CONCAT('%', :query, '%')
              )
            ORDER BY p.name ASC
            """)
    List<Product> searchActiveByNameOrBarcode(@Param("query") String query);
}
