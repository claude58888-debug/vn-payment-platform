# Devin Build Prompt: Vietnam 4th-Party Payment Platform

## Project Overview
Build a complete Vietnam 4th-party payment aggregation platform with:
- **Backend**: Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis 7 + RabbitMQ 3
- **Android App**: Notification listener helper app for merchant payment confirmation
- **Deployment**: Docker Compose

## Business Context
This is a 4th-party payment aggregation platform for the Vietnamese market. It aggregates multiple upstream banks/PSP channels, serves downstream merchants. Merchants deposit USDT (offshore custody), actual collection settles in VND through local banks. Since some upstream banks don't provide real-time callbacks, the system uses a "Merchant Payment Assistant App" to listen to bank deposit notifications as an auxiliary early-confirmation data source.

## System Roles
1. **4th-party platform (us)**: Aggregates upstream bank/payment channels, handles routing, accounting, settlement, callbacks, risk control
2. **Merchants**: Use our API/dashboard to collect payments, deposit USDT, settle in VND
3. **Upstream channels**: Banks, NAPAS, other PSPs - some have query APIs, some only have reconciliation files, no webhooks
4. **Merchant Assistant App (Android)**: Installed on merchant phones, listens to bank/wallet app deposit notifications, uploads structured data to platform

## Three Confirmation Paths
- **Path A (Primary)**: Upstream bank/channel query API + intraday/EOD reconciliation files -> platform internal final status
- **Path B (Auxiliary)**: Merchant phone assistant app listens to bank app notifications -> reports to platform -> early confirmation
- **Path C (External)**: Platform -> merchant system HTTP callback + reconciliation reports

## Transaction State Machine
CREATED -> PROCESSING -> SUCCESS_PENDING_RECON / SUCCESS_CONFIRMED / FAILED / UNKNOWN

## Tech Stack & Project Structure
```
vn-payment-platform/
├── pom.xml
├── docker-compose.yml
├── sql/
│   └── init.sql
├── src/main/java/com/vnpay/platform/
│   ├── VnPaymentApplication.java
│   ├── config/ (RedisConfig, RabbitMQConfig, WebSecurityConfig)
│   ├── common/ (ApiResponse, HmacUtil, SnowflakeIdGenerator)
│   ├── entity/ (Merchant, MerchantUsdtAccount, PaymentTransaction, DeviceBinding, DeviceNotification, DeviceParsedTransaction, SettlementBatch, TransactionLedger)
│   ├── enums/ (TransactionStatus, PayMethod, MatchSource)
│   ├── mapper/ (MyBatis-Plus mappers for all entities)
│   ├── service/ (MerchantService, DepositService, PaymentService, RoutingService, ChannelAdapter, DeviceMatchService, ReconciliationService, CallbackService, SettlementService, RiskControlService, LedgerService)
│   ├── controller/ (MerchantController, PaymentController, DeviceController, InternalReportController)
│   └── scheduler/ (QueryTaskScheduler, ReconciliationScheduler, SettlementScheduler)
├── src/main/resources/
│   ├── application.yml
│   └── mapper/ (MyBatis XML mappers)
└── android-app/
    └── app/src/main/java/com/vnpay/assistant/
        ├── NotificationListenerService.java
        ├── NotificationParser.java
        └── ApiUploader.java
```

## Architecture Layers
1. **Access Layer**: Merchant Web Dashboard, Merchant API Gateway (REST/JSON, HMAC signing), Payment Assistant App (Android), Internal Operations Dashboard
2. **Business Service Layer**: Merchant management, USDT deposit & asset service, Payment gateway & routing, Bank card/payment transaction service, Settlement & accounting, Device notification processing, Risk control & AML
3. **Channel Adapter Layer**: Bank/NAPAS/PSP adapters, USDT custody/exchange adapters
4. **Infrastructure Layer**: API Gateway/Auth (JWT/OAuth2), Message Queue (RabbitMQ), Config Center, Logging/Monitoring/Alerting

## Key Implementation Details

