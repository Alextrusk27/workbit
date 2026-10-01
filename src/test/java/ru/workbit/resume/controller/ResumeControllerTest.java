package ru.workbit.resume.controller;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.workbit.exception.UnprocessableEntityException;
import ru.workbit.exception.controller.ExceptionController;
import ru.workbit.resume.dto.ResumeResponse;
import ru.workbit.resume.model.Resume;
import ru.workbit.resume.service.ResumeService;
import ru.workbit.security.config.RateLimitProperties;
import ru.workbit.security.config.SecurityConfig;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.JWTService;
import ru.workbit.security.service.RateLimiterService;
import ru.workbit.security.service.UserDetailsServiceImpl;

@WebMvcTest(ResumeController.class)
@Import({SecurityConfig.class, ExceptionController.class})
@EnableConfigurationProperties(RateLimitProperties.class)
@DisplayName("ResumeControllerTest")
class ResumeControllerTest {

    private static final String BASE = "/api/v1/resumes";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESUME_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant UPLOADED_AT = Instant.parse("2026-10-01T10:15:30Z");
    private static final String FILENAME = "Иванов Java.pdf";
    private static final byte[] PDF_BYTES = "%PDF-1.7 content".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ResumeService resumeService;

    @MockitoBean
    JWTService jwtService;

    @MockitoBean
    UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    RateLimiterService rateLimiter;

    private CustomUserDetails principal() {
        return new CustomUserDetails(USER_ID, "user@example.com", List.of());
    }

    private ResumeResponse aResume(UUID id, String name) {
        return new ResumeResponse(id, name, Resume.Format.PDF, name + ".pdf", 1024, UPLOADED_AT);
    }

    private MockMultipartFile aFile() {
        return new MockMultipartFile("file", FILENAME, "application/pdf", PDF_BYTES);
    }

    @Nested
    @DisplayName("Upload")
    class Upload {

        @Test
        @DisplayName("Возвращает 201, Location и тело с метаданными резюме")
        void returns201WithLocationAndBody() throws Exception {
            // given
            when(resumeService.upload(any(), any(), any())).thenReturn(aResume(RESUME_ID, "Иванов Java"));

            // when / then
            mvc.perform(multipart(BASE).file(aFile()).with(user(principal())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", BASE + "/" + RESUME_ID))
                    .andExpect(jsonPath("$.id").value(RESUME_ID.toString()))
                    .andExpect(jsonPath("$.name").value("Иванов Java"))
                    .andExpect(jsonPath("$.format").value("PDF"))
                    .andExpect(jsonPath("$.originalFilename").value("Иванов Java.pdf"))
                    .andExpect(jsonPath("$.sizeBytes").value(1024))
                    .andExpect(jsonPath("$.uploadedAt").exists());
        }

        @Test
        @DisplayName("Передаёт в сервис id пользователя из принципала и загруженный файл")
        void passesUserIdAndFileToService() throws Exception {
            // given
            when(resumeService.upload(any(), any(), any())).thenReturn(aResume(RESUME_ID, "Иванов Java"));

            // when
            mvc.perform(multipart(BASE).file(aFile()).with(user(principal())))
                    .andExpect(status().isCreated());

            // then
            verify(resumeService).upload(eq(USER_ID), eq(FILENAME), aryEq(PDF_BYTES));
        }

        @Test
        @DisplayName("Возвращает 422 и сообщение об ошибке, когда формат не поддерживается")
        void returns422WhenFormatUnsupported() throws Exception {
            // given
            when(resumeService.upload(any(), any(), any()))
                    .thenThrow(new UnprocessableEntityException("Unsupported format"));

            // when / then
            mvc.perform(multipart(BASE).file(aFile()).with(user(principal())))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0]").value("Unsupported format"));
        }

        @Test
        @DisplayName("Возвращает 401 без аутентификации и не вызывает сервис")
        void returns401WithoutAuth() throws Exception {
            // when / then
            mvc.perform(multipart(BASE).file(aFile()))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(resumeService);
        }
    }

    @Nested
    @DisplayName("List")
    class ListResumes {

        @Test
        @DisplayName("Возвращает 200 и массив в порядке, отданном сервисом")
        void returns200WithOrderedArray() throws Exception {
            // given
            var secondId = UUID.fromString("33333333-3333-3333-3333-333333333333");
            when(resumeService.list(any())).thenReturn(List.of(aResume(RESUME_ID, "Новое"), aResume(secondId, "Старое")));

            // when / then
            mvc.perform(get(BASE).with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(RESUME_ID.toString()))
                    .andExpect(jsonPath("$[0].name").value("Новое"))
                    .andExpect(jsonPath("$[1].id").value(secondId.toString()))
                    .andExpect(jsonPath("$[1].name").value("Старое"));
        }

        @Test
        @DisplayName("Вызывает сервис с id текущего пользователя")
        void callsServiceWithCurrentUserId() throws Exception {
            // given
            when(resumeService.list(any())).thenReturn(List.of());

            // when
            mvc.perform(get(BASE).with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));

            // then
            verify(resumeService).list(USER_ID);
        }

        @Test
        @DisplayName("Возвращает 401 без аутентификации и не вызывает сервис")
        void returns401WithoutAuth() throws Exception {
            // when / then
            mvc.perform(get(BASE))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(resumeService);
        }
    }
}
