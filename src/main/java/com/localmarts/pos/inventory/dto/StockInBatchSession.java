package com.localmarts.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Server-side snapshot of a parsed/reviewed upload, held in the HttpSession
 * between POST /upload and POST /confirm. Nothing in here is trusted from
 * the browser again except via the batchToken match.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockInBatchSession implements Serializable {
    private String batchToken;
    private String fileName;
    private LocalDateTime uploadedAt;
    private List<StockInReviewRow> rows;
}
