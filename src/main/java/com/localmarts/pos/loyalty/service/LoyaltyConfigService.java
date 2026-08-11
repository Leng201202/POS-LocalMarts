package com.localmarts.pos.loyalty.service;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.audit.utils.AuditChangeHelper;
import com.localmarts.pos.loyalty.dtos.LoyaltyConfigRequest;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.repository.LoyaltyConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoyaltyConfigService {

    private final String entity = "Loyalty-Config";

    private final LoyaltyConfigRepository loyaltyConfigRepository;
    private final AuditService auditService;

    @Transactional
    public LoyaltyConfig getConfig() {
        return findOrCreateConfig();
    }

    @Transactional
    public LoyaltyConfig update(
            LoyaltyConfigRequest request,
            Long updatedBy
    ) {
        LoyaltyConfig config = findOrCreateConfig();

        validateBusinessRules(request, updatedBy, config.getId());

        List<AuditDetails> details = new AuditChangeHelper()
                .addChange("minimumPurchaseAmount", config.getMinimumPurchaseAmount(), request.minimumPurchaseAmount())
                .addChange("spendingAmount", config.getSpendingAmount(), request.spendingAmount())
                .addChange("earnedPoints", config.getEarnedPoints(), request.earnedPoints())
                .addChange("redeemPoints", config.getRedeemPoints(), request.redeemPoints())
                .addChange("redeemValue", config.getRedeemValue(), request.redeemValue())
                .addChange("minimumRedeemPoints", config.getMinimumRedeemPoints(), request.minimumRedeemPoints())
                .addChange("earningEnabled", config.isEarningEnabled(), request.earningEnabled())
                .addChange("redemptionEnabled", config.isRedemptionEnabled(), request.redemptionEnabled())
                .build();

        config.setMinimumPurchaseAmount(request.minimumPurchaseAmount());
        config.setSpendingAmount(request.spendingAmount());
        config.setEarnedPoints(request.earnedPoints());
        config.setRedeemPoints(request.redeemPoints());
        config.setRedeemValue(request.redeemValue());
        config.setMinimumRedeemPoints(request.minimumRedeemPoints());
        config.setEarningEnabled(request.earningEnabled());
        config.setRedemptionEnabled(request.redemptionEnabled());
        config.setUpdatedBy(updatedBy);

        LoyaltyConfig saved = loyaltyConfigRepository.save(config);
        auditService.logSuccess(updatedBy, AuditAction.UPDATE, entity, saved.getId(), "Updated loyalty configuration", details);
        return saved;
    }

    private LoyaltyConfig findOrCreateConfig() {
        return loyaltyConfigRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> createDefaultConfig());
    }

    private LoyaltyConfig createDefaultConfig() {
        LoyaltyConfig config = LoyaltyConfig.builder()
                .minimumPurchaseAmount(new BigDecimal("100.00"))
                .spendingAmount(new BigDecimal("10.00"))
                .earnedPoints(1L)
                .redeemPoints(100L)
                .redeemValue(new BigDecimal("10.00"))
                .minimumRedeemPoints(100L)
                .earningEnabled(true)
                .redemptionEnabled(true)
                .build();
        config.setCreatedBy(null);
        LoyaltyConfig saved = loyaltyConfigRepository.save(config);
        auditService.logSuccess(null, AuditAction.CREATE, entity, saved.getId(), "Created default loyalty configuration");
        return saved;
    }

    private void validateBusinessRules(
            LoyaltyConfigRequest request,
            Long actor,
            Long rowId
    ) {
        if (request.minimumRedeemPoints()
                % request.redeemPoints() != 0) {

            String message = "Minimum redeem points must be divisible by redeem points.";
            auditService.logFailed(actor, AuditAction.UPDATE, entity, rowId, "Failed to update loyalty configuration. " + message);
            throw new IllegalArgumentException(message);
        }
    }
}