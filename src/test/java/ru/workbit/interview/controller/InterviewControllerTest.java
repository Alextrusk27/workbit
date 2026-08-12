package ru.workbit.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.exception.PaymentRequiredException;
import ru.workbit.exception.VacancyFetchException;
import ru.workbit.exception.controller.ExceptionController;
import ru.workbit.interview.dto.CreateInterviewSessionRequest;
import ru.workbit.interview.dto.InterviewQuestionResponse;
import ru.workbit.interview.dto.InterviewReportResponse;
import ru.workbit.interview.dto.InterviewSessionResponse;
import ru.workbit.interview.dto.InterviewVacancyDetailResponse;
import ru.workbit.interview.dto.InterviewVacancyResponse;
import ru.workbit.interview.dto.SubmitAnswerBody;
import ru.workbit.interview.dto.SubmitAnswerRequest;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.service.InterviewService;
import ru.workbit.interview.service.InterviewVacancyService;
import ru.workbit.security.config.SecurityConfig;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.JWTService;
import ru.workbit.security.service.UserDetailsServiceImpl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
@Import({SecurityConfig.class, ExceptionController.class})
@DisplayName("InterviewControllerTest")
class InterviewControllerTest {

    private static final String BASE = "/api/v1/interview";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String VACANCY_URL = "https://hh.ru/vacancy/123456";

    @Autowired
    MockMvc mvc;

    // ObjectMapper создаём напрямую: в SB4 @WebMvcTest не включает JacksonAutoConfiguration автоматически
    private final ObjectMapper om = new ObjectMapper();

    @MockitoBean
    InterviewService interviewService;

    @MockitoBean
    InterviewVacancyService interviewVacancyService;

    // JWTAuthFilter-зависимости: нужны, чтобы SecurityConfig мог создать фильтр
    @MockitoBean
    JWTService jwtService;

    @MockitoBean
    UserDetailsServiceImpl userDetailsService;

    private CustomUserDetails principal() {
        return new CustomUserDetails(USER_ID, "user@example.com", List.of());
    }

    private InterviewSessionResponse sessionResponse(UUID sessionId) {
        return new InterviewSessionResponse(
                sessionId, "123456", "Java-разработчик", "ООО Ромашка", VACANCY_URL, "От 1 года до 3 лет",
                InterviewSession.Status.IN_PROGRESS, 3, 10, Instant.now(), null);
    }

    private InterviewQuestionResponse questionResponse(UUID questionId, boolean followUp) {
        return new InterviewQuestionResponse(
                questionId, 1, "Расскажите про индексы в PostgreSQL", followUp, null, null, null);
    }

    private InterviewReportResponse reportResponse(UUID sessionId, String recommendations) {
        return new InterviewReportResponse(
                UUID.randomUUID(), sessionId, 3.8, InterviewReport.OfferProbability.MEDIUM,
                "Хороший кандидат, есть пробелы в индексах", recommendations, null, Instant.now(), List.of());
    }

    private InterviewVacancyResponse vacancyResponse(String vacancyId) {
        return new InterviewVacancyResponse(
                vacancyId, "Java-разработчик", "ООО Ромашка", VACANCY_URL, "От 1 года до 3 лет",
                InterviewSession.Status.COMPLETED, 2, 4.0, InterviewReport.OfferProbability.HIGH, Instant.now());
    }

    private InterviewVacancyDetailResponse vacancyDetailResponse(String vacancyId) {
        return new InterviewVacancyDetailResponse(
                vacancyId, "Java-разработчик", "ООО Ромашка", VACANCY_URL, "От 1 года до 3 лет",
                List.of(), List.of());
    }

    // -------------------------------------------------------------------------
    // POST /sessions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CreateSession")
    class CreateSession {

        @Test
        @DisplayName("Возвращает 201, Location и тело сессии при валидном запросе")
        void returns201OnHappyPath() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            var request = new CreateInterviewSessionRequest(VACANCY_URL);
            when(interviewService.createSession(eq(VACANCY_URL), any())).thenReturn(sessionResponse(sessionId));

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/sessions/" + sessionId))
                    .andExpect(jsonPath("$.id").value(sessionId.toString()))
                    .andExpect(jsonPath("$.vacancyName").value("Java-разработчик"))
                    .andExpect(jsonPath("$.totalQuestions").value(10));
        }

