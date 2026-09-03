package com.localmarts.pos.loyalty.service;

import com.localmarts.pos.customer.CustomerRepository;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.entity.LoyaltyTransaction;
import com.localmarts.pos.loyalty.entity.TransactionType;
import com.localmarts.pos.loyalty.repository.LoyaltyTransactionRepository;
import com.localmarts.pos.sale.entity.Sale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoyaltyService {
    private final CustomerRepository customerRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final LoyaltyConfigService loyaltyConfigService;

    public List<LoyaltyTransaction> findAll(){
        return loyaltyTransactionRepository.findAllByOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public List<LoyaltyTransaction> findRecentForCustomer(Long customerId) {
        if (customerId == null) {
            return List.of();
        }
        return loyaltyTransactionRepository
                .findTop20ByCustomerIdOrderByTimestampDesc(customerId);
    }

    public long calculateEarnPoint(BigDecimal eligibleAmount){
        return calculateEarnPoint(
                eligibleAmount,
                loyaltyConfigService.getConfig()
        );
    }

    public LoyaltyConfig getCurrentConfig() {
        return loyaltyConfigService.getConfig();
    }

    public long calculateEarnPoint(
            BigDecimal eligibleAmount,
            LoyaltyConfig config
    ) {
        if (config == null) {
            throw new IllegalArgumentException("Loyalty config is required");
        }
        if(!config.isEarningEnabled()){
            return 0L;
        }
        if(eligibleAmount==null||
                eligibleAmount.compareTo(BigDecimal.ZERO)<=0){
            return 0L;
        }
        if(eligibleAmount.compareTo(config.getMinimumPurchaseAmount())<0){
            return 0L;
        }
        BigDecimal spendingAmount=config.getSpendingAmount();
        if(spendingAmount==null || spendingAmount.compareTo(BigDecimal.ZERO)<=0){
            throw new IllegalStateException("Loyalty spending amount must be greater than zero.");
        }
        long group= eligibleAmount.divideToIntegralValue(spendingAmount).longValue();

        return group*config.getEarnedPoints();
    }

    @Transactional
    public LoyaltyRefundImpact reverseEarnedPointsForRefund(
            Customer customer,
            Sale sale,
            BigDecimal remainingEligibleAmount
    ) {
        if (customer == null || sale == null) {
            return LoyaltyRefundImpact.none();
        }

        long originalEarned = sale.getPointEarned() == null
                ? 0L
                : Math.max(0L, sale.getPointEarned().longValue());
        long beforePoint = customer.getPointBalance() == null
                ? 0L
                : customer.getPointBalance();

        if (originalEarned == 0L) {
            return new LoyaltyRefundImpact(
                    0L,
                    0L,
                    0L,
                    beforePoint,
                    beforePoint
            );
        }

        LoyaltyConfig historicalConfig = loyaltyConfigService.getSaleConfig(
                sale.getTimestamp()
        );
        long remainingAllowed = calculateEarnPoint(
                remainingEligibleAmount,
                historicalConfig
        );
        long pointsToReverse = Math.min(
                originalEarned,
                Math.max(0L, originalEarned - remainingAllowed)
        );

        if (pointsToReverse == 0L) {
            return new LoyaltyRefundImpact(
                    originalEarned,
                    remainingAllowed,
                    0L,
                    beforePoint,
                    beforePoint
            );
        }

        long afterPoint = beforePoint - pointsToReverse;
        customer.setPointBalance(afterPoint);
        customerRepository.save(customer);

        LoyaltyTransaction transaction = LoyaltyTransaction.builder()
                .customer(customer)
                .sale(sale)
                .type(TransactionType.REFUND_EARN_REVERSAL)
                .points(-pointsToReverse)
                .beforePoint(beforePoint)
                .afterPoint(afterPoint)
                .build();
        loyaltyTransactionRepository.save(transaction);

        return new LoyaltyRefundImpact(
                originalEarned,
                remainingAllowed,
                pointsToReverse,
                beforePoint,
                afterPoint
        );
    }

    @Transactional
    public long earnPoint(Customer customer,Sale sale,BigDecimal eligibleAmount){
        return earnPoint(
                customer,
                sale,
                eligibleAmount,
                loyaltyConfigService.getConfig()
        );
    }

    @Transactional
    public long earnPoint(
            Customer customer,
            Sale sale,
            BigDecimal eligibleAmount,
            LoyaltyConfig config
    ) {
        long earnPoint=calculateEarnPoint(eligibleAmount, config);
        if(earnPoint<=0){
            return 0L;
        }
        long beforePoint=customer.getPointBalance();
        long afterPoint=earnPoint+beforePoint;
        customer.setPointBalance(afterPoint);
        customerRepository.save(customer);
        LoyaltyTransaction transaction=LoyaltyTransaction.builder()
                .customer(customer)
                .sale(sale)
                .type(TransactionType.EARN)
                .points(earnPoint)
                .beforePoint(beforePoint)
                .afterPoint(afterPoint)
                .build();
        loyaltyTransactionRepository.save(transaction);
        return earnPoint;
    }

    @Transactional
    public LoyaltyRedemption redeemPoints(
            Customer customer,
            Sale sale,
            long requiredPoints
    ) {
        if (customer == null || customer.getId() == null) {
            throw new IllegalArgumentException(
                    "An active member is required to use loyalty discounts"
            );
        }
        if (sale == null) {
            throw new IllegalArgumentException("Sale is required for redemption");
        }
        if (requiredPoints <= 0) {
            throw new IllegalArgumentException(
                    "Required loyalty points must be greater than zero"
            );
        }

        int deducted = customerRepository.deductPointsIfAvailable(
                customer.getId(),
                requiredPoints
        );
        if (deducted != 1) {
            throw new IllegalArgumentException(
                    "Loyalty points are no longer sufficient. Refresh the member and try again"
            );
        }

        long afterPoint = customerRepository
                .findPointBalanceValueById(customer.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to reload the member point balance"
                ));
        long beforePoint = Math.addExact(afterPoint, requiredPoints);
        customer.setPointBalance(afterPoint);

        LoyaltyTransaction transaction=LoyaltyTransaction.builder()
                .customer(customer)
                .sale(sale)
                .type(TransactionType.REDEEM)
                .points(-requiredPoints)
                .beforePoint(beforePoint)
                .afterPoint(afterPoint)
                .build();
        loyaltyTransactionRepository.save(transaction);
        return new LoyaltyRedemption(
                requiredPoints,
                beforePoint,
                afterPoint
        );
    }

    private void applyPointMovement(Customer customer, Sale sale, long pointChange, TransactionType type){
        long beforePoint=customer.getPointBalance();
        long afterPoint=beforePoint+pointChange;
        if(afterPoint<0){
            throw new IllegalArgumentException("Customer don't have enough point");
        }
        customer.setPointBalance(afterPoint);
        customerRepository.save(customer);
        LoyaltyTransaction transaction=LoyaltyTransaction.builder()
                .customer(customer)
                .sale(sale)
                .type(type)
                .points(pointChange)
                .beforePoint(beforePoint)
                .afterPoint(afterPoint)
                .build();
        loyaltyTransactionRepository.save(transaction);
    }


    public List <LoyaltyTransaction> filter(TransactionType type) {
        return loyaltyTransactionRepository.filter(type);
    }

    public record LoyaltyRefundImpact(
            long originalEarnedPoints,
            long remainingAllowedPoints,
            long reversedPoints,
            long beforeBalance,
            long afterBalance
    ) {
        public static LoyaltyRefundImpact none() {
            return new LoyaltyRefundImpact(0L, 0L, 0L, 0L, 0L);
        }
    }

    public record LoyaltyRedemption(
            long redeemedPoints,
            long beforeBalance,
            long afterBalance
    ) {
    }
}
