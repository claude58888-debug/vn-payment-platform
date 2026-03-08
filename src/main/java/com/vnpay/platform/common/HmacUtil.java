package com.vnpay.platform.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class HmacUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private HmacUtil() {
    }

    /**
     * Generate HMAC-SHA256 signature for the given data using the secret key.
     */
    public static String sign(String data, String secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC-SHA256 signature", e);
        }
    }

    /**
     * Verify HMAC-SHA256 signature.
     */
    public static boolean verify(String data, String secretKey, String signature) {
        String expectedSignature = sign(data, secretKey);
        return expectedSignature.equals(signature);
    }

    /**
     * Build sign string from sorted parameters map.
     * Format: key1=value1&key2=value2&...
     */
    public static String buildSignString(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        return sorted.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * Sign a parameter map with HMAC-SHA256.
     */
    public static String signParams(Map<String, String> params, String secretKey) {
        String signString = buildSignString(params);
        return sign(signString, secretKey);
    }

    /**
     * Verify signature for a parameter map.
     */
    public static boolean verifyParams(Map<String, String> params, String secretKey, String signature) {
        String signString = buildSignString(params);
        return verify(signString, secretKey, signature);
    }
}
