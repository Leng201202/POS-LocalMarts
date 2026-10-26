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

## Package as a Desktop Application

`jpackage` creates a native installer containing the application and its own Java runtime. End users do not need to install Java separately.

Packaging must be performed on the target operating system: build the macOS package on macOS and the Windows package on Windows.

### Prerequisites

- JDK 21 with `java`, `javac`, and `jpackage` available on `PATH`
- The repository source code
- Windows only: a compatible WiX Toolset installation available on `PATH`

Confirm the required tools:

```bash
java --version
jpackage --version
```

### macOS DMG

Run from the repository root:

```bash
./mvnw -DskipTests clean package && \
mkdir -p target/jpackage-input target/installer && \
cp target/pos-0.0.1-SNAPSHOT.jar target/jpackage-input/ && \
jpackage \
  --type dmg \
  --name "LocalMart POS" \
  --app-version "0.0.1" \
  --vendor "LocalMarts" \
  --description "LocalMart Point of Sale" \
  --input target/jpackage-input \
  --main-jar pos-0.0.1-SNAPSHOT.jar \
  --arguments "--server.address=127.0.0.1" \
  --arguments "--server.ssl.enabled=false" \
  --arguments "--localmart.browser.url=http://127.0.0.1:8080" \
  --java-options "-Dfile.encoding=UTF-8" \
  --mac-package-identifier "com.localmarts.pos" \
  --mac-package-name "LocalMart POS" \
  --mac-app-category "public.app-category.business" \
  --dest target/installer
```

Output:

```text
target/installer/LocalMart POS-0.0.1.dmg
```

Open the DMG and drag **LocalMart POS** into **Applications**. This command creates an unsigned development package, so macOS may require the first launch through **Control-click → Open**. Public distribution should use Apple Developer ID signing and notarization.

### Windows EXE

Open PowerShell in the repository root and run:

```powershell
.\mvnw.cmd -DskipTests clean package
New-Item -ItemType Directory -Force target\jpackage-input, target\installer | Out-Null
Copy-Item target\pos-0.0.1-SNAPSHOT.jar target\jpackage-input\

jpackage `
  --type exe `
  --name "LocalMart POS" `
  --app-version "0.0.1" `
  --vendor "LocalMarts" `
  --description "LocalMart Point of Sale" `
  --input target\jpackage-input `
  --main-jar pos-0.0.1-SNAPSHOT.jar `
  --arguments "--server.address=127.0.0.1" `
  --arguments "--server.ssl.enabled=false" `
  --arguments "--localmart.browser.url=http://127.0.0.1:8080" `
  --java-options "-Dfile.encoding=UTF-8" `
  --win-dir-chooser `
  --win-menu `
  --win-menu-group "LocalMart" `
  --win-shortcut `
  --dest target\installer
```

Windows output:

```text
target\installer\LocalMart POS-0.0.1.exe
```

Run the EXE and complete the installation wizard. An unsigned installer may trigger Microsoft Defender SmartScreen; production releases should be signed with a trusted Windows code-signing certificate.

### End-User Guide

1. Launch **LocalMart POS** from Applications on macOS or the Start menu/desktop shortcut on Windows.
2. Wait for the default browser to open `http://127.0.0.1:8080`.
3. Sign in with `admin` / `admin123` or `cashier` / `cashier123` for the initial setup.
4. Use the Admin workspace to configure users, products, categories, units, inventory, and loyalty rules before processing sales.
5. Keep the desktop application running while using the browser interface. Closing the application stops the local POS server.

Application data is stored outside the installed application so upgrades do not replace the database:

- macOS: `~/LocalMartPOS`
- Windows: `%USERPROFILE%\LocalMartPOS`

Back up this directory before upgrading or moving the application to another computer. Uninstalling the desktop application does not automatically delete it.

If the browser does not open automatically, navigate to `http://127.0.0.1:8080`. If that address is already in use, close the other LocalMart POS instance before launching the application again.
