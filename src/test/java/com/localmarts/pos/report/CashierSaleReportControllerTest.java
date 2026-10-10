package com.localmarts.pos.report;

import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.config.SecurityConfig;
import com.localmarts.pos.sale.entity.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.List;
import static com.localmarts.pos.report.ReportFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CashierSaleReportController.class, com.localmarts.pos.sale.SaleController.class})
@Import({SecurityConfig.class, CashierReportPreviewStore.class})
class CashierSaleReportControllerTest {
    @Autowired MockMvc mvc;
    @Autowired CashierReportPreviewStore store;
    @MockitoBean CurrentUserService currentUser;
    @MockitoBean CashierSaleReportService service;
    @MockitoBean CashierReportSubmissionService submissions;
    @MockitoBean CashierReportPdfService pdfs;
    @MockitoBean com.localmarts.pos.audit.AuditService audits;
    @MockitoBean com.localmarts.pos.sale.SaleService sales;
    @MockitoBean com.localmarts.pos.sale.RefundService refunds;
    @MockitoBean com.localmarts.pos.customer.CustomerService customers;
    @MockitoBean com.localmarts.pos.loyalty.service.LoyaltyConfigService loyalty;

    @Test void dashboardLinksCashierToReports() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        when(sales.getTodayRevenue(2L)).thenReturn(money("0"));
        when(sales.getRecentSales(2L)).thenReturn(List.of());
        mvc.perform(get("/cashier").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/cashier/reports\"")));
        mvc.perform(get("/cashier").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("href=\"/cashier/reports\""))));
    }


    @Test void cashierLandingAndFormRenderWithCsrfAndNoOwnershipInput() throws Exception {
        when(service.today()).thenReturn(LocalDate.of(2026,9,4));
        mvc.perform(get("/cashier/reports").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Generate Report")));
        mvc.perform(get("/cashier/reports/new").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("2026-09-04")))
                .andExpect(content().string(not(containsString("name=\"cashierId\""))));
        assertThat(ReportPeriodRequest.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("type","date","startDate","endDate");
    }
    @Test void forgedCashierIdCannotOverrideActorAndPreviewIsStoredAndRendered() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        var sale = sale(1,2,PaymentMethod.CASH);
        var report = new CashierSaleReportCalculator().calculate(2L,"cashier","Test Cashier",all(),List.of(sale),
                List.of(new RefundAuditReader().read(audit(49,sale,2,true),sale)));
        when(service.preview(eq(2L),any())).thenReturn(report);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/cashier/reports/preview").session(session).with(user("cashier").roles("CASHIER")).with(csrf())
                .param("type","DAILY").param("date","2026-09-04").param("cashierId","999").param("actorId","999"))
                .andExpect(redirectedUrl("/cashier/reports/preview"));
        verify(service).preview(eq(2L),argThat(r -> r.getDate().equals(LocalDate.of(2026,9,4))));
        assertThat(store.get(session,2L)).contains(report);
        mvc.perform(get("/cashier/reports/preview").session(session).with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Test Cashier")))
                .andExpect(content().string(containsString("Refund Activity")))
                .andExpect(content().string(containsString("Refund activity for <span>Test Cashier</span>")))
                .andExpect(content().string(not(containsString("Refunds Processed by You"))))
                .andExpect(content().string(not(containsString("processed by you"))))
                .andExpect(content().string(containsString("Unavailable")))
                .andExpect(content().string(containsString("22.50")))
                .andExpect(content().string(containsString("Submit Report")))
                .andExpect(content().string(not(containsString("PDF Export"))));
        when(currentUser.getCurrentUserId(any())).thenReturn(3L);
        mvc.perform(get("/cashier/reports/preview").session(session).with(user("other").roles("CASHIER")))
                .andExpect(redirectedUrl("/cashier/reports/new"));
    }
    @Test void emptyPreviewRendersAndStoreReplacesOnlyCurrentSnapshot() throws Exception {
        var calculator = new CashierSaleReportCalculator();
        var first=calculator.calculate(2L,"cashier","Cashier",all(),List.of(),List.of());
        var second=calculator.calculate(2L,"cashier","Cashier",all(),List.of(),List.of());
        MockHttpSession session=new MockHttpSession();store.save(session,first);store.save(session,second);
        assertThat(store.get(session,2L)).contains(second);
        assertThat(first.token()).isNotEqualTo(second.token());
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        mvc.perform(get("/cashier/reports/preview").session(session).with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("No paid sales")))
                .andExpect(content().string(containsString("No refunds were processed by <span>Cashier</span> during this report period.")))
                .andExpect(content().string(containsString("None")));
    }
    @Test void failedGenerationClearsOldPreviewAndDisplaysError() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        when(service.preview(eq(2L),any())).thenThrow(new ReportDataException("Cannot generate preview: refund Audit 49: missing refundTotal."));
        MockHttpSession session=new MockHttpSession();
        store.save(session,new CashierSaleReportCalculator().calculate(2L,"cashier","Cashier",all(),List.of(),List.of()));
        mvc.perform(post("/cashier/reports/preview").session(session).with(user("cashier").roles("CASHIER")).with(csrf())
                .param("type","DAILY").param("date","2026-09-04"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("missing refundTotal")));
        assertThat(store.get(session,2L)).isEmpty();
    }
    @Test void malformedFormDoesNotCalculateAndMissingSessionRedirects() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(2L);
        mvc.perform(post("/cashier/reports/preview").with(user("cashier").roles("CASHIER")).with(csrf())
                .param("type","INVALID").param("date","bad"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Choose a valid report type")));
        verifyNoInteractions(service);
        mvc.perform(get("/cashier/reports/preview").with(user("cashier").roles("CASHIER")))
                .andExpect(redirectedUrl("/cashier/reports/new"));
    }
    @Test void routesAreCashierOnlyAndPostRequiresCsrf() throws Exception {
        for(String path:List.of("/cashier/reports","/cashier/reports/new","/cashier/reports/preview")) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
            for(String role:List.of("ADMIN","OTHER"))
                mvc.perform(get(path).with(user("user").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/cashier/reports/preview").with(user("cashier").roles("CASHIER"))).andExpect(status().isForbidden());
        mvc.perform(post("/cashier/reports/preview").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/cashier/reports/preview").with(csrf())).andExpect(status().is3xxRedirection());
        verifyNoInteractions(service);
    }
}
