package com.vnpay.platform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.vnpay.platform.entity.DeviceBinding;
import com.vnpay.platform.entity.Merchant;
import com.vnpay.platform.entity.MerchantUsdtAccount;
import com.vnpay.platform.entity.PaymentTransaction;
import com.vnpay.platform.mapper.DeviceBindingMapper;
import com.vnpay.platform.mapper.MerchantMapper;
import com.vnpay.platform.mapper.MerchantUsdtAccountMapper;
import com.vnpay.platform.service.impl.RiskControlServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskControlServiceTest {

    @Mock
    private MerchantMapper merchantMapper;
    @Mock
    private MerchantUsdtAccountMapper usdtAccountMapper;
    @Mock
    private DeviceBindingMapper deviceBindingMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RiskControlServiceImpl riskControlService;

    private Merchant activeMerchant;

    @BeforeAll
    static void initMybatisCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Merchant.class);
        TableInfoHelper.initTableInfo(assistant, DeviceBinding.class);
        TableInfoHelper.initTableInfo(assistant, MerchantUsdtAccount.class);
    }

    @BeforeEach
    void setUp() {
        activeMerchant = new Merchant();
        activeMerchant.setMerchantId("M001");
        activeMerchant.setStatus("ACTIVE");
        activeMerchant.setSingleLimitVnd(50000000L);
        activeMerchant.setDailyLimitVnd(500000000L);
        activeMerchant.setRiskLevel("NORMAL");
    }

    @Test
    void preTransactionCheck_merchantNotActive_returnsFalse() {
        Merchant suspended = new Merchant();
        suspended.setMerchantId("M002");
        suspended.setStatus("SUSPENDED");

        boolean result = riskControlService.preTransactionCheck(
                suspended, new BigDecimal("100000"), "bank_transfer", "1.2.3.4");

        assertFalse(result);
    }

    @Test
    void preTransactionCheck_exceedsSingleLimit_returnsFalse() {
        boolean result = riskControlService.preTransactionCheck(
                activeMerchant, new BigDecimal("99999999"), "bank_transfer", "1.2.3.4");

        assertFalse(result);
    }

    @Test
    void preTransactionCheck_exceedsDailyLimit_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(startsWith("risk:daily:amount:"))).thenReturn("499999999");

        boolean result = riskControlService.preTransactionCheck(
                activeMerchant, new BigDecimal("10000000"), "bank_transfer", null);

        assertFalse(result);
    }

    @Test
    void preTransactionCheck_tooManyFailures_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(startsWith("risk:daily:amount:"))).thenReturn("0");
        when(valueOperations.get(startsWith("risk:daily:fail:"))).thenReturn("10");

        boolean result = riskControlService.preTransactionCheck(
                activeMerchant, new BigDecimal("100000"), "bank_transfer", null);

        assertFalse(result);
    }

    @Test
    void preTransactionCheck_lowRiskEquity_returnsFalse() {
        MerchantUsdtAccount account = new MerchantUsdtAccount();
        account.setMerchantId("M001");
        account.setRiskEquityVnd(new BigDecimal("500000")); // below 1M threshold

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(startsWith("risk:daily:amount:"))).thenReturn("0");
        when(valueOperations.get(startsWith("risk:daily:fail:"))).thenReturn("0");
        when(usdtAccountMapper.selectOne(any())).thenReturn(account);

        boolean result = riskControlService.preTransactionCheck(
                activeMerchant, new BigDecimal("100000"), "bank_transfer", null);

        assertFalse(result);
    }

    @Test
    void preTransactionCheck_ipRateLimitExceeded_returnsFalse() {
        MerchantUsdtAccount account = new MerchantUsdtAccount();
        account.setRiskEquityVnd(new BigDecimal("5000000"));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(startsWith("risk:daily:amount:"))).thenReturn("0");
        when(valueOperations.get(startsWith("risk:daily:fail:"))).thenReturn("0");
        when(usdtAccountMapper.selectOne(any())).thenReturn(account);
        when(valueOperations.increment(startsWith("risk:ip:"))).thenReturn(31L);

        boolean result = riskControlService.preTransactionCheck(
                activeMerchant, new BigDecimal("100000"), "bank_transfer", "1.2.3.4");

        assertFalse(result);
    }

    @Test
    void preTransactionCheck_allChecksPass_returnsTrue() {
        MerchantUsdtAccount account = new MerchantUsdtAccount();
        account.setRiskEquityVnd(new BigDecimal("5000000"));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(startsWith("risk:daily:amount:"))).thenReturn("0");
        when(valueOperations.get(startsWith("risk:daily:fail:"))).thenReturn("0");
        when(usdtAccountMapper.selectOne(any())).thenReturn(account);
        when(valueOperations.increment(startsWith("risk:ip:"))).thenReturn(1L);

        boolean result = riskControlService.preTransactionCheck(
                activeMerchant, new BigDecimal("100000"), "bank_transfer", "1.2.3.4");

        assertTrue(result);
    }

    @Test
    void postTransactionAnalysis_failedTransaction_incrementsFailCounter() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setTransactionId("TX001");
        tx.setMerchantId("M001");
        tx.setStatus("FAILED");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        riskControlService.postTransactionAnalysis(tx);

        verify(valueOperations).increment(startsWith("risk:daily:fail:"));
    }

    @Test
    void postTransactionAnalysis_successTransaction_doesNotIncrementFailCounter() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setTransactionId("TX001");
        tx.setMerchantId("M001");
        tx.setStatus("SUCCESS_CONFIRMED");

        riskControlService.postTransactionAnalysis(tx);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void checkRiskEquity_accountNotFound_doesNothing() {
        when(usdtAccountMapper.selectOne(any())).thenReturn(null);

        riskControlService.checkRiskEquity("M001");

        verify(usdtAccountMapper, never()).updateById(any());
    }

    @Test
    void checkRiskEquity_lowEquity_elevatesRiskLevel() {
        MerchantUsdtAccount account = new MerchantUsdtAccount();
        account.setMerchantId("M001");
        account.setUsdtBalance(new BigDecimal("10"));
        account.setUsdtFrozen(new BigDecimal("8"));
        account.setMarkPriceUsdtVnd(new BigDecimal("25000"));
        account.setRiskEquityVnd(BigDecimal.ZERO);

        when(usdtAccountMapper.selectOne(any())).thenReturn(account);
        when(merchantMapper.selectOne(any())).thenReturn(activeMerchant);

        riskControlService.checkRiskEquity("M001");

        verify(usdtAccountMapper).updateById(account);
        verify(merchantMapper).update(isNull(), any());
    }

    @Test
    void assessDeviceTrust_deviceNotFound_doesNothing() {
        when(deviceBindingMapper.selectOne(any())).thenReturn(null);

        riskControlService.assessDeviceTrust("DEV001", true);

        verify(deviceBindingMapper, never()).update(any(), any());
    }

    @Test
    void assessDeviceTrust_matchedHighAccuracy_promotesToTrusted() {
        DeviceBinding device = new DeviceBinding();
        device.setDeviceId("DEV001");
        device.setMatchAccuracy(new BigDecimal("0.85"));
        device.setTrustLevel("NEW");

        when(deviceBindingMapper.selectOne(any())).thenReturn(device);

        riskControlService.assessDeviceTrust("DEV001", true);

        verify(deviceBindingMapper).update(isNull(), any());
    }

    @Test
    void assessDeviceTrust_unmatchedLowAccuracy_demotesToSuspicious() {
        DeviceBinding device = new DeviceBinding();
        device.setDeviceId("DEV001");
        device.setMatchAccuracy(new BigDecimal("0.25"));
        device.setTrustLevel("TRUSTED");

        when(deviceBindingMapper.selectOne(any())).thenReturn(device);

        riskControlService.assessDeviceTrust("DEV001", false);

        verify(deviceBindingMapper).update(isNull(), any());
    }

    @Test
    void amlCheck_nullAddress_returnsFalse() {
        boolean result = riskControlService.amlCheck(null, "TRC20");

        assertFalse(result);
    }

    @Test
    void amlCheck_emptyAddress_returnsFalse() {
        boolean result = riskControlService.amlCheck("", "TRC20");

        assertFalse(result);
    }

    @Test
    void amlCheck_blacklistedAddress_returnsFalse() {
        when(redisTemplate.hasKey(startsWith("aml:blacklist:"))).thenReturn(true);

        boolean result = riskControlService.amlCheck("TXyz123blacklisted", "TRC20");

        assertFalse(result);
    }

    @Test
    void amlCheck_validAddress_returnsTrue() {
        when(redisTemplate.hasKey(startsWith("aml:blacklist:"))).thenReturn(false);

        boolean result = riskControlService.amlCheck("TXyz123valid", "TRC20");

        assertTrue(result);
    }
}
