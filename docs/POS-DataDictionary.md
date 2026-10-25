# LocalMart POS Data Dictionary

| Document | Value |
| --- | --- |
| Version | 2.0 — implementation baseline |
| Updated | 26 October 2026 |
| Source of truth | JPA entities and application services in `src/main/java` |
| Database | SQLite, managed by Hibernate with `ddl-auto: update` |
| Related documents | `POS-ER-Diagram.md`, `POS_Project_SRS_Documentation.md` |

## 1. Conventions

- Java property names are converted to `snake_case` database columns by Spring Boot's Hibernate naming strategy unless a `@Column` or `@JoinColumn` explicitly supplies the name.
- Monetary values use `BigDecimal`. Persisted sale and report amounts use `DECIMAL(14,2)`; loyalty configuration amounts use `DECIMAL(15,2)`.
- Date and time values use `LocalDate` and `LocalDateTime`. Report and refund business rules use the `Asia/Bangkok` store timezone.
- `PK` means primary key, `FK` means a JPA foreign-key relationship, and `UQ` means a database uniqueness constraint.
- Fields marked “application-required” are validated or initialized by services even when the JPA column is technically nullable.
- Refunds do not have a separate refund table. They are represented by sale/item state, inventory movements, loyalty transactions, and structured audit details.

## 2. Shared Auditable Fields

`users`, `categories`, `units`, `products`, `customers`, and `loyalty_config` inherit these fields from `BaseEntity`.

| Column | Java type | Nullability | Description |
| --- | --- | --- | --- |
| `active` | `Boolean` | NOT NULL | Soft-active flag; defaults to `true`. |
| `created_at` | `LocalDateTime` | NOT NULL | Hibernate creation timestamp. |
| `updated_at` | `LocalDateTime` | NOT NULL | Hibernate update timestamp. |
| `deleted_at` | `LocalDateTime` | Nullable | Soft-delete timestamp. |
| `created_by` | `Long` | NOT NULL | Actor ID that created the record; stored as a scalar, not a database FK. |
| `updated_by` | `Long` | Nullable | Actor ID that last updated the record. |
| `deleted_by` | `Long` | Nullable | Actor ID that soft-deleted the record. |

## 3. Entity Definitions

### 3.1 `users`

Stores authenticated administrators and cashiers.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | User identifier. |
| `full_name` | `String` | NOT NULL | Display name. |
| `username` | `String` | NOT NULL, UQ | Login name. |
| `password` | `String` | NOT NULL | BCrypt password hash. |
| `role` | `Role` | NOT NULL | `ADMIN` or `CASHIER`. |
| inherited fields | See §2 |  | Account activation and audit metadata. |

Application rules:

- Deactivated users cannot authenticate.
- The bootstrap initializer creates `admin` and `cashier` accounts only when those usernames do not already exist.

### 3.2 `categories`

Stores product categories.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Category identifier. |
| `name` | `String` | NOT NULL; active-name uniqueness enforced by service | Category name. |
| `description` | `String` | Nullable | Optional category description. |
| inherited fields | See §2 |  | Soft-delete and audit metadata. |

Application rules:

- Names are trimmed and compared case-insensitively among active, non-deleted categories.
- A category referenced by a product cannot be deleted.

### 3.3 `units`

Stores units of measure assigned to products.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Unit identifier. |
| `name` | `String` | NOT NULL; active uniqueness enforced by service | Full unit name. |
| `abbreviation` | `String` | NOT NULL; active uniqueness enforced by service | Short label such as `pcs` or `kg`. |
| inherited fields | See §2 |  | Soft-delete and audit metadata. |

Application rules:

- Name and abbreviation are trimmed and compared case-insensitively.
- A unit referenced by a product cannot be deleted.

### 3.4 `products`

