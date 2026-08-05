package com.localmarts.pos.loyalty.entity;

import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.sale.entity.Sale;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_transaction")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoyaltyTransaction {
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name="customer_id",nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    private Long points;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 40)
    private TransactionType type;

    @Column(nullable = false)
    private Long beforePoint;

    @Column(nullable = false)
    private Long afterPoint;

    @CreationTimestamp
    @Column(nullable = false,updatable = false)
    private LocalDateTime timestamp;





}
