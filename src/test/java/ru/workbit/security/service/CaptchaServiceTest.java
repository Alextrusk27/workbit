package ru.workbit.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.security.config.CaptchaProperties;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("CaptchaServiceTest")
class CaptchaServiceTest {

    private static final String VALIDATE_URL = "https://smartcaptcha.yandexcloud.net/validate";
    private static final String SERVER_KEY = "server-key";
    private static final String TOKEN = "captcha-token";
    private static final String IP = "127.0.0.1";

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(VALIDATE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
    }

    private CaptchaService aService(boolean enabled) {
        var properties = new CaptchaProperties(enabled, SERVER_KEY, VALIDATE_URL);
        return new CaptchaService(properties, builder.build());
    }

    @Nested
    @DisplayName("Validate")
    class Validate {

        @Test
        @DisplayName("Ничего не делает и не обращается к HTTP-клиенту, если капча выключена")
        void doesNothingWhenDisabled() {
            // given
            var service = aService(false);

            // when / then
            assertThatCode(() -> service.validate(TOKEN, IP)).doesNotThrowAnyException();
            mockServer.verify();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        @DisplayName("Бросает ForbiddenException, если токен null или пустой, не обращаясь к HTTP-клиенту")
        void throwsForbiddenWhenTokenBlank(String token) {
            // given
            var service = aService(true);

            // when / then
            assertThatThrownBy(() -> service.validate(token, IP))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Captcha validation failed");
            mockServer.verify();
        }

        @Test
        @DisplayName("Проходит без исключения, когда Яндекс отвечает статусом ok")
        void passesWhenYandexRespondsOk() {
            // given
            mockServer.expect(requestTo(VALIDATE_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(content().formDataContains(Map.of(
                            "secret", SERVER_KEY,
                            "token", TOKEN,
                            "ip", IP
                    )))
                    .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
            var service = aService(true);

            // when / then
            assertThatCode(() -> service.validate(TOKEN, IP)).doesNotThrowAnyException();
            mockServer.verify();
        }

        @Test
        @DisplayName("Бросает ForbiddenException, когда Яндекс отвечает статусом failed")
        void throwsForbiddenWhenYandexRespondsFailed() {
            // given
            mockServer.expect(requestTo(VALIDATE_URL))
                    .andRespond(withSuccess("{\"status\":\"failed\"}", MediaType.APPLICATION_JSON));
            var service = aService(true);

            // when / then
            assertThatThrownBy(() -> service.validate(TOKEN, IP))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Captcha validation failed");
            mockServer.verify();
        }

        @Test
        @DisplayName("Бросает ForbiddenException, когда тело ответа Яндекса пустое")
        void throwsForbiddenWhenYandexBodyIsNull() {
            // given
            mockServer.expect(requestTo(VALIDATE_URL))
                    .andRespond(withSuccess());
            var service = aService(true);

            // when / then
            assertThatThrownBy(() -> service.validate(TOKEN, IP))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Captcha validation failed");
            mockServer.verify();
        }

        @Test
        @DisplayName("Пропускает запрос без исключения, если сервис капчи недоступен")
        void skipsValidationWhenYandexUnavailable() {
            // given
            mockServer.expect(requestTo(VALIDATE_URL))
                    .andRespond(withException(new IOException("connection refused")));
            var service = aService(true);

            // when / then
            assertThatCode(() -> service.validate(TOKEN, IP)).doesNotThrowAnyException();
            mockServer.verify();
        }
    }
}