Stores sellable products and their current inventory values.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Product identifier. |
| `name` | `String` | Application-required | Product display name. |
| `barcode` | `String` | Active uniqueness enforced by service | Search/scanner barcode. |
| `size` | `String` | Nullable | Size or variant label. |
| `mfd` | `LocalDate` | Nullable | Manufacturing date. |
| `exp` | `LocalDate` | Nullable | Expiry date. |
| `selling_price` | `BigDecimal` | Application-required | Current selling price. |
| `stock_quantity` | `Integer` | Application-required, defaults to `0` on create | Current on-hand quantity. |
| `minimum_stock` | `Integer` | Application-required | Low-stock threshold. |
| `cost_price` | `BigDecimal` | Application-required | Current cost price used for new sale snapshots and profit reporting. |
| `discount_percent` | `BigDecimal(5,2)` | NOT NULL, default `0.00` | Product discount percentage. |
| `category_id` | `Long` | Nullable FK → `categories.id` | Product category. |
| `unit_id` | `Long` | Nullable FK → `units.id` | Unit of measure. |
| inherited fields | See §2 |  | Soft-delete and audit metadata. |

Application rules:

- Stock cannot be reduced below zero by a sale or adjustment.
- Product deletion is soft deletion.
- Historical sales retain product name, barcode, size, unit, price, discount, and cost snapshots in `sale_items`.

### 3.5 `customers`

Stores customer/member profiles used by loyalty and sales.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Customer identifier. |
| `name` | `String` | NOT NULL | Customer name. |
| `phone_number` | `String(13)` | NOT NULL; active uniqueness enforced by service | Lookup and member identifier. |
| `point_balance` | `Long` | Initialized to `0` | Current loyalty point balance. |
| `total_spending` | `Long` | Initialized to `0` | Accumulated spending value maintained by the application. |
| inherited fields | See §2 |  | Soft-delete and audit metadata. |

### 3.6 `inventorys`

Immutable-style stock movement ledger. The implemented table name is intentionally `inventorys`.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Movement identifier. |
| `product_id` | `Long` | NOT NULL, FK → `products.id` | Affected product. |
| `inventory_type` | `InventoryType` | NOT NULL, max 64 | Movement type. |
| `quantity` | `Integer` | NOT NULL | Absolute movement quantity recorded by the operation. |
| `before_stock` | `Integer` | NOT NULL | Stock immediately before the movement. |
| `after_stock` | `Integer` | NOT NULL | Stock immediately after the movement. |
| `reason` | `String(255)` | Nullable | Operator or system reason. |
| `actor_id` | `Long` | NOT NULL, FK → `users.id` | User responsible for the movement. |
| `timestamp` | `LocalDateTime` | NOT NULL, creation timestamp | Movement time. |

`InventoryType` values: `STOCK_IN`, `SALE`, `ADJUSTMENT`, `REFUND`, `DAMAGE`, `EXPIRED`, `MISSING`.

### 3.7 `sales`

Stores completed POS transactions and their payment, loyalty, and status totals.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Sale identifier. |
| `invoice_no` | `String(40)` | NOT NULL, UQ | Generated invoice number. |
| `cashier_id` | `Long` | NOT NULL, FK → `users.id` | Cashier/actor that completed the sale. |
| `customer_id` | `Long` | Nullable, FK → `customers.id` | Optional loyalty customer. |
| `subtotal` | `BigDecimal(14,2)` | NOT NULL | Gross item subtotal before discounts. |
| `total_discount` | `BigDecimal(14,2)` | NOT NULL | Total product and loyalty discount. |
| `point_discount` | `BigDecimal(14,2)` | NOT NULL, default `0.00` | Portion discounted through redeemed loyalty points. |
| `total_amount` | `BigDecimal(14,2)` | NOT NULL | Final payable amount. |
| `paid_amount` | `BigDecimal(14,2)` | NOT NULL | Amount recorded as paid. |
| `change_amount` | `BigDecimal(14,2)` | NOT NULL | Cash change; zero for exact non-cash payments. |
| `point_earned` | `Integer` | NOT NULL, default `0` | Points earned by this sale. |
| `point_redeemed` | `Integer` | NOT NULL, default `0` | Points redeemed by this sale. |
| `payment_method` | `PaymentMethod` | NOT NULL, max 20 | `CASH`, `CARD`, or `QR`. |
| `payment_status` | `PaymentStatus` | NOT NULL, max 20 | `PENDING`, `PAID`, or `FAILED`. |
| `sale_status` | `SaleStatus` | NOT NULL, max 30 | `COMPLETED`, `PARTIALLY_REFUNDED`, `REFUNDED`, or `VOIDED`. |
| `note` | `TEXT` | Nullable | Sale note, limited to 500 characters at request validation. |
| `timestamp` | `LocalDateTime` | NOT NULL | Sale completion time. |