        @Test
        @DisplayName("Передаёт в сервис userId из принципала")
        void passesUserIdFromPrincipal() throws Exception {
            // given
            var request = new CreateInterviewSessionRequest(VACANCY_URL);
            when(interviewService.createSession(eq(VACANCY_URL), any())).thenReturn(sessionResponse(UUID.randomUUID()));
            var userIdCaptor = ArgumentCaptor.forClass(UUID.class);

            // when
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // then
            verify(interviewService).createSession(eq(VACANCY_URL), userIdCaptor.capture());
            assertThat(userIdCaptor.getValue()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Возвращает 400, когда vacancyUrl пустая строка")
        void returns400WhenVacancyUrlBlank() throws Exception {
            // given
            var request = new CreateInterviewSessionRequest("");

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(interviewService);
        }

        @Test
        @DisplayName("Возвращает 400, когда vacancyUrl отсутствует")
        void returns400WhenVacancyUrlMissing() throws Exception {
            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(interviewService);
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var request = new CreateInterviewSessionRequest(VACANCY_URL);

            // when / then — эндпоинт защищён (.anyRequest().authenticated()), без токена -> 401
            mvc.perform(post(BASE + "/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }

        @Test
        @DisplayName("Возвращает 404, когда вакансия не найдена")
        void returns404WhenVacancyNotFound() throws Exception {
            // given
            var request = new CreateInterviewSessionRequest(VACANCY_URL);
            when(interviewService.createSession(eq(VACANCY_URL), any()))
                    .thenThrow(new NotFoundException("Vacancy not found"));

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("Vacancy not found"));
        }

        @Test
        @DisplayName("Возвращает 402, когда квота интервью исчерпана")
        void returns402WhenQuotaExhausted() throws Exception {
            // given
            var request = new CreateInterviewSessionRequest(VACANCY_URL);
            when(interviewService.createSession(eq(VACANCY_URL), any()))
                    .thenThrow(new PaymentRequiredException("Interview quota exhausted"));

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isPaymentRequired())
                    .andExpect(jsonPath("$.message").value("Payment required."))
                    .andExpect(jsonPath("$.errors[0]").value("Interview quota exhausted"));
        }

        @Test
        @DisplayName("Возвращает 503, когда hh.ru недоступен")
        void returns503WhenVacancyServiceUnavailable() throws Exception {
            // given
            var request = new CreateInterviewSessionRequest(VACANCY_URL);
            when(interviewService.createSession(eq(VACANCY_URL), any()))
                    .thenThrow(new VacancyFetchException("hh.ru unavailable", null));

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable());
        }
    }

    // -------------------------------------------------------------------------
    // GET /sessions/{sessionId}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetSession")
    class GetSession {

        @Test
        @DisplayName("Возвращает 200 с телом сессии")
        void returns200WithSession() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(interviewService.get(sessionId, USER_ID)).thenReturn(sessionResponse(sessionId));

            // when / then
            mvc.perform(get(BASE + "/sessions/" + sessionId)
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(sessionId.toString()));
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия не найдена")
        void returns404WhenNotFound() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(interviewService.get(sessionId, USER_ID)).thenThrow(new NotFoundException("Session not found"));

            // when / then
            mvc.perform(get(BASE + "/sessions/" + sessionId)
                            .with(user(principal())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("Session not found"));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var sessionId = UUID.randomUUID();

            // when / then
            mvc.perform(get(BASE + "/sessions/" + sessionId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /sessions/{sessionId}/questions/next
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("NextQuestion")
    class NextQuestion {

        @Test
        @DisplayName("Возвращает 200 со следующим вопросом")
        void returns200WithQuestion() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            var questionId = UUID.randomUUID();
            when(interviewService.nextQuestion(sessionId, USER_ID)).thenReturn(questionResponse(questionId, false));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/questions/next")
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionId").value(questionId.toString()))
                    .andExpect(jsonPath("$.followUp").value(false));
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия не найдена")
        void returns404WhenSessionNotFound() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(interviewService.nextQuestion(sessionId, USER_ID)).thenThrow(new NotFoundException("Session not found"));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/questions/next")
                            .with(user(principal())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 409, когда неотвеченных вопросов не осталось")
        void returns409WhenNoQuestionsLeft() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(interviewService.nextQuestion(sessionId, USER_ID)).thenThrow(new ConflictException("No questions left"));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/questions/next")
                            .with(user(principal())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0]").value("No questions left"));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var sessionId = UUID.randomUUID();

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/questions/next"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /sessions/{sessionId}/questions/{questionId}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SubmitAnswer")
    class SubmitAnswer {

        @Test
        @DisplayName("Возвращает 204 и передаёт в сервис корректный SubmitAnswerRequest")
        void returns204AndPassesRequestToService() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            var questionId = UUID.randomUUID();
            var body = new SubmitAnswerBody("Использую индексы и explain analyze");
            var requestCaptor = ArgumentCaptor.forClass(SubmitAnswerRequest.class);

            // when
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/questions/" + questionId)
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isNoContent());

            // then
            verify(interviewService).submitAnswer(requestCaptor.capture());
            var captured = requestCaptor.getValue();
            assertThat(captured.userId()).isEqualTo(USER_ID);
            assertThat(captured.sessionId()).isEqualTo(sessionId);
            assertThat(captured.questionId()).isEqualTo(questionId);
            assertThat(captured.answerText()).isEqualTo("Использую индексы и explain analyze");
        }

        @Test
        @DisplayName("Возвращает 400, когда answerText пустая строка")
        void returns400WhenAnswerTextBlank() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            var questionId = UUID.randomUUID();
            var body = new SubmitAnswerBody("");

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/questions/" + questionId)
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(interviewService);
        }

        @Test
        @DisplayName("Возвращает 403, когда вопрос принадлежит другому пользователю")
        void returns403WhenForbidden() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            var questionId = UUID.randomUUID();
            var body = new SubmitAnswerBody("Ответ");
            doThrow(new ForbiddenException("Access denied"))
                    .when(interviewService).submitAnswer(any());

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/questions/" + questionId)
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errors[0]").value("Access denied"));
        }

        @Test
        @DisplayName("Возвращает 404, когда вопрос не найден")
        void returns404WhenQuestionNotFound() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            var questionId = UUID.randomUUID();
            var body = new SubmitAnswerBody("Ответ");
            doThrow(new NotFoundException("Question not found"))
                    .when(interviewService).submitAnswer(any());

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/questions/" + questionId)
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 409, когда вопрос уже отвечен")
        void returns409WhenAlreadyAnswered() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            var questionId = UUID.randomUUID();
            var body = new SubmitAnswerBody("Ответ");
            doThrow(new ConflictException("Question already answered"))
                    .when(interviewService).submitAnswer(any());

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/questions/" + questionId)
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0]").value("Question already answered"));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            var questionId = UUID.randomUUID();
            var body = new SubmitAnswerBody("Ответ");

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/questions/" + questionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /sessions/{sessionId}/finish
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("FinishSession")
    class FinishSession {

        @Test
        @DisplayName("Возвращает 201, Location и отчёт; offerProbability сериализуется русским лейблом, recommendations null")
        void returns201WithReport() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(interviewService.createReport(sessionId, USER_ID)).thenReturn(reportResponse(sessionId, null));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/finish")
                            .with(user(principal())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/sessions/" + sessionId + "/report"))
                    .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                    .andExpect(jsonPath("$.offerProbability").value("Средняя"))
                    .andExpect(jsonPath("$.recommendations").doesNotExist());
        }

        @Test
        @DisplayName("Возвращает recommendations, когда сервис их вернул")
        void returnsRecommendationsWhenPresent() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(interviewService.createReport(sessionId, USER_ID))
                    .thenReturn(reportResponse(sessionId, "Повторить индексацию и explain analyze"));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/finish")
                            .with(user(principal())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.recommendations").value("Повторить индексацию и explain analyze"));
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия не найдена")
        void returns404WhenSessionNotFound() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(interviewService.createReport(sessionId, USER_ID)).thenThrow(new NotFoundException("Session not found"));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/finish")
                            .with(user(principal())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 409, когда отвечены не все вопросы")
        void returns409WhenNotAllAnswered() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(interviewService.createReport(sessionId, USER_ID))
                    .thenThrow(new ConflictException("Not all questions answered"));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/finish")
                            .with(user(principal())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0]").value("Not all questions answered"));
        }

        @Test
        @DisplayName("Возвращает 503, когда AI-сервис недоступен")
        void returns503WhenLlmUnavailable() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(interviewService.createReport(sessionId, USER_ID)).thenThrow(new LlmException("LLM недоступен"));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/finish")
                            .with(user(principal())))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var sessionId = UUID.randomUUID();

            // when / then
            mvc.perform(post(BASE + "/sessions/" + sessionId + "/finish"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /sessions/{sessionId}/report
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetReport")
    class GetReport {

        @Test
        @DisplayName("Возвращает 200 с отчётом; offerProbability сериализуется русским лейблом")
        void returns200WithReport() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(interviewService.getReport(sessionId, USER_ID)).thenReturn(reportResponse(sessionId, null));

            // when / then
            mvc.perform(get(BASE + "/sessions/" + sessionId + "/report")
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                    .andExpect(jsonPath("$.offerProbability").value("Средняя"))
                    .andExpect(jsonPath("$.recommendations").doesNotExist());
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия или отчёт не найдены")
        void returns404WhenNotFound() throws Exception {
            // given
            var sessionId = UUID.randomUUID();
            when(interviewService.getReport(sessionId, USER_ID)).thenThrow(new NotFoundException("Report not found"));

            // when / then
            mvc.perform(get(BASE + "/sessions/" + sessionId + "/report")
                            .with(user(principal())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("Report not found"));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var sessionId = UUID.randomUUID();

            // when / then
            mvc.perform(get(BASE + "/sessions/" + sessionId + "/report"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /vacancies
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetAllVacancies")
    class GetAllVacancies {

        @Test
        @DisplayName("Возвращает 200 со списком вакансий пользователя")
        void returns200WithVacancies() throws Exception {
            // given
            when(interviewVacancyService.getAll(USER_ID)).thenReturn(List.of(vacancyResponse("123456")));

            // when / then
            mvc.perform(get(BASE + "/vacancies")
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].vacancyId").value("123456"))
                    .andExpect(jsonPath("$[0].bestOffer").value("Высокая"));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/vacancies"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewVacancyService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /vacancies/{vacancyId}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetVacancy")
    class GetVacancy {

        @Test
        @DisplayName("Возвращает 200 с деталями вакансии")
        void returns200WithVacancy() throws Exception {
            // given
            var vacancyId = "123456";
            when(interviewVacancyService.get(vacancyId, USER_ID)).thenReturn(vacancyDetailResponse(vacancyId));

            // when / then
            mvc.perform(get(BASE + "/vacancies/" + vacancyId)
                            .with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vacancyId").value(vacancyId))
                    .andExpect(jsonPath("$.vacancyName").value("Java-разработчик"));
        }

        @Test
        @DisplayName("Возвращает 404, когда у пользователя нет интервью по вакансии")
        void returns404WhenNotFound() throws Exception {
            // given
            var vacancyId = "123456";
            when(interviewVacancyService.get(vacancyId, USER_ID)).thenThrow(new NotFoundException("Vacancy not found"));

            // when / then
            mvc.perform(get(BASE + "/vacancies/" + vacancyId)
                            .with(user(principal())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("Vacancy not found"));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/vacancies/123456"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewVacancyService);
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /vacancies/{vacancyId}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DeleteVacancy")
    class DeleteVacancy {

        @Test
        @DisplayName("Возвращает 204 и передаёт в сервис vacancyId и userId")
        void returns204AndDeletesVacancy() throws Exception {
            // given
            var vacancyId = "123456";

            // when / then
            mvc.perform(delete(BASE + "/vacancies/" + vacancyId)
                            .with(user(principal())))
                    .andExpect(status().isNoContent());

            verify(interviewVacancyService).delete(vacancyId, USER_ID);
        }

        @Test
        @DisplayName("Возвращает 404, когда у пользователя нет интервью по вакансии")
        void returns404WhenNotFound() throws Exception {
            // given
            var vacancyId = "123456";
            doThrow(new NotFoundException("Vacancy not found"))
                    .when(interviewVacancyService).delete(vacancyId, USER_ID);

            // when / then
            mvc.perform(delete(BASE + "/vacancies/" + vacancyId)
                            .with(user(principal())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("Vacancy not found"));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(delete(BASE + "/vacancies/123456"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewVacancyService);
        }
    }
}
