package ru.workbit.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.workbit.exception.LlmException;

@DisplayName("SingleFlightTest")
class SingleFlightTest {

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void waitUntilWaiting(Thread thread) {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING) {
                return;
            }
            if (state == Thread.State.TERMINATED) {
                throw new AssertionError("Joiner thread terminated before entering WAITING state");
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Joiner thread did not reach WAITING state within deadline");
    }

    private static Throwable thrownBy(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            return t;
        }
        throw new AssertionError("Expected an exception to be thrown");
    }

    @Nested
    @DisplayName("Run")
    class Run {

        private final SingleFlight singleFlight = new SingleFlight();

        @Test
        @DisplayName("Присоединившийся ждёт лидера и получает тот же результат, задача выполняется один раз")
        void joinerGetsSameResultAsLeaderTaskCalledOnce()
                throws InterruptedException, ExecutionException, TimeoutException {
            // given
            SingleFlight.Key key = new SingleFlight.Key("scope", "leader-join");
            AtomicInteger callCount = new AtomicInteger();
            CountDownLatch taskStarted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Object leaderResultValue = new Object();

            CompletableFuture<Object> leaderFuture = CompletableFuture.supplyAsync(() ->
                    singleFlight.run(key, () -> {
                        callCount.incrementAndGet();
                        taskStarted.countDown();
                        awaitLatch(release);
                        return leaderResultValue;
                    }));
            assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue();

            AtomicReference<Object> joinerResult = new AtomicReference<>();
            Thread joiner = Thread.ofPlatform().start(() ->
                    joinerResult.set(singleFlight.run(key, () -> {
                        callCount.incrementAndGet();
                        return new Object();
                    })));
            waitUntilWaiting(joiner);

            // when
            release.countDown();
            Object leaderResult = leaderFuture.get(5, TimeUnit.SECONDS);
            joiner.join(5000);

            // then
            assertThat(callCount.get()).isEqualTo(1);
            assertThat(leaderResult).isSameAs(leaderResultValue);
            assertThat(joinerResult.get()).isSameAs(leaderResultValue);
        }

        @Test
        @DisplayName("Исключение лидера доходит до присоединившегося тем же экземпляром, не обёрнутым в CompletionException")
        void joinerGetsSameExceptionInstanceAsLeader()
                throws InterruptedException, ExecutionException, TimeoutException {
            // given
            SingleFlight.Key key = new SingleFlight.Key("scope", "leader-failure");
            LlmException failure = new LlmException("boom");
            CountDownLatch taskStarted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);

            CompletableFuture<Throwable> leaderThrownFuture = CompletableFuture.supplyAsync(() ->
                    thrownBy(() -> singleFlight.run(key, () -> {
                        taskStarted.countDown();
                        awaitLatch(release);
                        throw failure;
                    })));
            assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue();

            AtomicReference<Throwable> joinerThrown = new AtomicReference<>();
            Thread joiner = Thread.ofPlatform().start(() ->
                    joinerThrown.set(thrownBy(() -> singleFlight.run(key, () -> "unused"))));
            waitUntilWaiting(joiner);

            // when
            release.countDown();
            Throwable leaderThrown = leaderThrownFuture.get(5, TimeUnit.SECONDS);
            joiner.join(5000);

            // then
            assertThat(leaderThrown).isSameAs(failure);
            assertThat(joinerThrown.get()).isSameAs(failure);
        }

        @Test
        @DisplayName("После завершения лидера ключ снимается - следующий вызов с тем же ключом выполняет задачу снова")
        void keyIsReleasedAfterCompletionSoNextCallRunsTaskAgain() {
            // given
            SingleFlight.Key key = new SingleFlight.Key("scope", "released-key");
            AtomicInteger callCount = new AtomicInteger();

            // when
            String first = singleFlight.run(key, () -> {
                callCount.incrementAndGet();
                return "first";
            });
            String second = singleFlight.run(key, () -> {
                callCount.incrementAndGet();
                return "second";
            });

            // then
            assertThat(callCount.get()).isEqualTo(2);
            assertThat(first).isEqualTo("first");
            assertThat(second).isEqualTo("second");
        }

        @Test
        @DisplayName("Разные ключи не ждут друг друга")
        void differentKeysDoNotWaitForEachOther() throws InterruptedException, ExecutionException, TimeoutException {
            // given
            SingleFlight.Key keyA = new SingleFlight.Key("scope", "a");
            SingleFlight.Key keyB = new SingleFlight.Key("scope", "b");
            CountDownLatch taskAStarted = new CountDownLatch(1);
            CountDownLatch releaseA = new CountDownLatch(1);

            CompletableFuture<String> resultAFuture = CompletableFuture.supplyAsync(() ->
                    singleFlight.run(keyA, () -> {
                        taskAStarted.countDown();
                        awaitLatch(releaseA);
                        return "a";
                    }));
            assertThat(taskAStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            String resultB = singleFlight.run(keyB, () -> "b");

            // then
            assertThat(resultB).isEqualTo("b");
            assertThat(resultAFuture.isDone()).isFalse();

            releaseA.countDown();
            assertThat(resultAFuture.get(5, TimeUnit.SECONDS)).isEqualTo("a");
        }
    }
}
