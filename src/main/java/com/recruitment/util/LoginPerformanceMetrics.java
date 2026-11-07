package com.recruitment.util;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoginPerformanceMetrics {

    /** Logger used for operation-only authentication timing messages. */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(LoginPerformanceMetrics.class);

    private LoginPerformanceMetrics() {
    }

    /**
     * Starts a monotonic timer for an authentication operation.
     *
     * @return the monotonic start time in nanoseconds
     */
    public static long startTimer() {
        return System.nanoTime();
    }

    /**
     * Logs elapsed milliseconds without including request or credential data.
     *
     * @param operation fixed operation label
     * @param startNanos monotonic start time in nanoseconds
     */
    public static void logDuration(
            final String operation,
            final long startNanos) {
        long durationNanos = Math.max(
                0L,
                System.nanoTime() - startNanos);
        LOGGER.info(
                "PERF auth.login operation={} duration_ms={}",
                operation,
                TimeUnit.NANOSECONDS.toMillis(durationNanos));
    }
}
