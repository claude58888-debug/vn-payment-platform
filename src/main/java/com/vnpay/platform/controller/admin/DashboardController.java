package com.vnpay.platform.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.vnpay.platform.common.SnowflakeIdGenerator;
import com.vnpay.platform.entity.*;
import com.vnpay.platform.mapper.*;
import com.vnpay.platform.service.DepositService;
import com.vnpay.platform.service.MerchantService;
import com.vnpay.platform.service.ReconciliationService;
import com.vnpay.platform.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final MerchantMapper merchantMapper;
    private final MerchantUsdtAccountMapper usdtAccountMapper;
    private final MerchantVndAccountMapper vndAccountMapper;
    private final PaymentTransactionMapper transactionMapper;
    private final ChannelConfigMapper channelConfigMapper;
    private final DeviceBindingMapper deviceBindingMapper;
    private final DeviceNotificationMapper notificationMapper;
    private final DepositOrderMapper depositOrderMapper;
    private final SettlementBatchMapper settlementBatchMapper;
    private final ReconciliationRecordMapper reconciliationRecordMapper;
    private final MerchantService merchantService;
    private final DepositService depositService;
    private final SettlementService settlementService;
    private final ReconciliationService reconciliationService;
    private final SnowflakeIdGenerator idGenerator;

    // ==================== Dashboard Home ====================

    @GetMapping
    public String dashboard(Model model) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        // Total transactions today
        Long totalToday = transactionMapper.selectCount(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .ge(PaymentTransaction::getCreatedAt, todayStart)
                        .le(PaymentTransaction::getCreatedAt, todayEnd));

        // Success count today
        Long successToday = transactionMapper.selectCount(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .ge(PaymentTransaction::getCreatedAt, todayStart)
                        .le(PaymentTransaction::getCreatedAt, todayEnd)
                        .in(PaymentTransaction::getStatus, "SUCCESS_CONFIRMED", "SUCCESS_PENDING_RECON"));

        // Success rate
        double successRate = totalToday > 0 ? (successToday * 100.0 / totalToday) : 0;

        // Total amount today
        List<PaymentTransaction> todayTxns = transactionMapper.selectList(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .ge(PaymentTransaction::getCreatedAt, todayStart)
                        .le(PaymentTransaction::getCreatedAt, todayEnd)
                        .in(PaymentTransaction::getStatus, "SUCCESS_CONFIRMED", "SUCCESS_PENDING_RECON"));
        BigDecimal totalAmount = todayTxns.stream()
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Active merchants
        Long activeMerchants = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getStatus, "ACTIVE"));

        // Pending orders
        Long pendingOrders = transactionMapper.selectCount(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .in(PaymentTransaction::getStatus, "CREATED", "PROCESSING"));

        model.addAttribute("totalToday", totalToday);
        model.addAttribute("successRate", String.format("%.1f", successRate));
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("activeMerchants", activeMerchants);
        model.addAttribute("pendingOrders", pendingOrders);

        // Hourly volume data for chart (last 24 hours)
        List<String> hourLabels = new ArrayList<>();
        List<Long> hourCounts = new ArrayList<>();
        for (int i = 23; i >= 0; i--) {
            LocalDateTime hourStart = LocalDateTime.now().minusHours(i).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime hourEnd = hourStart.plusHours(1);
            hourLabels.add(String.format("%02d:00", hourStart.getHour()));
            Long count = transactionMapper.selectCount(
                    new LambdaQueryWrapper<PaymentTransaction>()
                            .ge(PaymentTransaction::getCreatedAt, hourStart)
                            .lt(PaymentTransaction::getCreatedAt, hourEnd));
            hourCounts.add(count);
        }
        model.addAttribute("hourLabels", hourLabels);
        model.addAttribute("hourCounts", hourCounts);

        // Channel distribution
        List<ChannelConfig> channels = channelConfigMapper.selectList(null);
        List<String> channelNames = channels.stream().map(ChannelConfig::getChannelName).collect(Collectors.toList());
        List<Long> channelCounts = new ArrayList<>();
        for (ChannelConfig ch : channels) {
            Long count = transactionMapper.selectCount(
                    new LambdaQueryWrapper<PaymentTransaction>()
                            .eq(PaymentTransaction::getChannelId, ch.getChannelId())
                            .ge(PaymentTransaction::getCreatedAt, todayStart));
            channelCounts.add(count);
        }
        model.addAttribute("channelNames", channelNames);
        model.addAttribute("channelCounts", channelCounts);

        return "dashboard/index";
    }

    // ==================== Merchant Management ====================

    @GetMapping("/merchants")
    public String merchants(Model model,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Merchant> query = new LambdaQueryWrapper<>();
        if (search != null && !search.isEmpty()) {
            query.and(w -> w.like(Merchant::getMerchantName, search)
                    .or().like(Merchant::getMerchantId, search));
        }
        if (status != null && !status.isEmpty()) {
            query.eq(Merchant::getStatus, status);
        }
        query.orderByDesc(Merchant::getCreatedAt);
        List<Merchant> merchants = merchantMapper.selectList(query);
        model.addAttribute("merchants", merchants);
        model.addAttribute("search", search);
        model.addAttribute("statusFilter", status);
        return "dashboard/merchants";
    }

    @PostMapping("/merchants/create")
    public String createMerchant(@RequestParam String merchantName,
                                 @RequestParam(required = false) String contactName,
                                 @RequestParam(required = false) String contactPhone,
                                 @RequestParam(required = false) String contactEmail,
                                 @RequestParam(required = false) String notifyUrl,
                                 @RequestParam(required = false) String feeRate,
                                 @RequestParam(required = false) String settlementCycle) {
        Map<String, Object> params = new HashMap<>();
        params.put("merchantName", merchantName);
        params.put("contactName", contactName);
        params.put("contactPhone", contactPhone);
        params.put("contactEmail", contactEmail);
        params.put("notifyUrl", notifyUrl);
        if (feeRate != null && !feeRate.isEmpty()) {
            params.put("feeRate", feeRate);
        }
        if (settlementCycle != null && !settlementCycle.isEmpty()) {
            params.put("settlementCycle", settlementCycle);
        }
        merchantService.createMerchant(params);
        return "redirect:/dashboard/merchants";
    }

    @GetMapping("/merchants/{merchantId}")
    public String merchantDetail(@PathVariable String merchantId, Model model) {
        Merchant merchant = merchantService.getByMerchantId(merchantId);
        if (merchant == null) {
            return "redirect:/dashboard/merchants";
        }
        MerchantUsdtAccount usdtAccount = usdtAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantUsdtAccount>()
                        .eq(MerchantUsdtAccount::getMerchantId, merchantId));
        MerchantVndAccount vndAccount = vndAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantVndAccount>()
                        .eq(MerchantVndAccount::getMerchantId, merchantId));
        model.addAttribute("merchant", merchant);
        model.addAttribute("usdtAccount", usdtAccount);
        model.addAttribute("vndAccount", vndAccount);
        return "dashboard/merchant-detail";
    }

    @PostMapping("/merchants/{merchantId}/toggle")
    public String toggleMerchant(@PathVariable String merchantId) {
        Merchant merchant = merchantService.getByMerchantId(merchantId);
        if (merchant != null) {
            String newStatus = "ACTIVE".equals(merchant.getStatus()) ? "SUSPENDED" : "ACTIVE";
            merchantMapper.update(null,
                    new LambdaUpdateWrapper<Merchant>()
                            .eq(Merchant::getMerchantId, merchantId)
                            .set(Merchant::getStatus, newStatus));
        }
        return "redirect:/dashboard/merchants/" + merchantId;
    }

    // ==================== Transaction Management ====================

    @GetMapping("/transactions")
    public String transactions(Model model,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String merchantId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                               @RequestParam(required = false) String amountMin,
                               @RequestParam(required = false) String amountMax) {
        LambdaQueryWrapper<PaymentTransaction> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            query.eq(PaymentTransaction::getStatus, status);
        }
        if (merchantId != null && !merchantId.isEmpty()) {
            query.eq(PaymentTransaction::getMerchantId, merchantId);
        }
        if (startDate != null) {
            query.ge(PaymentTransaction::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            query.le(PaymentTransaction::getCreatedAt, endDate.atTime(LocalTime.MAX));
        }
        if (amountMin != null && !amountMin.isEmpty()) {
            query.ge(PaymentTransaction::getAmount, new BigDecimal(amountMin));
        }
        if (amountMax != null && !amountMax.isEmpty()) {
            query.le(PaymentTransaction::getAmount, new BigDecimal(amountMax));
        }
        query.orderByDesc(PaymentTransaction::getCreatedAt);
        query.last("LIMIT 500");
        List<PaymentTransaction> transactions = transactionMapper.selectList(query);
        List<Merchant> merchants = merchantMapper.selectList(null);
        model.addAttribute("transactions", transactions);
        model.addAttribute("merchants", merchants);
        model.addAttribute("statusFilter", status);
        model.addAttribute("merchantIdFilter", merchantId);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "dashboard/transactions";
    }

    @GetMapping("/transactions/{transactionId}")
    public String transactionDetail(@PathVariable String transactionId, Model model) {
        PaymentTransaction txn = transactionMapper.selectOne(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .eq(PaymentTransaction::getTransactionId, transactionId));
        if (txn == null) {
            return "redirect:/dashboard/transactions";
        }
        model.addAttribute("transaction", txn);
        return "dashboard/transaction-detail";
    }

    // ==================== Deposit Management ====================

    @GetMapping("/deposits")
    public String deposits(Model model,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) String merchantId) {
        LambdaQueryWrapper<DepositOrder> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            query.eq(DepositOrder::getStatus, status);
        }
        if (merchantId != null && !merchantId.isEmpty()) {
            query.eq(DepositOrder::getMerchantId, merchantId);
        }
        query.orderByDesc(DepositOrder::getCreatedAt);
        List<DepositOrder> deposits = depositOrderMapper.selectList(query);
        List<Merchant> merchants = merchantMapper.selectList(null);

        // Balance overview per merchant
        List<MerchantUsdtAccount> usdtAccounts = usdtAccountMapper.selectList(null);
        model.addAttribute("deposits", deposits);
        model.addAttribute("merchants", merchants);
        model.addAttribute("usdtAccounts", usdtAccounts);
        model.addAttribute("statusFilter", status);
        model.addAttribute("merchantIdFilter", merchantId);
        return "dashboard/deposits";
    }

    @PostMapping("/deposits/{depositOrderId}/confirm")
    public String confirmDeposit(@PathVariable String depositOrderId,
                                 @RequestParam(required = false) String txHash,
                                 @RequestParam(required = false) String actualAmount) {
        BigDecimal amount = (actualAmount != null && !actualAmount.isEmpty())
                ? new BigDecimal(actualAmount) : null;
        depositService.confirmDeposit(depositOrderId, txHash, amount);
        return "redirect:/dashboard/deposits";
    }

    @PostMapping("/deposits/{depositOrderId}/reject")
    public String rejectDeposit(@PathVariable String depositOrderId) {
        depositOrderMapper.update(null,
                new LambdaUpdateWrapper<DepositOrder>()
                        .eq(DepositOrder::getDepositOrderId, depositOrderId)
                        .set(DepositOrder::getStatus, "FAILED"));
        return "redirect:/dashboard/deposits";
    }

    // ==================== Device Management ====================

    @GetMapping("/devices")
    public String devices(Model model,
                          @RequestParam(required = false) String merchantId,
                          @RequestParam(required = false) String trustLevel) {
        LambdaQueryWrapper<DeviceBinding> query = new LambdaQueryWrapper<>();
        if (merchantId != null && !merchantId.isEmpty()) {
            query.eq(DeviceBinding::getMerchantId, merchantId);
        }
        if (trustLevel != null && !trustLevel.isEmpty()) {
            query.eq(DeviceBinding::getTrustLevel, trustLevel);
        }
        query.orderByDesc(DeviceBinding::getCreatedAt);
        List<DeviceBinding> devices = deviceBindingMapper.selectList(query);

        // Recent notifications
        List<DeviceNotification> notifications = notificationMapper.selectList(
                new LambdaQueryWrapper<DeviceNotification>()
                        .orderByDesc(DeviceNotification::getReceivedAt)
                        .last("LIMIT 50"));

        List<Merchant> merchants = merchantMapper.selectList(null);
        model.addAttribute("devices", devices);
        model.addAttribute("notifications", notifications);
        model.addAttribute("merchants", merchants);
        model.addAttribute("merchantIdFilter", merchantId);
        model.addAttribute("trustLevelFilter", trustLevel);
        return "dashboard/devices";
    }

    @PostMapping("/devices/{deviceId}/toggle")
    public String toggleDevice(@PathVariable String deviceId) {
        DeviceBinding device = deviceBindingMapper.selectOne(
                new LambdaQueryWrapper<DeviceBinding>()
                        .eq(DeviceBinding::getDeviceId, deviceId));
        if (device != null) {
            String newStatus = "ACTIVE".equals(device.getStatus()) ? "INACTIVE" : "ACTIVE";
            deviceBindingMapper.update(null,
                    new LambdaUpdateWrapper<DeviceBinding>()
                            .eq(DeviceBinding::getDeviceId, deviceId)
                            .set(DeviceBinding::getStatus, newStatus));
        }
        return "redirect:/dashboard/devices";
    }

    // ==================== Channel Management ====================

    @GetMapping("/channels")
    public String channels(Model model) {
        List<ChannelConfig> channels = channelConfigMapper.selectList(
                new LambdaQueryWrapper<ChannelConfig>().orderByDesc(ChannelConfig::getWeight));
        model.addAttribute("channels", channels);
        return "dashboard/channels";
    }

    @PostMapping("/channels/{channelId}/toggle")
    public String toggleChannel(@PathVariable String channelId) {
        ChannelConfig channel = channelConfigMapper.selectOne(
                new LambdaQueryWrapper<ChannelConfig>()
                        .eq(ChannelConfig::getChannelId, channelId));
        if (channel != null) {
            String newStatus = "ACTIVE".equals(channel.getStatus()) ? "INACTIVE" : "ACTIVE";
            channelConfigMapper.update(null,
                    new LambdaUpdateWrapper<ChannelConfig>()
                            .eq(ChannelConfig::getChannelId, channelId)
                            .set(ChannelConfig::getStatus, newStatus));
        }
        return "redirect:/dashboard/channels";
    }

    @PostMapping("/channels/{channelId}/update")
    public String updateChannel(@PathVariable String channelId,
                                @RequestParam(required = false) Integer weight,
                                @RequestParam(required = false) Integer circuitBreakerThreshold) {
        LambdaUpdateWrapper<ChannelConfig> update = new LambdaUpdateWrapper<ChannelConfig>()
                .eq(ChannelConfig::getChannelId, channelId);
        if (weight != null) {
            update.set(ChannelConfig::getWeight, weight);
        }
        if (circuitBreakerThreshold != null) {
            update.set(ChannelConfig::getCircuitBreakerThreshold, circuitBreakerThreshold);
        }
        channelConfigMapper.update(null, update);
        return "redirect:/dashboard/channels";
    }

    @PostMapping("/channels/{channelId}/reset-breaker")
    public String resetBreaker(@PathVariable String channelId) {
        channelConfigMapper.updateCircuitBreakerStatus(channelId, "CLOSED");
        return "redirect:/dashboard/channels";
    }

    // ==================== Settlement ====================

    @GetMapping("/settlement")
    public String settlement(Model model,
                             @RequestParam(required = false) String merchantId,
                             @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SettlementBatch> query = new LambdaQueryWrapper<>();
        if (merchantId != null && !merchantId.isEmpty()) {
            query.eq(SettlementBatch::getMerchantId, merchantId);
        }
        if (status != null && !status.isEmpty()) {
            query.eq(SettlementBatch::getStatus, status);
        }
        query.orderByDesc(SettlementBatch::getSettlementDate);
        List<SettlementBatch> batches = settlementBatchMapper.selectList(query);
        List<Merchant> merchants = merchantMapper.selectList(null);
        model.addAttribute("batches", batches);
        model.addAttribute("merchants", merchants);
        model.addAttribute("merchantIdFilter", merchantId);
        model.addAttribute("statusFilter", status);
        return "dashboard/settlement";
    }

    @PostMapping("/settlement/trigger")
    public String triggerSettlement(@RequestParam String merchantId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        settlementService.generateSettlement(merchantId, date);
        return "redirect:/dashboard/settlement";
    }

    @GetMapping("/settlement/{batchId}")
    public String settlementDetail(@PathVariable String batchId, Model model) {
        SettlementBatch batch = settlementBatchMapper.selectOne(
                new LambdaQueryWrapper<SettlementBatch>()
                        .eq(SettlementBatch::getBatchId, batchId));
        if (batch == null) {
            return "redirect:/dashboard/settlement";
        }
        model.addAttribute("batch", batch);
        return "dashboard/settlement-detail";
    }

    // ==================== Reconciliation ====================

    @GetMapping("/reconciliation")
    public String reconciliation(Model model,
                                 @RequestParam(required = false) String channelId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LambdaQueryWrapper<ReconciliationRecord> query = new LambdaQueryWrapper<>();
        if (channelId != null && !channelId.isEmpty()) {
            query.eq(ReconciliationRecord::getChannelId, channelId);
        }
        if (startDate != null) {
            query.ge(ReconciliationRecord::getReconDate, startDate);
        }
        if (endDate != null) {
            query.le(ReconciliationRecord::getReconDate, endDate);
        }
        query.orderByDesc(ReconciliationRecord::getReconDate);
        List<ReconciliationRecord> records = reconciliationRecordMapper.selectList(query);
        List<ChannelConfig> channels = channelConfigMapper.selectList(null);
        model.addAttribute("records", records);
        model.addAttribute("channels", channels);
        model.addAttribute("channelIdFilter", channelId);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "dashboard/reconciliation";
    }

    @PostMapping("/reconciliation/trigger")
    public String triggerReconciliation(@RequestParam String channelId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        reconciliationService.reconcile(channelId, date);
        return "redirect:/dashboard/reconciliation";
    }

    // ==================== Risk Control ====================

    @GetMapping("/risk")
    public String riskControl(Model model) {
        // Merchant risk equity overview
        List<MerchantUsdtAccount> usdtAccounts = usdtAccountMapper.selectList(null);
        List<Merchant> merchants = merchantMapper.selectList(null);
        Map<String, Merchant> merchantMap = merchants.stream()
                .collect(Collectors.toMap(Merchant::getMerchantId, m -> m, (a, b) -> a));

        // Suspicious devices
        List<DeviceBinding> suspiciousDevices = deviceBindingMapper.selectList(
                new LambdaQueryWrapper<DeviceBinding>()
                        .in(DeviceBinding::getTrustLevel, "SUSPICIOUS", "BLOCKED"));

        // HIGH risk merchants
        List<Merchant> highRiskMerchants = merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getRiskLevel, "HIGH"));

        model.addAttribute("usdtAccounts", usdtAccounts);
        model.addAttribute("merchantMap", merchantMap);
        model.addAttribute("suspiciousDevices", suspiciousDevices);
        model.addAttribute("highRiskMerchants", highRiskMerchants);
        return "dashboard/risk";
    }

    // ==================== Reports ====================

    @GetMapping("/reports")
    public String reports(Model model,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();

        List<Merchant> merchants = merchantMapper.selectList(null);
        List<ChannelConfig> channels = channelConfigMapper.selectList(null);

        // Transaction summary per merchant
        List<Map<String, Object>> merchantSummary = new ArrayList<>();
        for (Merchant m : merchants) {
            Long total = transactionMapper.selectCount(
                    new LambdaQueryWrapper<PaymentTransaction>()
                            .eq(PaymentTransaction::getMerchantId, m.getMerchantId())
                            .ge(PaymentTransaction::getCreatedAt, startDate.atStartOfDay())
                            .le(PaymentTransaction::getCreatedAt, endDate.atTime(LocalTime.MAX)));
            Long success = transactionMapper.selectCount(
                    new LambdaQueryWrapper<PaymentTransaction>()
                            .eq(PaymentTransaction::getMerchantId, m.getMerchantId())
                            .in(PaymentTransaction::getStatus, "SUCCESS_CONFIRMED", "SUCCESS_PENDING_RECON")
                            .ge(PaymentTransaction::getCreatedAt, startDate.atStartOfDay())
                            .le(PaymentTransaction::getCreatedAt, endDate.atTime(LocalTime.MAX)));
            Map<String, Object> summary = new HashMap<>();
            summary.put("merchantId", m.getMerchantId());
            summary.put("merchantName", m.getMerchantName());
            summary.put("totalCount", total);
            summary.put("successCount", success);
            summary.put("successRate", total > 0 ? String.format("%.1f", success * 100.0 / total) : "0.0");
            merchantSummary.add(summary);
        }

        // Channel summary
        List<Map<String, Object>> channelSummary = new ArrayList<>();
        for (ChannelConfig ch : channels) {
            Long total = transactionMapper.selectCount(
                    new LambdaQueryWrapper<PaymentTransaction>()
                            .eq(PaymentTransaction::getChannelId, ch.getChannelId())
                            .ge(PaymentTransaction::getCreatedAt, startDate.atStartOfDay())
                            .le(PaymentTransaction::getCreatedAt, endDate.atTime(LocalTime.MAX)));
            Map<String, Object> summary = new HashMap<>();
            summary.put("channelId", ch.getChannelId());
            summary.put("channelName", ch.getChannelName());
            summary.put("totalCount", total);
            channelSummary.add(summary);
        }

        model.addAttribute("merchantSummary", merchantSummary);
        model.addAttribute("channelSummary", channelSummary);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "dashboard/reports";
    }

    // ==================== Settings ====================

    @GetMapping("/settings")
    public String settings(Model model) {
        List<ChannelConfig> channels = channelConfigMapper.selectList(null);
        model.addAttribute("channels", channels);
        return "dashboard/settings";
    }
}
