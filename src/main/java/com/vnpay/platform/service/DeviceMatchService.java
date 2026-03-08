package com.vnpay.platform.service;

import com.vnpay.platform.entity.DeviceParsedTransaction;

import java.util.Map;

public interface DeviceMatchService {

    /**
     * Process a raw device notification: parse, match to transactions, update status.
     */
    void processNotification(Map<String, Object> notificationData);

    /**
     * Attempt to match a parsed device transaction to platform transactions.
     */
    void matchTransaction(DeviceParsedTransaction parsed);

    /**
     * Validate device signature for notification data.
     */
    boolean validateDeviceSignature(String deviceId, String data, String signature);
}
