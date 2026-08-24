package com.localmarts.pos.loyalty.service;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.audit.utils.AuditChangeHelper;
import com.localmarts.pos.loyalty.dtos.LoyaltyConfigRequest;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.repository.LoyaltyConfigRepository;
import com.localmarts.pos.sale.entity.Sale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    public LoyaltyConfig getSaleConfig(LocalDateTime saleTimeStamp){
        if(saleTimeStamp==null) throw new IllegalArgumentException(
                "Sale timestamp is required"
        );
        return loyaltyConfigRepository.findFirstByCreatedAtLessThanEqualOrderByCreatedAtDesc(
                saleTimeStamp
        ).orElseThrow(()->new IllegalStateException(
                        "No Loyalty config for this sale"
                )
        );
    }

    @Transactional
    public LoyaltyConfig update(
            LoyaltyConfigRequest request,
            Long updatedBy
    ) {
        LoyaltyConfig currConfig = findOrCreateConfig();

        validateBusinessRules(request, updatedBy, currConfig.getId());

        List<AuditDetails> details = new AuditChangeHelper()
                .addChange("minimumPurchaseAmount", currConfig.getMinimumPurchaseAmount(), request.minimumPurchaseAmount())
                .addChange("spendingAmount", currConfig.getSpendingAmount(), request.spendingAmount())
                .addChange("earnedPoints", currConfig.getEarnedPoints(), request.earnedPoints())
                .addChange("redeemPoints", currConfig.getRedeemPoints(), request.redeemPoints())
                .addChange("redeemValue", currConfig.getRedeemValue(), request.redeemValue())
                .addChange("minimumRedeemPoints", currConfig.getMinimumRedeemPoints(), request.minimumRedeemPoints())
                .addChange("earningEnabled", currConfig.isEarningEnabled(), request.earningEnabled())
                .addChange("redemptionEnabled", currConfig.isRedemptionEnabled(), request.redemptionEnabled())
                .build();
        if (details.isEmpty()) {
            return currConfig;
        }
        currConfig.setActive(false);
        currConfig.setUpdatedBy(updatedBy);
        loyaltyConfigRepository.save(currConfig);

        LoyaltyConfig newConfig=LoyaltyConfig.builder()
                        .minimumPurchaseAmount(request.minimumPurchaseAmount())
                                .spendingAmount(request.spendingAmount())
                                        .earnedPoints(request.earnedPoints())
                                                .redeemPoints(request.redeemPoints())
                                                        .redeemValue(request.redeemValue())
                                                                .minimumRedeemPoints(request.minimumRedeemPoints())
                                                                        .earningEnabled(request.earningEnabled())
                                                                                .redemptionEnabled(request.redemptionEnabled())
                                                                                        .build();
        newConfig.setActive(true);
        newConfig.setCreatedBy(updatedBy);
        newConfig.setUpdatedBy(updatedBy);
        LoyaltyConfig newUpdated=loyaltyConfigRepository.save(newConfig);
        auditService.logSuccess(updatedBy, AuditAction.UPDATE, entity, newUpdated.getId(), "Updated loyalty configuration", details);
        return newUpdated;
    }

    private LoyaltyConfig findOrCreateConfig() {
        return loyaltyConfigRepository.findFirstByActiveTrueOrderByIdDesc()
                .orElseGet(this::createDefaultConfig);
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
        config.setCreatedBy(0L);
        LoyaltyConfig saved = loyaltyConfigRepository.save(config);
        auditService.logSuccess(0L, AuditAction.CREATE, entity, saved.getId(), "Created default loyalty configuration");
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