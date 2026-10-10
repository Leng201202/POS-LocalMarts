package com.localmarts.pos.report;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static com.localmarts.pos.report.ReportFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CashierSaleReportController.class)
@Import({SecurityConfig.class,CashierReportPreviewStore.class})
class CashierReportSubmissionControllerTest {
    @Autowired MockMvc mvc;
    @Autowired CashierReportPreviewStore store;
    @MockitoBean CurrentUserService currentUser;
    @MockitoBean CashierSaleReportService calculations;
    @MockitoBean CashierReportSubmissionService submissions;
    @MockitoBean CashierReportPdfService pdfs;
    @MockitoBean AuditService audits;
    CashierReportSnapshot preview() {
        return new CashierSaleReportCalculator().calculate(2L,"cashier2","Frozen Cashier",all(),List.of(),List.of());
    }
    @Test void acceptsOnlyTokenAndCashAndConsumesPreviewAfterCommitWithoutRecalculation() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        var p=preview();var session=new MockHttpSession();store.save(session,p);
        when(submissions.submit(eq(2L),same(p),eq(money("10.50")))).thenReturn(8L);
        mvc.perform(post("/cashier/reports/submit").session(session).with(user("cashier").roles("CASHIER")).with(csrf())
                .param("previewToken",p.token().toString()).param("realWorldCash","10.50")
                .param("cashierId","999").param("systemCash","999").param("cashDifference","999")
                .param("snapshotJson","{bad}").param("startDate","2000-01-01").param("endDate","2000-01-02"))
                .andExpect(redirectedUrl("/cashier/reports/8"));
        verify(submissions).submit(eq(2L),same(p),eq(money("10.50")));
        assertThat(store.get(session,2L)).isEmpty();verifyNoInteractions(calculations);
        mvc.perform(post("/cashier/reports/submit").session(session).with(user("cashier").roles("CASHIER")).with(csrf())
                .param("previewToken",p.token().toString()).param("realWorldCash","10.50"))
                .andExpect(redirectedUrl("/cashier/reports/new"));
        verify(submissions,times(1)).submit(any(),any(),any());
        assertThat(ReportSubmissionRequest.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("previewToken","realWorldCash");
    }
    @Test void missingWrongMalformedReplacedAndOtherOwnerTokensCannotSubmit() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        var p=preview();var session=new MockHttpSession();store.save(session,p);
        for(String token:List.of(UUID.randomUUID().toString(),"invalid",""))
            mvc.perform(post("/cashier/reports/submit").session(session).with(user("cashier").roles("CASHIER")).with(csrf())
                    .param("previewToken",token).param("realWorldCash","0"))
                    .andExpect(redirectedUrl("/cashier/reports/new"));
        store.save(session,preview());
        mvc.perform(post("/cashier/reports/submit").session(session).with(user("cashier").roles("CASHIER")).with(csrf())
                .param("previewToken",p.token().toString()).param("realWorldCash","0"))
                .andExpect(redirectedUrl("/cashier/reports/new"));
        store.save(session,p);when(currentUser.getCurrentUserId(any())).thenReturn(3L);
        mvc.perform(post("/cashier/reports/submit").session(session).with(user("other").roles("CASHIER")).with(csrf())
                .param("previewToken",p.token().toString()).param("realWorldCash","0"))
                .andExpect(redirectedUrl("/cashier/reports/new"));
        verifyNoInteractions(submissions);
    }
    @Test void serverRejectsInvalidCashAndRetainsPreview() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        var p=preview();var session=new MockHttpSession();store.save(session,p);
        for(String amount:List.of("", "-1", "0.001", "1000000000000", "not money"))
            mvc.perform(post("/cashier/reports/submit").session(session).with(user("cashier").roles("CASHIER")).with(csrf())
                    .param("previewToken",p.token().toString()).param("realWorldCash",amount))
                    .andExpect(redirectedUrl("/cashier/reports/preview")).andExpect(flash().attributeExists("error"));
        verifyNoInteractions(submissions);assertThat(store.get(session,2L)).contains(p);
    }
    @Test void duplicateAndRacingDuplicateProvideScopedExistingLinkAndDoNotConsumePreview() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        var p=preview();var session=new MockHttpSession();store.save(session,p);
        when(submissions.submit(eq(2L),same(p),any())).thenThrow(new DuplicateReportException(8L))
                .thenThrow(new DataIntegrityViolationException("unique constraint"))
                .thenThrow(new org.springframework.orm.jpa.JpaSystemException(new RuntimeException("SQLite unique violation")));
        when(submissions.existing(2L,p)).thenReturn(Optional.of(8L));
        for(int i=0;i<3;i++)
            mvc.perform(post("/cashier/reports/submit").session(session).with(user("cashier").roles("CASHIER")).with(csrf())
                    .param("previewToken",p.token().toString()).param("realWorldCash","1"))
                    .andExpect(redirectedUrl("/cashier/reports/preview")).andExpect(flash().attribute("existingReportId",8L));
        assertThat(store.get(session,2L)).contains(p);
    }
    @Test void listAndImmutableDetailAreOwnerScopedAndUseFrozenContent() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        var p=preview();var saved=new SavedReportView(8L,p.period().type(),p.period().startDate(),p.period().endDate(),
                p.period().generatedAt(),p.period().generatedAt().plusHours(1),p.systemCash(),money("1"),money("1"),p);
        when(submissions.list(2L)).thenReturn(List.of(saved));when(submissions.view(2L,8L)).thenReturn(Optional.of(saved));
        mvc.perform(get("/cashier/reports").param("cashierId","999").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("My Reports")))
                .andExpect(content().string(containsString("/cashier/reports/8")))
                .andExpect(content().string(containsString("/cashier/reports/8/pdf")));
        mvc.perform(get("/cashier/reports/8").param("cashierId","999").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Submitted Cashier Sale Report")))
                .andExpect(content().string(containsString("Frozen Cashier"))).andExpect(content().string(containsString("OVER")))
                .andExpect(content().string(containsString("Refund Activity")))
                .andExpect(content().string(containsString("Refund activity for <span>Frozen Cashier</span>")))
                .andExpect(content().string(containsString("No refunds were processed by <span>Frozen Cashier</span> during this report period.")))
                .andExpect(content().string(not(containsString("Refunds Processed by You"))))
                .andExpect(content().string(not(containsString("processed by you"))))
                .andExpect(content().string(not(containsString("Submit Report"))))
                .andExpect(content().string(not(containsString("name=\"realWorldCash\""))))
                .andExpect(content().string(not(containsString("snapshotJson"))))
                .andExpect(content().string(containsString("Download PDF")));
        when(submissions.view(2L,9L)).thenReturn(Optional.empty());
        when(submissions.view(2L,999L)).thenReturn(Optional.empty());
        for(long id:List.of(9L,999L)) mvc.perform(get("/cashier/reports/"+id).with(user("cashier").roles("CASHIER"))).andExpect(status().isNotFound());
        verify(submissions).list(2L);verifyNoInteractions(calculations);
    }
    @Test void unsupportedSnapshotHasClearErrorAndActivePreviewWarns() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        when(submissions.view(2L,8L)).thenThrow(new ReportDataException("Unsupported saved report snapshot version: 2."));
        mvc.perform(get("/cashier/reports/8").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isUnprocessableEntity()).andExpect(content().string(containsString("Unsupported saved report snapshot version")));
        var session=new MockHttpSession();store.save(session,preview());
        mvc.perform(get("/cashier/reports/preview").session(session).with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Later transactions will not be added")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("After submission, this report cannot be edited")))
                .andExpect(content().string(not(containsString("Download PDF"))));
    }
    @Test void cashiersOnlyAndCsrfRequiredAndNoEditDeleteRoutes() throws Exception {
        for(String role:List.of("ADMIN","OTHER")) {
            mvc.perform(post("/cashier/reports/submit").with(user("user").roles(role)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(get("/cashier/reports/8").with(user("user").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/cashier/reports/submit").with(user("cashier").roles("CASHIER"))).andExpect(status().isForbidden());
        mvc.perform(post("/cashier/reports/submit").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(delete("/cashier/reports/8").with(user("cashier").roles("CASHIER")).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/cashier/reports/8").with(user("cashier").roles("CASHIER")).with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(submissions);
    }
}
