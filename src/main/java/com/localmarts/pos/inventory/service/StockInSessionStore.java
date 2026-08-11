package com.localmarts.pos.inventory.service;

import com.localmarts.pos.inventory.dto.StockInBatchSession;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Thin wrapper around the HttpSession attribute used to hold a parsed
 * stock-in batch between upload and confirm. No business logic - just
 * storage plumbing, kept out of the controller.
 */
@Component
public class StockInSessionStore {

    private static final String SESSION_KEY = "stockInBatchSession";

    public void save(HttpSession session, StockInBatchSession batch) {
        session.setAttribute(SESSION_KEY, batch);
    }

    public Optional<StockInBatchSession> get(HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        if (value instanceof StockInBatchSession batch) {
            return Optional.of(batch);
        }
        return Optional.empty();
    }

    public void clear(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
    }
}
