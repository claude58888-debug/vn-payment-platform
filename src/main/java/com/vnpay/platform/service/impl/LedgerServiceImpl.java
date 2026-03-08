package com.vnpay.platform.service.impl;

import com.vnpay.platform.common.SnowflakeIdGenerator;
import com.vnpay.platform.entity.DepositLedger;
import com.vnpay.platform.entity.TransactionLedger;
import com.vnpay.platform.mapper.DepositLedgerMapper;
import com.vnpay.platform.mapper.TransactionLedgerMapper;
import com.vnpay.platform.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final TransactionLedgerMapper ledgerMapper;
    private final DepositLedgerMapper depositLedgerMapper;
    private final SnowflakeIdGenerator idGenerator;

    @Override
    public void recordPayment(String merchantId, String transactionId,
                               BigDecimal amount, BigDecimal feeAmount) {
        // Record payment entry: debit merchant receivable, credit platform transit
        TransactionLedger ledger = new TransactionLedger();
        ledger.setLedgerId("L" + idGenerator.nextStringId());
        ledger.setMerchantId(merchantId);
        ledger.setTransactionId(transactionId);
        ledger.setLedgerType("PAYMENT");
        ledger.setDebitAccount("MERCHANT:" + merchantId + ":RECEIVABLE");
        ledger.setCreditAccount("PLATFORM:TRANSIT");
        ledger.setAmount(amount);
        ledger.setCurrency("VND");
        ledger.setRemark("Payment received");
        ledgerMapper.insert(ledger);

        // Record fee entry if applicable
        if (feeAmount != null && feeAmount.compareTo(BigDecimal.ZERO) > 0) {
            recordFee(merchantId, transactionId, feeAmount);
        }

        log.debug("Recorded payment ledger: merchant={}, tx={}, amount={}",
                merchantId, transactionId, amount);
    }

    @Override
    public void recordRefund(String merchantId, String transactionId, BigDecimal amount) {
        TransactionLedger ledger = new TransactionLedger();
        ledger.setLedgerId("L" + idGenerator.nextStringId());
        ledger.setMerchantId(merchantId);
        ledger.setTransactionId(transactionId);
        ledger.setLedgerType("REFUND");
        ledger.setDebitAccount("PLATFORM:TRANSIT");
        ledger.setCreditAccount("MERCHANT:" + merchantId + ":RECEIVABLE");
        ledger.setAmount(amount);
        ledger.setCurrency("VND");
        ledger.setRemark("Payment refunded");
        ledgerMapper.insert(ledger);
    }

    @Override
    public void recordFee(String merchantId, String transactionId, BigDecimal amount) {
        TransactionLedger ledger = new TransactionLedger();
        ledger.setLedgerId("L" + idGenerator.nextStringId());
        ledger.setMerchantId(merchantId);
        ledger.setTransactionId(transactionId);
        ledger.setLedgerType("FEE");
        ledger.setDebitAccount("MERCHANT:" + merchantId + ":RECEIVABLE");
        ledger.setCreditAccount("PLATFORM:FEE_INCOME");
        ledger.setAmount(amount);
        ledger.setCurrency("VND");
        ledger.setRemark("Transaction fee");
        ledgerMapper.insert(ledger);
    }

    @Override
    public void recordSettlement(String merchantId, String batchId, BigDecimal amount) {
        TransactionLedger ledger = new TransactionLedger();
        ledger.setLedgerId("L" + idGenerator.nextStringId());
        ledger.setMerchantId(merchantId);
        ledger.setTransactionId(batchId);
        ledger.setLedgerType("SETTLEMENT");
        ledger.setDebitAccount("PLATFORM:TRANSIT");
        ledger.setCreditAccount("MERCHANT:" + merchantId + ":SETTLED");
        ledger.setAmount(amount);
        ledger.setCurrency("VND");
        ledger.setRemark("Settlement payout");
        ledgerMapper.insert(ledger);
    }

    @Override
    public void recordDeposit(String merchantId, String depositOrderId,
                               BigDecimal amountUsdt, BigDecimal balanceBefore,
                               BigDecimal balanceAfter) {
        DepositLedger ledger = new DepositLedger();
        ledger.setMerchantId(merchantId);
        ledger.setDepositOrderId(depositOrderId);
        ledger.setLedgerType("DEPOSIT");
        ledger.setAmountUsdt(amountUsdt);
        ledger.setBalanceBefore(balanceBefore);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setRemark("USDT deposit confirmed");
        depositLedgerMapper.insert(ledger);
    }

    @Override
    public void recordWithdraw(String merchantId, String depositOrderId,
                                BigDecimal amountUsdt, BigDecimal balanceBefore,
                                BigDecimal balanceAfter) {
        DepositLedger ledger = new DepositLedger();
        ledger.setMerchantId(merchantId);
        ledger.setDepositOrderId(depositOrderId);
        ledger.setLedgerType("WITHDRAW");
        ledger.setAmountUsdt(amountUsdt);
        ledger.setBalanceBefore(balanceBefore);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setRemark("USDT withdrawal");
        depositLedgerMapper.insert(ledger);
    }
}
