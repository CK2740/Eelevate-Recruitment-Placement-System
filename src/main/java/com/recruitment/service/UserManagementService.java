package com.recruitment.service;

import com.recruitment.dto.AuthenticatedUserResponse;
import com.recruitment.dto.CreateUserRequest;
import com.recruitment.dto.PublicAccountType;
import com.recruitment.dto.PublicRegistrationRequest;
import com.recruitment.dto.RecruiterApprovalRequest;
import com.recruitment.dto.RecruiterRegistrationResponse;
import com.recruitment.dto.UpdateUserRoleRequest;
import com.recruitment.dto.UserManagementPageResponse;
import com.recruitment.dto.UserManagementResponse;
import com.recruitment.model.RoleCode;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class UserManagementService {

    private static final String INSERT_USER = """
            INSERT INTO users (username, email, password_hash, full_name, is_active, account_status)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserManagementService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AuthenticatedUserResponse createUser(CreateUserRequest request) {
        return insertUser(request, true, "APPROVED");
    }

    @Transactional
    public AuthenticatedUserResponse registerPublic(PublicRegistrationRequest request) {
        RoleCode role = request.accountType() == PublicAccountType.CANDIDATE
                ? RoleCode.CANDIDATE
                : RoleCode.RECRUITER;
        boolean active = role == RoleCode.CANDIDATE;
        String accountStatus = active ? "APPROVED" : "PENDING_APPROVAL";
        AuthenticatedUserResponse created = insertUser(
                new CreateUserRequest(request.email(), request.fullName(), request.password(), role),
                active,
                accountStatus);
        auditLogService.log(
                created.userId(),
                active ? "CANDIDATE_REGISTERED" : "RECRUITER_REGISTRATION_SUBMITTED",
                "AUTHENTICATION",
                created.userId(),
                Map.of("accountStatus", accountStatus));
        return created;
    }

    private AuthenticatedUserResponse insertUser(
            CreateUserRequest request,
            boolean active,
            String accountStatus) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        ensureBcryptPasswordLength(request.password());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(INSERT_USER, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, normalizedEmail);
                statement.setString(2, normalizedEmail);
                statement.setString(3, passwordEncoder.encode(request.password()));
                statement.setString(4, request.fullName().trim());
                statement.setBoolean(5, active);
                statement.setString(6, accountStatus);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
        }

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("The database did not return the created user ID");
        }
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT role_id FROM roles WHERE role_code = ?",
                Long.class,
                request.role().name());
        if (roleId == null) {
            throw new IllegalStateException("The requested role is not configured");
        }
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                generatedId.longValue(),
                roleId);
        if (request.role() == RoleCode.CANDIDATE) {
            jdbcTemplate.update("""
                    INSERT INTO candidate_portal_profiles (user_id, candidate_id)
                    VALUES (?, (SELECT candidate_id FROM candidates WHERE LOWER(email) = LOWER(?)))
                    """,
                    generatedId.longValue(),
                    normalizedEmail);
        }

        return new AuthenticatedUserResponse(
                generatedId.longValue(),
                normalizedEmail,
                request.fullName().trim(),
                List.of(request.role().name()));
    }

    @Transactional(readOnly = true)
    public List<RecruiterRegistrationResponse> getPendingRecruiters() {
        return jdbcTemplate.query("""
                SELECT u.user_id, u.email, u.full_name, u.created_at
                FROM users u
                JOIN user_roles ur ON ur.user_id = u.user_id
                JOIN roles r ON r.role_id = ur.role_id
                WHERE r.role_code = 'RECRUITER'
                  AND u.account_status = 'PENDING_APPROVAL'
                ORDER BY u.created_at, u.user_id
                """, (resultSet, rowNumber) -> new RecruiterRegistrationResponse(
                resultSet.getLong("user_id"),
                resultSet.getString("email"),
                resultSet.getString("full_name"),
                resultSet.getTimestamp("created_at").toLocalDateTime()));
    }

    @Transactional(readOnly = true)
    public UserManagementPageResponse getUsers(int page, int size) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        List<UserManagementResponse> users = jdbcTemplate.query("""
                SELECT u.user_id, u.email, u.full_name, u.is_active, u.account_status,
                       u.created_at, GROUP_CONCAT(r.role_code ORDER BY r.role_code SEPARATOR ',') AS roles
                FROM users u
                LEFT JOIN user_roles ur ON ur.user_id = u.user_id
                LEFT JOIN roles r ON r.role_id = ur.role_id
                GROUP BY u.user_id, u.email, u.full_name, u.is_active, u.account_status, u.created_at
                ORDER BY u.created_at DESC, u.user_id DESC
                LIMIT ? OFFSET ?
                """,
                (resultSet, rowNumber) -> new UserManagementResponse(
                        resultSet.getLong("user_id"),
                        resultSet.getString("email"),
                        resultSet.getString("full_name"),
                        resultSet.getString("roles") == null
                                ? List.of()
                                : List.of(resultSet.getString("roles").split(",")),
                        resultSet.getBoolean("is_active"),
                        resultSet.getString("account_status"),
                        resultSet.getTimestamp("created_at").toLocalDateTime()),
                size,
                (long) page * size);
        long count = total == null ? 0 : total;
        return new UserManagementPageResponse(
                users, page, size, count, (int) Math.ceil((double) count / size));
    }

    @Transactional
    public UserManagementResponse updateUserRole(
            long userId,
            UpdateUserRoleRequest request,
            long adminUserId) {
        UserAccountState account;
        try {
            account = jdbcTemplate.queryForObject("""
                    SELECT account_status, is_active
                    FROM users
                    WHERE user_id = ?
                    FOR UPDATE
                    """,
                    (resultSet, rowNumber) -> new UserAccountState(
                            resultSet.getString("account_status"),
                            resultSet.getBoolean("is_active")),
                    userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found");
        }
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found");
        }
        if ("PENDING_APPROVAL".equals(account.status())
                && request.role() != RoleCode.RECRUITER) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pending recruiter requests must be approved or rejected before changing their role");
        }

        List<String> previousRoles = jdbcTemplate.query("""
                SELECT r.role_code
                FROM user_roles ur
                JOIN roles r ON r.role_id = ur.role_id
                WHERE ur.user_id = ?
                """,
                (resultSet, rowNumber) -> resultSet.getString("role_code"),
                userId);
        if (account.active()
                && "APPROVED".equals(account.status())
                && previousRoles.contains(RoleCode.ADMIN.name())
                && request.role() != RoleCode.ADMIN) {
            List<Long> activeAdministrators = jdbcTemplate.query("""
                    SELECT u.user_id
                    FROM users u
                    JOIN user_roles ur ON ur.user_id = u.user_id
                    JOIN roles r ON r.role_id = ur.role_id
                    WHERE r.role_code = 'ADMIN'
                      AND u.is_active = TRUE
                      AND u.account_status = 'APPROVED'
                    FOR UPDATE
                    """,
                    (resultSet, rowNumber) -> resultSet.getLong("user_id"));
            if (activeAdministrators.size() <= 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The last active administrator cannot be assigned a different role");
            }
        }

        if (!previousRoles.equals(List.of(request.role().name()))) {
            Long roleId = jdbcTemplate.queryForObject(
                    "SELECT role_id FROM roles WHERE role_code = ?",
                    Long.class,
                    request.role().name());
            if (roleId == null) {
                throw new IllegalStateException("The requested role is not configured");
            }
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId);
            jdbcTemplate.update(
                    "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                    userId,
                    roleId);
            if (request.role() == RoleCode.CANDIDATE) {
                jdbcTemplate.update("""
                        INSERT IGNORE INTO candidate_portal_profiles (user_id, candidate_id)
                        SELECT u.user_id,
                               (SELECT c.candidate_id FROM candidates c
                                WHERE LOWER(c.email) = LOWER(u.email))
                        FROM users u
                        WHERE u.user_id = ?
                        """,
                        userId);
            }
            auditLogService.log(
                    adminUserId,
                    "USER_ROLE_CHANGED",
                    "USER",
                    userId,
                    Map.of("previousRoles", previousRoles, "newRole", request.role().name()));
        }
        return findUser(userId);
    }

    private UserManagementResponse findUser(long userId) {
        return jdbcTemplate.queryForObject("""
                SELECT u.user_id, u.email, u.full_name, u.is_active, u.account_status,
                       u.created_at, GROUP_CONCAT(r.role_code ORDER BY r.role_code SEPARATOR ',') AS roles
                FROM users u
                LEFT JOIN user_roles ur ON ur.user_id = u.user_id
                LEFT JOIN roles r ON r.role_id = ur.role_id
                WHERE u.user_id = ?
                GROUP BY u.user_id, u.email, u.full_name, u.is_active, u.account_status, u.created_at
                """,
                (resultSet, rowNumber) -> new UserManagementResponse(
                        resultSet.getLong("user_id"),
                        resultSet.getString("email"),
                        resultSet.getString("full_name"),
                        resultSet.getString("roles") == null
                                ? List.of()
                                : List.of(resultSet.getString("roles").split(",")),
                        resultSet.getBoolean("is_active"),
                        resultSet.getString("account_status"),
                        resultSet.getTimestamp("created_at").toLocalDateTime()),
                userId);
    }

    @Transactional
    public void reviewRecruiter(
            long recruiterId,
            long adminUserId,
            RecruiterApprovalRequest.Decision decision) {
        boolean approved = decision == RecruiterApprovalRequest.Decision.APPROVE;
        int updated = jdbcTemplate.update("""
                UPDATE users u
                JOIN user_roles ur ON ur.user_id = u.user_id
                JOIN roles r ON r.role_id = ur.role_id
                SET u.is_active = ?, u.account_status = ?, u.updated_at = CURRENT_TIMESTAMP
                WHERE u.user_id = ?
                  AND r.role_code = 'RECRUITER'
                  AND u.account_status = 'PENDING_APPROVAL'
                """, approved, approved ? "APPROVED" : "REJECTED", recruiterId);
        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Pending recruiter registration was not found");
        }
        auditLogService.log(
                adminUserId,
                approved ? "RECRUITER_REGISTRATION_APPROVED" : "RECRUITER_REGISTRATION_REJECTED",
                "USER",
                recruiterId,
                Map.of("decision", decision.name()));
    }

    public boolean hasAnyUsers() {
        Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        return userCount != null && userCount > 0;
    }

    private static void ensureBcryptPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must not exceed 72 UTF-8 bytes");
        }

    }

    private record UserAccountState(String status, boolean active) {
    }
}
