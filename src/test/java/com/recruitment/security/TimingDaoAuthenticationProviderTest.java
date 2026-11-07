package com.recruitment.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(OutputCaptureExtension.class)
class TimingDaoAuthenticationProviderTest {

    @Test
    void successfulAuthenticationLogsProviderChecksAndSuccessProcessing(CapturedOutput output)
            throws Exception {
        String username = "provider-test-user";
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername(username))
                .thenReturn(User.withUsername(username)
                        .password("stored-password-hash")
                        .authorities("ROLE_CANDIDATE")
                        .build());
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.matches("raw-password", "stored-password-hash")).thenReturn(true);

        TimingDaoAuthenticationProvider provider = new TimingDaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.afterPropertiesSet();

        Authentication result = provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, "raw-password"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getAuthorities()).extracting("authority").containsExactly("ROLE_CANDIDATE");
        verify(passwordEncoder, times(1)).matches("raw-password", "stored-password-hash");
        assertThat(output)
                .contains("operation=provider_pre_authentication_checks duration_ms=")
                .contains("operation=provider_post_authentication_checks duration_ms=")
                .contains("operation=provider_success_authentication duration_ms=")
                .doesNotContain(username, "raw-password", "stored-password-hash");
    }

    @Test
    void failedPreAuthenticationCheckIsTimedAndPreservesProviderCredentialCheck(CapturedOutput output)
            throws Exception {
        String username = "disabled-provider-user";
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername(username))
                .thenReturn(User.withUsername(username)
                        .password("stored-password-hash")
                        .authorities("ROLE_CANDIDATE")
                        .disabled(true)
                        .build());
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        TimingDaoAuthenticationProvider provider = new TimingDaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.afterPropertiesSet();

        DisabledException exception = assertThrows(DisabledException.class, () -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, "raw-password")));

        assertThat(exception).hasMessage("User is disabled");
        verify(passwordEncoder, times(1)).matches("raw-password", "stored-password-hash");
        assertThat(output)
                .contains("operation=provider_pre_authentication_checks duration_ms=")
                .doesNotContain("operation=provider_post_authentication_checks")
                .doesNotContain("operation=provider_success_authentication")
                .doesNotContain(username, "raw-password", "stored-password-hash");
    }
}
