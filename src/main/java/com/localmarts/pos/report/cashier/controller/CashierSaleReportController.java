package com.localmarts.pos.report.cashier.controller;

import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.report.common.ReportDataException;
import com.localmarts.pos.report.cashier.dto.ReportPeriodRequest;
import com.localmarts.pos.report.cashier.dto.ReportSubmissionRequest;
import com.localmarts.pos.report.cashier.exception.DuplicateReportException;
import com.localmarts.pos.report.cashier.model.SavedReportView;
import com.localmarts.pos.report.cashier.service.CashierReportPdfService;
import com.localmarts.pos.report.cashier.service.CashierReportPreviewStore;
import com.localmarts.pos.report.cashier.service.CashierReportSubmissionService;
import com.localmarts.pos.report.cashier.service.CashierSaleReportService;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cashier/reports")
public class CashierSaleReportController {
    private final CurrentUserService currentUser;
    private final CashierSaleReportService reports;
    private final CashierReportPreviewStore previews;
    private final CashierReportSubmissionService submissions;
    private final CashierReportPdfService pdfs;
    private final AuditService audits;
    public CashierSaleReportController(CurrentUserService currentUser, CashierSaleReportService reports,
            CashierReportPreviewStore previews, CashierReportSubmissionService submissions,
            CashierReportPdfService pdfs, AuditService audits) {
        this.currentUser = currentUser; this.reports = reports; this.previews = previews;
        this.submissions = submissions; this.pdfs = pdfs; this.audits = audits;
    }
    @InitBinder("request")
    void bindPeriodOnly(WebDataBinder binder) {
        binder.setAllowedFields("type", "date", "startDate", "endDate");
    }
    @InitBinder("submission")
    void bindSubmissionOnly(WebDataBinder binder) {
        binder.setAllowedFields("previewToken", "realWorldCash");
    }
    @GetMapping
    public String index(Authentication authentication, Model model) {
        model.addAttribute("savedReports", submissions.list(currentUser.getCurrentUserId(authentication)));
        return "cashier/reports/index";
    }
    @GetMapping("/{id}")
    public String saved(@PathVariable Long id, Authentication authentication, Model model) {
        SavedReportView saved = submissions.view(currentUser.getCurrentUserId(authentication), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found."));
        model.addAttribute("saved", saved);
        model.addAttribute("report", saved.snapshot());
        return "cashier/reports/preview";
    }
    @GetMapping(value = "/{id:\\d+}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, Authentication authentication) {
        CashierReportPdfService.GeneratedPdf pdf;
        try {
            pdf = pdfs.generate(currentUser.getCurrentUserId(authentication), id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found."));
        } catch (ReportDataException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(), exception);
        }
        byte[] content = pdf.content();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .body(content);
    }
    @ExceptionHandler(ReportDataException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public String snapshotError(ReportDataException exception, Model model) {
        model.addAttribute("error", exception.getMessage());
        return "cashier/reports/error";
    }
    @PostMapping("/submit")
    public String submit(@Valid @ModelAttribute("submission") ReportSubmissionRequest request, BindingResult errors,
            Authentication authentication, HttpSession session, RedirectAttributes redirect) {
        Long actorId = currentUser.getCurrentUserId(authentication);
        var preview = previews.get(session, actorId, request.getPreviewToken());
        if (errors.hasErrors() || preview.isEmpty()) {
            String message = preview.isEmpty() ? "This preview is missing, replaced, or unavailable for your account. Generate a new preview."
                    : "Enter Real-World Cash between 0 and 999999999999.99 with at most two decimals.";
            return submissionFailure(actorId, message, null, preview.isEmpty(), redirect);
        }
        try {
            Long id = submissions.submit(actorId, preview.get(), request.getRealWorldCash());
            // The transactional service has returned after commit; never consume the preview on failure.
            previews.removeSubmitted(session, actorId, request.getPreviewToken());
            return "redirect:/cashier/reports/" + id;
        } catch (DuplicateReportException ex) {
            return submissionFailure(actorId, ex.getMessage(), ex.getReportId(), false, redirect);
        } catch (DataAccessException ex) {
            // The failed transaction has rolled back. SQLite may expose a unique violation as
            // JpaSystemException rather than DataIntegrityViolationException. Resolve only a
            // confirmed racing duplicate using another owner-scoped read; rethrow other failures.
            var existing = submissions.existing(actorId, preview.get());
            if (existing.isEmpty()) throw ex;
            return submissionFailure(actorId, new DuplicateReportException(existing.get()).getMessage(), existing.get(), false, redirect);
        } catch (IllegalArgumentException ex) {
            return submissionFailure(actorId, ex.getMessage(), null, false, redirect);
        }
    }
    private String submissionFailure(Long actorId, String message, Long existingId, boolean newPreview, RedirectAttributes redirect) {
        audits.logFailed(actorId, AuditAction.CREATE, "CashierSaleReport", null, message);
        redirect.addFlashAttribute("error", message);
        if (existingId != null) redirect.addFlashAttribute("existingReportId", existingId);
        return newPreview ? "redirect:/cashier/reports/new" : "redirect:/cashier/reports/preview";
    }
    @GetMapping("/new")
    public String form(Model model) {
        ReportPeriodRequest request = new ReportPeriodRequest();
        request.setDate(reports.today());
        model.addAttribute("request", request);
        return "cashier/reports/new";
    }
    @PostMapping("/preview")
    public String generate(@ModelAttribute("request") ReportPeriodRequest request, BindingResult errors,
            Authentication authentication, HttpSession session, Model model) {
        Long actorId = currentUser.getCurrentUserId(authentication);
        previews.clear(session);
        if (errors.hasErrors()) {
            model.addAttribute("error", "Choose a valid report type and valid dates.");
            return "cashier/reports/new";
        }
        try {
            previews.save(session, reports.preview(actorId, request));
            return "redirect:/cashier/reports/preview";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "cashier/reports/new";
        }
    }
    @GetMapping("/preview")
    public String preview(Authentication authentication, HttpSession session, Model model, RedirectAttributes redirect) {
        var preview = previews.get(session, currentUser.getCurrentUserId(authentication));
        if (preview.isEmpty()) {
            redirect.addFlashAttribute("error", "Generate a new preview for your account.");
            return "redirect:/cashier/reports/new";
        }
        model.addAttribute("report", preview.get());
        return "cashier/reports/preview";
    }
}
