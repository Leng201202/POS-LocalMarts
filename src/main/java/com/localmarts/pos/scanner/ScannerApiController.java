package com.localmarts.pos.scanner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Phone-facing JSON API. Public (see SecurityConfig: /api/scanner/** and
 * CSRF is not required here since the phone holds no cashier session/cookie -
 * the scanner token in the path is what authorizes each call.
 *
 * This must stay limited to join / leave / send-a-barcode. It must never
 * expose ADMIN or CASHIER functionality.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ScannerApiController {

    private final ScannerSessionService scannerSessionService;

    @PostMapping("/api/scanner/session/{token}/connect")
    public ResponseEntity<?> connect(@PathVariable String token) {
        boolean connected = scannerSessionService.connect(token);
        if (!connected) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("connected", true));
    }

    @PostMapping("/api/scanner/session/{token}/disconnect")
    public ResponseEntity<?> disconnect(@PathVariable String token) {
        boolean disconnected = scannerSessionService.disconnect(token);
        if (!disconnected) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("connected", false));
    }

    @PostMapping("/api/scanner/session/{token}/scan")
    public ResponseEntity<?> scan(@PathVariable String token, @RequestBody(required = false) Map<String, String> body) {
        Optional<ScannerSession> sessionOpt = scannerSessionService.findByToken(token);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Invalid or unknown scanner session"));
        }

        ScannerSession session = sessionOpt.get();
        if (!session.isConnected()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "error", "Scanner is not connected"));
        }

        String barcode = body == null ? null : body.get("barcode");
        if (barcode == null || barcode.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Barcode is required"));
        }

        barcode = barcode.trim();
        session.touch();
        log.info("Received barcode: {} (terminal={}, token={})", barcode, session.getTerminalId(), token);

        return ResponseEntity.ok(Map.of("success", true, "barcode", barcode));
    }
}
