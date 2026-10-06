package ru.workbit.resume;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.AuthTestConfig;
import ru.workbit.auth.dto.RequestCodeRequest;
import ru.workbit.auth.dto.VerifyCodeRequest;
import ru.workbit.exception.dto.ApiError;
import ru.workbit.resume.service.ResumeFileStorage;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(AuthTestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long-enough-for-hmac",
        "jwt.expiration=3600000",
        "app.security.rate-limit.limit=1000",
        "app.security.rate-limit.verify-code.limit=1000",
        "app.mail.from-name=Workbit",
        "app.mail.from-mail=noreply@workbit.ru",
        "app.mail.base-url=https://workbit.ru",
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "llm.gateway.base-url=http://localhost",
        "app.resume.enabled=false"
})
@DisplayName("ResumeDisabledIT")
class ResumeDisabledIT extends AbstractPostgresIT {

    private static final String AUTH = "/api/v1/auth";
    private static final String RESUMES = "/api/v1/resumes";
    private static final String ACCESS_COOKIE = "access_token";

    @Autowired
    TestRestTemplate rest;

    @Autowired
    AuthTestConfig.CodeCaptor codeCaptor;

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("Не создаёт файловое хранилище, поэтому старт не зависит от каталога резюме")
    void doesNotCreateFileStorage() {
        assertThat(context.getBeanProvider(ResumeFileStorage.class).getIfAvailable()).isNull();
    }

    @Test
    @DisplayName("Отвечает 404 на ручку резюме авторизованному пользователю")
    void respondsNotFound() {
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, ACCESS_COOKIE + "=" + loginAccessToken());

        var response = rest.exchange(RESUMES, HttpMethod.GET, new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.NOT_FOUND.name());
    }

    private String loginAccessToken() {
        var email = "user-" + UUID.randomUUID() + "@example.com";
        var requested = rest.postForEntity(
                AUTH + "/request-code", new RequestCodeRequest(email, true, null), Void.class);
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.OK);
        var code = codeCaptor.getCode(email);
        assertThat(code).isNotNull();
        var verified = rest.postForEntity(
                AUTH + "/verify-code", new VerifyCodeRequest(email, code), Void.class);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        return extractCookieValue(verified, ACCESS_COOKIE);
    }

    private static String extractCookieValue(ResponseEntity<?> response, String cookieName) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        return setCookies.stream()
                .filter(header -> header.startsWith(cookieName + "="))
                .map(header -> header.substring(cookieName.length() + 1, header.indexOf(';')))
                .findFirst()
                .orElseThrow();
    }
}