Application rules:

- A sale contains at least one requested item.
- Card and QR payments must equal the final total; cash may exceed it and produces change.
- Checkout validates active products, requested quantities, prices, and available stock in one transaction.

### 3.8 `sale_items`

Stores each sale line plus immutable commercial snapshots needed for receipts, refunds, and profit reports.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Sale-line identifier. |
| `sale_id` | `Long` | NOT NULL, FK → `sales.id` | Parent sale. |
| `product_id` | `Long` | NOT NULL, FK → `products.id` | Original product. |
| `product_name_snapshot` | `String` | NOT NULL | Name at checkout. |
| `barcode_snapshot` | `String` | NOT NULL | Barcode at checkout. |
| `size_snapshot` | `String` | Nullable | Size at checkout. |
| `unit_snapshot` | `String` | Nullable | Unit at checkout. |
| `quantity` | `Integer` | NOT NULL | Quantity sold. |
| `unit_price` | `BigDecimal(14,2)` | NOT NULL | Unit selling price at checkout. |
| `gross_subtotal` | `BigDecimal(14,2)` | NOT NULL | Price × quantity before discounts. |
| `discount_percent_snapshot` | `BigDecimal(5,2)` | NOT NULL | Product discount percentage at checkout. |
| `discount_amount` | `BigDecimal(14,2)` | NOT NULL | Line discount amount. |
| `net_total` | `BigDecimal(14,2)` | NOT NULL | Final line total. |
| `cost_price_snapshot` | `BigDecimal(14,2)` | NOT NULL | Cost price at checkout. |
| `sale_status` | `SaleStatus` | NOT NULL, max 30 | Current line refund status. |
| `refund_quantity` | `Integer` | NOT NULL, default `0` | Quantity returned by the one permitted refund operation. |

### 3.9 `loyalty_config`

Stores loyalty earning and redemption configuration. Configuration changes create a new active configuration history entry rather than rewriting sale history.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Configuration identifier. |
| `minimum_purchase_amount` | `BigDecimal(15,2)` | NOT NULL | Minimum eligible purchase. |
| `spending_amount` | `BigDecimal(15,2)` | NOT NULL | Spend step used for earning. |
| `earned_points` | `Long` | NOT NULL | Points earned per spend step. |
| `redeem_points` | `Long` | NOT NULL | Point step used for redemption. |
| `redeem_value` | `BigDecimal(15,2)` | NOT NULL | Currency value per redemption step. |
| `minimum_redeem_points` | `Long` | NOT NULL | Minimum balance required to redeem. |
| `earning_enabled` | `boolean` | NOT NULL, default `true` | Enables point earning. |
| `redemption_enabled` | `boolean` | NOT NULL, default `true` | Enables point redemption. |
| inherited fields | See §2 |  | Activation and audit metadata. |

### 3.10 `loyalty_transaction`

Ledger of every customer point movement.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Loyalty transaction identifier. |
| `customer_id` | `Long` | NOT NULL, FK → `customers.id` | Customer whose balance changed. |
| `sale_id` | `Long` | Nullable, FK → `sales.id` | Related sale when applicable. |
| `points` | `Long` | NOT NULL | Point amount associated with the movement. |
| `type` | `TransactionType` | NOT NULL, max 40 | Movement category. |
| `before_point` | `Long` | NOT NULL | Balance before the movement. |
| `after_point` | `Long` | NOT NULL | Balance after the movement. |
| `timestamp` | `LocalDateTime` | NOT NULL, creation timestamp | Movement time. |

`TransactionType` values: `EARN`, `REDEEM`, `REFUND_EARN_REVERSAL`, `REFUND_REDEEM_RESTORE`, `ADJUSTMENT`.

### 3.11 `audit_log`

