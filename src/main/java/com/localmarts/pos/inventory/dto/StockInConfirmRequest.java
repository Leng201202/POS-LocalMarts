package com.localmarts.pos.inventory.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class StockInConfirmRequest {
    private String batchToken;
    private List<StockInConfirmRow> rows = new ArrayList<>();
}
