# VnPay Merchant Payment Assistant App

Android notification listener app that captures bank deposit notifications and uploads
structured data to the VnPay 4th-party payment platform.

## Features
- Listens to notifications from major Vietnamese bank apps
- Parses deposit amounts, card numbers, and timestamps
- Uploads structured data with HMAC-SHA256 signature
- Supports: Vietcombank, Techcombank, MB Bank, BIDV, VietinBank, ACB, Sacombank, TPBank, VIB, HDBank, MoMo, ZaloPay

## Setup
1. Build with Android Studio (minSdk 26, targetSdk 34)
2. Install on merchant's phone
3. Enable notification access: Settings > Apps > Special access > Notification access
4. Configure device binding via the platform API (POST /api/device/bind)
5. The app will automatically capture and upload bank notifications

## Architecture
- `NotificationListenerService`: Captures bank app notifications via Android NotificationListenerService
- `NotificationParser`: Extracts amount, card last4, time using bank-specific regex patterns
- `ApiUploader`: Sends structured JSON with HMAC signature to POST /api/device/notification
