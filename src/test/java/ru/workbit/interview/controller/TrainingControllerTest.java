package ru.workbit.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.TooManyRequestsException;
import ru.workbit.exception.UnprocessableEntityException;
import ru.workbit.exception.controller.ExceptionController;
import ru.workbit.interview.dto.CreateSessionRequest;
import ru.workbit.interview.dto.NormalizeInputRequest;
import ru.workbit.interview.dto.NormalizeInputResponse;
import ru.workbit.interview.dto.TrainingOptionsResponse;
import ru.workbit.interview.dto.TrainingSessionResponse;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.SessionStatus;
import ru.workbit.interview.service.TrainingService;
import ru.workbit.security.config.RateLimitProperties;
import ru.workbit.security.config.SecurityConfig;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.JWTService;
import ru.workbit.security.service.RateLimiterService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrainingController.class)
@Import({SecurityConfig.class, ExceptionController.class})
@EnableConfigurationProperties(RateLimitProperties.class)
@DisplayName("TrainingControllerTest")
class TrainingControllerTest {

    private static final String BASE = "/api/v1/interview/training";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    MockMvc mvc;

    // ObjectMapper создаём напрямую: в SB4 @WebMvcTest не включает JacksonAutoConfiguration автоматически
    private final ObjectMapper om = new ObjectMapper();

    @MockitoBean
    TrainingService trainingService;

    // JWTAuthFilter-зависимости: нужны, чтобы SecurityConfig мог создать фильтр
    @MockitoBean
    JWTService jwtService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    RateLimiterService rateLimiter;

    private CustomUserDetails principal() {
        return new CustomUserDetails(USER_ID, "user@example.com", "hash", List.of());
    }

    private TrainingSessionResponse sessionResponse(String profession, String topic) {
        return new TrainingSessionResponse(
                UUID.randomUUID(), profession, topic, Level.MIDDLE, SessionStatus.IN_PROGRESS,
                0, Instant.now(), null);
    }

    // -------------------------------------------------------------------------
    // POST /sessions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CreateSession")
    class CreateSession {

        @Test
        @DisplayName("Возвращает 201, Location и тело с profession/topic при валидном запросе")
        void returns201OnHappyPath() throws Exception {
            // given
            var request = new CreateSessionRequest("Java-разработчик", "Spring Boot", Level.MIDDLE);
            var response = sessionResponse("Java-разработчик", "Spring Boot");
            when(trainingService.create(any(), any())).thenReturn(response);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.profession").value("Java-разработчик"))
                    .andExpect(jsonPath("$.topic").value("Spring Boot"));
        }

        @Test
        @DisplayName("Возвращает 201, когда профессия — произвольная строка не из справочника")
        void returns201WhenProfessionIsFreeformNotInDictionary() throws Exception {
            // given
            var request = new CreateSessionRequest("Астролог", null, Level.JUNIOR);
            var response = sessionResponse("Астролог", null);
            when(trainingService.create(any(), any())).thenReturn(response);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.profession").value("Астролог"));
        }

        @Test
        @DisplayName("Возвращает 201, когда topic отсутствует")
        void returns201WhenTopicMissing() throws Exception {
            // given
            var request = new CreateSessionRequest("Java-разработчик", null, Level.SENIOR);
            var response = sessionResponse("Java-разработчик", null);
            when(trainingService.create(any(), any())).thenReturn(response);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.topic").doesNotExist());
        }

