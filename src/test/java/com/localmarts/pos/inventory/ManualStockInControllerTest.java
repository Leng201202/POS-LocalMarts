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
class ManualStockInControllerTest {
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


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ADMIN", "CASHIER"})
    void permittedRolesCanRenderAndSubmitWithoutChangingMetadata(String role) throws Exception {
        var principal = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("receiver").roles(role);
        when(currentUser.getCurrentUserId(any())).thenReturn(7L);
        when(currentUser.isAdmin(any())).thenReturn(role.equals("ADMIN"));
        when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).role(Role.valueOf(role)).build()));
        Product product = Product.builder().id(1L).name("Water").stockQuantity(15)
                .costPrice(new java.math.BigDecimal("5")).sellingPrice(new java.math.BigDecimal("8"))
                .mfd(java.time.LocalDate.of(2026, 1, 1)).exp(java.time.LocalDate.of(2027, 1, 1))
                .minimumStock(2).discountPercent(java.math.BigDecimal.ZERO).build();
        Product original = Product.builder().build();
        org.springframework.beans.BeanUtils.copyProperties(product, original);
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(movements.save(any())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(get("/inventory/stock-in/manual").with(principal))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Confirm Manual Stock In")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/inventory/stock-in/manual").with(principal).with(csrf())
                .param("productId", "1").param("quantity", "20").param("reason", "  New delivery  ")
                .param("actorId", "999").param("type", "ADJUSTMENT").param("inventoryType", "SALE")
                .param("costPrice", "999").param("sellingPrice", "999").param("stockQuantity", "999")
                .param("beforeStock", "999").param("afterStock", "999")
                .param("mfd", "2000-01-01").param("exp", "2001-01-01")
                .param("category.id", "999").param("unit.id", "999")
                .param("minimumStock", "999").param("discountPercent", "99"))
                .andExpect(redirectedUrl("/inventory/stock-in/manual"))
                .andExpect(flash().attribute("successMessage", "Manual Stock In saved: stock increased from 15 to 35."));
        org.assertj.core.api.Assertions.assertThat(product.getStockQuantity()).isEqualTo(35);
        org.assertj.core.api.Assertions.assertThat(product).usingRecursiveComparison()
                .ignoringFields("stockQuantity").isEqualTo(original);
        verify(movements).save(argThat(m -> m.getInventoryType() == InventoryType.STOCK_IN
                && m.getQuantity() == 20 && m.getBeforeStock() == 15 && m.getAfterStock() == 35
                && m.getActor().getId() == 7L && m.getReason().equals("New delivery")));
        verify(audits).logSuccess(eq(7L), any(), eq("Inventory"), any(), eq("Manual Stock In"), anyList());
        verifyNoMoreInteractions(audits);
    }

    @Test @WithMockUser(roles = "CASHIER")
    void forgedUnavailableProductIdIsRejectedAndFailureAudited() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(7L);
        when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).role(Role.CASHIER).build()));
        when(products.findById(999L)).thenReturn(Optional.empty());
        mvc.perform(post("/inventory/stock-in/manual").with(csrf()).param("productId", "999")
                .param("quantity", "20").param("reason", "Delivery"))
                .andExpect(redirectedUrl("/inventory/stock-in/manual"))
                .andExpect(flash().attributeExists("errorMessage"));
        verifyNoInteractions(movements);
        verify(audits).logFailed(eq(7L), any(), eq("Inventory"), isNull(), anyString());
        verifyNoMoreInteractions(audits);
    }

    @Test @WithMockUser(roles = "CASHIER")
    void malformedQuantityHasFriendlyErrorAndNoWrites() throws Exception {
        for (String quantity : List.of("1.5", "2147483648", "abc")) {
            mvc.perform(post("/inventory/stock-in/manual").with(csrf())
                    .param("productId", "1").param("quantity", quantity).param("reason", "Delivery"))
                    .andExpect(redirectedUrl("/inventory/stock-in/manual"))
                    .andExpect(flash().attributeExists("errorMessage"));
        }
        verifyNoInteractions(movements, audits);
    }

    @Test @WithMockUser(roles = "CASHIER")
    void cashierStillDeniedBatchAndProductsAndCsrfRequired() throws Exception {
        for (String path : List.of("/inventory/stock-in", "/inventory/stock-in/methods", "/inventory/template", "/products", "/products/create")) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
        for (String path : List.of("/inventory/stock-in/upload", "/inventory/stock-in/confirm", "/inventory/stock-in/cancel")) {
            mvc.perform(post(path).with(csrf())).andExpect(status().isForbidden());
        }
        mvc.perform(post("/inventory/stock-in/manual")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminRetainsBatchPageAccess() throws Exception {
        mvc.perform(get("/inventory/stock-in")).andExpect(status().isOk())
                .andExpect(view().name("common/inventory/stock-in"));
    }

    @Test void anonymousDenied() throws Exception {
        mvc.perform(get("/inventory/stock-in/manual")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/inventory/stock-in/manual").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/inventory/adjustment/products")).andExpect(status().is3xxRedirection());
    }

    @Test @WithMockUser(roles = "OTHER")
    void otherRoleDenied() throws Exception {
        mvc.perform(get("/inventory/stock-in/manual")).andExpect(status().isForbidden());
        mvc.perform(post("/inventory/stock-in/manual").with(csrf())).andExpect(status().isForbidden());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ADMIN", "CASHIER"})
    void inventoryNavigationAndSharedLookupRespectRoles(String role) throws Exception {
        var principal = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("receiver").roles(role);
        when(currentUser.isAdmin(any())).thenReturn(role.equals("ADMIN"));
        when(movements.filter(isNull(), isNull(), isNull(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        String destination = role.equals("ADMIN") ? "/inventory/stock-in/methods" : "/inventory/stock-in/manual";
        mvc.perform(get("/inventory").with(principal)).andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        org.springframework.util.StringUtils.countOccurrencesOf(
                                result.getResponse().getContentAsString(), "href=\"/inventory/stock-in")).isEqualTo(1))
                .andExpect(content().string(containsString("href=\"" + destination + "\"")))
                .andExpect(content().string(not(containsString("Manual Stock In"))))
                .andExpect(content().string(not(containsString("Batch Stock In"))))
                .andExpect(content().string(containsString("Stock Adjustment")));
        when(products.searchActiveByNameOrBarcode("water")).thenReturn(List.of(
                Product.builder().id(1L).name("Water").stockQuantity(15).build()));
        mvc.perform(get("/inventory/adjustment/products").with(principal).param("keyword", "water"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].stockQuantity").value(15))
                .andExpect(jsonPath("$[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$[0].mfd").doesNotExist())
                .andExpect(jsonPath("$[0].exp").doesNotExist());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void stockInSelectionOffersExistingMethodsAndRoleAwareNavigation() throws Exception {
        when(currentUser.isAdmin(any())).thenReturn(true);
        mvc.perform(get("/inventory/stock-in/methods"))
                .andExpect(status().isOk())
                .andExpect(view().name("common/inventory/stock-in-methods"))
                .andExpect(content().string(containsString("href=\"/inventory/stock-in/manual\"")))
                .andExpect(content().string(containsString("href=\"/inventory/stock-in\"")))
                .andExpect(content().string(containsString("Add received stock to one existing product.")))
                .andExpect(content().string(containsString("Upload an Excel file to receive multiple products at once.")))
                .andExpect(content().string(containsString("href=\"/admin\"")))
                .andExpect(content().string(containsString("href=\"/inventory\"")));
        verifyNoInteractions(movements, products, audits, batches);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void sharedSelectionTemplateHidesBatchWhenNavigationPermissionIsAbsent() throws Exception {
        when(currentUser.isAdmin(any())).thenReturn(false);
        mvc.perform(get("/inventory/stock-in/methods"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Manual Stock In")))
                .andExpect(content().string(not(containsString("Batch Stock In"))))
                .andExpect(content().string(containsString("href=\"/cashier\"")));
    }

}
