package com.recruitment.service;

import com.recruitment.dto.CreateUserRequest;
import com.recruitment.model.RoleCode;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.security.bootstrap-admin.enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final Environment environment;
    private final UserManagementService userManagementService;

    public BootstrapAdminInitializer(
            Environment environment,
            UserManagementService userManagementService) {
        this.environment = environment;
        this.userManagementService = userManagementService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (userManagementService.hasAnyUsers()) {
            return;
        }

        String email = requiredEnvironmentVariable("BOOTSTRAP_ADMIN_EMAIL");
        String fullName = requiredEnvironmentVariable("BOOTSTRAP_ADMIN_NAME");
        String password = requiredEnvironmentVariable("BOOTSTRAP_ADMIN_PASSWORD");
        if (password.getBytes(StandardCharsets.UTF_8).length < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Bootstrap administrator password must be 12-72 UTF-8 bytes");
        }
        userManagementService.createUser(new CreateUserRequest(
                email,
                fullName,
                password,
                RoleCode.ADMIN));
    }

    private String requiredEnvironmentVariable(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap administrator initialization requires environment variable " + name);
        }
        return value;
    }
}
