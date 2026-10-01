package ru.workbit.resume.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.exception.TooManyRequestsException;
import ru.workbit.exception.UnprocessableEntityException;
import ru.workbit.exception.controller.ExceptionController;
import ru.workbit.resume.dto.ResumeFile;
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
            when(resumeService.upload(USER_ID, FILENAME, PDF_BYTES)).thenReturn(aResume(RESUME_ID, "Иванов Java"));

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
            when(resumeService.upload(USER_ID, FILENAME, PDF_BYTES)).thenReturn(aResume(RESUME_ID, "Иванов Java"));

            // when
            mvc.perform(multipart(BASE).file(aFile()).with(user(principal())))
                    .andExpect(status().isCreated());

            // then
            verify(resumeService).upload(USER_ID, FILENAME, PDF_BYTES);
        }

        @Test
        @DisplayName("Возвращает 422 и сообщение об ошибке, когда формат не поддерживается")
        void returns422WhenFormatUnsupported() throws Exception {
            // given
            when(resumeService.upload(USER_ID, FILENAME, PDF_BYTES))
                    .thenThrow(new UnprocessableEntityException("Unsupported format"));

            // when / then
            mvc.perform(multipart(BASE).file(aFile()).with(user(principal())))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0]").value("Unsupported format"));
        }

        @Test
        @DisplayName("Возвращает 400 с сообщением про часть file, когда прислана другая часть")
        void returns400WhenFilePartMissingButOtherPartPresent() throws Exception {
            // given
            var other = new MockMultipartFile("other", FILENAME, "application/pdf", PDF_BYTES);

            // when / then
            mvc.perform(multipart(BASE).file(other).with(user(principal())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation Failed"))
                    .andExpect(jsonPath("$.errors[0]").value(containsString("'file'")));
            verifyNoInteractions(resumeService);
        }

        @Test
        @DisplayName("Возвращает 400 с сообщением про часть file, когда multipart пустой")
        void returns400WhenMultipartEmpty() throws Exception {
            // when / then
            mvc.perform(multipart(BASE).with(user(principal())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(containsString("'file'")));
            verifyNoInteractions(resumeService);
        }

        @Test
        @DisplayName("Возвращает 413 и File too large, когда сервис бросает MaxUploadSizeExceededException")
        void returns413WhenUploadTooLarge() throws Exception {
            // given
            when(resumeService.upload(USER_ID, FILENAME, PDF_BYTES))
                    .thenThrow(new MaxUploadSizeExceededException(5L * 1024 * 1024));

            // when / then
            mvc.perform(multipart(BASE).file(aFile()).with(user(principal())))
                    .andExpect(status().isContentTooLarge())
                    .andExpect(jsonPath("$.message").value("Content too large."))
                    .andExpect(jsonPath("$.errors[0]").value("File too large"));
        }

        @Test
        @DisplayName("Возвращает 409 и сообщение, когда достигнут лимит резюме")
        void returns409WhenLimitReached() throws Exception {
            // given
            when(resumeService.upload(USER_ID, FILENAME, PDF_BYTES))
                    .thenThrow(new ConflictException("Resume limit reached"));

            // when / then
            mvc.perform(multipart(BASE).file(aFile()).with(user(principal())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0]").value("Resume limit reached"));
        }

        @Test
        @DisplayName("Возвращает 429 и сообщение, когда превышен суточный лимит загрузок")
        void returns429WhenTooManyRequests() throws Exception {
            // given
            when(resumeService.upload(USER_ID, FILENAME, PDF_BYTES))
                    .thenThrow(new TooManyRequestsException("Too many requests"));

            // when / then
            mvc.perform(multipart(BASE).file(aFile()).with(user(principal())))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.errors[0]").value("Too many requests"));
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
            when(resumeService.list(USER_ID)).thenReturn(List.of(aResume(RESUME_ID, "Новое"), aResume(secondId, "Старое")));

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
            when(resumeService.list(USER_ID)).thenReturn(List.of());

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

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("Возвращает 204 без тела и вызывает сервис с id пользователя и id из пути")
        void returns204AndCallsService() throws Exception {
            // when
            mvc.perform(delete(BASE + "/" + RESUME_ID).with(user(principal())))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            // then
            verify(resumeService).delete(USER_ID, RESUME_ID);
        }

        @Test
        @DisplayName("Возвращает 404 и сообщение, когда резюме не найдено")
        void returns404WhenNotFound() throws Exception {
            // given
            doThrow(new NotFoundException("Resume not found")).when(resumeService).delete(USER_ID, RESUME_ID);

            // when / then
            mvc.perform(delete(BASE + "/" + RESUME_ID).with(user(principal())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("Resume not found"));
        }

        @Test
        @DisplayName("Возвращает 400, когда id в пути не UUID, и не вызывает сервис")
        void returns400WhenIdNotUuid() throws Exception {
            // when / then
            mvc.perform(delete(BASE + "/not-a-uuid").with(user(principal())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation Failed"))
                    .andExpect(jsonPath("$.errors[0]").value("Parameter 'id' should be of type java.util.UUID"));
            verifyNoInteractions(resumeService);
        }

        @Test
        @DisplayName("Возвращает 401 без аутентификации и не вызывает сервис")
        void returns401WithoutAuth() throws Exception {
            // when / then
            mvc.perform(delete(BASE + "/" + RESUME_ID))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(resumeService);
        }
    }

    @Nested
    @DisplayName("File")
    class File {

        private static final String DOCX_TYPE =
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        private String url() {
            return BASE + "/" + RESUME_ID + "/file";
        }

        @Test
        @DisplayName("Отдаёт PDF: 200, application/pdf, inline и исходные байты")
        void returnsPdfInline() throws Exception {
            // given
            when(resumeService.file(USER_ID, RESUME_ID))
                    .thenReturn(new ResumeFile("resume.pdf", Resume.Format.PDF, null, PDF_BYTES));

            // when / then
            mvc.perform(get(url()).with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(header().string("Content-Disposition", startsWith("inline")))
                    .andExpect(content().bytes(PDF_BYTES));
            verify(resumeService).file(USER_ID, RESUME_ID);
        }

        @Test
        @DisplayName("Отдаёт DOCX: тип DOCX и attachment")
        void returnsDocxAsAttachment() throws Exception {
            // given
            var bytes = new byte[] {'P', 'K', 3, 4, 1, 2};
            when(resumeService.file(USER_ID, RESUME_ID))
                    .thenReturn(new ResumeFile("resume.docx", Resume.Format.DOCX, null, bytes));

            // when / then
            mvc.perform(get(url()).with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", DOCX_TYPE))
                    .andExpect(header().string("Content-Disposition", startsWith("attachment")))
                    .andExpect(content().bytes(bytes));
        }

        @Test
        @DisplayName("Отдаёт TXT в windows-1251 с charset в Content-Type и байтами без перекодирования")
        void returnsTxtWindows1251Untouched() throws Exception {
            // given
            var charset = Charset.forName("windows-1251");
            var bytes = "Привет, резюме".getBytes(charset);
            when(resumeService.file(USER_ID, RESUME_ID))
                    .thenReturn(new ResumeFile("resume.txt", Resume.Format.TXT, charset, bytes));

            // when / then
            mvc.perform(get(url()).with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", containsString("text/plain")))
                    .andExpect(header().string("Content-Type", containsString("charset=windows-1251")))
                    .andExpect(header().string("Content-Disposition", startsWith("inline")))
                    .andExpect(content().bytes(bytes));
        }

        @Test
        @DisplayName("Отдаёт TXT в UTF-8 с charset=UTF-8")
        void returnsTxtUtf8() throws Exception {
            // given
            var bytes = "Привет".getBytes(StandardCharsets.UTF_8);
            when(resumeService.file(USER_ID, RESUME_ID))
                    .thenReturn(new ResumeFile("resume.txt", Resume.Format.TXT, StandardCharsets.UTF_8, bytes));

            // when / then
            mvc.perform(get(url()).with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", containsString("charset=UTF-8")))
                    .andExpect(content().bytes(bytes));
        }

        @Test
        @DisplayName("Кодирует кириллическое имя файла по RFC 5987 в Content-Disposition")
        void encodesCyrillicFilename() throws Exception {
            // given
            when(resumeService.file(USER_ID, RESUME_ID))
                    .thenReturn(new ResumeFile(FILENAME, Resume.Format.PDF, null, PDF_BYTES));

            // when / then
            mvc.perform(get(url()).with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
                    .andExpect(header().string("Content-Disposition",
                            containsString("%D0%98%D0%B2%D0%B0%D0%BD%D0%BE%D0%B2")))
                    .andExpect(header().string("Content-Disposition", not(containsString("Иванов"))));
        }

        @Test
        @DisplayName("Добавляет Content-Security-Policy: sandbox и X-Frame-Options: DENY")
        void addsSecurityHeaders() throws Exception {
            // given
            when(resumeService.file(USER_ID, RESUME_ID))
                    .thenReturn(new ResumeFile("resume.pdf", Resume.Format.PDF, null, PDF_BYTES));

            // when / then
            mvc.perform(get(url()).with(user(principal())))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy", "sandbox"))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
        }

        @Test
        @DisplayName("Возвращает 404 и Resume not found, когда резюме не найдено")
        void returns404WhenResumeNotFound() throws Exception {
            // given
            when(resumeService.file(USER_ID, RESUME_ID)).thenThrow(new NotFoundException("Resume not found"));

            // when / then
            mvc.perform(get(url()).with(user(principal())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("Resume not found"));
        }

        @Test
        @DisplayName("Возвращает 404 и Resume file missing, когда файла нет на диске")
        void returns404WhenFileMissing() throws Exception {
            // given
            when(resumeService.file(USER_ID, RESUME_ID)).thenThrow(new NotFoundException("Resume file missing"));

            // when / then
            mvc.perform(get(url()).with(user(principal())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("Resume file missing"));
        }

        @Test
        @DisplayName("Возвращает 400, когда id в пути не UUID, и не вызывает сервис")
        void returns400WhenIdNotUuid() throws Exception {
            // when / then
            mvc.perform(get(BASE + "/not-a-uuid/file").with(user(principal())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("Parameter 'id' should be of type java.util.UUID"));
            verifyNoInteractions(resumeService);
        }

        @Test
        @DisplayName("Возвращает 401 без аутентификации и не вызывает сервис")
        void returns401WithoutAuth() throws Exception {
            // when / then
            mvc.perform(get(url()))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(resumeService);
        }
    }

    @Nested
    @DisplayName("Rename")
    class Rename {

        private String url() {
            return BASE + "/" + RESUME_ID;
        }

        private String body(String name) {
            return "{\"name\":\"" + name + "\"}";
        }

        private ResultActions rename(String json) throws Exception {
            return mvc.perform(patch(url()).contentType(MediaType.APPLICATION_JSON).content(json)
                    .with(user(principal())));
        }

        @Test
        @DisplayName("Возвращает 200 и резюме с новым названием, в сервис уходит обрезанное название")
        void returns200AndPassesStrippedName() throws Exception {
            // given
            when(resumeService.rename(USER_ID, RESUME_ID, "Новое")).thenReturn(aResume(RESUME_ID, "Новое"));

            // when / then
            rename(body("  Новое  "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(RESUME_ID.toString()))
                    .andExpect(jsonPath("$.name").value("Новое"))
                    .andExpect(jsonPath("$.format").value("PDF"))
                    .andExpect(jsonPath("$.originalFilename").value("Новое.pdf"))
                    .andExpect(jsonPath("$.sizeBytes").value(1024))
                    .andExpect(jsonPath("$.uploadedAt").exists());
            verify(resumeService).rename(USER_ID, RESUME_ID, "Новое");
        }

        @Test
        @DisplayName("Принимает 100 символов после обрезки пробелов")
        void accepts100CharsAfterStrip() throws Exception {
            // given
            var name = "ы".repeat(100);
            when(resumeService.rename(USER_ID, RESUME_ID, name)).thenReturn(aResume(RESUME_ID, name));

            // when / then
            rename(body("  " + name + "  "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(name));
            verify(resumeService).rename(USER_ID, RESUME_ID, name);
        }

        @Test
        @DisplayName("Возвращает 400 на пустую строку и не вызывает сервис")
        void returns400WhenNameEmpty() throws Exception {
            // when / then
            rename(body(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation Failed"))
                    .andExpect(jsonPath("$.errors.length()").value(1));
            verifyNoInteractions(resumeService);
        }

        @Test
        @DisplayName("Возвращает 400 на строку из одних пробелов и не вызывает сервис")
        void returns400WhenNameBlank() throws Exception {
            // when / then
            rename(body("     "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation Failed"));
            verifyNoInteractions(resumeService);
        }

        @Test
        @DisplayName("Возвращает 400 на 101 символ и не вызывает сервис")
        void returns400WhenNameTooLong() throws Exception {
            // when / then
            rename(body("ы".repeat(101)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation Failed"));
            verifyNoInteractions(resumeService);
        }

        @Test
        @DisplayName("Возвращает 400, когда name равен null, и не вызывает сервис")
        void returns400WhenNameNull() throws Exception {
            // when / then
            rename("{\"name\":null}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation Failed"));
            verifyNoInteractions(resumeService);
        }

        @Test
        @DisplayName("Возвращает 400, когда name отсутствует в JSON, и не вызывает сервис")
        void returns400WhenNameMissing() throws Exception {
            // when / then
            rename("{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation Failed"));
            verifyNoInteractions(resumeService);
        }

        @Test
        @DisplayName("Возвращает 404 и Resume not found, когда резюме не найдено")
        void returns404WhenNotFound() throws Exception {
            // given
            when(resumeService.rename(USER_ID, RESUME_ID, "Новое")).thenThrow(new NotFoundException("Resume not found"));

            // when / then
            rename(body("Новое"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("Resume not found"));
        }

        @Test
        @DisplayName("Возвращает 400, когда id в пути не UUID, и не вызывает сервис")
        void returns400WhenIdNotUuid() throws Exception {
            // when / then
            mvc.perform(patch(BASE + "/not-a-uuid").contentType(MediaType.APPLICATION_JSON).content(body("Новое"))
                            .with(user(principal())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("Parameter 'id' should be of type java.util.UUID"));
            verifyNoInteractions(resumeService);
        }

        @Test
        @DisplayName("Возвращает 401 без аутентификации и не вызывает сервис")
        void returns401WithoutAuth() throws Exception {
            // when / then
            mvc.perform(patch(url()).contentType(MediaType.APPLICATION_JSON).content(body("Новое")))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(resumeService);
        }
    }
}
