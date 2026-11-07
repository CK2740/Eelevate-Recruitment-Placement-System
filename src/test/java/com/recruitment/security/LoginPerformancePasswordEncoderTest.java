package com.recruitment.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class LoginPerformancePasswordEncoderTest {

    @Mock
    private PasswordEncoder delegate;

    @Test
    void matchesDelegatesAndLogsOnlyOperationAndDuration(CapturedOutput output) {
        when(delegate.matches("sensitive-password", "sensitive-hash")).thenReturn(true);
        LoginPerformancePasswordEncoder passwordEncoder = new LoginPerformancePasswordEncoder(delegate);

        assertThat(passwordEncoder.matches("sensitive-password", "sensitive-hash")).isTrue();

        verify(delegate).matches("sensitive-password", "sensitive-hash");
        assertThat(output)
                .contains("PERF auth.login operation=bcrypt_password_match duration_ms=")
                .doesNotContain("sensitive-password", "sensitive-hash");
    }

    @Test
    void failedMatchStillLogsDuration(CapturedOutput output) {
        when(delegate.matches("sensitive-password", "sensitive-hash"))
                .thenThrow(new IllegalArgumentException("match failed"));
        LoginPerformancePasswordEncoder passwordEncoder = new LoginPerformancePasswordEncoder(delegate);

        assertThrows(
                IllegalArgumentException.class,
                () -> passwordEncoder.matches("sensitive-password", "sensitive-hash"));

        assertThat(output)
                .contains("PERF auth.login operation=bcrypt_password_match duration_ms=")
                .doesNotContain("sensitive-password", "sensitive-hash");
    }

    @Test
    void nonMatchMethodsDelegateWithoutChangingTheirBehavior() {
        when(delegate.encode("password")).thenReturn("encoded");
        when(delegate.upgradeEncoding("encoded")).thenReturn(true);
        LoginPerformancePasswordEncoder passwordEncoder = new LoginPerformancePasswordEncoder(delegate);

        assertThat(passwordEncoder.encode("password")).isEqualTo("encoded");
        assertThat(passwordEncoder.upgradeEncoding("encoded")).isTrue();

        verify(delegate).encode("password");
        verify(delegate).upgradeEncoding("encoded");
    }
}
