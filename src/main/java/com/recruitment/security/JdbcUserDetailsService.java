package com.recruitment.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class JdbcUserDetailsService implements UserDetailsService {

    private static final String USER_QUERY = """
            SELECT user_id, email, password_hash, full_name,
                   (is_active AND account_status = 'APPROVED') AS is_active
            FROM users
            WHERE email = ?
            """;

    private static final String ROLE_QUERY = """
            SELECT r.role_code
            FROM roles r
            JOIN user_roles ur ON ur.role_id = r.role_id
            WHERE ur.user_id = ?
            ORDER BY r.role_code
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        UserPrincipal user;
        try {
            user = jdbcTemplate.queryForObject(USER_QUERY, USER_ROW_MAPPER, normalizedEmail);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new UsernameNotFoundException("User not found", exception);
        }

        List<GrantedAuthority> authorities = jdbcTemplate.query(
                ROLE_QUERY,
                (resultSet, rowNumber) ->
                        new SimpleGrantedAuthority("ROLE_" + resultSet.getString("role_code")),
                user.getUserId());
        return new UserPrincipal(
                user.getUserId(),
                user.getUsername(),
                user.getFullName(),
                user.getPassword(),
                user.isEnabled(),
                List.copyOf(authorities));
    }

    private static final RowMapper<UserPrincipal> USER_ROW_MAPPER = JdbcUserDetailsService::mapUser;

    private static UserPrincipal mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UserPrincipal(
                resultSet.getLong("user_id"),
                resultSet.getString("email"),
                resultSet.getString("full_name"),
                resultSet.getString("password_hash"),
                resultSet.getBoolean("is_active"),
                List.of());
    }
}
