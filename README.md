# Vietnam 4th-Party Payment Platform

A complete payment aggregation platform for the Vietnamese market.

## Tech Stack
- **Backend**: Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis 7 + RabbitMQ 3
- **Android**: Notification Listener Payment Assistant App
- **Deploy**: Docker Compose

## Architecture
This platform acts as a 4th-party payment orchestrator:
- Aggregates multiple upstream banks (Vietcombank, Techcombank, MB Bank), NAPAS, and PSPs (MoMo)
- Serves downstream merchants via REST API with HMAC signing
- Merchants deposit USDT as collateral, transactions settle in VND
- Three-path confirmation: bank API polling, device notification matching, reconciliation files

## Project Structure
```
vn-payment-platform/
├── DEVIN_PROMPT.md          # Full build instructions for Devin AI
├── docker-compose.yml       # MySQL + Redis + RabbitMQ + App
├── sql/init.sql             # Complete database schema (12 tables)
├── pom.xml                  # Maven dependencies
└── src/main/java/com/vnpay/platform/
    ├── config/              # Redis, RabbitMQ, Security configs
    ├── common/              # ApiResponse, HmacUtil, SnowflakeIdGenerator
    ├── entity/              # JPA entities for all 12 tables
    ├── enums/               # TransactionStatus, PayMethod, MatchSource
    ├── mapper/              # MyBatis-Plus mappers
    ├── service/             # Core business logic (11 services)
    ├── controller/          # REST API endpoints
    └── scheduler/           # Polling, reconciliation, settlement jobs
```

## Quick Start
```bash
git clone https://github.com/claude58888-debug/vn-payment-platform.git
cd vn-payment-platform
docker-compose up -d
```

## Documentation
- [Technical Specification](https://docs.google.com/document/d/1MvoncbSEFXKOdokatCg9m_YmFbyg2oOqpKa5TebTVEY/edit) - Full system design (10 pages)
- [Code Implementation](https://docs.google.com/document/d/1Cd_QIiF8jIJOCacHKSb5HTmHK0eam_RBgg3nkORiI6A/edit) - Complete source code (51 pages)
- [DEVIN_PROMPT.md](./DEVIN_PROMPT.md) - AI build instructions

## Key Features
- USDT deposit management with risk equity calculation
- Smart payment routing with circuit breaker
- Android notification listener for early payment confirmation
- Bank reconciliation file processing
- Double-entry bookkeeping ledger
- Merchant callback with exponential retry
- AML risk control

## API Endpoints
| Category | Method | Endpoint | Description |
|----------|--------|----------|-------------|
| Merchant | POST | /api/merchant/create | Create merchant |
| Deposit | POST | /api/merchant/deposit/usdt/create | Generate USDT deposit address |
| Payment | POST | /api/payment/create | Create payment order |
| Payment | GET | /api/payment/status | Query payment status |
| Device | POST | /api/device/bind | Bind Android device |
| Device | POST | /api/device/notification | Upload bank notification |
| Settlement | GET | /api/settlement/summary | Settlement summary |

## Status
- [x] Technical specification complete
- [x] Database schema designed (12 tables)
- [x] Docker Compose infrastructure
- [x] Full code implementation documented
- [ ] Devin build & deploy
- [ ] Integration testing
