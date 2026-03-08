package com.vnpay.assistant;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Android NotificationListenerService that captures bank app deposit notifications.
 * This is the core of the Merchant Payment Assistant App (Path B - Auxiliary Confirmation).
 *
 * Required permissions in AndroidManifest.xml:
 * - android.permission.BIND_NOTIFICATION_LISTENER_SERVICE
 *
 * The user must manually enable notification access in Settings > Apps > Special access.
 */
public class NotificationListenerService extends android.service.notification.NotificationListenerService {

    private static final String TAG = "VnPayNotificationListener";

    // Vietnamese bank app package names to monitor
    private static final Set<String> MONITORED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.VCB",                          // Vietcombank
            "vn.com.techcombank.bb.app",       // Techcombank
            "com.mbmobile",                     // MB Bank
            "com.vnpay.bidv",                   // BIDV
            "com.vietinbank.ipay",              // VietinBank
            "com.ftl.mobilebank",               // ACB
            "com.sacombank.ewallet",            // Sacombank
            "vn.com.tpb.mb",                    // TPBank
            "com.vib.mytransaction",            // VIB
            "com.hdbank.mobilebanking",         // HDBank
            "com.VNPTePay.MoMo",               // MoMo
            "io.zalopay.user",                  // ZaloPay
            "vn.vnpay.ghichu"                   // VNPay
    ));

    private NotificationParser parser;
    private ApiUploader uploader;

    @Override
    public void onCreate() {
        super.onCreate();
        parser = new NotificationParser();
        uploader = new ApiUploader(this);
        Log.i(TAG, "NotificationListenerService created");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        // Only process notifications from monitored bank apps
        if (!MONITORED_PACKAGES.contains(packageName)) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) {
            return;
        }

        Bundle extras = notification.extras;
        if (extras == null) {
            return;
        }

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = textCs != null ? textCs.toString() : "";

        if (text.isEmpty()) {
            return;
        }

        Log.d(TAG, "Bank notification captured: package=" + packageName + ", title=" + title);

        // Parse the notification
        NotificationParser.ParsedNotification parsed = parser.parse(packageName, title, text);

        if (parsed != null && parsed.isDeposit()) {
            Log.i(TAG, "Deposit notification detected: " + parsed);

            // Upload to platform
            uploader.upload(packageName, title, text, parsed);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // No action needed when notifications are dismissed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "NotificationListenerService destroyed");
    }
}
