package com.vnpay.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.common.BusinessException;
import com.vnpay.platform.common.SnowflakeIdGenerator;
import com.vnpay.platform.entity.ChannelConfig;
import com.vnpay.platform.entity.Merchant;
import com.vnpay.platform.entity.PaymentTransaction;
import com.vnpay.platform.mapper.PaymentTransactionMapper;
import com.vnpay.platform.service.impl.PaymentServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentTransactionMapper transactionMapper;
    @Mock
    private MerchantService merchantService;
    @Mock
    private RoutingService routingService;
    @Mock
    private ChannelAdapter channelAdapter;
    @Mock
    private RiskControlService riskControlService;
    @Mock
    private CallbackService callbackService;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private SnowflakeIdGenerator idGenerator;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Merchant activeMerchant;
    private ChannelConfig channel;

    @BeforeAll
    static void initMybatisCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PaymentTransaction.class);
    }

    @BeforeEach
    void setUp() {
        activeMerchant = new Merchant();
        activeMerchant.setMerchantId("M001");
        activeMerchant.setMerchantName("Test Merchant");
        activeMerchant.setStatus("ACTIVE");
        activeMerchant.setFeeRate(new BigDecimal("0.015"));
        activeMerchant.setSingleLimitVnd(50000000L);
        activeMerchant.setDailyLimitVnd(500000000L);
        activeMerchant.setNotifyUrl("https://example.com/notify");

        channel = new ChannelConfig();
        channel.setChannelId("CH001");
        channel.setChannelName("Test Channel");
        channel.setWeight(100);
        channel.setSuccessRate(new BigDecimal("0.95"));
    }

    @Test
    void createPayment_missingRequiredFields_returnsBadRequest() {
        Map<String, Object> params = new HashMap<>();
        params.put("merchantId", "M001");
        // missing merchantOrderId and amount

        ApiResponse<Map<String, Object>> response = paymentService.createPayment(params);

        assertEquals(400, response.getCode());
        assertTrue(response.getMessage().contains("required"));
    }

    @Test
    void createPayment_negativeAmount_returnsBadRequest() {
        Map<String, Object> params = new HashMap<>();
        params.put("merchantId", "M001");
        params.put("merchantOrderId", "ORD001");
        params.put("amount", "-100");

        ApiResponse<Map<String, Object>> response = paymentService.createPayment(params);

        assertEquals(400, response.getCode());
    }

    @Test
    void createPayment_merchantNotFound_returnsNotFound() {
        Map<String, Object> params = new HashMap<>();
        params.put("merchantId", "INVALID");
        params.put("merchantOrderId", "ORD001");
        params.put("amount", "100000");

        when(merchantService.getByMerchantId("INVALID")).thenReturn(null);

        ApiResponse<Map<String, Object>> response = paymentService.createPayment(params);

        assertEquals(404, response.getCode());
    }

    @Test
    void createPayment_merchantNotActive_returnsForbidden() {
        Merchant suspended = new Merchant();
        suspended.setMerchantId("M002");
        suspended.setStatus("SUSPENDED");

        Map<String, Object> params = new HashMap<>();
        params.put("merchantId", "M002");
        params.put("merchantOrderId", "ORD001");
        params.put("amount", "100000");

        when(merchantService.getByMerchantId("M002")).thenReturn(suspended);

        ApiResponse<Map<String, Object>> response = paymentService.createPayment(params);

        assertEquals(403, response.getCode());
    }

    @Test
    void createPayment_exceedsSingleLimit_returnsError() {
        Map<String, Object> params = new HashMap<>();
        params.put("merchantId", "M001");
        params.put("merchantOrderId", "ORD001");
        params.put("amount", "99999999");

        when(merchantService.getByMerchantId("M001")).thenReturn(activeMerchant);

        ApiResponse<Map<String, Object>> response = paymentService.createPayment(params);

        assertEquals(400, response.getCode());
    }

    @Test
    void createPayment_riskControlReject_returnsForbidden() {
        Map<String, Object> params = new HashMap<>();
        params.put("merchantId", "M001");
        params.put("merchantOrderId", "ORD001");
        params.put("amount", "100000");
        params.put("payMethod", "bank_transfer");

        when(merchantService.getByMerchantId("M001")).thenReturn(activeMerchant);
        when(riskControlService.preTransactionCheck(eq(activeMerchant), any(), eq("bank_transfer"), any()))
                .thenReturn(false);

        ApiResponse<Map<String, Object>> response = paymentService.createPayment(params);

        assertEquals(403, response.getCode());
    }

    @Test
    void createPayment_success_returnsProcessing() {
        Map<String, Object> params = new HashMap<>();
        params.put("merchantId", "M001");
        params.put("merchantOrderId", "ORD001");
        params.put("amount", "100000");
        params.put("payMethod", "bank_transfer");

        when(merchantService.getByMerchantId("M001")).thenReturn(activeMerchant);
        when(riskControlService.preTransactionCheck(any(), any(), any(), any())).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(routingService.selectChannel(anyString(), any())).thenReturn(channel);
        when(idGenerator.nextStringId()).thenReturn("12345678");

        Map<String, Object> channelResult = new HashMap<>();
        channelResult.put("channelTransactionId", "CTX001");
        channelResult.put("payUrl", "https://pay.example.com");
        when(channelAdapter.submitPayment(any(), anyString(), any(), anyString(), any()))
                .thenReturn(channelResult);

        ApiResponse<Map<String, Object>> response = paymentService.createPayment(params);

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals("PROCESSING", response.getData().get("status"));
        verify(transactionMapper).insert(any(PaymentTransaction.class));
    }

    @Test
    void getPaymentStatus_notFound_returnsNotFound() {
        when(transactionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ApiResponse<PaymentTransaction> response = paymentService.getPaymentStatus("TX001", "M001");

        assertEquals(404, response.getCode());
    }

    @Test
    void getPaymentStatus_found_returnsTransaction() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setTransactionId("TX001");
        tx.setMerchantId("M001");
        tx.setStatus("PROCESSING");

        when(transactionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(tx);

        ApiResponse<PaymentTransaction> response = paymentService.getPaymentStatus("TX001", "M001");

        assertEquals(200, response.getCode());
        assertEquals("TX001", response.getData().getTransactionId());
    }

    @Test
    void refundPayment_transactionNotFound_returnsNotFound() {
        when(transactionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ApiResponse<Map<String, Object>> response = paymentService.refundPayment("TX001", "M001", "test");

        assertEquals(404, response.getCode());
    }

    @Test
    void refundPayment_invalidStatus_returnsBadRequest() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setTransactionId("TX001");
        tx.setStatus("PROCESSING");

        when(transactionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(tx);

        ApiResponse<Map<String, Object>> response = paymentService.refundPayment("TX001", "M001", "test");

        assertEquals(400, response.getCode());
    }

    @Test
    void refundPayment_success_initiatesRefund() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setTransactionId("TX001");
        tx.setMerchantId("M001");
        tx.setStatus("SUCCESS_CONFIRMED");
        tx.setAmount(new BigDecimal("100000"));

        when(transactionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(tx);

        ApiResponse<Map<String, Object>> response = paymentService.refundPayment("TX001", "M001", "test refund");

        assertEquals(200, response.getCode());
        assertEquals("PROCESSING", response.getData().get("refundStatus"));
        verify(ledgerService).recordRefund("M001", "TX001", new BigDecimal("100000"));
    }

    @Test
    void updateTransactionStatus_notFound_throwsException() {
        when(transactionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                paymentService.updateTransactionStatus("TX999", "SUCCESS_CONFIRMED", "CHANNEL"));
    }

    @Test
    void updateTransactionStatus_success_updatesAndCallsServices() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setTransactionId("TX001");
        tx.setMerchantId("M001");
        tx.setStatus("PROCESSING");
        tx.setAmount(new BigDecimal("100000"));
        tx.setFeeAmount(new BigDecimal("1500"));

        when(transactionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(tx);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        paymentService.updateTransactionStatus("TX001", "SUCCESS_CONFIRMED", "CHANNEL");

        verify(transactionMapper).update(isNull(), any());
        verify(ledgerService).recordPayment("M001", "TX001", new BigDecimal("100000"), new BigDecimal("1500"));
        verify(callbackService).sendCallback(any(PaymentTransaction.class));
        verify(riskControlService).postTransactionAnalysis(any(PaymentTransaction.class));
    }
}
