package com.localmarts.pos.loyalty.service;

import com.localmarts.pos.customer.CustomerRepository;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.entity.LoyaltyTransaction;
import com.localmarts.pos.loyalty.entity.TransactionType;
import com.localmarts.pos.loyalty.repository.LoyaltyConfigRepository;
import com.localmarts.pos.loyalty.repository.LoyaltyTransactionRepository;
import com.localmarts.pos.sale.entity.Sale;
import lombok.RequiredArgsConstructor;
import org.apache.poi.hpsf.Decimal;
import org.jspecify.annotations.Nullable;
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

    public long calculateEarnPoint(BigDecimal eligibleAmount){
        LoyaltyConfig config=loyaltyConfigService.getConfig();
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
    public long earnPoint(Customer customer,Sale sale,BigDecimal eligibleAmount){
        long earnPoint=calculateEarnPoint(eligibleAmount);
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
    public BigDecimal redeemPoint(Customer customer,Sale sale,long requestedPoint){
        LoyaltyConfig config=loyaltyConfigService.getConfig();
        if(!config.isRedemptionEnabled()){
            throw new IllegalArgumentException("Redemption is currently disabled");
        }
        if(requestedPoint<=0){
            return BigDecimal.ZERO;
        }
        BigDecimal discountAmount = getBigDecimal(customer, requestedPoint, config);
        long beforePoint=customer.getPointBalance();
        long afterPoint=beforePoint-requestedPoint;
        customer.setPointBalance(afterPoint);
        customerRepository.save(customer);

        LoyaltyTransaction transaction=LoyaltyTransaction.builder()
                .customer(customer)
                .sale(sale)
                .type(TransactionType.REDEEM)
                .points(-requestedPoint)
                .beforePoint(beforePoint)
                .afterPoint(afterPoint)
                .build();
        loyaltyTransactionRepository.save(transaction);
        return discountAmount;
    }

    private static BigDecimal getBigDecimal(Customer customer, long requestedPoint, LoyaltyConfig config) {
        if(requestedPoint < config.getMinimumRedeemPoints()){
            throw new IllegalArgumentException("Minimum redeem point is "+ config.getMinimumRedeemPoints());
        }
        long currentBalance= customer.getPointBalance();
        if(requestedPoint >currentBalance) {
            throw new IllegalArgumentException("Customer don't have point enough");
        }
        if(requestedPoint % config.getRedeemPoints()!=0){
            throw new IllegalArgumentException(
                    "Point must be redeem in groups of "+ config.getRedeemPoints()
            );
        }
        long redeemGroup= requestedPoint / config.getRedeemPoints();
        BigDecimal discountAmount= config.getRedeemValue().multiply(
                BigDecimal.valueOf(redeemGroup)
        );
        return discountAmount;
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


    public List<LoyaltyTransaction> filter(TransactionType type) {
        return loyaltyTransactionRepository.filter(type);
    }
}