Stores successful and failed create/update/delete activity.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Audit event identifier. |
| `actor` | `Long` | NOT NULL, scalar user ID | Actor responsible for the operation. This is not mapped as a DB FK. |
| `action` | `AuditAction` | NOT NULL, max 20 | `CREATE`, `UPDATE`, or `DELETE`. |
| `status` | `AuditStatus` | NOT NULL, max 20 | `SUCCESS` or `FAILED`. |
| `entity_name` | `String(100)` | NOT NULL | Logical entity or operation name. |
| `row_id` | `Long` | Nullable | Related record ID. |
| `description` | `TEXT` | Nullable | Human-readable event description. |
| `timestamp` | `LocalDateTime` | NOT NULL, creation timestamp | Event time. |

### 3.12 `audit_details`

Stores field-level changes and structured refund/report details attached to an audit event.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Detail identifier. |
| `field_name` | `String(100)` | NOT NULL | Changed field or structured detail key. |
| `old_data` | `TEXT` | Nullable | Previous value. |
| `new_data` | `TEXT` | Nullable | New value. |
| `audit_id` | `Long` | NOT NULL, FK → `audit_log.id` | Parent audit event. |

Deleting an audit event through JPA cascades to its details.

### 3.13 `cashier_sale_reports`

Stores immutable, owner-scoped cashier report submissions and their versioned JSON snapshots.

| Column | Java / logical type | Constraints | Description |
| --- | --- | --- | --- |
| `id` | `Long` | PK, generated | Saved report identifier. |
| `cashier_id` | `Long` | NOT NULL, immutable FK → `users.id` | Report owner. |
| `report_type` | `ReportType` | NOT NULL, immutable | `DAILY`, `WEEKLY`, or `CUSTOM`. |
| `start_date` | `LocalDate` | NOT NULL, immutable | Inclusive report start date. |
| `end_date` | `LocalDate` | NOT NULL, immutable | Inclusive report end date. |
| `generated_at` | `LocalDateTime` | NOT NULL, immutable | Preview generation timestamp. |
| `submitted_at` | `LocalDateTime` | NOT NULL, immutable | Final submission timestamp. |
| `system_cash` | `BigDecimal(14,2)` | NOT NULL, immutable | Cash receipts minus cash refunds calculated from system records. |
| `real_world_cash` | `BigDecimal(14,2)` | NOT NULL, immutable | Cash counted by the cashier. |
| `cash_difference` | `BigDecimal(14,2)` | NOT NULL, immutable | `real_world_cash - system_cash`. |
| `snapshot_version` | `SMALLINT` | NOT NULL, immutable | Snapshot schema version; current version is `1`. |
| `snapshot_json` | `TEXT` | NOT NULL, immutable | Serialized report detail, timezone, and currency snapshot. |

Constraints and application rules:

- Composite UQ/index: (`cashier_id`, `start_date`, `end_date`).
- Saved reports can only be listed, viewed, and exported by their owner.
- The cash difference status is `OVER`, `SHORT`, or `BALANCED` according to its sign.
- Snapshot version 1 records timezone `Asia/Bangkok` and currency `THB`.

## 4. Non-Persistent Domain Types

| Type | Values / purpose |
| --- | --- |
| `StockInRowStatus` | `EXISTING_PRODUCT`, `NEW_PRODUCT`, `NEEDS_MAPPING`, `INVALID`; used during spreadsheet review only. |
| `RefundReason` | `DEFECTIVE`, `EXPIRED`, `WRONG_ITEM`, `CUSTOMER_CHANGED_MIND`, `OTHER`. |
| `ReturnCondition` | `SELLABLE`, `DAMAGED`, `EXPIRED`; determines the resulting inventory movement. |
| `CashierReportSnapshot` | Versioned report payload containing summaries, payment rows, sale lines, item lines, and refund lines. |
| `AdminReportPeriod` / `ReportPeriod` | Validated date-range value objects for admin and cashier reporting. |

## 5. Important Integrity Boundaries

- Soft deletion is used for master data; historical sales retain references and value snapshots.
- Sale checkout, stock updates, loyalty changes, and refund processing are transactional service operations.
- A sale may have at most one refund operation and must be refunded within seven days.
- Sellable returns create `REFUND` inventory movements; damaged and expired returns create zero-stock-impact `DAMAGE` or `EXPIRED` movements.
- Non-cash refunds are recorded in the POS only; the application does not call an external card or QR payment processor.
- Audit actor and `BaseEntity` actor fields are logical user IDs but are intentionally not database foreign keys.
