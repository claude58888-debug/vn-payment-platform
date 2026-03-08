package com.vnpay.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.common.SnowflakeIdGenerator;
import com.vnpay.platform.entity.DepositOrder;
import com.vnpay.platform.entity.MerchantUsdtAccount;
import com.vnpay.platform.mapper.DepositOrderMapper;
import com.vnpay.platform.mapper.MerchantUsdtAccountMapper;
import com.vnpay.platform.service.DepositService;
import com.vnpay.platform.service.LedgerService;
import com.vnpay.platform.service.RiskControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositServiceImpl implements DepositService {

    private final DepositOrderMapper depositOrderMapper;
    private final MerchantUsdtAccountMapper usdtAccountMapper;
    private final LedgerService ledgerService;
    private final RiskControlService riskControlService;
    private final SnowflakeIdGenerator idGenerator;

    @Override
    @Transactional
    public ApiResponse<DepositOrder> createDeposit(String merchantId, BigDecimal amountUsdt,
                                                    String network) {
        if (amountUsdt == null || amountUsdt.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.badRequest("Amount must be positive");
        }

        if (network == null) {
            network = "TRC20";
        }

        // Generate deposit order
        String depositOrderId = "D" + idGenerator.nextStringId();

        // Generate deposit address (simulated - in production, calls custody service)
        String depositAddress = generateDepositAddress(network);

        DepositOrder order = new DepositOrder();
        order.setDepositOrderId(depositOrderId);
        order.setMerchantId(merchantId);
        order.setAmountUsdt(amountUsdt);
        order.setNetwork(network);
        order.setDepositAddress(depositAddress);
        order.setStatus("PENDING");
        order.setExpiredAt(LocalDateTime.now().plusHours(2));

        depositOrderMapper.insert(order);

        log.info("Deposit order created: orderId={}, merchantId={}, amount={} USDT, network={}",
                depositOrderId, merchantId, amountUsdt, network);

        return ApiResponse.success(order);
    }

    @Override
    public ApiResponse<DepositOrder> getDepositStatus(String depositOrderId) {
        DepositOrder order = depositOrderMapper.selectOne(
                new LambdaQueryWrapper<DepositOrder>()
                        .eq(DepositOrder::getDepositOrderId, depositOrderId));

        if (order == null) {
            return ApiResponse.notFound("Deposit order not found");
        }

        return ApiResponse.success(order);
    }

    @Override
    public ApiResponse<MerchantUsdtAccount> getUsdtBalance(String merchantId) {
        MerchantUsdtAccount account = usdtAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantUsdtAccount>()
                        .eq(MerchantUsdtAccount::getMerchantId, merchantId));

        if (account == null) {
            return ApiResponse.notFound("USDT account not found for merchant: " + merchantId);
        }

        return ApiResponse.success(account);
    }

    @Override
    @Transactional
    public ApiResponse<Map<String, Object>> withdrawUsdt(String merchantId, BigDecimal amountUsdt,
                                                          String toAddress) {
        if (amountUsdt == null || amountUsdt.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.badRequest("Amount must be positive");
        }

        // AML check on withdrawal address
        if (!riskControlService.amlCheck(toAddress, "TRC20")) {
            return ApiResponse.error(403, "Withdrawal address failed AML check");
        }

        MerchantUsdtAccount account = usdtAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantUsdtAccount>()
                        .eq(MerchantUsdtAccount::getMerchantId, merchantId));

        if (account == null) {
            return ApiResponse.notFound("USDT account not found");
        }

        BigDecimal availableBalance = account.getUsdtBalance().subtract(account.getUsdtFrozen());
        if (availableBalance.compareTo(amountUsdt) < 0) {
            return ApiResponse.badRequest("Insufficient USDT balance. Available: " + availableBalance);
        }

        BigDecimal balanceBefore = account.getUsdtBalance();

        // Deduct balance
        int updated = usdtAccountMapper.updateBalance(merchantId, amountUsdt.negate());
        if (updated == 0) {
            return ApiResponse.error("Failed to deduct USDT balance");
        }

        BigDecimal balanceAfter = balanceBefore.subtract(amountUsdt);

        // Record ledger
        String withdrawOrderId = "W" + idGenerator.nextStringId();
        ledgerService.recordWithdraw(merchantId, withdrawOrderId, amountUsdt,
                balanceBefore, balanceAfter);

        // Update risk equity
        riskControlService.checkRiskEquity(merchantId);

        Map<String, Object> result = new HashMap<>();
        result.put("withdrawOrderId", withdrawOrderId);
        result.put("amount", amountUsdt);
        result.put("toAddress", toAddress);
        result.put("status", "PROCESSING");
        result.put("balanceAfter", balanceAfter);

        log.info("USDT withdrawal initiated: merchantId={}, amount={}, toAddress={}",
                merchantId, amountUsdt, toAddress);

        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public void confirmDeposit(String depositOrderId, String txHash, BigDecimal actualAmount) {
        DepositOrder order = depositOrderMapper.selectOne(
                new LambdaQueryWrapper<DepositOrder>()
                        .eq(DepositOrder::getDepositOrderId, depositOrderId));

        if (order == null || !"PENDING".equals(order.getStatus())) {
            log.warn("Cannot confirm deposit: orderId={}, status={}",
                    depositOrderId, order != null ? order.getStatus() : "null");
            return;
        }

        // Get current balance
        MerchantUsdtAccount account = usdtAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantUsdtAccount>()
                        .eq(MerchantUsdtAccount::getMerchantId, order.getMerchantId()));

        BigDecimal balanceBefore = account != null ? account.getUsdtBalance() : BigDecimal.ZERO;

        // Update deposit order
        order.setTxHash(txHash);
        order.setActualAmountUsdt(actualAmount);
        order.setStatus("CONFIRMED");
        order.setConfirmedAt(LocalDateTime.now());
        depositOrderMapper.updateById(order);

        // Add balance
        usdtAccountMapper.updateBalance(order.getMerchantId(), actualAmount);

        BigDecimal balanceAfter = balanceBefore.add(actualAmount);

        // Record ledger
        ledgerService.recordDeposit(order.getMerchantId(), depositOrderId, actualAmount,
                balanceBefore, balanceAfter);

        // Update risk equity
        riskControlService.checkRiskEquity(order.getMerchantId());

        log.info("Deposit confirmed: orderId={}, merchantId={}, amount={} USDT, txHash={}",
                depositOrderId, order.getMerchantId(), actualAmount, txHash);
    }

    private String generateDepositAddress(String network) {
        // Simulated address generation - in production, calls custody/exchange API
        String prefix = "TRC20".equals(network) ? "T" : "0x";
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 33);
    }
}
