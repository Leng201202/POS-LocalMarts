package com.localmarts.pos.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ScannerSessionService {

    private static final long PAIRING_TTL_MINUTES = 5;

    private final Map<String, ScannerSession> sessions = new ConcurrentHashMap<>();

    public ScannerSession createSession(String terminalId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        ScannerSession session = ScannerSession.builder()
                .token(token)
                .terminalId(terminalId)
                .createdAt(LocalDateTime.now())
                .pairingExpiresAt(LocalDateTime.now().plusMinutes(PAIRING_TTL_MINUTES))
                .build();
        sessions.put(token, session);
        return session;
    }

    public Optional<ScannerSession> findByToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        ScannerSession session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isPairingExpired()) {
            sessions.remove(token, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public boolean connect(String token) {
        return findByToken(token)
                .map(session -> {
                    session.markConnected();
                    return true;
                })
                .orElse(false);
    }

    public boolean disconnect(String token) {
        return findByToken(token)
                .map(session -> {
                    session.markDisconnected();
                    return true;
                })
                .orElse(false);
    }

    public void delete(String token) {
        sessions.remove(token);
    }

    /** Called on cashier logout / server-level reset so scanners don't stay paired to a session that's gone. */
    public void disconnectAll() {
        if (!sessions.isEmpty()) {
            log.info("Disconnecting all scanner sessions ({})", sessions.size());
        }
        sessions.clear();
    }

    /** Sweeps sessions that were never joined within the pairing window. Connected sessions are never touched here. */
    @Scheduled(fixedRate = 60_000)
    public void purgeExpiredPairings() {
        sessions.values().removeIf(session -> {
            boolean expired = session.isPairingExpired();
            if (expired) {
                log.debug("Removing expired, unjoined scanner session {}", session.getToken());
            }
            return expired;
        });
    }
}
