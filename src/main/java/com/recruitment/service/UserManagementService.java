package com.recruitment.service;

import com.recruitment.dto.AuthenticatedUserResponse;
import com.recruitment.dto.CreateUserRequest;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
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
            INSERT INTO users (username, email, password_hash, full_name)
            VALUES (?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthenticatedUserResponse createUser(CreateUserRequest request) {
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

        return new AuthenticatedUserResponse(
                generatedId.longValue(),
                normalizedEmail,
                request.fullName().trim(),
                List.of(request.role().name()));
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
}
