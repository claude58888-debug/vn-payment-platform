package com.vnpay.assistant;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses bank app notification text to extract structured transaction data.
 * Supports major Vietnamese banks with specific regex patterns per bank.
 */
public class NotificationParser {

    private static final String TAG = "NotificationParser";

    // Bank-specific patterns
    private static final Map<String, Pattern[]> BANK_PATTERNS = new HashMap<>();

    static {
        // Vietcombank: "TK 1234 | GD: +1,000,000 VND luc 08/03/2026 10:30"
        BANK_PATTERNS.put("com.VCB", new Pattern[]{
                Pattern.compile("TK\\s*(\\d{4})", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\+?([\\d,\\.]+)\\s*VND", Pattern.CASE_INSENSITIVE),
                Pattern.compile("luc\\s*(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2})", Pattern.CASE_INSENSITIVE)
        });

        // Techcombank: "Tai khoan *1234 +1.000.000 VND"
        BANK_PATTERNS.put("vn.com.techcombank.bb.app", new Pattern[]{
                Pattern.compile("\\*(\\d{4})", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\+([\\d\\.]+)\\s*VND", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2})", Pattern.CASE_INSENSITIVE)
        });

        // MB Bank: "So TK: ***1234 so tien +1,000,000 VND"
        BANK_PATTERNS.put("com.mbmobile", new Pattern[]{
                Pattern.compile("\\*{2,}(\\d{4})", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\+([\\d,\\.]+)\\s*VND", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2})", Pattern.CASE_INSENSITIVE)
        });
    }

    // Generic fallback patterns
    private static final Pattern GENERIC_AMOUNT = Pattern.compile(
            "\\+?([\\d,\\.]+)\\s*(?:VND|đ|dong)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_CARD = Pattern.compile(
            "(?:TK|account|card|\\*{2,})(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_CREDIT = Pattern.compile(
            "(?:\\+|nhan|credit|received|deposit|GD\\s*CR|chuyen\\s*den|nhan\\s*tien)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_TIME = Pattern.compile(
            "(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2})", Pattern.CASE_INSENSITIVE);

    /**
     * Parse a bank notification into structured data.
     */
    public ParsedNotification parse(String packageName, String title, String text) {
        try {
            String fullText = title + " " + text;

            // Try bank-specific patterns first
            Pattern[] patterns = BANK_PATTERNS.get(packageName);
            if (patterns != null) {
                return parseWithPatterns(fullText, patterns, packageName);
            }

            // Fallback to generic patterns
            return parseGeneric(fullText, packageName);

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse notification: " + e.getMessage());
            return null;
        }
    }

    private ParsedNotification parseWithPatterns(String text, Pattern[] patterns, String packageName) {
        ParsedNotification result = new ParsedNotification();
        result.bankPackage = packageName;
        result.bankLabel = getBankLabel(packageName);

        // Pattern[0] = card last 4
        if (patterns.length > 0) {
            Matcher m = patterns[0].matcher(text);
            if (m.find()) {
                result.cardLast4 = m.group(1);
            }
        }

        // Pattern[1] = amount
        if (patterns.length > 1) {
            Matcher m = patterns[1].matcher(text);
            if (m.find()) {
                result.amount = parseAmount(m.group(1));
            }
        }

        // Pattern[2] = time
        if (patterns.length > 2) {
            Matcher m = patterns[2].matcher(text);
            if (m.find()) {
                result.txTime = m.group(1);
            }
        }

        // Determine direction
        result.direction = GENERIC_CREDIT.matcher(text).find() ? "CREDIT" : "DEBIT";

        return result;
    }

    private ParsedNotification parseGeneric(String text, String packageName) {
        ParsedNotification result = new ParsedNotification();
        result.bankPackage = packageName;
        result.bankLabel = getBankLabel(packageName);

        // Amount
        Matcher amountMatcher = GENERIC_AMOUNT.matcher(text);
        if (amountMatcher.find()) {
            result.amount = parseAmount(amountMatcher.group(1));
        }

        // Card last 4
        Matcher cardMatcher = GENERIC_CARD.matcher(text);
        if (cardMatcher.find()) {
            result.cardLast4 = cardMatcher.group(1);
        }

        // Time
        Matcher timeMatcher = GENERIC_TIME.matcher(text);
        if (timeMatcher.find()) {
            result.txTime = timeMatcher.group(1);
        } else {
            // Use current time
            result.txTime = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .format(new Date());
        }

        // Direction
        result.direction = GENERIC_CREDIT.matcher(text).find() ? "CREDIT" : "DEBIT";

        return result;
    }

    private long parseAmount(String amountStr) {
        // Remove thousand separators (comma or dot) and parse
        String cleaned = amountStr.replace(",", "").replace(".", "");
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String getBankLabel(String packageName) {
        switch (packageName) {
            case "com.VCB": return "Vietcombank";
            case "vn.com.techcombank.bb.app": return "Techcombank";
            case "com.mbmobile": return "MB Bank";
            case "com.vnpay.bidv": return "BIDV";
            case "com.vietinbank.ipay": return "VietinBank";
            case "com.ftl.mobilebank": return "ACB";
            case "com.sacombank.ewallet": return "Sacombank";
            case "vn.com.tpb.mb": return "TPBank";
            case "com.vib.mytransaction": return "VIB";
            case "com.hdbank.mobilebanking": return "HDBank";
            case "com.VNPTePay.MoMo": return "MoMo";
            case "io.zalopay.user": return "ZaloPay";
            default: return "Unknown";
        }
    }

    /**
     * Parsed notification data structure.
     */
    public static class ParsedNotification {
        public String bankPackage;
        public String bankLabel;
        public long amount;
        public String cardLast4;
        public String direction;  // CREDIT or DEBIT
        public String txTime;

        public boolean isDeposit() {
            return "CREDIT".equals(direction) && amount > 0;
        }

        @Override
        public String toString() {
            return "ParsedNotification{" +
                    "bank=" + bankLabel +
                    ", amount=" + amount +
                    ", card=" + cardLast4 +
                    ", direction=" + direction +
                    ", time=" + txTime +
                    '}';
        }
    }
}
