package com.recruitment.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.h2.jdbcx.JdbcDataSource;

@ExtendWith(OutputCaptureExtension.class)
class LoginTransactionTimingListenerTest {

    private final LoginTransactionTimingListener listener = new LoginTransactionTimingListener();

    @Test
    void successfulCommitLogsOnlySuccessfulCompletion(CapturedOutput output) {
        TransactionExecution transaction = newTransaction();

        try (LoginTransactionTimingListener.Scope scope = listener.start()) {
            assertThat(scope).isNotNull();
            listener.beforeBegin(transaction);
            listener.afterBegin(transaction, null);
            listener.beforeCommit(transaction);
            listener.afterCommit(transaction, null);
            listener.afterCommit(transaction, null);
        }

        assertThat(output.getOut())
                .contains("operation=rate_limit_transaction_begin_setup duration_ms=")
                .contains("operation=rate_limit_transaction_commit_completion duration_ms=")
                .doesNotContain("operation=rate_limit_transaction_commit_failure_completion");
        assertThat(count(output.getOut(), "operation=rate_limit_transaction_commit_completion"))
                .isEqualTo(1);
    }

    @Test
    void failedCommitCallbackLogsOnlyFailureCompletion(CapturedOutput output) {
        TransactionExecution transaction = newTransaction();

        try (LoginTransactionTimingListener.Scope scope = listener.start()) {
            assertThat(scope).isNotNull();
            listener.beforeBegin(transaction);
            listener.afterBegin(transaction, null);
            listener.beforeCommit(transaction);
            listener.afterCommit(transaction, new IllegalStateException("commit failed"));
            listener.afterCommit(transaction, null);
        }

        assertThat(output.getOut())
                .contains("operation=rate_limit_transaction_commit_failure_completion duration_ms=")
                .doesNotContain("operation=rate_limit_transaction_commit_completion");
        assertThat(count(
                output.getOut(),
                "operation=rate_limit_transaction_commit_failure_completion"))
                .isEqualTo(1);
    }

    @Test
    void successfulRollbackLogsOnlySuccessfulCompletion(CapturedOutput output) {
        TransactionExecution transaction = newTransaction();

        try (LoginTransactionTimingListener.Scope scope = listener.start()) {
            assertThat(scope).isNotNull();
            listener.beforeBegin(transaction);
            listener.afterBegin(transaction, null);
            listener.beforeRollback(transaction);
            listener.afterRollback(transaction, null);
            listener.afterRollback(transaction, null);
        }

        assertThat(output.getOut())
                .contains("operation=rate_limit_transaction_rollback_completion duration_ms=")
                .doesNotContain("operation=rate_limit_transaction_rollback_failure_completion");
        assertThat(count(output.getOut(), "operation=rate_limit_transaction_rollback_completion"))
                .isEqualTo(1);
    }

    @Test
    void failedRollbackCallbackLogsOnlyFailureCompletion(CapturedOutput output) {
        TransactionExecution transaction = newTransaction();

        try (LoginTransactionTimingListener.Scope scope = listener.start()) {
            assertThat(scope).isNotNull();
            listener.beforeBegin(transaction);
            listener.afterBegin(transaction, null);
            listener.beforeRollback(transaction);
            listener.afterRollback(transaction, new IllegalStateException("rollback failed"));
            listener.afterRollback(transaction, null);
        }

        assertThat(output.getOut())
                .contains("operation=rate_limit_transaction_rollback_failure_completion duration_ms=")
                .doesNotContain("operation=rate_limit_transaction_rollback_completion");
        assertThat(count(
                output.getOut(),
                "operation=rate_limit_transaction_rollback_failure_completion"))
                .isEqualTo(1);
    }

    @Test
    void commitFailureFallbackLogsOneRecoveryDuration(CapturedOutput output) {
        TransactionExecution transaction = newTransaction();

        try (LoginTransactionTimingListener.Scope scope = listener.start()) {
            assertThat(scope).isNotNull();
            listener.beforeBegin(transaction);
            listener.afterBegin(transaction, null);
            listener.beforeCommit(transaction);
            listener.afterRollback(transaction, null);
            listener.afterRollback(transaction, null);
        }

        assertThat(output.getOut())
                .contains("operation=rate_limit_transaction_commit_failure_recovery_completion duration_ms=")
                .doesNotContain("operation=rate_limit_transaction_commit_failure_completion")
                .doesNotContain("operation=rate_limit_transaction_rollback_completion")
                .doesNotContain("operation=rate_limit_transaction_rollback_failure_completion");
        assertThat(count(
                output.getOut(),
                "operation=rate_limit_transaction_commit_failure_recovery_completion"))
                .isEqualTo(1);
    }

