package com.localmarts.pos.inventory;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.category.CategoryService;
import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.config.SecurityConfig;
import com.localmarts.pos.inventory.service.*;
import com.localmarts.pos.inventory.entity.InventoryType;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.unit.UnitService;
import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.UserService;
import com.localmarts.pos.user.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
@Import({SecurityConfig.class, InventoryService.class})
class StockAdjustmentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean InventoryRepository movements;
    @MockitoBean ProductRepository products;
    @MockitoBean UserRepository users;
    @MockitoBean CurrentUserService currentUser;
    @MockitoBean AuditService audits;
    @MockitoBean UserService userService;
    @MockitoBean StockInTemplateService templates;
    @MockitoBean StockInFileService files;
    @MockitoBean StockInBatchService batches;
    @MockitoBean StockInSessionStore sessions;
    @MockitoBean CategoryService categories;
    @MockitoBean UnitService units;

    @Test @WithMockUser(roles = "CASHIER")
    void cashierPageOnlyOffersAllowedTypes() throws Exception {
        mvc.perform(get("/inventory/adjustment")).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"DAMAGE\"")))
                .andExpect(content().string(containsString("value=\"EXPIRED\"")))
                .andExpect(content().string(not(containsString("value=\"MISSING\""))))
                .andExpect(content().string(not(containsString("value=\"ADJUSTMENT\""))))
                .andExpect(content().string(containsString("href=\"/cashier\"")));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminPageOffersCorrection() throws Exception {
        when(currentUser.isAdmin(any())).thenReturn(true);
        mvc.perform(get("/inventory/adjustment")).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"ADJUSTMENT\"")))
                .andExpect(content().string(containsString("value=\"MISSING\"")));
    }

    @Test @WithMockUser(roles = "CASHIER")
    void forgedCashierRequestsDeniedByRealService() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(7L);
        when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).role(Role.CASHIER).build()));
        for (String type : List.of("MISSING", "ADJUSTMENT")) {
            mvc.perform(post("/inventory/adjustment").with(csrf())
                    .param("productId", "1").param("type", type).param("amount", "2").param("reason", "Check"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(products, movements, audits);
    }

    @Test @WithMockUser(roles = "CASHIER")
    void cashierCannotAccessStockInOrProductManagement() throws Exception {
        for (String path : List.of("/inventory/stock-in", "/inventory/template", "/products", "/products/search")) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
        for (String path : List.of("/inventory/stock-in/upload", "/inventory/stock-in/confirm", "/inventory/stock-in/cancel")) {
            mvc.perform(post(path).with(csrf())).andExpect(status().isForbidden());
        }
    }

    @Test @WithMockUser(roles = "CASHIER")
    void lookupExposesOnlyAdjustmentFields() throws Exception {
        when(products.searchActiveByNameOrBarcode("Milk")).thenReturn(List.of(
                Product.builder().id(1L).name("Milk").stockQuantity(10).build()));
        mvc.perform(get("/inventory/adjustment/products").param("keyword", "Milk"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].stockQuantity").value(10))
                .andExpect(jsonPath("$[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$[0].sellingPrice").doesNotExist());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void businessFailureRedirectsAndAuditsOnce() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(7L);
        when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).role(Role.ADMIN).build()));
        mvc.perform(post("/inventory/adjustment").with(csrf()).param("type", "DAMAGE")
                .param("productId", "1").param("amount", "0").param("reason", "Check"))
                .andExpect(redirectedUrl("/inventory/adjustment")).andExpect(flash().attributeExists("errorMessage"));
        verify(audits).logFailed(eq(7L), any(), eq("Inventory"), isNull(), anyString());
        verifyNoMoreInteractions(audits);
        verifyNoInteractions(movements);
    }

    @Test @WithMockUser(roles = "CASHIER")
    void fractionalAndOverflowAmountsAreRejected() throws Exception {
        for (String amount : List.of("1.5", "2147483648")) {
            mvc.perform(post("/inventory/adjustment").with(csrf()).param("amount", amount))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(movements);
    }

    @Test @WithMockUser(roles = "CASHIER")
    void csrfRequired() throws Exception {
        mvc.perform(post("/inventory/adjustment")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "OTHER")
    void unrelatedRoleDenied() throws Exception {
        mvc.perform(get("/inventory/adjustment")).andExpect(status().isForbidden());
        mvc.perform(get("/inventory/adjustment/products")).andExpect(status().isForbidden());
        mvc.perform(post("/inventory/adjustment").with(csrf())).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles = "ADMIN")
    void inventorySummaryUsesCombinedAdjustmentCountWithCurrentFilters() throws Exception {
        when(movements.filter(eq("water"), eq(InventoryType.DAMAGE), eq(7L), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(movements.countFilteredAdjustments("water", InventoryType.DAMAGE, 7L)).thenReturn(1L);
        mvc.perform(get("/inventory").param("keyword", " water ").param("type", "DAMAGE").param("actorId", "7"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("adjustmentCount", 1L));
        verify(movements).countFilteredAdjustments("water", InventoryType.DAMAGE, 7L);
        verify(movements).countFilteredByType("water", InventoryType.DAMAGE, 7L, InventoryType.STOCK_IN);
        verify(movements).countFilteredByType("water", InventoryType.DAMAGE, 7L, InventoryType.SALE);
    }

}
