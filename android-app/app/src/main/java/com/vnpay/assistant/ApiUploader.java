package com.vnpay.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Uploads parsed notification data to the platform API.
 * POST /api/device/notification with HMAC-SHA256 signature.
 */
public class ApiUploader {

    private static final String TAG = "ApiUploader";
    private static final String PREFS_NAME = "vnpay_assistant";
    private static final String KEY_API_BASE_URL = "api_base_url";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_MERCHANT_ID = "merchant_id";
    private static final String KEY_AUTH_KEY = "auth_key";

    private final Context context;
    private final ExecutorService executor;

    public ApiUploader(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Upload notification data to the platform asynchronously.
     */
    public void upload(String bankPackage, String title, String rawText,
                       NotificationParser.ParsedNotification parsed) {
        executor.execute(() -> {
            try {
                doUpload(bankPackage, title, rawText, parsed);
            } catch (Exception e) {
                Log.e(TAG, "Upload failed: " + e.getMessage(), e);
            }
        });
    }

    private void doUpload(String bankPackage, String title, String rawText,
                          NotificationParser.ParsedNotification parsed) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiBaseUrl = prefs.getString(KEY_API_BASE_URL, "http://localhost:8080");
        String deviceId = prefs.getString(KEY_DEVICE_ID, "");
        String merchantId = prefs.getString(KEY_MERCHANT_ID, "");
        String authKey = prefs.getString(KEY_AUTH_KEY, "");

        if (deviceId.isEmpty() || merchantId.isEmpty() || authKey.isEmpty()) {
            Log.w(TAG, "Device not configured. Please bind device first.");
            return;
        }

        // Build JSON payload
        JSONObject payload = new JSONObject();
        payload.put("deviceId", deviceId);
        payload.put("merchantId", merchantId);
        payload.put("bankPackage", bankPackage);
        payload.put("bankLabel", parsed.bankLabel);
        payload.put("rawTitle", title);
        payload.put("rawText", rawText);
        payload.put("amount", parsed.amount);
        payload.put("cardLast4", parsed.cardLast4);
        payload.put("direction", parsed.direction);
        payload.put("txTime", parsed.txTime);
        long timestamp = System.currentTimeMillis();
        payload.put("timestamp", timestamp);

        // Generate HMAC-SHA256 signature using the same timestamp
        String signData = rawText + "|" + timestamp;
        String signature = hmacSha256(signData, authKey);
        payload.put("signature", signature);

        // Send HTTP POST
        String urlStr = apiBaseUrl + "/api/device/notification";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Device-Id", deviceId);
        conn.setRequestProperty("X-Merchant-Id", merchantId);
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            Log.i(TAG, "Notification uploaded successfully: amount=" + parsed.amount);
        } else {
            Log.w(TAG, "Upload failed with status: " + responseCode);
        }

        conn.disconnect();
    }

    /**
     * Generate HMAC-SHA256 signature.
     */
    private String hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Configure the uploader with device binding information.
     * Called after successful device binding via the platform API.
     */
    public void configure(String apiBaseUrl, String deviceId, String merchantId, String authKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_API_BASE_URL, apiBaseUrl)
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_MERCHANT_ID, merchantId)
                .putString(KEY_AUTH_KEY, authKey)
                .apply();
        Log.i(TAG, "Uploader configured: deviceId=" + deviceId + ", merchantId=" + merchantId);
    }
}
