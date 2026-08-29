package com.localmarts.pos.scanner;

import com.localmarts.pos.product.ProductService;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.unit.entity.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PosScannerControllerTests {

    private TestProductService productService;
    private PosScannerController controller;

    @BeforeEach
    void setUp() {
        productService = new TestProductService();
        controller = new PosScannerController(productService);
    }

    @Test
    void portalUsesDedicatedScannerView() {
        assertEquals("scanner/connect", controller.scannerPortal());
    }

    @Test
    void lookupRejectsBlankBarcode() {
        ResponseEntity<?> response = controller.lookup("  ");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(null, productService.lastBarcode);
    }

    @Test
    void lookupReturnsActiveProductDetails() {
        Product product = Product.builder()
                .id(12L)
                .name("Orange Juice")
                .barcode("8850000000012")
                .size("1 L")
                .unit(Unit.builder().name("Bottle").abbreviation("btl").build())
                .sellingPrice(new BigDecimal("65.00"))
                .discountPercent(new BigDecimal("5.00"))
                .stockQuantity(8)
                .build();
        productService.result = Optional.of(product);

        ResponseEntity<?> response = controller.lookup("8850000000012");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PosScannerController.ScannerProduct body = assertInstanceOf(
                PosScannerController.ScannerProduct.class,
                response.getBody()
        );
        assertEquals(12L, body.id());
        assertEquals("Orange Juice", body.name());
        assertEquals("Bottle", body.unit());
        assertEquals(8, body.stockQuantity());
        assertEquals("8850000000012", productService.lastBarcode);
    }

    @Test
    void lookupReturnsNotFoundForUnknownBarcode() {
        ResponseEntity<?> response = controller.lookup("missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    private static final class TestProductService extends ProductService {
        private Optional<Product> result = Optional.empty();
        private String lastBarcode;

        private TestProductService() {
            super(null, null);
        }

        @Override
        public Optional<Product> findActiveByBarcode(String barcode) {
            lastBarcode = barcode;
            return result;
        }
    }
}
