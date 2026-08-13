package com.localmarts.pos.scanner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Map;

/**
 * POS-side scanner pairing screen and session-management API.
 * Everything under /pos/scanner/** requires an authenticated ADMIN or CASHIER
 * (enforced in SecurityConfig) - the phone never calls these endpoints.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PosScannerController {

    private final ScannerSessionService scannerSessionService;

    @Value("${server.port}")
    private int serverPort;

    @GetMapping("/pos/scanner")
    public String scannerConnectPage() {
        return "scanner/connect";
    }

    @PostMapping("/pos/scanner/session")
    @ResponseBody
    public ResponseEntity<?> createSession() {
        ScannerSession session = scannerSessionService.createSession("POS-01");
        String joinUrl = "https://" + resolveLanIp() + ":" + serverPort + "/scanner/join/" + session.getToken();
        return ResponseEntity.ok(Map.of(
                "token", session.getToken(),
                "terminalId", session.getTerminalId(),
                "expiresAt", session.getPairingExpiresAt(),
                "joinUrl", joinUrl
        ));
    }

    @GetMapping("/pos/scanner/session/{token}/status")
    @ResponseBody
    public ResponseEntity<?> getStatus(@PathVariable String token) {
        return scannerSessionService.findByToken(token)
                .map(session -> ResponseEntity.ok(Map.of("connected", session.isConnected())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/pos/scanner/session/{token}")
    @ResponseBody
    public ResponseEntity<?> deleteSession(@PathVariable String token) {
        scannerSessionService.delete(token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Best-effort LAN IPv4 lookup so the QR code points phones at an address
     * they can actually reach instead of "localhost" (which only resolves on
     * the POS machine itself).
     */
    private String resolveLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || iface.isVirtual() || !iface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Unable to resolve LAN IP address, falling back to localhost", e);
        }
        return "localhost";
    }
}
