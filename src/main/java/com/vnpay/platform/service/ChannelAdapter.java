package com.vnpay.platform.service;

import com.vnpay.platform.entity.ChannelConfig;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Adapter interface for upstream bank/PSP channel communication.
 */
public interface ChannelAdapter {

    /**
     * Submit a payment to the upstream channel.
     * Returns a map with at minimum: channelTransactionId, redirectUrl (if applicable).
     */
    Map<String, Object> submitPayment(ChannelConfig channel, String transactionId,
                                       BigDecimal amount, String payMethod,
                                       Map<String, Object> extraParams);

    /**
     * Query transaction status from upstream channel.
     * Returns a map with: status (SUCCESS/FAILED/PROCESSING), channelTransactionId, etc.
     */
    Map<String, Object> queryTransaction(ChannelConfig channel, String transactionId,
                                          String channelTransactionId);

    /**
     * Submit a refund request to the upstream channel.
     */
    Map<String, Object> submitRefund(ChannelConfig channel, String transactionId,
                                      BigDecimal amount, String reason);
}
