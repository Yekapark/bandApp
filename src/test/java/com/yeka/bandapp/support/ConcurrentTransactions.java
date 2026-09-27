package com.yeka.bandapp.support;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 잠금 대기를 확인하고 첫 트랜잭션을 커밋한다. 두 번째 요청의 도메인 오류만 결과로 돌려준다. */
public final class ConcurrentTransactions {
    private ConcurrentTransactions() { }

    public static ErrorCode overlap(PlatformTransactionManager transactions, JdbcTemplate jdbc,
                                    Runnable firstAction, Runnable secondAction) throws Exception {
        var firstReady = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var secondReady = new CountDownLatch(1);
        var secondPid = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        Future<?> first = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            firstAction.run();
            firstReady.countDown();
            await(commit);
        }));
        try {
            await(firstReady);
            Future<ErrorCode> second = pool.submit(() -> {
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(status -> {
                        secondPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                        secondReady.countDown();
                        secondAction.run();
                    });
                    return null;
                } catch (BusinessException e) {
                    return e.errorCode();
                }
            });
            await(secondReady);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean blocked = false;
            while (!blocked && System.nanoTime() < deadline) {
                assertThat(second.isDone()).as("첫 트랜잭션 커밋까지 기다려야 한다").isFalse();
                blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                        "select wait_event_type = 'Lock' from pg_stat_activity where pid = ?",
                        Boolean.class, secondPid.get()));
                if (!blocked) Thread.sleep(20);
            }
            assertThat(blocked).as("실제 DB 잠금 대기").isTrue();
            commit.countDown();
            first.get(15, TimeUnit.SECONDS);
            return second.get(15, TimeUnit.SECONDS);
        } finally {
            commit.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
