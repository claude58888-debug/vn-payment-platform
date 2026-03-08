package com.vnpay.platform.service.impl;

import com.vnpay.platform.entity.ChannelConfig;
import com.vnpay.platform.service.ChannelAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simulated channel adapter for upstream bank/PSP communication.
 * In production, each channel would have its own adapter implementation
 * communicating with real bank APIs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelAdapterImpl implements ChannelAdapter {

    @Override
    public Map<String, Object> submitPayment(ChannelConfig channel, String transactionId,
                                              BigDecimal amount, String payMethod,
                                              Map<String, Object> extraParams) {
        log.info("Submitting payment to channel: {} | txId={}, amount={}, method={}",
                channel.getChannelId(), transactionId, amount, payMethod);

        Map<String, Object> result = new HashMap<>();

        // Simulate channel response
        String channelTxId = channel.getChannelId() + "_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16);
        result.put("channelTransactionId", channelTxId);
        result.put("status", "PROCESSING");

        // Generate payment URL/QR for different methods
        switch (payMethod) {
            case "qrcode":
                result.put("qrCodeUrl", "https://pay." + channel.getChannelId().toLowerCase()
                        + ".vn/qr/" + channelTxId);
                result.put("qrCodeData", "00020101021238620010" + channelTxId);
                break;
            case "bank_transfer":
                result.put("bankAccount", "9704" + transactionId.substring(transactionId.length() - 8));
                result.put("bankName", channel.getChannelName());
                result.put("transferContent", "PAY " + transactionId);
                break;
            case "card":
            default:
                result.put("redirectUrl", "https://pay." + channel.getChannelId().toLowerCase()
                        + ".vn/card/" + channelTxId);
                break;
        }

        log.info("Channel {} response: channelTxId={}", channel.getChannelId(), channelTxId);
        return result;
    }

    @Override
    public Map<String, Object> queryTransaction(ChannelConfig channel, String transactionId,
                                                 String channelTransactionId) {
        log.info("Querying transaction from channel: {} | txId={}, channelTxId={}",
                channel.getChannelId(), transactionId, channelTransactionId);

        Map<String, Object> result = new HashMap<>();
        result.put("channelTransactionId", channelTransactionId);

        // Simulate query response - in production this would call real bank API
        // For simulation: randomly return SUCCESS/PROCESSING/FAILED
        // In real implementation, this calls the channel's query API
        result.put("status", "PROCESSING");
        result.put("channelMessage", "Transaction is being processed");
        result.put("queryTime", System.currentTimeMillis());

        return result;
    }

    @Override
    public Map<String, Object> submitRefund(ChannelConfig channel, String transactionId,
                                             BigDecimal amount, String reason) {
        log.info("Submitting refund to channel: {} | txId={}, amount={}, reason={}",
                channel.getChannelId(), transactionId, amount, reason);

        Map<String, Object> result = new HashMap<>();
        String refundTxId = "REF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        result.put("refundTransactionId", refundTxId);
        result.put("status", "PROCESSING");
        result.put("message", "Refund submitted to channel");

        return result;
    }
}
