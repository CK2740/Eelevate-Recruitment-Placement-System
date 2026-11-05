package com.recruitment.dto;

import com.recruitment.model.RoleCode;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull RoleCode role) {
}
