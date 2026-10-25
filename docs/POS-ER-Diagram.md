# LocalMart POS Entity–Relationship Model

| Document | Value |
| --- | --- |
| Version | 2.0 — implementation baseline |
| Updated | 26 October 2026 |
| Scope | Persistent JPA entities in the completed application |
| Related documents | `POS-DataDictionary.md`, `POS_Project_SRS_Documentation.md` |

## 1. Purpose

This document describes the database model implemented by LocalMart POS. It replaces the earlier six-entity draft and removes questionnaire-related entities that do not exist in this project.

## 2. Entity–Relationship Diagram

```mermaid
erDiagram
    USERS {
        bigint id PK
        string username UK
        string password
        string full_name
        string role
        boolean active
    }

    CATEGORIES {
        bigint id PK
        string name
        string description
        boolean active
    }

    UNITS {
        bigint id PK
        string name
        string abbreviation
        boolean active
    }

    PRODUCTS {
        bigint id PK
        bigint category_id FK
        bigint unit_id FK
        string barcode
        string name
        decimal selling_price
        decimal cost_price
        decimal discount_percent
        int stock_quantity
        int minimum_stock
        boolean active
    }

    CUSTOMERS {
        bigint id PK
        string name
        string phone_number
        bigint point_balance
        bigint total_spending
        boolean active
    }

    INVENTORYS {
        bigint id PK
        bigint product_id FK
        bigint actor_id FK
        string inventory_type
        int quantity
        int before_stock
        int after_stock
        string reason
        datetime timestamp
    }

    SALES {
        bigint id PK
        string invoice_no UK
        bigint cashier_id FK
        bigint customer_id FK
        decimal subtotal
        decimal total_discount
        decimal point_discount
        decimal total_amount
        decimal paid_amount
        decimal change_amount
        string payment_method
        string payment_status
        string sale_status
        datetime timestamp
    }

    SALE_ITEMS {
        bigint id PK
        bigint sale_id FK
        bigint product_id FK
        string product_name_snapshot
        string barcode_snapshot
        int quantity
        int refund_quantity
        decimal unit_price
        decimal discount_amount
        decimal net_total
        decimal cost_price_snapshot
        string sale_status
    }

    LOYALTY_CONFIG {
        bigint id PK
        decimal minimum_purchase_amount
        decimal spending_amount
        bigint earned_points
        bigint redeem_points
        decimal redeem_value
        bigint minimum_redeem_points
        boolean earning_enabled
        boolean redemption_enabled
        boolean active
    }

    LOYALTY_TRANSACTION {
        bigint id PK
        bigint customer_id FK
        bigint sale_id FK
        bigint points
        string type
        bigint before_point
        bigint after_point
        datetime timestamp
    }

    AUDIT_LOG {
        bigint id PK
        bigint actor
        string action
        string status
        string entity_name
        bigint row_id
        string description
        datetime timestamp
    }

    AUDIT_DETAILS {
        bigint id PK
        bigint audit_id FK
        string field_name
        string old_data
        string new_data
    }

    CASHIER_SALE_REPORTS {
        bigint id PK
        bigint cashier_id FK
        string report_type
        date start_date
        date end_date
        datetime generated_at
        datetime submitted_at
        decimal system_cash
        decimal real_world_cash
        decimal cash_difference
        smallint snapshot_version
        string snapshot_json
    }

    CATEGORIES o|--o{ PRODUCTS : categorizes
    UNITS o|--o{ PRODUCTS : measures
    PRODUCTS ||--o{ INVENTORYS : records_movements
    USERS ||--o{ INVENTORYS : performs

    USERS ||--o{ SALES : processes
    CUSTOMERS o|--o{ SALES : identifies_member
    SALES ||--o{ SALE_ITEMS : contains
    PRODUCTS ||--o{ SALE_ITEMS : sold_as

    CUSTOMERS ||--o{ LOYALTY_TRANSACTION : owns
    SALES o|--o{ LOYALTY_TRANSACTION : generates

    AUDIT_LOG ||--o{ AUDIT_DETAILS : contains

    USERS ||--o{ CASHIER_SALE_REPORTS : submits
```

