package ru.workbit.training.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import ru.workbit.security.service.UserDetailsServiceImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.exception.PaymentRequiredException;
import ru.workbit.exception.TooManyRequestsException;
import ru.workbit.exception.UnprocessableEntityException;
import ru.workbit.exception.controller.ExceptionController;
import ru.workbit.training.dto.CreateSessionRequest;
import ru.workbit.training.dto.FeedbackRequest;
import ru.workbit.training.dto.NormalizeInputRequest;
import ru.workbit.training.dto.NormalizeInputResponse;
import ru.workbit.training.dto.ReferenceAnswerResponse;
import ru.workbit.training.dto.TrainingOptionsResponse;
import ru.workbit.training.dto.TrainingQuestionResponse;
import ru.workbit.training.dto.TrainingSessionResponse;
import ru.workbit.training.model.TrainingSession;
import ru.workbit.training.model.TrainingUserFeedback;
import ru.workbit.training.service.TrainingService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    private static final String BASE = "/api/v1/training";
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
    UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    RateLimiterService rateLimiter;

    private CustomUserDetails principal() {
        return new CustomUserDetails(USER_ID, "user@example.com", List.of());
    }

    private TrainingSessionResponse sessionResponse(String skill, String profession) {
        return new TrainingSessionResponse(
                UUID.randomUUID(), skill, profession, TrainingSession.Level.MIDDLE, TrainingSession.Status.IN_PROGRESS,
                0, 10, Instant.now(), null);
    }

    // -------------------------------------------------------------------------
    // POST /sessions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CreateSession")
    class CreateSession {

        @Test
        @DisplayName("Возвращает 201, Location и тело с skill/profession при валидном запросе")
        void returns201OnHappyPath() throws Exception {
            // given
            var request = new CreateSessionRequest("Spring Boot", "Java-разработчик", TrainingSession.Level.MIDDLE);
            var response = sessionResponse("Spring Boot", "Java-разработчик");
            when(trainingService.create(any(), any())).thenReturn(response);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.skill").value("Spring Boot"))
                    .andExpect(jsonPath("$.profession").value("Java-разработчик"))
                    .andExpect(jsonPath("$.totalQuestions").value(10));
        }

        @Test
        @DisplayName("Возвращает 400, когда skill пустая строка")
        void returns400WhenSkillBlank() throws Exception {
            // given
            var request = new CreateSessionRequest("", "Java-разработчик", TrainingSession.Level.MIDDLE);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда profession пустая строка")
        void returns400WhenProfessionBlank() throws Exception {
            // given
            var request = new CreateSessionRequest("Spring Boot", "", TrainingSession.Level.MIDDLE);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда skill длиннее 100 символов")
        void returns400WhenSkillTooLong() throws Exception {
            // given
            var request = new CreateSessionRequest("a".repeat(101), "Java-разработчик", TrainingSession.Level.MIDDLE);

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
            var request = new CreateSessionRequest("Spring Boot", "b".repeat(101), TrainingSession.Level.MIDDLE);

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
            // given — level со значением, отсутствующим в enum TrainingSession.Level
            var body = """
                    {"skill":"Spring Boot","profession":"Java-разработчик","level":"NotALevel"}
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
            var request = new CreateSessionRequest("Spring Boot", "Java-разработчик", TrainingSession.Level.MIDDLE);

            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(post(BASE + "/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 422, когда сервис не распознал навык")
        void returns422WhenSkillNotRecognized() throws Exception {
            // given
            var request = new CreateSessionRequest("Ктулхурделла", "Java-разработчик", TrainingSession.Level.MIDDLE);
            when(trainingService.create(any(), any()))
                    .thenThrow(new UnprocessableEntityException("Skill not recognized"));

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value("UNPROCESSABLE_CONTENT"))
                    .andExpect(jsonPath("$.message").value("Unprocessable content."))
                    .andExpect(jsonPath("$.errors[0]").value("Skill not recognized"));
        }

        @Test
        @DisplayName("Возвращает 422, когда сервис не распознал профессию")
        void returns422WhenProfessionNotRecognized() throws Exception {
            // given
            var request = new CreateSessionRequest("Spring Boot", "Астролог", TrainingSession.Level.MIDDLE);
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

        @Test
        @DisplayName("Возвращает 402, когда квота тренировок исчерпана")
        void returns402WhenQuotaExhausted() throws Exception {
            // given
            var request = new CreateSessionRequest("Spring Boot", "Java-разработчик", TrainingSession.Level.MIDDLE);
            when(trainingService.create(any(), any()))
                    .thenThrow(new PaymentRequiredException("Training quota exhausted"));

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isPaymentRequired())
                    .andExpect(jsonPath("$.message").value("Payment required."))
                    .andExpect(jsonPath("$.errors[0]").value("Training quota exhausted"));
        }
    }

    // -------------------------------------------------------------------------
    // GET /options
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetOptions")
    class GetOptions {

        @Test
        @DisplayName("Возвращает 200 со списком навыков, профессий, уровнями и капами")
        void returns200WithOptions() throws Exception {
            // given
            var response = new TrainingOptionsResponse(
                    List.of("Spring Boot", "Docker"),
                    List.of("Java-разработчик", "Frontend-разработчик"),
                    List.of(TrainingSession.Level.JUNIOR, TrainingSession.Level.MIDDLE, TrainingSession.Level.SENIOR),
                    10, 50, 3);
            when(trainingService.getOptions()).thenReturn(response);

            // when / then
            mvc.perform(get(BASE + "/options")
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.skills").isArray())
                    .andExpect(jsonPath("$.skills[0]").value("Spring Boot"))
                    .andExpect(jsonPath("$.skills[1]").value("Docker"))
                    .andExpect(jsonPath("$.professions").isArray())
                    .andExpect(jsonPath("$.professions[0]").value("Java-разработчик"))
                    .andExpect(jsonPath("$.professions[1]").value("Frontend-разработчик"))
                    .andExpect(jsonPath("$.levels").isArray())
                    .andExpect(jsonPath("$.levels[0]").value("Начинающий"))
                    .andExpect(jsonPath("$.questionCap").value(10))
                    .andExpect(jsonPath("$.maxQuestions").value(50))
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
        @DisplayName("Передаёт в rate limiter ключ с префиксом \"suggest-professions:\"")
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
            assertThat(keyCaptor.getValue()).startsWith("suggest-professions:");
        }
    }

    // -------------------------------------------------------------------------
    // GET /suggest/skills
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SuggestSkills")
    class SuggestSkills {

        @Test
        @DisplayName("Возвращает 200 и передаёт профессию в сервис, когда она указана")
        void returns200WhenProfessionProvided() throws Exception {
            // given
            when(trainingService.suggestSkills("Java-разработчик", "spr")).thenReturn(List.of("Spring Boot"));

            // when
            mvc.perform(get(BASE + "/suggest/skills")
                            .with(user(principal()))
                            .param("profession", "Java-разработчик")
                            .param("query", "spr"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("Spring Boot"));

            // then
            verify(trainingService).suggestSkills("Java-разработчик", "spr");
        }

        @Test
        @DisplayName("Возвращает 200 и передаёт null вместо профессии в сервис, когда она не указана")
        void returns200WhenProfessionMissing() throws Exception {
            // given
            when(trainingService.suggestSkills(isNull(), eq("spr"))).thenReturn(List.of("Spring Boot"));

            // when
            mvc.perform(get(BASE + "/suggest/skills")
                            .with(user(principal()))
                            .param("query", "spr"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("Spring Boot"));

            // then
            verify(trainingService).suggestSkills(isNull(), eq("spr"));
        }

        @Test
        @DisplayName("Возвращает 400, когда query отсутствует")
        void returns400WhenQueryMissing() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/suggest/skills")
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
            mvc.perform(get(BASE + "/suggest/skills")
                            .with(user(principal()))
                            .param("query", "spr"))
                    .andExpect(status().isTooManyRequests());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(get(BASE + "/suggest/skills")
                            .param("query", "spr"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Передаёт в rate limiter ключ с префиксом \"suggest-skills:\" — бакет отдельный от профессий")
        void passesKeyWithSuggestPrefixToRateLimiter() throws Exception {
            // given
            when(trainingService.suggestSkills(isNull(), eq("spr"))).thenReturn(List.of("Spring Boot"));
            var keyCaptor = ArgumentCaptor.forClass(String.class);

            // when
            mvc.perform(get(BASE + "/suggest/skills")
                            .with(user(principal()))
                            .param("query", "spr"))
                    .andExpect(status().isOk());

            // then
            verify(rateLimiter).check(keyCaptor.capture(), any(RateLimitProperties.Bucket.class));
            assertThat(keyCaptor.getValue()).startsWith("suggest-skills:");
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
            var request = new NormalizeInputRequest("спринг", "джава дев");
            var response = new NormalizeInputResponse(
                    true, List.of("Spring Boot"), true, List.of("Java-разработчик"), true);
            when(trainingService.normalizeInput(any())).thenReturn(response);

            // when / then
            mvc.perform(post(BASE + "/normalize")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.skillRecognized").value(true))
                    .andExpect(jsonPath("$.skillSuggestions[0]").value("Spring Boot"))
                    .andExpect(jsonPath("$.professionRecognized").value(true))
                    .andExpect(jsonPath("$.professionSuggestions[0]").value("Java-разработчик"))
                    .andExpect(jsonPath("$.skillFitsProfession").value(true));
        }

        @Test
        @DisplayName("Возвращает 400, когда skill пустая строка")
        void returns400WhenSkillBlank() throws Exception {
            // given
            var request = new NormalizeInputRequest("", "джава дев");

            // when / then
            mvc.perform(post(BASE + "/normalize")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда profession пустая строка")
        void returns400WhenProfessionBlank() throws Exception {
            // given
            var request = new NormalizeInputRequest("спринг", "");

            // when / then
            mvc.perform(post(BASE + "/normalize")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда skill длиннее 100 символов")
        void returns400WhenSkillTooLong() throws Exception {
            // given
            var request = new NormalizeInputRequest("a".repeat(101), "джава дев");

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
            var request = new NormalizeInputRequest("спринг", "b".repeat(101));

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
            var request = new NormalizeInputRequest("спринг", "джава дев");
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
            var request = new NormalizeInputRequest("спринг", "джава дев");
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
            var request = new NormalizeInputRequest("спринг", "джава дев");

            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(post(BASE + "/normalize")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /sessions/{sessionId}/questions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("AnsweredQuestions")
    class AnsweredQuestions {

        @Test
        @DisplayName("Возвращает 200 со списком отвеченных вопросов")
        void returns200WithAnsweredQuestions() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            var questionId = UUID.randomUUID();
            var question = new TrainingQuestionResponse(
                    questionId, 1, "Что такое индекс в PostgreSQL?", "Структура для ускорения поиска", null, null);
            when(trainingService.getAnsweredQuestions(sessionId, USER_ID)).thenReturn(List.of(question));

            // when / then
            mvc.perform(get(BASE + "/sessions/" + sessionId + "/questions")
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].questionId").value(questionId.toString()))
                    .andExpect(jsonPath("$[0].answerText").value("Структура для ускорения поиска"))
                    .andExpect(jsonPath("$[1]").doesNotExist());
        }

        @Test
        @DisplayName("Возвращает 200 с пустым массивом, когда вопросов ещё нет")
        void returns200WithEmptyArray() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(trainingService.getAnsweredQuestions(sessionId, USER_ID)).thenReturn(List.of());

            // when / then
            mvc.perform(get(BASE + "/sessions/" + sessionId + "/questions")
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия не найдена")
        void returns404WhenNotFound() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(trainingService.getAnsweredQuestions(sessionId, USER_ID))
                    .thenThrow(new NotFoundException("Session not found"));

            // when / then
            mvc.perform(get(BASE + "/sessions/" + sessionId + "/questions")
                            .with(user(principal())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("Session not found"));
        }

        @Test
        @DisplayName("Возвращает 400, когда sessionId не является UUID")
        void returns400WhenSessionIdNotUuid() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/sessions/not-a-uuid/questions")
                            .with(user(principal())))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var sessionId = UUID.randomUUID();

            // when / then
            mvc.perform(get(BASE + "/sessions/" + sessionId + "/questions"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /sessions/{sessionId}/questions/{questionId}/reference-answer
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetReferenceAnswer")
    class GetReferenceAnswer {

        private final UUID sessionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        private final UUID questionId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        private String uri() {
            return BASE + "/sessions/" + sessionId + "/questions/" + questionId + "/reference-answer";
        }

        @Test
        @DisplayName("Возвращает 200 с эталонным ответом из сервиса")
        void returns200OnHappyPath() throws Exception {
            // given
            when(trainingService.getReferenceAnswer(sessionId, questionId, USER_ID))
                    .thenReturn(new ReferenceAnswerResponse("Использую индексы и explain analyze"));

            // when / then
            mvc.perform(get(uri())
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.answer").value("Использую индексы и explain analyze"));
        }

        @Test
        @DisplayName("Возвращает 403, когда вопрос принадлежит другому пользователю")
        void returns403WhenForbidden() throws Exception {
            // given
            when(trainingService.getReferenceAnswer(sessionId, questionId, USER_ID))
                    .thenThrow(new ForbiddenException("Access denied"));

            // when / then
            mvc.perform(get(uri())
                            .with(user(principal())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Возвращает 404, когда вопрос не найден")
        void returns404WhenQuestionNotFound() throws Exception {
            // given
            when(trainingService.getReferenceAnswer(sessionId, questionId, USER_ID))
                    .thenThrow(new NotFoundException("Question not found"));

            // when / then
            mvc.perform(get(uri())
                            .with(user(principal())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 409, когда вопрос не принадлежит указанной сессии")
        void returns409WhenQuestionFromAnotherSession() throws Exception {
            // given
            when(trainingService.getReferenceAnswer(sessionId, questionId, USER_ID))
                    .thenThrow(new ConflictException("Invalid session"));

            // when / then
            mvc.perform(get(uri())
                            .with(user(principal())))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Возвращает 503, когда AI-сервис недоступен")
        void returns503WhenLlmUnavailable() throws Exception {
            // given
            when(trainingService.getReferenceAnswer(sessionId, questionId, USER_ID))
                    .thenThrow(new LlmException("Reference answer is not available"));

            // when / then
            mvc.perform(get(uri())
                            .with(user(principal())))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(get(uri()))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /sessions/{sessionId}/questions/more
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("AddQuestions")
    class AddQuestions {

        private final UUID sessionId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        private String uri() {
            return BASE + "/sessions/" + sessionId + "/questions/more";
        }

        @Test
        @DisplayName("Возвращает 200 с обновлённой сессией из сервиса")
        void returns200OnHappyPath() throws Exception {
            // given
            var response = new TrainingSessionResponse(
                    sessionId, "Spring Boot", "Java-разработчик", TrainingSession.Level.MIDDLE,
                    TrainingSession.Status.IN_PROGRESS, 10, 20, Instant.now(), null);
            when(trainingService.addQuestions(sessionId, USER_ID)).thenReturn(response);

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(sessionId.toString()))
                    .andExpect(jsonPath("$.answeredCount").value(10))
                    .andExpect(jsonPath("$.totalQuestions").value(20));
        }

        @Test
        @DisplayName("Передаёт sessionId и userId в сервис")
        void passesSessionIdAndUserIdToService() throws Exception {
            // given
            when(trainingService.addQuestions(sessionId, USER_ID))
                    .thenReturn(sessionResponse("Spring Boot", "Java-разработчик"));

            // when
            mvc.perform(post(uri())
                            .with(user(principal())))
                    .andExpect(status().isOk());

            // then
            verify(trainingService).addQuestions(sessionId, USER_ID);
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия не найдена")
        void returns404WhenSessionNotFound() throws Exception {
            // given
            when(trainingService.addQuestions(sessionId, USER_ID))
                    .thenThrow(new NotFoundException("Session not found"));

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 409, когда достигнут потолок вопросов или новых вопросов больше нет")
        void returns409WhenLimitReachedOrNoNewQuestions() throws Exception {
            // given
            when(trainingService.addQuestions(sessionId, USER_ID))
                    .thenThrow(new ConflictException("No new questions available"));

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal())))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Возвращает 503, когда AI-сервис недоступен")
        void returns503WhenLlmUnavailable() throws Exception {
            // given
            when(trainingService.addQuestions(sessionId, USER_ID))
                    .thenThrow(new LlmException("LLM недоступен"));

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal())))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(post(uri()))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /sessions/{sessionId}/restart
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("RestartSession")
    class RestartSession {

        private final UUID sessionId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        private String uri() {
            return BASE + "/sessions/" + sessionId + "/restart";
        }

        @Test
        @DisplayName("Возвращает 200 с сессией в исходном состоянии")
        void returns200OnHappyPath() throws Exception {
            // given
            var response = new TrainingSessionResponse(
                    sessionId, "Spring Boot", "Java-разработчик", TrainingSession.Level.MIDDLE,
                    TrainingSession.Status.CREATED, 0, 10, Instant.now(), null);
            when(trainingService.restart(sessionId, USER_ID)).thenReturn(response);

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CREATED"))
                    .andExpect(jsonPath("$.answeredCount").value(0));
        }

        @Test
        @DisplayName("Передаёт sessionId и userId в сервис")
        void passesSessionIdAndUserIdToService() throws Exception {
            // given
            when(trainingService.restart(sessionId, USER_ID))
                    .thenReturn(sessionResponse("Spring Boot", "Java-разработчик"));

            // when
            mvc.perform(post(uri())
                            .with(user(principal())))
                    .andExpect(status().isOk());

            // then
            verify(trainingService).restart(sessionId, USER_ID);
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия не найдена")
        void returns404WhenSessionNotFound() throws Exception {
            // given
            when(trainingService.restart(sessionId, USER_ID))
                    .thenThrow(new NotFoundException("Session not found"));

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 409, когда тренировка ещё не завершена")
        void returns409WhenNotCompleted() throws Exception {
            // given
            when(trainingService.restart(sessionId, USER_ID))
                    .thenThrow(new ConflictException("Session not completed"));

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal())))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(post(uri()))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /sessions/{sessionId}/questions/{questionId}/feedback
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SubmitQuestionFeedback")
    class SubmitQuestionFeedback {

        private final UUID sessionId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        private final UUID questionId = UUID.fromString("77777777-7777-7777-7777-777777777777");

        private String uri() {
            return BASE + "/sessions/" + sessionId + "/questions/" + questionId + "/feedback";
        }

        @Test
        @DisplayName("Возвращает 204 и передаёт в сервис sessionId, questionId, userId и тело запроса")
        void returns204AndPassesRequestToService() throws Exception {
            // given
            var request = new FeedbackRequest(TrainingUserFeedback.Vote.DOWN, List.of("Оценка занижена"), "Комментарий");

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            // then
            verify(trainingService).submitQuestionFeedback(sessionId, questionId, USER_ID, request);
        }

        @Test
        @DisplayName("Возвращает 400, когда vote отсутствует")
        void returns400WhenVoteMissing() throws Exception {
            // given
            var body = """
                    {"reasons":[]}
                    """;

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда reasons отсутствует")
        void returns400WhenReasonsMissing() throws Exception {
            // given
            var body = """
                    {"vote":"DOWN"}
                    """;

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда reasons длиннее 10 элементов")
        void returns400WhenReasonsTooMany() throws Exception {
            // given
            var request = new FeedbackRequest(TrainingUserFeedback.Vote.DOWN, List.of(
                    "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"), null);

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда элемент reasons длиннее 100 символов")
        void returns400WhenReasonTooLong() throws Exception {
            // given
            var request = new FeedbackRequest(TrainingUserFeedback.Vote.DOWN, List.of("a".repeat(101)), null);

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 400, когда comment длиннее 2000 символов")
        void returns400WhenCommentTooLong() throws Exception {
            // given
            var request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), "a".repeat(2001));

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 403, когда вопрос принадлежит другому пользователю")
        void returns403WhenForbidden() throws Exception {
            // given
            var request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);
            doThrow(new ForbiddenException("Access denied"))
                    .when(trainingService).submitQuestionFeedback(sessionId, questionId, USER_ID, request);

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Возвращает 404, когда вопрос не найден")
        void returns404WhenQuestionNotFound() throws Exception {
            // given
            var request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);
            doThrow(new NotFoundException("Question not found"))
                    .when(trainingService).submitQuestionFeedback(sessionId, questionId, USER_ID, request);

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 409, когда вопрос не принадлежит указанной сессии")
        void returns409WhenQuestionFromAnotherSession() throws Exception {
            // given
            var request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);
            doThrow(new ConflictException("Invalid session"))
                    .when(trainingService).submitQuestionFeedback(sessionId, questionId, USER_ID, request);

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);

            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(post(uri())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /sessions/{sessionId}/report/feedback
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SubmitReportFeedback")
    class SubmitReportFeedback {

        private final UUID sessionId = UUID.fromString("88888888-8888-8888-8888-888888888888");

        private String uri() {
            return BASE + "/sessions/" + sessionId + "/report/feedback";
        }

        @Test
        @DisplayName("Возвращает 204 и передаёт в сервис sessionId, userId и тело запроса")
        void returns204AndPassesRequestToService() throws Exception {
            // given
            var request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            // then
            verify(trainingService).submitReportFeedback(sessionId, USER_ID, request);
        }

        @Test
        @DisplayName("Возвращает 400, когда vote отсутствует")
        void returns400WhenVoteMissing() throws Exception {
            // given
            var body = """
                    {"reasons":[]}
                    """;

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия или отчёт не найдены")
        void returns404WhenNotFound() throws Exception {
            // given
            var request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);
            doThrow(new NotFoundException("Report not found"))
                    .when(trainingService).submitReportFeedback(sessionId, USER_ID, request);

            // when / then
            mvc.perform(post(uri())
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);

            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(post(uri())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(trainingService);
        }
    }
}
