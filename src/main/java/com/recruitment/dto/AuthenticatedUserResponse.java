package com.recruitment.dto;

import com.recruitment.security.UserPrincipal;
import java.util.List;

public record AuthenticatedUserResponse(long userId, String email, String fullName, List<String> roles) {

    public static AuthenticatedUserResponse from(UserPrincipal principal) {
        List<String> roles = principal.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority().substring("ROLE_".length()))
                .sorted()
                .toList();
        return new AuthenticatedUserResponse(
                principal.getUserId(),
                principal.getUsername(),
                principal.getFullName(),
                roles);
    }
}
