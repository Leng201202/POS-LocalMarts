package com.localmarts.pos.scanner;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * A temporary pairing between a phone scanner and a POS terminal.
 * Mutable fields are volatile because connect/disconnect/scan requests
 * for the same session can arrive on different HTTP worker threads.
 */
@Getter
@Builder
public class ScannerSession {
    private final String token;
    private final String terminalId;
    private final LocalDateTime createdAt;

    /** Only enforced while the phone has not yet joined. */
    private final LocalDateTime pairingExpiresAt;

    @Builder.Default
    private volatile boolean connected = false;

    @Builder.Default
    private volatile LocalDateTime lastSeenAt = null;

    public void markConnected() {
        this.connected = true;
        this.lastSeenAt = LocalDateTime.now();
    }

    public void markDisconnected() {
        this.connected = false;
        this.lastSeenAt = LocalDateTime.now();
    }

    public void touch() {
        this.lastSeenAt = LocalDateTime.now();
    }

    /**
     * The 5-minute pairing window only matters before the phone joins.
     * Once connected, the session lives until it is explicitly
     * disconnected/deleted or the server restarts.
     */
    public boolean isPairingExpired() {
        return !connected && LocalDateTime.now().isAfter(pairingExpiresAt);
    }
}
