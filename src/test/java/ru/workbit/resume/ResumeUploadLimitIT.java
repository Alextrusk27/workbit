package ru.workbit.resume;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.AuthTestConfig;
import ru.workbit.auth.dto.RequestCodeRequest;
import ru.workbit.auth.dto.VerifyCodeRequest;
import ru.workbit.exception.dto.ApiError;
import ru.workbit.resume.dto.ResumeResponse;
import tools.jackson.databind.ObjectMapper;

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
        "llm.gateway.base-url=http://localhost"
})
@DisplayName("ResumeUploadLimitIT")
class ResumeUploadLimitIT extends AbstractPostgresIT {

    private static final String AUTH = "/api/v1/auth";
    private static final String RESUMES = "/api/v1/resumes";
    private static final String ACCESS_COOKIE = "access_token";
    private static final String BOUNDARY = "----workbit-upload-limit-boundary";
    private static final int MB = 1024 * 1024;
    private static final byte[] PDF_MAGIC = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    TestRestTemplate rest;

    @Autowired
    AuthTestConfig.CodeCaptor codeCaptor;

    @Autowired
    ObjectMapper objectMapper;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

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

    private static byte[] pdfOfSize(int size) {
        var content = new byte[size];
        Arrays.fill(content, (byte) 'x');
        System.arraycopy(PDF_MAGIC, 0, content, 0, PDF_MAGIC.length);
        return content;
    }

    private static byte[] multipartBody(String partName, String filename, byte[] content) {
        var out = new ByteArrayOutputStream(content.length + 512);
        var head = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n";
        var tail = "\r\n--" + BOUNDARY + "--\r\n";
        out.writeBytes(head.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(content);
        out.writeBytes(tail.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private HttpResponse<String> postResume(String accessToken, byte[] body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + RESUMES))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY)
                .header(HttpHeaders.COOKIE, ACCESS_COOKIE + "=" + accessToken)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertFileTooLarge(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        var error = objectMapper.readValue(response.body(), ApiError.class);
        assertThat(error.status()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.name());
        assertThat(error.errors()).containsExactly("File too large");
    }

    @Nested
    @DisplayName("POST /api/v1/resumes")
    class Upload {

        @Test
        @DisplayName("Отвечает 413 с телом, когда файл больше max-file-size, но запрос меньше max-request-size")
        void rejectsFileOverFileLimit() throws Exception {
            // given
            var token = loginAccessToken();
            var body = multipartBody("file", "big.pdf", pdfOfSize(5 * MB + MB / 2));

            // when
            var response = postResume(token, body);

            // then
            assertFileTooLarge(response);
        }

        @Test
        @DisplayName("Отвечает 413 с телом, когда запрос больше max-request-size, но меньше max-swallow-size")
        void rejectsRequestOverRequestLimit() throws Exception {
            // given
            var token = loginAccessToken();
            var body = multipartBody("file", "huge.pdf", pdfOfSize(7 * MB));

            // when
            var response = postResume(token, body);

            // then
            assertFileTooLarge(response);
        }

        @Test
        @DisplayName("Создаёт резюме, когда валидный PDF чуть меньше 5 МБ")
        void acceptsPdfJustUnderLimit() throws Exception {
            // given
            var token = loginAccessToken();
            var size = 5 * MB - 1024;
            var body = multipartBody("file", "ok.pdf", pdfOfSize(size));

            // when
            var response = postResume(token, body);

            // then
            assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
            var created = objectMapper.readValue(response.body(), ResumeResponse.class);
            assertThat(created.format().name()).isEqualTo("PDF");
            assertThat(created.sizeBytes()).isEqualTo(size);
        }

        @Test
        @DisplayName("Отвечает 400, когда в запросе нет части file")
        void rejectsMissingFilePart() throws Exception {
            // given
            var token = loginAccessToken();
            var body = multipartBody("other", "x.pdf", pdfOfSize(1024));

            // when
            var response = postResume(token, body);

            // then
            assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }
    }
}
