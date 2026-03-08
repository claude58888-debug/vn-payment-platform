-- Vietnam 4th-Party Payment Platform Database Schema
-- Version: 1.0
-- Date: 2026-03-09

CREATE DATABASE IF NOT EXISTS vn_payment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vn_payment;

-- Merchant table
CREATE TABLE merchant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id VARCHAR(32) NOT NULL UNIQUE COMMENT 'Merchant unique ID',
    merchant_name VARCHAR(128) NOT NULL,
    merchant_secret VARCHAR(128) NOT NULL COMMENT 'HMAC signing secret',
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    contact_email VARCHAR(128),
    notify_url VARCHAR(512) COMMENT 'Callback URL',
    fee_rate DECIMAL(6,4) DEFAULT 0.0100 COMMENT 'Transaction fee rate',
    settlement_cycle VARCHAR(16) DEFAULT 'T1' COMMENT 'T0/T1/T2',
    risk_level VARCHAR(16) DEFAULT 'NORMAL' COMMENT 'LOW/NORMAL/HIGH',
    daily_limit_vnd BIGINT DEFAULT 500000000 COMMENT 'Daily transaction limit VND',
    single_limit_vnd BIGINT DEFAULT 50000000 COMMENT 'Single transaction limit VND',
    status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUSPENDED/BLOCKED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB;

-- Merchant USDT deposit account
CREATE TABLE merchant_usdt_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id VARCHAR(32) NOT NULL UNIQUE,
    usdt_balance DECIMAL(20,8) DEFAULT 0 COMMENT 'Available USDT balance',
    usdt_frozen DECIMAL(20,8) DEFAULT 0 COMMENT 'Frozen USDT',
    wallet_type VARCHAR(16) DEFAULT 'EXCHANGE' COMMENT 'EXCHANGE/CUSTODY/ON_CHAIN',
    wallet_ref VARCHAR(256) COMMENT 'External wallet reference',
    mark_price_usdt_vnd DECIMAL(20,2) DEFAULT 25000 COMMENT 'USDT/VND mark price',
    risk_equity_vnd DECIMAL(20,2) DEFAULT 0 COMMENT '(usdt_balance-usdt_frozen)*mark_price',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id)
) ENGINE=InnoDB;

-- VND settlement account
CREATE TABLE merchant_vnd_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id VARCHAR(32) NOT NULL UNIQUE,
    balance_vnd DECIMAL(20,2) DEFAULT 0,
    pending_settlement_vnd DECIMAL(20,2) DEFAULT 0,
    settled_amount_vnd DECIMAL(20,2) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id)
) ENGINE=InnoDB;

-- Payment transaction
CREATE TABLE payment_transaction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_id VARCHAR(32) NOT NULL UNIQUE COMMENT 'Platform transaction ID (snowflake)',
    merchant_id VARCHAR(32) NOT NULL,
    merchant_order_id VARCHAR(64) NOT NULL COMMENT 'Merchant side order ID',
    amount DECIMAL(20,2) NOT NULL COMMENT 'Amount in VND',
    currency VARCHAR(8) DEFAULT 'VND',
    pay_method VARCHAR(32) COMMENT 'card/qrcode/bank_transfer',
    channel_id VARCHAR(32) COMMENT 'Upstream channel used',
    channel_transaction_id VARCHAR(128) COMMENT 'Upstream reference',
    status VARCHAR(32) DEFAULT 'CREATED' COMMENT 'CREATED/PROCESSING/SUCCESS_PENDING_RECON/SUCCESS_CONFIRMED/FAILED/UNKNOWN',
    match_source VARCHAR(16) COMMENT 'CHANNEL/DEVICE/RECONCILIATION',
    notify_url VARCHAR(512),
    callback_status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/SENT/CONFIRMED/FAILED',
    callback_count INT DEFAULT 0,
    extra JSON COMMENT 'Extra params (card info token, etc)',
    fee_amount DECIMAL(20,2) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant_order (merchant_id, merchant_order_id),
    INDEX idx_status (status),
    INDEX idx_channel (channel_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB;

-- Transaction ledger (double-entry bookkeeping)
CREATE TABLE transaction_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ledger_id VARCHAR(32) NOT NULL UNIQUE,
    merchant_id VARCHAR(32) NOT NULL,
    transaction_id VARCHAR(32),
    ledger_type VARCHAR(32) NOT NULL COMMENT 'PAYMENT/REFUND/FEE/SETTLEMENT/DEPOSIT/WITHDRAW',
    debit_account VARCHAR(64) NOT NULL,
    credit_account VARCHAR(64) NOT NULL,
    amount DECIMAL(20,2) NOT NULL,
    currency VARCHAR(8) DEFAULT 'VND',
    remark VARCHAR(256),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant (merchant_id),
    INDEX idx_transaction (transaction_id)
) ENGINE=InnoDB;

