package com.localmarts.pos.report.cashier.controller;

import com.localmarts.pos.report.cashier.service.CashierReportPdfService;
import com.localmarts.pos.report.cashier.service.CashierReportPreviewStore;
import com.localmarts.pos.report.cashier.service.CashierReportSubmissionService;
import com.localmarts.pos.report.cashier.service.CashierSaleReportService;
import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.config.SecurityConfig;
import com.localmarts.pos.report.common.ReportDataException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CashierSaleReportController.class)
@Import({SecurityConfig.class, CashierReportPreviewStore.class})
class CashierReportPdfControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CurrentUserService currentUser;
    @MockitoBean CashierSaleReportService calculations;
    @MockitoBean CashierReportSubmissionService submissions;
    @MockitoBean CashierReportPdfService pdfs;
    @MockitoBean AuditService audits;

    @Test
    void cashierDownloadsOwnedReportAsPdfAttachment() throws Exception {
        byte[] bytes = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        when(pdfs.generate(2L, 8L)).thenReturn(Optional.of(
                new CashierReportPdfService.GeneratedPdf("cashier-sale-report-8.pdf", bytes)
        ));

        mvc.perform(get("/cashier/reports/8/pdf").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"cashier-sale-report-8.pdf\""))
                .andExpect(content().bytes(bytes));

        verify(currentUser).getCurrentUserId(any());
        verify(pdfs).generate(2L, 8L);
        verifyNoInteractions(submissions, calculations, audits);
    }

    @Test
    void missingAndOtherCashierReportIdsReturnTheSameNotFoundResponse() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L, 3L);
        when(pdfs.generate(2L, 999L)).thenReturn(Optional.empty());
        when(pdfs.generate(3L, 8L)).thenReturn(Optional.empty());

        mvc.perform(get("/cashier/reports/999/pdf").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/cashier/reports/8/pdf").with(user("other").roles("CASHIER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void previewHasNoPdfEndpointAndAdminCannotExport() throws Exception {
        mvc.perform(get("/cashier/reports/preview/pdf").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/cashier/reports/8/pdf").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/cashier/reports/8/pdf"))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(pdfs);
    }

    @Test
    void unsupportedSnapshotVersionReturnsClearUnprocessableStatus() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        when(pdfs.generate(2L, 8L))
                .thenThrow(new ReportDataException("Unsupported saved report snapshot version: 2."));

        mvc.perform(get("/cashier/reports/8/pdf").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isUnprocessableContent());
    }
}
