package ru.workbit.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.exception.controller.ExceptionController;
import ru.workbit.interview.dto.*;
import ru.workbit.interview.model.*;
import ru.workbit.interview.service.InterviewService;
import ru.workbit.security.config.SecurityConfig;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.JWTService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
@Import({SecurityConfig.class, ExceptionController.class})
@DisplayName("InterviewControllerTest")
class InterviewControllerTest {

    private static final String BASE = "/api/v1/interview";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID QUESTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    MockMvc mvc;

    // ObjectMapper создаём напрямую: в SB4 @WebMvcTest не включает JacksonAutoConfiguration автоматически
    private final ObjectMapper om = new ObjectMapper();

    @MockitoBean
    InterviewService interviewService;

    // JWTAuthFilter-зависимости: нужны, чтобы SecurityConfig мог создать фильтр
    @MockitoBean
    JWTService jwtService;

    @MockitoBean
    UserDetailsService userDetailsService;

    private CustomUserDetails principal() {
        return new CustomUserDetails(USER_ID, "user@example.com", "hash", true, List.of());
    }

    private CreateSessionRequest aCreateSessionRequest() {
        return new CreateSessionRequest(Profession.JAVA_DEV, Level.MIDDLE, CompanyType.PRODUCT, 10);
    }

    private SessionResponse aSessionResponse() {
        return new SessionResponse(
                SESSION_ID, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                SessionStatus.CREATED, 10, 0,
                Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    private QuestionResponse aQuestionResponse() {
        return new QuestionResponse(QUESTION_ID, 1, "Что такое индекс в БД?", null, null, null);
    }

    private SessionReport aSessionReport() {
        return new SessionReport(
                UUID.randomUUID(), SESSION_ID, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                10, 7.5, "Хороший результат", OfferProbability.MEDIUM,
                Instant.parse("2026-01-02T00:00:00Z"));
    }

    private InterviewOptionsResponse anOptionsResponse() {
        return new InterviewOptionsResponse(
                List.of(Profession.values()), List.of(Level.values()), List.of(CompanyType.values()),
                CreateSessionRequest.MIN_QUESTIONS, CreateSessionRequest.MAX_QUESTIONS);
    }

    // -------------------------------------------------------------------------
    // GET /options
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetOptions")
    class GetOptions {

        @Test
        @DisplayName("Возвращает 200 и справочник значений")
        void returns200WithOptions() throws Exception {
            // given
            when(interviewService.getOptions()).thenReturn(anOptionsResponse());

            // when / then
            mvc.perform(get(BASE + "/options").with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.minQuestions").value(CreateSessionRequest.MIN_QUESTIONS))
                    .andExpect(jsonPath("$.maxQuestions").value(CreateSessionRequest.MAX_QUESTIONS));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/options"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }
    }

    // -------------------------------------------------------------------------
    // POST /sessions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CreateSession")
    class CreateSession {

        @Test
        @DisplayName("Возвращает 201 и Location при успешном создании")
        void returns201WithLocation() throws Exception {
            // given
            var request = aCreateSessionRequest();
            when(interviewService.createSession(any(CreateSessionRequest.class), eq(USER_ID)))
                    .thenReturn(aSessionResponse());

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, "/sessions/" + SESSION_ID))
                    .andExpect(jsonPath("$.id").value(SESSION_ID.toString()));

            verify(interviewService).createSession(any(CreateSessionRequest.class), eq(USER_ID));
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var request = aCreateSessionRequest();

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }

        @Test
        @DisplayName("Возвращает 400, когда profession отсутствует")
        void returns400WhenProfessionMissing() throws Exception {
            // given
            var request = new CreateSessionRequest(null, Level.MIDDLE, CompanyType.PRODUCT, 10);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Возвращает 400, когда level отсутствует")
        void returns400WhenLevelMissing() throws Exception {
            // given
            var request = new CreateSessionRequest(Profession.JAVA_DEV, null, CompanyType.PRODUCT, 10);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Возвращает 400, когда companyType отсутствует")
        void returns400WhenCompanyTypeMissing() throws Exception {
            // given
            var request = new CreateSessionRequest(Profession.JAVA_DEV, Level.MIDDLE, null, 10);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Возвращает 400, когда totalQuestions отсутствует")
        void returns400WhenTotalQuestionsMissing() throws Exception {
            // given
            var request = new CreateSessionRequest(Profession.JAVA_DEV, Level.MIDDLE, CompanyType.PRODUCT, null);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Возвращает 400, когда totalQuestions меньше минимума")
        void returns400WhenTotalQuestionsBelowMin() throws Exception {
            // given
            var request = new CreateSessionRequest(
                    Profession.JAVA_DEV, Level.MIDDLE, CompanyType.PRODUCT,
                    CreateSessionRequest.MIN_QUESTIONS - 1);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(interviewService);
        }

        @Test
        @DisplayName("Возвращает 400, когда totalQuestions больше максимума")
        void returns400WhenTotalQuestionsAboveMax() throws Exception {
            // given
            var request = new CreateSessionRequest(
                    Profession.JAVA_DEV, Level.MIDDLE, CompanyType.PRODUCT,
                    CreateSessionRequest.MAX_QUESTIONS + 1);

            // when / then
            mvc.perform(post(BASE + "/sessions")
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(interviewService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /sessions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetAllSessions")
    class GetAllSessions {

        @Test
        @DisplayName("Возвращает 200 и список сессий")
        void returns200WithList() throws Exception {
            // given
            when(interviewService.getAllSessions(USER_ID)).thenReturn(List.of(aSessionResponse()));

            // when / then
            mvc.perform(get(BASE + "/sessions").with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(SESSION_ID.toString()));

            verify(interviewService).getAllSessions(USER_ID);
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/sessions"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /sessions/{sessionId}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetSession")
    class GetSession {

        @Test
        @DisplayName("Возвращает 200 и сессию")
        void returns200WithSession() throws Exception {
            // given
            when(interviewService.getSession(SESSION_ID, USER_ID)).thenReturn(aSessionResponse());

            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID).with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(SESSION_ID.toString()));
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия не найдена")
        void returns404WhenNotFound() throws Exception {
            // given
            when(interviewService.getSession(SESSION_ID, USER_ID))
                    .thenThrow(new NotFoundException("Session not found"));

            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID).with(user(principal())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /sessions/{sessionId}/continue
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ContinueSession")
    class ContinueSession {

        @Test
        @DisplayName("Возвращает 200 и следующий вопрос")
        void returns200WithQuestion() throws Exception {
            // given
            when(interviewService.continueSession(SESSION_ID, USER_ID)).thenReturn(aQuestionResponse());

            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID + "/continue").with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionId").value(QUESTION_ID.toString()));
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия не найдена или вопросов не осталось")
        void returns404WhenNotFound() throws Exception {
            // given
            when(interviewService.continueSession(SESSION_ID, USER_ID))
                    .thenThrow(new NotFoundException("This session finished"));

            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID + "/continue").with(user(principal())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID + "/continue"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }
    }

    // -------------------------------------------------------------------------
    // GET /sessions/{sessionId}/questions/{index}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetQuestion")
    class GetQuestion {

        @Test
        @DisplayName("Возвращает 200 и вопрос по индексу")
        void returns200WithQuestion() throws Exception {
            // given
            when(interviewService.getQuestion(new QuestionRequest(SESSION_ID, 1, USER_ID)))
                    .thenReturn(aQuestionResponse());

            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID + "/questions/1").with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderIndex").value(1));
        }

        @Test
        @DisplayName("Возвращает 404, когда вопрос не найден")
        void returns404WhenNotFound() throws Exception {
            // given
            when(interviewService.getQuestion(new QuestionRequest(SESSION_ID, 99, USER_ID)))
                    .thenThrow(new NotFoundException("Question not found"));

            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID + "/questions/99").with(user(principal())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID + "/questions/1"))
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
        @DisplayName("Возвращает 200 и evaluate=false по умолчанию")
        void returns200WithEvaluateFalseByDefault() throws Exception {
            // given
            var body = new SubmitAnswerBody("Использую индексы");
            when(interviewService.submitAnswer(any(SubmitAnswerRequest.class))).thenReturn(aQuestionResponse());

            // when
            mvc.perform(post(BASE + "/sessions/" + SESSION_ID + "/questions/" + QUESTION_ID)
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // then
            var captor = org.mockito.ArgumentCaptor.forClass(SubmitAnswerRequest.class);
            verify(interviewService).submitAnswer(captor.capture());
            var captured = captor.getValue();
            org.assertj.core.api.Assertions.assertThat(captured.userId()).isEqualTo(USER_ID);
            org.assertj.core.api.Assertions.assertThat(captured.sessionId()).isEqualTo(SESSION_ID);
            org.assertj.core.api.Assertions.assertThat(captured.questionId()).isEqualTo(QUESTION_ID);
            org.assertj.core.api.Assertions.assertThat(captured.evaluate()).isFalse();
            org.assertj.core.api.Assertions.assertThat(captured.answerText()).isEqualTo("Использую индексы");
        }

        @Test
        @DisplayName("Прокидывает evaluate=true из query-параметра")
        void propagatesEvaluateTrue() throws Exception {
            // given
            var body = new SubmitAnswerBody("Использую индексы");
            when(interviewService.submitAnswer(any(SubmitAnswerRequest.class))).thenReturn(aQuestionResponse());

            // when
            mvc.perform(post(BASE + "/sessions/" + SESSION_ID + "/questions/" + QUESTION_ID)
                            .with(user(principal()))
                            .queryParam("evaluate", "true")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // then
            var captor = org.mockito.ArgumentCaptor.forClass(SubmitAnswerRequest.class);
            verify(interviewService).submitAnswer(captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().evaluate()).isTrue();
        }

        @Test
        @DisplayName("Возвращает 400, когда answerText пустой")
        void returns400WhenAnswerTextBlank() throws Exception {
            // given
            var body = new SubmitAnswerBody("");

            // when / then
            mvc.perform(post(BASE + "/sessions/" + SESSION_ID + "/questions/" + QUESTION_ID)
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
            var body = new SubmitAnswerBody("Использую индексы");
            when(interviewService.submitAnswer(any(SubmitAnswerRequest.class)))
                    .thenThrow(new ForbiddenException("Access denied"));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + SESSION_ID + "/questions/" + QUESTION_ID)
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Возвращает 404, когда вопрос не найден")
        void returns404WhenNotFound() throws Exception {
            // given
            var body = new SubmitAnswerBody("Использую индексы");
            when(interviewService.submitAnswer(any(SubmitAnswerRequest.class)))
                    .thenThrow(new NotFoundException("Question not found"));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + SESSION_ID + "/questions/" + QUESTION_ID)
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 409, когда вопрос уже отвечен или из другой сессии")
        void returns409WhenConflict() throws Exception {
            // given
            var body = new SubmitAnswerBody("Использую индексы");
            when(interviewService.submitAnswer(any(SubmitAnswerRequest.class)))
                    .thenThrow(new ConflictException("Question already answered"));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + SESSION_ID + "/questions/" + QUESTION_ID)
                            .with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // given
            var body = new SubmitAnswerBody("Использую индексы");

            // when / then
            mvc.perform(post(BASE + "/sessions/" + SESSION_ID + "/questions/" + QUESTION_ID)
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
        @DisplayName("Возвращает 201 и Location при успешном завершении")
        void returns201WithLocation() throws Exception {
            // given
            when(interviewService.finishSession(SESSION_ID, USER_ID)).thenReturn(aSessionReport());

            // when / then
            mvc.perform(post(BASE + "/sessions/" + SESSION_ID + "/finish").with(user(principal())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, "/sessions/" + SESSION_ID + "/report"))
                    .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()));
        }

        @Test
        @DisplayName("Возвращает 403, когда сессия принадлежит другому пользователю")
        void returns403WhenForbidden() throws Exception {
            // given
            when(interviewService.finishSession(SESSION_ID, USER_ID))
                    .thenThrow(new ForbiddenException("Access denied"));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + SESSION_ID + "/finish").with(user(principal())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия не найдена")
        void returns404WhenNotFound() throws Exception {
            // given
            when(interviewService.finishSession(SESSION_ID, USER_ID))
                    .thenThrow(new NotFoundException("Session not found"));

            // when / then
            mvc.perform(post(BASE + "/sessions/" + SESSION_ID + "/finish").with(user(principal())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(post(BASE + "/sessions/" + SESSION_ID + "/finish"))
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
        @DisplayName("Возвращает 200 и отчёт")
        void returns200WithReport() throws Exception {
            // given
            when(interviewService.getSessionReport(SESSION_ID, USER_ID)).thenReturn(aSessionReport());

            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID + "/report").with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()));
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия не найдена")
        void returns404WhenNotFound() throws Exception {
            // given
            when(interviewService.getSessionReport(SESSION_ID, USER_ID))
                    .thenThrow(new NotFoundException("Session not found"));

            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID + "/report").with(user(principal())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/sessions/" + SESSION_ID + "/report"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /sessions/{sessionId}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DeleteSession")
    class DeleteSession {

        @Test
        @DisplayName("Возвращает 204 при успешном удалении")
        void returns204OnSuccess() throws Exception {
            // given
            doNothing().when(interviewService).deleteSession(SESSION_ID, USER_ID);

            // when / then
            mvc.perform(delete(BASE + "/sessions/" + SESSION_ID).with(user(principal())))
                    .andExpect(status().isNoContent());

            verify(interviewService).deleteSession(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("Возвращает 404, когда сессия не найдена")
        void returns404WhenNotFound() throws Exception {
            // given
            doThrow(new NotFoundException("Session not found"))
                    .when(interviewService).deleteSession(SESSION_ID, USER_ID);

            // when / then
            mvc.perform(delete(BASE + "/sessions/" + SESSION_ID).with(user(principal())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Возвращает 401, когда нет аутентификации")
        void returns401WithoutAuthentication() throws Exception {
            // when / then
            mvc.perform(delete(BASE + "/sessions/" + SESSION_ID))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(interviewService);
        }
    }
}
