# Recruitment and Placement Management System

A Java 21 and Spring Boot application for managing recruitment and placement workflows. Phase 2 adds the initial MySQL schema through Flyway; business APIs and authentication will be implemented in later phases.

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

The application requires a reachable MySQL database at startup while Flyway is enabled. Flyway runs the Phase 2 initial schema migration automatically. Set `FLYWAY_ENABLED=false` only when intentionally starting without the database; this disables schema migration.

```powershell
mvn spring-boot:run
```

Check application health at `http://localhost:8080/api/health`.

Create the database once in MySQL, then configure the connection using environment variables before starting the application:

```powershell
mysql -u root -p -e "CREATE DATABASE recruitment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
$env:DB_URL = "jdbc:mysql://localhost:3306/recruitment_db?serverTimezone=UTC"
$env:DB_USERNAME = "your_local_username"
$env:DB_PASSWORD = "your_local_password"
mvn spring-boot:run
```

`DB_URL` may be set to a different JDBC URL as needed. Do not commit credentials; local secret files are excluded by `.gitignore`. Flyway is enabled by default and applies `src/main/resources/db/migration/V1__create_initial_schema.sql`. The migration creates the normalized business schema but does not insert sample users or candidate data; it seeds only the three initial role records.

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

Other routes are denied until authentication and authorization are implemented. The initial schema supports users and roles, candidates, companies, jobs and eligibility criteria, applications and status history, interviews and feedback, offers, placements, and audit logs. Business APIs and authentication remain out of scope until later phases.
