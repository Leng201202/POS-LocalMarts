package com.localmarts.pos.scanner;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Page the phone opens after scanning the pairing QR code.
 * Everything under /scanner/** is public (SecurityConfig) - the phone never
 * logs in. Authorization is the possession of a valid, unexpired token.
 */
@Controller
@RequiredArgsConstructor
public class ScannerMobileController {

    private final ScannerSessionService scannerSessionService;

    @GetMapping("/scanner/join/{token}")
    public String scannerJoinPage(@PathVariable String token, Model model) {
        return scannerSessionService.findByToken(token)
                .map(session -> {
                    model.addAttribute("token", token);
                    model.addAttribute("terminalId", session.getTerminalId());
                    return "scanner/scanner";
                })
                .orElse("scanner/invalid-session");
    }
}
