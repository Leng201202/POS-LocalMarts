package com.localmarts.pos.inventory.dto;

/** Deliberately excludes pricing and product-management information. */
public record AdjustmentProduct(Long id, String name, String barcode, String size,
                                String unitName, Integer stockQuantity) {}
