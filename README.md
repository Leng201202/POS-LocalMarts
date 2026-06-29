# POS-LocalMarts

LocalMart POS is an offline Java-based Point of Sale (POS) system designed for small businesses. Built with Spring Boot, Thymeleaf, and SQLite, it provides sales processing, inventory management, customer management, reporting, and audit logging in a single-store environment without requiring an internet connection.

## Tech Stack

- Java 21
- Spring Boot
- Thymeleaf
- SQLite
- Spring Data JPA
- Maven

## Directory Architecture Example
Spring Application Server
```text
src/main/java/com/localmart/
├── LocalMartApplication.java
├── config/
├── common/
└── feature/
        controller/
        repository/
        service/
        entity/
```
UI Architecture
```text
src/main/resources/
├── application.yml
├── templates/
│   └── feature/
└── static/
    ├── css/
    ├── js/
    └── images/

```