    @Test
    void commitFailureArgumentOnFallbackIsNotMisclassifiedAsRollbackFailure(CapturedOutput output) {
        TransactionExecution transaction = newTransaction();

        try (LoginTransactionTimingListener.Scope scope = listener.start()) {
            assertThat(scope).isNotNull();
            listener.beforeBegin(transaction);
            listener.afterBegin(transaction, null);
            listener.beforeCommit(transaction);
            listener.afterRollback(transaction, new IllegalStateException("commit failed"));
        }

        assertThat(output.getOut())
                .contains("operation=rate_limit_transaction_commit_failure_recovery_completion duration_ms=")
                .doesNotContain("operation=rate_limit_transaction_rollback_failure_completion");
        assertThat(count(
                output.getOut(),
                "operation=rate_limit_transaction_commit_failure_recovery_completion"))
                .isEqualTo(1);
    }

    @Test
    void springCommitFailureWithSuccessfulFallbackRollbackLogsCombinedRecovery(CapturedOutput output) {
        TransactionTemplate transactionTemplate = transactionTemplate(false);

        assertCommitFailure(transactionTemplate);

        assertThat(output.getOut())
                .contains("operation=rate_limit_transaction_commit_failure_recovery_completion duration_ms=")
                .doesNotContain("operation=rate_limit_transaction_rollback_completion")
                .doesNotContain("operation=rate_limit_transaction_rollback_failure_completion");
        assertThat(count(
                output.getOut(),
                "operation=rate_limit_transaction_commit_failure_recovery_completion"))
                .isEqualTo(1);
    }

    @Test
    void springCommitFailureWithFailedFallbackRollbackLogsSingleCombinedRecoveryDuration(CapturedOutput output) {
        TransactionTemplate transactionTemplate = transactionTemplate(true);

        assertCommitFailure(transactionTemplate);

        assertThat(output.getOut())
                .contains("operation=rate_limit_transaction_commit_failure_recovery_completion duration_ms=")
                .doesNotContain("operation=rate_limit_transaction_rollback_completion")
                .doesNotContain("operation=rate_limit_transaction_rollback_failure_completion");
        assertThat(count(
                output.getOut(),
                "operation=rate_limit_transaction_commit_failure_recovery_completion"))
                .isEqualTo(1);
    }

    @Test
    void transactionsOutsideTheLoginScopeAreNotTimed(CapturedOutput output) {
        TransactionExecution transaction = newTransaction();

        listener.beforeBegin(transaction);
        listener.afterBegin(transaction, null);
        listener.beforeCommit(transaction);
        listener.afterCommit(transaction, null);
        listener.beforeRollback(transaction);
        listener.afterRollback(transaction, null);

        assertThat(output.getOut())
                .doesNotContain("rate_limit_transaction_begin_setup")
                .doesNotContain("rate_limit_transaction_commit_")
                .doesNotContain("rate_limit_transaction_rollback_");
    }

    private static TransactionExecution newTransaction() {
        TransactionExecution transaction = mock(TransactionExecution.class);
        when(transaction.isNewTransaction()).thenReturn(true);
        return transaction;
    }

    private TransactionTemplate transactionTemplate(boolean failRollback) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:login-timing-" + UUID.randomUUID());
        dataSource.setUser("sa");
        dataSource.setPassword("");

        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource) {
            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                throw new TransactionSystemException("commit failed");
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                if (failRollback) {
                    throw new TransactionSystemException("rollback failed");
                }
                super.doRollback(status);
            }
        };
        transactionManager.setRollbackOnCommitFailure(true);
        transactionManager.setTransactionExecutionListeners(List.of(listener));
        return new TransactionTemplate(transactionManager);
    }

    private void assertCommitFailure(TransactionTemplate transactionTemplate) {
        try (LoginTransactionTimingListener.Scope scope = listener.start()) {
            assertThat(scope).isNotNull();
            TransactionSystemException failure = assertThrows(
                    TransactionSystemException.class,
                    () -> transactionTemplate.execute(status -> null));
            assertThat(failure).hasMessageContaining("failed");
        }
    }

    private static long count(String value, String substring) {
        return value.split(java.util.regex.Pattern.quote(substring), -1).length - 1;
    }
}
