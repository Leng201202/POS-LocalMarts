package com.localmarts.pos.report;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class CashierReportPreviewStore {
    private static final String KEY = "cashierSaleReportPreview";
    public void save(HttpSession session, CashierReportSnapshot preview) {
        synchronized (session) { session.setAttribute(KEY, preview); }
    }
    public Optional<CashierReportSnapshot> get(HttpSession session, Long actorId) {
        Object value = session.getAttribute(KEY);
        return value instanceof CashierReportSnapshot preview && preview.ownerActorId().equals(actorId)
                ? Optional.of(preview) : Optional.empty();
    }
    public Optional<CashierReportSnapshot> get(HttpSession session, Long actorId, java.util.UUID token) {
        return get(session, actorId).filter(preview -> preview.token().equals(token));
    }
    public void removeSubmitted(HttpSession session, Long actorId, java.util.UUID token) {
        synchronized (session) {
            if (get(session, actorId, token).isPresent()) clear(session);
        }
    }
    public void clear(HttpSession session) {
        synchronized (session) { session.removeAttribute(KEY); }
    }
}
