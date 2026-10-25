# LocalMart POS

LocalMart POS is an offline point-of-sale system for small businesses. It combines sales processing, inventory, customer and loyalty management, reporting, and audit logging in a single-store Spring Boot application backed by SQLite.

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Data JPA
- Spring Security
- Thymeleaf
- SQLite
- Apache PDFBox and Apache POI for report exports
- Maven Wrapper

## Project Structure

Application code uses the `com.localmarts.pos` base package and is grouped by feature.

```text
src/main/java/com/localmarts/pos/
├── audit/
├── category/
├── common/
├── config/
├── customer/
├── inventory/
├── loyalty/
├── product/
├── report/
├── sale/
├── scanner/
├── unit/
└── user/
```

## Report Module

The report module is separated first by audience (`admin` and `cashier`) and then by responsibility. Code shared by both report areas lives in `report/common`.

```text
src/main/java/com/localmarts/pos/report/
├── common/
│   ├── PdfTextSupport.java
│   ├── ReportDataException.java
│   └── ReportTime.java
├── admin/
│   ├── controller/
│   ├── dto/
│   ├── model/
│   ├── repository/
│   └── service/
└── cashier/
    ├── config/
    ├── controller/
    ├── dto/
    ├── entity/
    ├── exception/
    ├── model/
    ├── repository/
    └── service/
```

- `controller`: HTTP endpoints and view-model preparation
- `dto`: request and report data-transfer objects
- `model`: report periods, snapshots, types, and read views
- `entity`: persisted report records
- `repository`: database access
- `service`: report generation, calculation, submission, PDF, and snapshot logic
- `config`: report-specific database initialization
- `exception`: report-specific exceptions
- `common`: utilities and exceptions shared by admin and cashier reporting

Report tests mirror the production package structure under:

```text
src/test/java/com/localmarts/pos/report/
```

The related UI files are located under:

```text
src/main/resources/
├── templates/admin/report/
├── templates/cashier/report/
├── templates/cashier/reports/
├── static/css/cashier-report.css
└── static/js/cashier-report.js
```

## Build and Test

Compile and run all Java tests:

```bash
./mvnw test
```

Run the cashier report JavaScript tests:

```bash
node --test src/test/js/cashier-report.test.cjs
```

## Run the Application

```bash
./mvnw spring-boot:run
```

By default, the application uses `https://localhost:8080` and stores its SQLite database and local runtime files under `~/LocalMartPOS`.
