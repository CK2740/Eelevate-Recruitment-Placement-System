package com.recruitment.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class UserPrincipal implements UserDetails {

    private final long userId;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final boolean active;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(
            long userId,
            String email,
            String fullName,
            String passwordHash,
            boolean active,
            List<GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.active = active;
        this.authorities = List.copyOf(authorities);
    }

    public long getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