        @Test
        @DisplayName("Возвращает 400, когда profession пустая строка")
        void returns400WhenProfessionBlank() throws Exception {
            // given
            var request = new CreateSessionRequest("", "Spring Boot", Level.MIDDLE);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда profession длиннее 100 символов")
        void returns400WhenProfessionTooLong() throws Exception {
            // given
            var request = new CreateSessionRequest("a".repeat(101), "Spring Boot", Level.MIDDLE);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда topic длиннее 100 символов")
        void returns400WhenTopicTooLong() throws Exception {
            // given
            var request = new CreateSessionRequest("Java-разработчик", "b".repeat(101), Level.MIDDLE);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда level невалиден")
        void returns400WhenLevelInvalid() throws Exception {
            // given — level со значением, отсутствующим в enum Level
            var body = """
                    {"profession":"Java-разработчик","topic":"Spring Boot","level":"NotALevel"}
                    """;

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var request = new CreateSessionRequest("Java-разработчик", "Spring Boot", Level.MIDDLE);

            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(post(BASE + "/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 422, когда сервис не распознал профессию")
        void returns422WhenProfessionNotRecognized() throws Exception {
            // given
            var request = new CreateSessionRequest("Астролог", "Гороскопы", Level.MIDDLE);
            when(trainingService.create(any(), any()))
                    .thenThrow(new UnprocessableEntityException("Profession not recognized"));

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value("UNPROCESSABLE_CONTENT"))
                    .andExpect(jsonPath("$.message").value("Unprocessable content."))
                    .andExpect(jsonPath("$.errors[0]").value("Profession not recognized"));
        }
    }

    // -------------------------------------------------------------------------
    // GET /options
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetOptions")
    class GetOptions {

        @Test
        @DisplayName("Возвращает 200 со списком профессий-строк, уровнями и капами")
        void returns200WithOptions() throws Exception {
            // given
            var response = new TrainingOptionsResponse(
                    List.of("Java-разработчик", "Frontend-разработчик"),
                    List.of(Level.JUNIOR, Level.MIDDLE, Level.SENIOR),
                    10, 3);
            when(trainingService.getOptions()).thenReturn(response);

            // when / then
            mvc.perform(get(BASE + "/options")
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.professions").isArray())
                    .andExpect(jsonPath("$.professions[0]").value("Java-разработчик"))
                    .andExpect(jsonPath("$.professions[1]").value("Frontend-разработчик"))
                    .andExpect(jsonPath("$.levels").isArray())
                    .andExpect(jsonPath("$.levels[0]").value("Начинающий"))
                    .andExpect(jsonPath("$.questionCap").value(10))
                    .andExpect(jsonPath("$.minAnswersToFinish").value(3));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(get(BASE + "/options"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /suggest/professions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SuggestProfessions")
    class SuggestProfessions {

        @Test
        @DisplayName("Возвращает 200 со списком подсказок из сервиса")
        void returns200WithSuggestions() throws Exception {
            // given
            when(trainingService.suggestProfessions("ja")).thenReturn(List.of("Java-разработчик"));

            // when / then
            mvc.perform(get(BASE + "/suggest/professions")
                            .with(user(principal()))
                            .param("query", "ja"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("Java-разработчик"));
        }

        @Test
        @DisplayName("Возвращает 400, когда query отсутствует")
        void returns400WhenQueryMissing() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/suggest/professions")
                            .with(user(principal())))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 429, когда rate limiter отклоняет запрос")
        void returns429WhenRateLimited() throws Exception {
            // given
            doThrow(new TooManyRequestsException("Too many requests"))
                    .when(rateLimiter).check(anyString(), any(RateLimitProperties.Bucket.class));

            // when / then
            mvc.perform(get(BASE + "/suggest/professions")
                            .with(user(principal()))
                            .param("query", "ja"))
                    .andExpect(status().isTooManyRequests());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(get(BASE + "/suggest/professions")
                            .param("query", "ja"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Передаёт в rate limiter ключ с префиксом \"suggest:\"")
        void passesKeyWithSuggestPrefixToRateLimiter() throws Exception {
            // given
            when(trainingService.suggestProfessions("ja")).thenReturn(List.of("Java-разработчик"));
            var keyCaptor = ArgumentCaptor.forClass(String.class);

            // when
            mvc.perform(get(BASE + "/suggest/professions")
                            .with(user(principal()))
                            .param("query", "ja"))
                    .andExpect(status().isOk());

            // then
            verify(rateLimiter).check(keyCaptor.capture(), any(RateLimitProperties.Bucket.class));
            assertThat(keyCaptor.getValue()).startsWith("suggest:");
        }
    }

    // -------------------------------------------------------------------------
    // GET /suggest/topics
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SuggestTopics")
    class SuggestTopics {

        @Test
        @DisplayName("Возвращает 200 со списком подсказок из сервиса")
        void returns200WithSuggestions() throws Exception {
            // given
            when(trainingService.suggestTopics("Java-разработчик", "spr")).thenReturn(List.of("Spring Boot"));

            // when / then
            mvc.perform(get(BASE + "/suggest/topics")
                            .with(user(principal()))
                            .param("profession", "Java-разработчик")
                            .param("query", "spr"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("Spring Boot"));
        }

        @Test
        @DisplayName("Возвращает 400, когда profession отсутствует")
        void returns400WhenProfessionMissing() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/suggest/topics")
                            .with(user(principal()))
                            .param("query", "spr"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 429, когда rate limiter отклоняет запрос")
        void returns429WhenRateLimited() throws Exception {
            // given
            doThrow(new TooManyRequestsException("Too many requests"))
                    .when(rateLimiter).check(anyString(), any(RateLimitProperties.Bucket.class));

            // when / then
            mvc.perform(get(BASE + "/suggest/topics")
                            .with(user(principal()))
                            .param("profession", "Java-разработчик")
                            .param("query", "spr"))
                    .andExpect(status().isTooManyRequests());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(get(BASE + "/suggest/topics")
                            .param("profession", "Java-разработчик")
                            .param("query", "spr"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /normalize
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("NormalizeInput")
    class NormalizeInput {

        @Test
        @DisplayName("Возвращает 200 с телом из сервиса при валидном запросе")
        void returns200OnHappyPath() throws Exception {
            // given
            var request = new NormalizeInputRequest("джава дев", "спринг");
            var response = new NormalizeInputResponse(
                    true, List.of("Java-разработчик"), true, List.of("Spring"), true);
            when(trainingService.normalizeInput(any())).thenReturn(response);

            // when / then
            mvc.perform(post(BASE + "/normalize")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.professionRecognized").value(true))
                    .andExpect(jsonPath("$.professionSuggestions[0]").value("Java-разработчик"))
                    .andExpect(jsonPath("$.topicFitsProfession").value(true));
        }

        @Test
        @DisplayName("Возвращает 200 и передаёт в сервис topic=null, когда topic отсутствует")
        void returns200WhenTopicMissing() throws Exception {
            // given
            var request = new NormalizeInputRequest("джава дев", null);
            var response = new NormalizeInputResponse(
                    true, List.of("Java-разработчик"), null, null, null);
            when(trainingService.normalizeInput(any())).thenReturn(response);
            var requestCaptor = ArgumentCaptor.forClass(NormalizeInputRequest.class);

            // when
            mvc.perform(post(BASE + "/normalize")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // then
            verify(trainingService).normalizeInput(requestCaptor.capture());
            assertThat(requestCaptor.getValue().topic()).isNull();
        }

        @Test
        @DisplayName("Возвращает 400, когда profession пустая строка")
        void returns400WhenProfessionBlank() throws Exception {
            // given
            var request = new NormalizeInputRequest("", "спринг");

            // when / then
            mvc.perform(post(BASE + "/normalize")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда profession длиннее 100 символов")
        void returns400WhenProfessionTooLong() throws Exception {
            // given
            var request = new NormalizeInputRequest("a".repeat(101), "спринг");

            // when / then
            mvc.perform(post(BASE + "/normalize")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда topic длиннее 100 символов")
        void returns400WhenTopicTooLong() throws Exception {
            // given
            var request = new NormalizeInputRequest("джава дев", "b".repeat(101));

            // when / then
            mvc.perform(post(BASE + "/normalize")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 429 и передаёт в rate limiter ключ с префиксом \"normalize:\", когда лимит превышен")
        void returns429WhenRateLimited() throws Exception {
            // given
            var request = new NormalizeInputRequest("джава дев", "спринг");
            doThrow(new TooManyRequestsException("Too many requests"))
                    .when(rateLimiter).check(anyString(), any(RateLimitProperties.Bucket.class));
            var keyCaptor = ArgumentCaptor.forClass(String.class);

            // when
            mvc.perform(post(BASE + "/normalize")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests());

            // then
            verify(rateLimiter).check(keyCaptor.capture(), any(RateLimitProperties.Bucket.class));
            assertThat(keyCaptor.getValue()).startsWith("normalize:");
            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 503, когда сервис бросает LlmException")
        void returns503WhenLlmUnavailable() throws Exception {
            // given
            var request = new NormalizeInputRequest("джава дев", "спринг");
            when(trainingService.normalizeInput(any())).thenThrow(new LlmException("LLM недоступен"));

            // when / then
            mvc.perform(post(BASE + "/normalize")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var request = new NormalizeInputRequest("джава дев", "спринг");

            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(post(BASE + "/normalize")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }
    }
}
