# Recruitment and Placement Management System

A Java 21 and Spring Boot application for managing recruitment and placement workflows. Phase 2 adds the initial MySQL schema through Flyway, and Phase 3 adds session-based authentication and authorization.

## Technology

- Java 21
- Spring Boot 3.5.16 and Maven
- Spring Web, Spring JDBC, Spring Security, and Bean Validation
- MySQL Connector/J and Flyway
- React, Vite, and JavaScript for the frontend

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

The application requires a reachable MySQL database at startup while Flyway is enabled. Flyway applies the versioned schema and data migrations automatically, beginning with the Phase 2 initial schema. Set `FLYWAY_ENABLED=false` only when intentionally starting without the database; this disables schema migration.

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

## Frontend

The React + Vite frontend is in `frontend/`; its production build is served from Spring Boot's static resources. The role shown by the UI never determines backend permissions.

## Authentication setup

The authentication API uses a server-managed session. Obtain a CSRF token from `GET /api/auth/csrf` before sending registration, login, logout, or user-creation requests. Send the returned token in the response's `headerName` header and retain cookies. Session cookies are HttpOnly, SameSite=Lax, and Secure by default; for local HTTP development only, set `$env:SESSION_COOKIE_SECURE = "false"`.

The application has exactly three active roles: `ADMIN`, `RECRUITER`, and `CANDIDATE`. Flyway migration V7 adds `CANDIDATE`, safely assigns former `PLACEMENT_OFFICER` users to `RECRUITER` without changing their user IDs or account credentials, and removes the retired role links and role record. All existing user and recruitment records are preserved.

Public registration offers Candidate and Recruiter account types. Candidate registrations receive only `CANDIDATE`, become active immediately, and are authenticated through the existing CSRF-protected session flow. Recruiter registrations receive only `RECRUITER`, remain `PENDING_APPROVAL`, and are not authenticated until an Admin approves them. The Admin dashboard lists pending recruiter requests; Admins can approve or reject them. Rejections remain inactive. Decisions are recorded in the audit log. Public requests cannot create `ADMIN` accounts or set arbitrary roles. Passwords must be at least 12 characters, are BCrypt-hashed, and duplicate email addresses return a conflict response.

Migration V7 adds the Candidate role and account approval state, changes existing `PLACEMENT_OFFICER` role assignments to `RECRUITER` without changing user IDs or account credentials, and removes the retired role. Migration V8 creates Candidate Portal profile links and metadata, seeding profiles for existing Candidate accounts when possible. Candidate Portal APIs derive ownership from the authenticated session; the browser never supplies candidate IDs for profile, application, interview, offer, or resume access. Candidates can update education and contact details, upload a private PDF/DOC/DOCX resume (maximum 10 MB), browse open jobs, apply, and review their own application history, interviews, and offers.

Resume files are stored outside static web resources in `./data/resumes` by default. Set `CANDIDATE_RESUME_STORAGE_DIR` to a persistent, access-restricted directory in deployments; back up this directory along with the database. Resume downloads are served only through authenticated Candidate Portal endpoints.

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

Bootstrap creates an administrator only when the users table is empty. The password is stored as a BCrypt hash; the environment variables should be removed after initialization. Creating users with other roles through `POST /api/auth/users` remains restricted to administrators.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Returns application status and timestamp; no authentication required |
| GET | `/api/auth/csrf` | Issues a CSRF token for session-authenticated requests |
| POST | `/api/auth/register` | Public Candidate or Recruiter registration; candidates are authenticated immediately, recruiters await Admin approval |
| POST | `/api/auth/login` | Authenticates email/password and creates a server session |
| POST | `/api/auth/logout` | Invalidates the current session |
| GET | `/api/auth/me` | Returns the authenticated user's identity and server-assigned roles |
| POST | `/api/auth/users` | Creates a user; requires the `ADMIN` role |
| GET | `/api/auth/users` | Lists accounts without password data; requires `ADMIN` |
| PATCH | `/api/auth/users/{userId}/role` | Assigns one of `ADMIN`, `RECRUITER`, or `CANDIDATE`; requires `ADMIN` |
| GET | `/api/auth/admin/recruiters/pending` | Lists recruiter registration requests awaiting approval; requires `ADMIN` |
| POST | `/api/auth/admin/recruiters/{id}/approval` | Approves or rejects a pending recruiter; requires `ADMIN` |
| POST | `/api/candidates` | Creates a candidate; requires `ADMIN` or `RECRUITER` |
| GET | `/api/candidates/{id}` | Returns a candidate; requires `ADMIN` or `RECRUITER` |
| GET | `/api/candidates` | Returns a paginated, searchable, filterable candidate list |
| PUT | `/api/candidates/{id}` | Updates candidate details; requires `ADMIN` or `RECRUITER` |
| PATCH | `/api/candidates/{id}/status` | Changes active status; requires `ADMIN` or `RECRUITER` |
| DELETE | `/api/candidates/{id}` | Deletes an unlinked candidate; requires `ADMIN` or `RECRUITER` |
| GET | `/api/candidates/{id}/resume` | Downloads a candidate resume; requires `ADMIN` or `RECRUITER` |
| GET / PUT | `/api/portal/profile` | Read or update the authenticated Candidate's profile |
| POST / GET | `/api/portal/resume` | Upload or download the authenticated Candidate's private resume |
| GET | `/api/portal/jobs` | Browse open, non-expired jobs with search and filters |
| POST | `/api/portal/jobs/{jobId}/applications` | Apply as the authenticated Candidate; eligibility and duplicate checks use existing application logic |
| GET | `/api/portal/applications` | List the authenticated Candidate's applications |
| GET | `/api/portal/applications/{applicationId}/history` | View history only for an application owned by the authenticated Candidate |
| GET | `/api/portal/interviews` | List the authenticated Candidate's interviews |
| GET | `/api/portal/offers` | List the authenticated Candidate's offers |
| GET | `/api/portal/notifications` | List recent application, interview, and offer updates for the authenticated Candidate |

The role a client displays or submits is never used to grant permissions. Roles are read from the database and applied by Spring Security.

Candidate listing accepts optional `search`, `name`, `email`, `technology`, `passingYear`, and `status` (`ACTIVE` or `INACTIVE`) query parameters, plus zero-based `page` (default `0`) and `size` (default `20`, maximum `100`). A candidate with linked applications cannot be deleted.
