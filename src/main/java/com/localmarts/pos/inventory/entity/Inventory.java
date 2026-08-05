package com.localmarts.pos.inventory.entity;


import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventorys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id",nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 64,name = "inventory_type")
    private InventoryType inventoryType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false,name = "before_stock")
    private Integer beforeStock;

    @Column(nullable = false,name = "after_stock")
    private Integer afterStock;

    @Column(name = "reason",length = 255)
    private String reason;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id",nullable = false)
    private User actor;

    @CreationTimestamp
    @Column(nullable = false,updatable = false)
    private LocalDateTime timestamp;

}
