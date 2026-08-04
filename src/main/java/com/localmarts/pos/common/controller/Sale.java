package com.localmarts.pos.common.controller;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Sale {
    @Id
    private Long id;
}