## 3. Entity Summary

| Entity / table | Responsibility |
| --- | --- |
| `User` / `users` | Authentication, role, activation, and user audit metadata. |
| `Category` / `categories` | Product grouping. |
| `Unit` / `units` | Product unit of measure. |
| `Product` / `products` | Product master data, pricing, discount, and current stock. |
| `Customer` / `customers` | Member identity, spending, and loyalty balance. |
| `Inventory` / `inventorys` | Stock movement ledger for stock-in, sales, refunds, adjustments, damage, expiry, and missing stock. |
| `Sale` / `sales` | Transaction header, payment totals, status, customer, cashier, and loyalty summary. |
| `SaleItem` / `sale_items` | Transaction lines and immutable commercial snapshots. |
| `LoyaltyConfig` / `loyalty_config` | Versioned earning and redemption rules. |
| `LoyaltyTransaction` / `loyalty_transaction` | Point movement ledger. |
| `Audit` / `audit_log` | Successful or failed business-operation audit event. |
| `AuditDetails` / `audit_details` | Field changes and structured event details. |
| `CashierSaleReport` / `cashier_sale_reports` | Immutable owner-scoped cashier reconciliation report and JSON snapshot. |

## 4. Cardinality and Optionality

| Relationship | Cardinality | Implementation meaning |
| --- | --- | --- |
| Category → Product | 1 → 0..N; category optional on product | A category can group many products. Product creation workflows normally assign one. |
| Unit → Product | 1 → 0..N; unit optional on product | A unit can be reused by many products. |
| Product → Inventory | 1 → 0..N | Every inventory movement references exactly one product. |
| User → Inventory | 1 → 0..N | Every inventory movement references exactly one actor. |
| User → Sale | 1 → 0..N | Every sale references exactly one cashier/actor. |
| Customer → Sale | 0..1 → 0..N | A sale may be anonymous or linked to one active member. |
| Sale → SaleItem | 1 → 0..N at schema level | Checkout requires at least one item at the application layer. |
| Product → SaleItem | 1 → 0..N | Each sale item references its product and also stores value snapshots. |
| Customer → LoyaltyTransaction | 1 → 0..N | Every point movement belongs to one customer. |
| Sale → LoyaltyTransaction | 0..1 → 0..N | A point movement may be associated with a sale. |
| Audit → AuditDetails | 1 → 0..N | An audit event may contain field-level changes or structured refund data. |
| User → CashierSaleReport | 1 → 0..N | Every saved cashier report has one owner. |

## 5. Logical References Not Enforced as Foreign Keys

- `audit_log.actor` stores a user ID as a scalar for durable audit history.
- `created_by`, `updated_by`, and `deleted_by` inherited from `BaseEntity` store actor IDs as scalars.
- `audit_log.row_id` is polymorphic: its target is identified by `entity_name`, so it cannot be represented by one database FK.
- `cashier_sale_reports.snapshot_json` contains detached sale, item, payment, and refund details. These snapshot references are historical values rather than relational FKs.

## 6. Refund Persistence Model

There is no standalone refund entity. A processed refund updates and records data across existing entities:

1. `sales.sale_status` becomes `PARTIALLY_REFUNDED` or `REFUNDED`.
2. `sale_items.refund_quantity` and item status reflect returned quantities.
3. `inventorys` records `REFUND`, `DAMAGE`, or `EXPIRED` return outcomes.
4. `loyalty_transaction` records earned-point reversals or redeemed-point restoration.
5. `audit_log` and `audit_details` preserve the refund amount, reasons, conditions, quantities, payment method, and item allocation used by cashier reporting.

## 7. Database-Level Uniqueness

- `users.username`
- `sales.invoice_no`
- (`cashier_sale_reports.cashier_id`, `start_date`, `end_date`)

Category names, unit names/abbreviations, active customer phone numbers, and active product barcodes are enforced by application services rather than database unique constraints.
