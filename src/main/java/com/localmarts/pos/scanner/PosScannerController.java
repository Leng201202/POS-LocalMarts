package com.localmarts.pos.scanner;

import com.localmarts.pos.product.ProductService;
import com.localmarts.pos.product.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Authenticated portal for a dedicated wired USB barcode scanner.
 * Dedicated scanners act as keyboards, so the browser only needs a focused
 * input; there is no phone pairing, camera access, or public scanner API.
 */
@Controller
@RequestMapping("/pos/scanner")
@RequiredArgsConstructor
public class PosScannerController {

    private final ProductService productService;

    @GetMapping
    public String scannerPortal() {
        return "scanner/connect";
    }

    @GetMapping("/lookup")
    @ResponseBody
    public ResponseEntity<?> lookup(@RequestParam(required = false) String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Barcode is required"));
        }

        return productService.findActiveByBarcode(barcode)
                .map(product -> ResponseEntity.ok(ScannerProduct.from(product)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    record ScannerProduct(
            Long id,
            String name,
            String barcode,
            String size,
            String unit,
            BigDecimal sellingPrice,
            BigDecimal discountPercent,
            Integer stockQuantity) {

        static ScannerProduct from(Product product) {
            return new ScannerProduct(
                    product.getId(),
                    product.getName(),
                    product.getBarcode(),
                    product.getSize(),
                    product.getUnit() == null ? null : product.getUnit().getName(),
                    product.getSellingPrice(),
                    product.getDiscountPercent(),
                    product.getStockQuantity()
            );
        }
    }
}