### USDT Deposit System
- Two accounts per merchant: VND settlement account + USDT deposit account
- USDT deposit flow: Merchant requests -> generate deposit address -> monitor chain/custody callback -> update balance -> update risk equity
- Formula: risk_equity_vnd = (usdt_balance - usdt_frozen) x mark_price_usdt_vnd
- Risk linkage: Platform assigns merchant limits based on risk_equity_vnd

### Payment Gateway & Routing
- API: POST /api/payment/create
- Routing model: "Payment Orchestrator" - merchants only connect to us, we route to different acquirers/PSPs
- Routing optimization: success rate, cost reduction, stability (auto circuit-breaker)
- Config: channel weights, health thresholds, circuit breaker rules, BIN mapping rules

### Bank Polling & Reconciliation
- Polling: Scheduled task scans PROCESSING orders, calls ChannelAdapter.query()
- Reconciliation files: Daily download from banks (SFTP/Email/Portal), parse and match
- Anomaly handling: phone notification vs bank records cross-validation

### Android Assistant App
- Uses NotificationListenerService to capture bank app notifications
- Parses amount, card last 4, time using regex per bank
- Uploads JSON with HMAC signature to POST /api/device/notification
- Device trust management: new devices are auxiliary only, proven devices can trigger early confirmation

### Merchant Callback
- Two-stage: optional early callback on SUCCESS_PENDING_RECON, final callback on SUCCESS_CONFIRMED/FAILED
- Retry: exponential backoff (0s, 30s, 2min, 10min, 1h, max N times)
- HMAC-SHA256 signature

### Settlement
- Daily EOD: calculate settleable amount = all SUCCESS_CONFIRMED - fees - risk reserve
- Risk check -> generate settlement batch -> call bank/NAPAS for disbursement

### Risk Control
- Deposit-linked: low risk_equity_vnd -> reduce limits or stop
- Device: mismatch between phone notification and bank records -> flag device
- Transaction: same card multiple failures -> block, cross-region IP -> force 3DS
- AML: track deposit source addresses, flag mixers/sanctioned exchanges

## API Endpoints

### Merchant Management
- POST /api/merchant/create
- GET /api/merchant/info

### USDT Deposit
- POST /api/merchant/deposit/usdt/create
- GET /api/merchant/deposit/usdt/status
- GET /api/deposit/usdt/balance
- POST /api/merchant/deposit/usdt/withdraw

### Payment
- POST /api/payment/create
- GET /api/payment/status
- POST /api/payment/refund

### Settlement
- GET /api/settlement/summary
- GET /api/payment/statement

### Device
- POST /api/device/bind
- POST /api/device/unbind
- POST /api/device/notification

### Internal Reports
- GET /internal/report/transactions
- GET /internal/report/channels
- GET /internal/report/reconciliation

## Database Tables (MySQL)
See sql/init.sql for complete schema. Key tables:
- merchant, merchant_usdt_account
- payment_transaction, transaction_ledger
- device_binding, device_notification, device_parsed_transaction
- settlement_batch, channel_config
- deposit_order, deposit_ledger

## Build Instructions for Devin

1. Clone this repo: `git clone https://github.com/claude58888-debug/vn-payment-platform.git`
2. Read the two Google Docs for full technical spec and code:
   - Technical Spec: https://docs.google.com/document/d/1MvoncbSEFXKOdokatCg9m_YmFbyg2oOqpKa5TebTVEY/edit
   - Code Implementation: https://docs.google.com/document/d/1Cd_QIiF8jIJOCacHKSb5HTmHK0eam_RBgg3nkORiI6A/edit
3. Implement all Java source files as specified in the Code Implementation doc (51 pages)
4. Set up Docker Compose with MySQL 8, Redis 7, RabbitMQ 3
5. Ensure all APIs are functional and testable via curl/Postman
6. Deploy and provide access URL

## Priority Order
1. Database schema (init.sql) + Docker Compose
2. Entity + Enum classes
3. Core services: PaymentService, RoutingService, MerchantService
4. Controllers + API endpoints
5. Schedulers (polling, reconciliation, settlement)
6. Android app notification listener
7. Integration testing
