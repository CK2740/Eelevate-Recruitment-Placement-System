# Recruitment and Placement Management System

A Java 21 and Spring Boot application for managing recruitment and placement workflows. The project is being implemented in phases; this initial phase establishes the backend foundation and a public health endpoint.

## Technology

- Java 21
- Spring Boot 3.5.16 and Maven
- Spring Web, Spring JDBC, Spring Security, and Bean Validation
- MySQL Connector/J and Flyway
- HTML, CSS, and vanilla JavaScript are planned for the frontend

The backend follows a layered structure: Controller → Service → DAO → MySQL. JPA and Hibernate are not used.

## Prerequisites

- Java 21
- Maven 3.9+
- MySQL 8.0+ when database-backed features and migrations are enabled

## Build and test

```powershell
mvn clean test
```

## Run the application

The Phase 1 application can start without a running MySQL server. Flyway is configured but must remain disabled until database migrations are created in Phase 2.

```powershell
mvn spring-boot:run
```

Check application health at `http://localhost:8080/api/health`.

To configure a local MySQL database, set these environment variables before starting the application:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/recruitment_db?serverTimezone=UTC"
$env:DB_USERNAME = "your_local_username"
$env:DB_PASSWORD = "your_local_password"
mvn spring-boot:run
```

`DB_URL` may be set to a different JDBC URL as needed. Do not commit credentials; local secret files are excluded by `.gitignore`. Phase 1 contains no business tables or database migration scripts.

## Backend package layout

```text
com.recruitment
├── config
├── controller
├── dao
├── dto
├── exception
├── model
├── security
├── service
├── util
└── validation
```

## Planned UI pages

The frontend is planned as 12 pages built with HTML, CSS, and vanilla JavaScript: login, dashboard, candidates, candidate form, candidate details, companies, jobs, job form, applications, interviews, placement process, and reports. The role shown by the login UI will not determine backend permissions.

## Phase 1 API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Returns application status and timestamp; no authentication required |

Other routes are denied until authentication and authorization are implemented. Business APIs, database tables, and migration scripts are intentionally out of scope for Phase 1.
