package com.recruitment.util;

import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

public final class LoginTransactionTimingListener implements TransactionExecutionListener {

    private final ThreadLocal<TimingState> currentState = new ThreadLocal<>();

    public Scope start() {
        TimingState previous = currentState.get();
        TimingState current = new TimingState();
        currentState.set(current);
        return new Scope(current, previous);
    }

    @Override
    public void beforeBegin(TransactionExecution transaction) {
        TimingState state = currentState.get();
        if (state != null && transaction.isNewTransaction()) {
            state.transaction = transaction;
            state.beginStartedAt = LoginPerformanceMetrics.startTimer();
            state.beginTiming = true;
        }
    }

    @Override
    public void afterBegin(TransactionExecution transaction, Throwable failure) {
        TimingState state = currentState.get();
        if (isTracked(state, transaction) && state.beginTiming) {
            try {
                LoginPerformanceMetrics.logDuration(
                        "rate_limit_transaction_begin_setup", state.beginStartedAt);
            } finally {
                state.beginTiming = false;
            }
        }
    }

    @Override
    public void beforeCommit(TransactionExecution transaction) {
        TimingState state = currentState.get();
        if (isTracked(state, transaction)) {
            state.commitStartedAt = LoginPerformanceMetrics.startTimer();
            state.commitTiming = true;
        }
    }

    @Override
    public void afterCommit(TransactionExecution transaction, Throwable failure) {
        TimingState state = currentState.get();
        if (isTracked(state, transaction) && state.commitTiming) {
            try {
                LoginPerformanceMetrics.logDuration(
                        failure == null
                                ? "rate_limit_transaction_commit_completion"
                                : "rate_limit_transaction_commit_failure_completion",
                        state.commitStartedAt);
            } finally {
                state.commitTiming = false;
            }
        }
    }

    @Override
    public void beforeRollback(TransactionExecution transaction) {
        TimingState state = currentState.get();
        if (isTracked(state, transaction)) {
            state.rollbackStartedAt = LoginPerformanceMetrics.startTimer();
            state.rollbackTiming = true;
        }
    }

    @Override
    public void afterRollback(TransactionExecution transaction, Throwable failure) {
        TimingState state = currentState.get();
        if (!isTracked(state, transaction)) {
            return;
        }
        boolean commitTiming = state.commitTiming;
        boolean rollbackTiming = state.rollbackTiming;
        long commitStartedAt = state.commitStartedAt;
        long rollbackStartedAt = state.rollbackStartedAt;
        state.commitTiming = false;
        state.rollbackTiming = false;
        try {
            if (commitTiming) {
                LoginPerformanceMetrics.logDuration(
                        "rate_limit_transaction_commit_failure_recovery_completion",
                        commitStartedAt);
            } else if (rollbackTiming) {
                LoginPerformanceMetrics.logDuration(
                        failure == null
                                ? "rate_limit_transaction_rollback_completion"
                                : "rate_limit_transaction_rollback_failure_completion",
                        rollbackStartedAt);
            }
        } finally {
            state.commitTiming = false;
            state.rollbackTiming = false;
        }
    }

    private static boolean isTracked(TimingState state, TransactionExecution transaction) {
        return state != null && state.transaction == transaction;
    }

    private static final class TimingState {

        private TransactionExecution transaction;
        private long beginStartedAt;
        private boolean beginTiming;
        private long commitStartedAt;
        private boolean commitTiming;
        private long rollbackStartedAt;
        private boolean rollbackTiming;

    }

    public final class Scope implements AutoCloseable {

        private final TimingState state;
        private final TimingState previous;

        private Scope(TimingState state, TimingState previous) {
            this.state = state;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (currentState.get() == state) {
                if (previous == null) {
                    currentState.remove();
                } else {
                    currentState.set(previous);
                }
            }
        }
    }
}
