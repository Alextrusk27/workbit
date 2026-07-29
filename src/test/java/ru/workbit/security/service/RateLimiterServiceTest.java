package ru.workbit.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.exception.TooManyRequestsException;
import ru.workbit.security.config.RateLimitProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimiterServiceTest")
class RateLimiterServiceTest {

    private static final String KEY = "127.0.0.1";
    private static final int LIMIT = 3;

    private RateLimiterService aRateLimiter(int limit, Duration window) {
        return new RateLimiterService(new RateLimitProperties(limit, window, null, null, null));
    }

    @Nested
    @DisplayName("Check")
    class Check {

        @Test
        @DisplayName("Не бросает исключение, пока число вызовов не превышает лимит")
        void doesNotThrowUpToLimit() {
            // given
            var service = aRateLimiter(LIMIT, Duration.ofMinutes(1));

            // when / then
            assertThatCode(() -> {
                for (int i = 0; i < LIMIT; i++) {
                    service.check(KEY);
                }
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Бросает TooManyRequestsException при превышении лимита в том же окне")
        void throwsWhenLimitExceededInSameWindow() {
            // given
            var service = aRateLimiter(LIMIT, Duration.ofMinutes(1));
            for (int i = 0; i < LIMIT; i++) {
                service.check(KEY);
            }

            // when / then
            assertThatThrownBy(() -> service.check(KEY))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage("Too many requests");
        }

        @Test
        @DisplayName("Разные ключи имеют независимые счётчики")
        void independentCountersPerKey() {
            // given
            var service = aRateLimiter(LIMIT, Duration.ofMinutes(1));
            for (int i = 0; i < LIMIT; i++) {
                service.check("key-a");
            }

            // when / then
            assertThatCode(() -> service.check("key-b")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Счётчик сбрасывается после истечения окна")
        void resetsCounterAfterWindowExpires() throws InterruptedException {
            // given
            var service = aRateLimiter(LIMIT, Duration.ofMillis(50));
            for (int i = 0; i < LIMIT; i++) {
                service.check(KEY);
            }
            Thread.sleep(200);

            // when / then
            assertThatCode(() -> service.check(KEY)).doesNotThrowAnyException();
        }
    }
}
