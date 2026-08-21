package com.localmarts.pos.scanner;

import com.localmarts.pos.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Pages the phone opens after scanning the pairing QR code and after each
 * barcode scan. Everything under /scanner/** is public (SecurityConfig) -
 * the phone never logs in. Authorization is the possession of a valid,
 * unexpired token.
 */
@Controller
@RequiredArgsConstructor
public class ScannerMobileController {

    private final ScannerSessionService scannerSessionService;
    private final ProductService productService;

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

    @GetMapping("/scanner/{token}/result")
    public String scanResultPage(
            @PathVariable String token,
            @RequestParam String barcode,
            Model model) {

        return scannerSessionService.findByToken(token)
                .map(session -> {
                    model.addAttribute("token", token);
                    model.addAttribute("terminalId", session.getTerminalId());
                    model.addAttribute("barcode", barcode);
                    productService.findByBarcode(barcode)
                            .ifPresentOrElse(
                                    product -> model.addAttribute("product", product),
                                    () -> model.addAttribute("productNotFound", true)
                            );
                    return "scanner/scan-result";
                })
                .orElse("scanner/invalid-session");
    }
}
