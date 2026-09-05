package com.localmarts.pos.loyalty.service;

import com.localmarts.pos.customer.CustomerRepository;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.entity.LoyaltyTransaction;
import com.localmarts.pos.loyalty.entity.TransactionType;
import com.localmarts.pos.loyalty.repository.LoyaltyTransactionRepository;
import com.localmarts.pos.sale.entity.Sale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private LoyaltyTransactionRepository loyaltyTransactionRepository;

    @Mock
    private LoyaltyConfigService loyaltyConfigService;

    private LoyaltyService loyaltyService;

    @BeforeEach
    void setUp() {
        loyaltyService = new LoyaltyService(
                customerRepository,
                loyaltyTransactionRepository,
                loyaltyConfigService
        );
    }

    @Test
    void fullRefundReversesAllOriginallyEarnedPoints() {
        Sale sale = sale(20);
        Customer customer = customer(40);
        LoyaltyConfig historicalConfig = config("100.00", "10.00", 1L);
        mockPointMovement(customer, -20L, 20L);
        when(loyaltyConfigService.getSaleConfig(sale.getTimestamp()))
                .thenReturn(historicalConfig);

        LoyaltyService.LoyaltyRefundImpact impact =
                loyaltyService.reverseEarnedPointsForRefund(
                        customer,
                        sale,
                        BigDecimal.ZERO
                );

        assertThat(impact.originalEarnedPoints()).isEqualTo(20);
        assertThat(impact.remainingAllowedPoints()).isZero();
        assertThat(impact.reversedPoints()).isEqualTo(20);
        assertThat(customer.getPointBalance()).isEqualTo(20);
        assertReversalTransaction(customer, sale, -20L, 40L, 20L);
    }

    @Test
    void partialRefundAboveMinimumReversesOnlyExcessPoints() {
        Sale sale = sale(20);
        Customer customer = customer(25);
        mockPointMovement(customer, -5L, 20L);
        when(loyaltyConfigService.getSaleConfig(sale.getTimestamp()))
                .thenReturn(config("100.00", "10.00", 1L));

        LoyaltyService.LoyaltyRefundImpact impact =
                loyaltyService.reverseEarnedPointsForRefund(
                        customer,
                        sale,
                        new BigDecimal("150.00")
                );

        assertThat(impact.remainingAllowedPoints()).isEqualTo(15);
        assertThat(impact.reversedPoints()).isEqualTo(5);
        assertThat(customer.getPointBalance()).isEqualTo(20);
        assertReversalTransaction(customer, sale, -5L, 25L, 20L);
    }

    @Test
    void partialRefundBelowHistoricalMinimumReversesAllEarnedPoints() {
        Sale sale = sale(11);
        Customer customer = customer(30);
        mockPointMovement(customer, -11L, 19L);
        when(loyaltyConfigService.getSaleConfig(sale.getTimestamp()))
                .thenReturn(config("100.00", "10.00", 1L));

        LoyaltyService.LoyaltyRefundImpact impact =
                loyaltyService.reverseEarnedPointsForRefund(
                        customer,
                        sale,
                        new BigDecimal("93.00")
                );

        assertThat(impact.remainingAllowedPoints()).isZero();
        assertThat(impact.reversedPoints()).isEqualTo(11);
        assertThat(customer.getPointBalance()).isEqualTo(19);
    }

    @Test
    void refundUsesHistoricalConfigInsteadOfCurrentActiveConfig() {
        Sale sale = sale(20);
        Customer customer = customer(20);
        mockPointMovement(customer, -5L, 15L);
        LoyaltyConfig historicalConfig = config("100.00", "10.00", 1L);
        when(loyaltyConfigService.getSaleConfig(sale.getTimestamp()))
                .thenReturn(historicalConfig);

        LoyaltyService.LoyaltyRefundImpact impact =
                loyaltyService.reverseEarnedPointsForRefund(
                        customer,
                        sale,
                        new BigDecimal("150.00")
                );

        assertThat(impact.remainingAllowedPoints()).isEqualTo(15);
        assertThat(impact.reversedPoints()).isEqualTo(5);
        verify(loyaltyConfigService).getSaleConfig(sale.getTimestamp());
        verify(loyaltyConfigService, never()).getConfig();
    }

    @Test
    void reversalAllowsCustomerBalanceToBecomeNegative() {
        Sale sale = sale(11);
        Customer customer = customer(3);
        mockPointMovement(customer, -8L, -5L);
        when(loyaltyConfigService.getSaleConfig(sale.getTimestamp()))
                .thenReturn(config("0.00", "10.00", 1L));

        LoyaltyService.LoyaltyRefundImpact impact =
                loyaltyService.reverseEarnedPointsForRefund(
                        customer,
                        sale,
                        new BigDecimal("30.00")
                );

        assertThat(impact.remainingAllowedPoints()).isEqualTo(3);
        assertThat(impact.reversedPoints()).isEqualTo(8);
        assertThat(impact.beforeBalance()).isEqualTo(3);
        assertThat(impact.afterBalance()).isEqualTo(-5);
        assertThat(customer.getPointBalance()).isEqualTo(-5);
        assertReversalTransaction(customer, sale, -8L, 3L, -5L);
    }

    @Test
    void checkoutCalculationStillLoadsCurrentActiveConfig() {
        LoyaltyConfig currentConfig = config("100.00", "10.00", 2L);
        when(loyaltyConfigService.getConfig()).thenReturn(currentConfig);

        long points = loyaltyService.calculateEarnPoint(
                new BigDecimal("150.00")
        );

        assertThat(points).isEqualTo(30);
        verify(loyaltyConfigService).getConfig();
    }

    @Test
    void recentCustomerHistoryUsesLimitedNewestFirstRepositoryQuery() {
        LoyaltyTransaction newest = LoyaltyTransaction.builder().id(2L).build();
        LoyaltyTransaction older = LoyaltyTransaction.builder().id(1L).build();
        when(loyaltyTransactionRepository
                .findTop20ByCustomerIdOrderByTimestampDesc(7L))
                .thenReturn(List.of(newest, older));

        List<LoyaltyTransaction> result =
                loyaltyService.findRecentForCustomer(7L);

        assertThat(result).containsExactly(newest, older);
        verify(loyaltyTransactionRepository)
                .findTop20ByCustomerIdOrderByTimestampDesc(7L);
    }

    @Test
    void redemptionUsesConditionalDeductionAndCreatesRedeemTransaction() {
        Sale sale = Sale.builder().id(8L).build();
        Customer customer = customer(120L);
        when(customerRepository.deductPointsIfAvailable(customer.getId(), 68L))
                .thenReturn(1);
        when(customerRepository.findPointBalanceValueById(customer.getId()))
                .thenReturn(java.util.Optional.of(52L));

        LoyaltyService.LoyaltyRedemption redemption =
                loyaltyService.redeemPoints(customer, sale, 68L);

        assertThat(redemption.redeemedPoints()).isEqualTo(68L);
        assertThat(redemption.beforeBalance()).isEqualTo(120L);
        assertThat(redemption.afterBalance()).isEqualTo(52L);
        assertThat(customer.getPointBalance()).isEqualTo(52L);

        ArgumentCaptor<LoyaltyTransaction> captor =
                ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(loyaltyTransactionRepository).save(captor.capture());
        LoyaltyTransaction transaction = captor.getValue();
        assertThat(transaction.getType()).isEqualTo(TransactionType.REDEEM);
        assertThat(transaction.getPoints()).isEqualTo(-68L);
        assertThat(transaction.getBeforePoint()).isEqualTo(120L);
        assertThat(transaction.getAfterPoint()).isEqualTo(52L);
        assertThat(transaction.getSale()).isSameAs(sale);
    }

    @Test
    void redemptionRejectsConcurrentOrInsufficientBalance() {
        Sale sale = Sale.builder().id(8L).build();
        Customer customer = customer(120L);
        when(customerRepository.deductPointsIfAvailable(customer.getId(), 68L))
                .thenReturn(0);

        assertThatThrownBy(() -> loyaltyService.redeemPoints(
                customer,
                sale,
                68L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer sufficient");

        verify(loyaltyTransactionRepository, never()).save(
                org.mockito.ArgumentMatchers.any(LoyaltyTransaction.class)
        );
    }

    @Test
    void refundRestorationAtomicallyAddsPointsAndRecordsContinuousBalance() {
        Sale sale = sale(0);
        Customer customer = customer(-10L);
        mockPointMovement(customer, 25L, 15L);

        LoyaltyService.LoyaltyRestoreImpact impact =
                loyaltyService.restoreRedeemedPointsForRefund(
                        customer,
                        sale,
                        25L
                );

        assertThat(impact.restoredPoints()).isEqualTo(25L);
        assertThat(impact.beforeBalance()).isEqualTo(-10L);
        assertThat(impact.afterBalance()).isEqualTo(15L);
        assertThat(customer.getPointBalance()).isEqualTo(15L);

        ArgumentCaptor<LoyaltyTransaction> captor =
                ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(loyaltyTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType())
                .isEqualTo(TransactionType.REFUND_REDEEM_RESTORE);
        assertThat(captor.getValue().getPoints()).isEqualTo(25L);
        assertThat(captor.getValue().getBeforePoint()).isEqualTo(-10L);
        assertThat(captor.getValue().getAfterPoint()).isEqualTo(15L);
    }

    @Test
    void zeroPointRestorationIsANoOp() {
        Sale sale = sale(0);
        Customer customer = customer(7L);

        LoyaltyService.LoyaltyRestoreImpact impact =
                loyaltyService.restoreRedeemedPointsForRefund(
                        customer,
                        sale,
                        0L
                );

        assertThat(impact.restoredPoints()).isZero();
        assertThat(impact.beforeBalance()).isEqualTo(7L);
        assertThat(impact.afterBalance()).isEqualTo(7L);
        verify(customerRepository, never()).applyPointMovement(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    private void assertReversalTransaction(
            Customer customer,
            Sale sale,
            long points,
            long before,
            long after
    ) {
        ArgumentCaptor<LoyaltyTransaction> captor =
                ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(loyaltyTransactionRepository).save(captor.capture());
        LoyaltyTransaction transaction = captor.getValue();
        assertThat(transaction.getCustomer()).isSameAs(customer);
        assertThat(transaction.getSale()).isSameAs(sale);
        assertThat(transaction.getType())
                .isEqualTo(TransactionType.REFUND_EARN_REVERSAL);
        assertThat(transaction.getPoints()).isEqualTo(points);
        assertThat(transaction.getBeforePoint()).isEqualTo(before);
        assertThat(transaction.getAfterPoint()).isEqualTo(after);
    }

    private Sale sale(int pointEarned) {
        return Sale.builder()
                .id(1L)
                .timestamp(LocalDateTime.of(2026, 8, 1, 10, 0))
                .pointEarned(pointEarned)
                .build();
    }

    private Customer customer(long pointBalance) {
        return Customer.builder()
                .id(2L)
                .pointBalance(pointBalance)
                .build();
    }

    private void mockPointMovement(
            Customer customer,
            long change,
            long resultingBalance
    ) {
        when(customerRepository.applyPointMovement(customer.getId(), change))
                .thenReturn(1);
        when(customerRepository.findPointBalanceValueById(customer.getId()))
                .thenReturn(java.util.Optional.of(resultingBalance));
    }

    private LoyaltyConfig config(
            String minimumPurchase,
            String spendingAmount,
            long earnedPoints
    ) {
        return LoyaltyConfig.builder()
                .minimumPurchaseAmount(new BigDecimal(minimumPurchase))
                .spendingAmount(new BigDecimal(spendingAmount))
                .earnedPoints(earnedPoints)
                .earningEnabled(true)
                .build();
    }
}