-- Channel config
CREATE TABLE channel_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel_id VARCHAR(32) NOT NULL UNIQUE,
    channel_name VARCHAR(64) NOT NULL,
    channel_type VARCHAR(32) COMMENT 'BANK/NAPAS/PSP',
    api_base_url VARCHAR(256),
    api_key VARCHAR(256),
    api_secret VARCHAR(256),
    weight INT DEFAULT 100 COMMENT 'Routing weight',
    success_rate DECIMAL(5,4) DEFAULT 0.9500,
    avg_latency_ms INT DEFAULT 500,
    fee_rate DECIMAL(6,4) DEFAULT 0.0050,
    has_query_api TINYINT DEFAULT 1,
    has_webhook TINYINT DEFAULT 0,
    has_recon_file TINYINT DEFAULT 1,
    circuit_breaker_threshold INT DEFAULT 5 COMMENT 'Consecutive failures to trigger breaker',
    circuit_breaker_status VARCHAR(16) DEFAULT 'CLOSED' COMMENT 'CLOSED/OPEN/HALF_OPEN',
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Device binding
CREATE TABLE device_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id VARCHAR(32) NOT NULL,
    device_id VARCHAR(64) NOT NULL UNIQUE,
    device_token VARCHAR(128) NOT NULL,
    auth_key VARCHAR(128) NOT NULL COMMENT 'HMAC key for device signing',
    device_info JSON COMMENT 'Device model, OS version, etc',
    trust_level VARCHAR(16) DEFAULT 'NEW' COMMENT 'NEW/TRUSTED/SUSPICIOUS/BLOCKED',
    match_accuracy DECIMAL(5,4) DEFAULT 0 COMMENT 'Historical match accuracy',
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant (merchant_id)
) ENGINE=InnoDB;

-- Device notifications (raw)
CREATE TABLE device_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notification_id VARCHAR(64) NOT NULL UNIQUE,
    merchant_id VARCHAR(32) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    bank_package VARCHAR(128) COMMENT 'Android package name',
    bank_label VARCHAR(64),
    raw_title VARCHAR(256),
    raw_text TEXT,
    signature VARCHAR(128),
    sent_at DATETIME,
    received_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_device (merchant_id, device_id),
    INDEX idx_received (received_at)
) ENGINE=InnoDB;

-- Parsed transactions from device
CREATE TABLE device_parsed_transaction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notification_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(32) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    bank_package VARCHAR(128),
    bank_label VARCHAR(64),
    direction VARCHAR(8) COMMENT 'CREDIT/DEBIT',
    amount DECIMAL(20,2),
    currency VARCHAR(8) DEFAULT 'VND',
    card_last4 VARCHAR(4),
    tx_time DATETIME,
    raw_text TEXT,
    match_status VARCHAR(16) DEFAULT 'UNMATCHED' COMMENT 'UNMATCHED/MATCHED/MULTI_CANDIDATES/NO_MATCH',
    matched_transaction_id VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant (merchant_id),
    INDEX idx_match (match_status),
    INDEX idx_amount_time (amount, tx_time)
) ENGINE=InnoDB;

-- USDT deposit orders
CREATE TABLE deposit_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    deposit_order_id VARCHAR(32) NOT NULL UNIQUE,
    merchant_id VARCHAR(32) NOT NULL,
    amount_usdt DECIMAL(20,8) NOT NULL,
    actual_amount_usdt DECIMAL(20,8),
    network VARCHAR(16) COMMENT 'TRC20/ERC20',
    deposit_address VARCHAR(128),
    tx_hash VARCHAR(128),
    status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/EXPIRED/FAILED',
    expired_at DATETIME,
    confirmed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant (merchant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB;

-- Deposit ledger
CREATE TABLE deposit_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id VARCHAR(32) NOT NULL,
    deposit_order_id VARCHAR(32),
    ledger_type VARCHAR(32) COMMENT 'DEPOSIT/FREEZE/DEDUCT/REFUND',
    amount_usdt DECIMAL(20,8) NOT NULL,
    balance_before DECIMAL(20,8),
    balance_after DECIMAL(20,8),
    remark VARCHAR(256),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant (merchant_id)
) ENGINE=InnoDB;

-- Settlement batch
CREATE TABLE settlement_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id VARCHAR(32) NOT NULL UNIQUE,
    merchant_id VARCHAR(32) NOT NULL,
    settlement_date DATE NOT NULL,
    total_amount_vnd DECIMAL(20,2),
    fee_amount_vnd DECIMAL(20,2),
    net_amount_vnd DECIMAL(20,2),
    risk_reserve_vnd DECIMAL(20,2) DEFAULT 0,
    status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/FAILED',
    bank_ref VARCHAR(128),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant_date (merchant_id, settlement_date)
) ENGINE=InnoDB;

-- Reconciliation records
CREATE TABLE reconciliation_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recon_date DATE NOT NULL,
    channel_id VARCHAR(32) NOT NULL,
    file_name VARCHAR(256),
    total_records INT DEFAULT 0,
    matched_records INT DEFAULT 0,
    unmatched_records INT DEFAULT 0,
    exception_records INT DEFAULT 0,
    status VARCHAR(16) DEFAULT 'PROCESSING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_date_channel (recon_date, channel_id)
) ENGINE=InnoDB;

-- Insert sample channel configs
INSERT INTO channel_config (channel_id, channel_name, channel_type, weight, has_query_api, has_webhook, has_recon_file) VALUES
('VCB_BANK', 'Vietcombank', 'BANK', 100, 1, 0, 1),
('TCB_BANK', 'Techcombank', 'BANK', 80, 1, 0, 1),
('MB_BANK', 'MB Bank', 'BANK', 70, 0, 0, 1),
('NAPAS_QR', 'NAPAS QR', 'NAPAS', 90, 1, 1, 1),
('MOMO_PSP', 'MoMo', 'PSP', 60, 1, 1, 0);
