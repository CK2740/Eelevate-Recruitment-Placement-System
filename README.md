# Recruitment and Placement Management System

A Java 21 and Spring Boot application for managing recruitment and placement workflows. Phase 2 adds the initial MySQL schema through Flyway, and Phase 3 adds session-based authentication and authorization.

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

## Authentication setup

The authentication API uses a server-managed session. Obtain a CSRF token from `GET /api/auth/csrf` before sending login, logout, or user-creation requests. Send the returned token in the response's `headerName` header and retain cookies. Session cookies are HttpOnly, SameSite=Lax, and Secure by default; for local HTTP development only, set `$env:SESSION_COOKIE_SECURE = "false"`.

The first administrator can be initialized once by explicitly enabling bootstrap and supplying environment variables before application startup:

```powershell
$env:BOOTSTRAP_ADMIN_ENABLED = "true"
$env:BOOTSTRAP_ADMIN_EMAIL = "admin@example.com"
$env:BOOTSTRAP_ADMIN_NAME = "System Administrator"
# Generate a unique bootstrap password for this installation.
$bootstrapBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bootstrapBytes)
$env:BOOTSTRAP_ADMIN_PASSWORD = [Convert]::ToBase64String($bootstrapBytes)
$rng.Dispose()
Remove-Variable bootstrapBytes, rng
mvn spring-boot:run
```

Bootstrap creates an administrator only when the users table is empty. The password is stored as a BCrypt hash; the environment variables should be removed after initialization. User creation through the API is restricted to administrators.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Returns application status and timestamp; no authentication required |
| GET | `/api/auth/csrf` | Issues a CSRF token for session-authenticated requests |
| POST | `/api/auth/login` | Authenticates email/password and creates a server session |
| POST | `/api/auth/logout` | Invalidates the current session |
| GET | `/api/auth/me` | Returns the authenticated user's identity and server-assigned roles |
| POST | `/api/auth/users` | Creates a user; requires the `ADMIN` role |

The role a client displays or submits is never used to grant permissions. Roles are read from the database and applied by Spring Security.
