package ru.workbit.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.TooManyRequestsException;
import ru.workbit.exception.UnprocessableEntityException;
import ru.workbit.resume.dto.ResumeResponse;
import ru.workbit.resume.model.Resume;
import ru.workbit.resume.model.mapper.ResumeMapper;
import ru.workbit.resume.model.mapper.ResumeMapperImpl;
import ru.workbit.resume.repository.ResumeRepository;
import ru.workbit.security.config.RateLimitProperties;
import ru.workbit.security.service.RateLimiterService;
import ru.workbit.util.SingleFlight;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResumeServiceTest")
class ResumeServiceTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String FILENAME = "Иванов Java.pdf";
    private static final byte[] PDF_BYTES = "%PDF-1.7 body".getBytes(StandardCharsets.US_ASCII);
    private static final RateLimitProperties.Bucket UPLOAD_BUCKET =
            new RateLimitProperties.Bucket(20, Duration.ofDays(1));
    private static final RateLimitProperties.Bucket OTHER_BUCKET =
            new RateLimitProperties.Bucket(1, Duration.ofMinutes(1));
    private static final RateLimitProperties RATE_LIMITS = new RateLimitProperties(
            10, Duration.ofMinutes(1), OTHER_BUCKET, OTHER_BUCKET, OTHER_BUCKET, OTHER_BUCKET, UPLOAD_BUCKET);
    private static final String RATE_LIMIT_KEY = "resume-upload:" + USER_ID;

    @Mock
    ResumeRepository resumeRepository;

    @Mock
    ResumeWriter writer;

    @Mock
    ResumeFileStorage storage;

    @Mock
    ResumeFormatDetector formatDetector;

    @Mock
    RateLimiterService rateLimiter;

    @Spy
    SingleFlight singleFlight = new SingleFlight();

    @Spy
    ResumeMapper resumeMapper = new ResumeMapperImpl();

    ResumeService service;

    @BeforeEach
    void setUp() {
        service = new ResumeService(resumeRepository, writer, storage, formatDetector, rateLimiter,
                RATE_LIMITS, singleFlight, resumeMapper);
    }

    private static Resume aResume(String name) {
        return Resume.builder()
                .userId(USER_ID)
                .name(name)
                .originalFilename(name + ".pdf")
                .format(Resume.Format.PDF)
                .sizeBytes(100)
                .build();
    }

    @Nested
    @DisplayName("Upload")
    class Upload {

        @Test
        @DisplayName("Пишет файл в storage с тем же resumeId, что уходит в writer.save, и возвращает ответ")
        void storesFileAndReturnsResponse() {
            // given
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenReturn(Resume.Format.PDF);
            when(writer.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            ResumeResponse response = service.upload(USER_ID, FILENAME, PDF_BYTES);

            // then
            ArgumentCaptor<UUID> storedId = ArgumentCaptor.forClass(UUID.class);
            verify(storage).write(eq(USER_ID), storedId.capture(),
                    eq(PDF_BYTES));
            ArgumentCaptor<Resume> saved = ArgumentCaptor.forClass(Resume.class);
            verify(writer).save(saved.capture());
            assertThat(saved.getValue().getId()).isEqualTo(storedId.getValue());
            assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(response.id()).isEqualTo(storedId.getValue());
            assertThat(response.name()).isEqualTo("Иванов Java");
            assertThat(response.originalFilename()).isEqualTo(FILENAME);
            assertThat(response.format()).isEqualTo(Resume.Format.PDF);
            assertThat(response.sizeBytes()).isEqualTo(PDF_BYTES.length);
            assertThat(response.uploadedAt()).isNotNull();
        }

        @Test
        @DisplayName("Передаёт в детектор имя без пути и сохраняет его же как originalFilename")
        void passesStrippedFilenameToDetector() {
            // given
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenReturn(Resume.Format.PDF);
            when(writer.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            ResumeResponse response = service.upload(USER_ID, "C:\\Users\\ivan\\docs/" + FILENAME, PDF_BYTES);

            // then
            verify(formatDetector).detect(PDF_BYTES, FILENAME);
            assertThat(response.originalFilename()).isEqualTo(FILENAME);
        }

        @Test
        @DisplayName("Передаёт в детектор пустое имя, подставляет resume.<ext> и название «Резюме», когда имя файла null")
        void usesDefaultFilenameWhenNull() {
            // given
            when(formatDetector.detect(PDF_BYTES, "")).thenReturn(Resume.Format.PDF);
            when(writer.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            ResumeResponse response = service.upload(USER_ID, null, PDF_BYTES);

            // then
            assertThat(response.originalFilename()).isEqualTo("resume.pdf");
            assertThat(response.name()).isEqualTo("Резюме");
        }

        @Test
        @DisplayName("Подставляет resume.<ext> и название «Резюме», когда имя файла состоит из пробелов")
        void usesDefaultFilenameWhenBlank() {
            // given
            when(formatDetector.detect(PDF_BYTES, "")).thenReturn(Resume.Format.PDF);
            when(writer.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            ResumeResponse response = service.upload(USER_ID, "   ", PDF_BYTES);

            // then
            assertThat(response.originalFilename()).isEqualTo("resume.pdf");
            assertThat(response.name()).isEqualTo("Резюме");
        }

        @Test
        @DisplayName("Пробрасывает UnprocessableEntityException от детектора и не трогает лимитер, storage и writer")
        void propagatesUnsupportedFormat() {
            // given
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenThrow(new UnprocessableEntityException("Unsupported format"));

            // when / then
            assertThatThrownBy(() -> service.upload(USER_ID, FILENAME, PDF_BYTES))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage("Unsupported format");
            verifyNoInteractions(rateLimiter, storage, writer);
        }

        @ParameterizedTest
        @ValueSource(longs = {3, 4})
        @DisplayName("Бросает ConflictException без детектора, лимитера, storage и writer, когда резюме уже 3 или больше")
        void throwsConflictWhenLimitReached(long count) {
            // given
            when(resumeRepository.countByUserId(USER_ID)).thenReturn(count);

            // when / then
            assertThatThrownBy(() -> service.upload(USER_ID, FILENAME, PDF_BYTES))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Resume limit reached");
            verifyNoInteractions(formatDetector, rateLimiter, storage, writer);
        }

        @Test
        @DisplayName("Загружает резюме, когда уже 2 из 3 допустимых")
        void uploadsWhenBelowLimit() {
            // given
            when(resumeRepository.countByUserId(USER_ID)).thenReturn(2L);
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenReturn(Resume.Format.PDF);
            when(writer.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            ResumeResponse response = service.upload(USER_ID, FILENAME, PDF_BYTES);

            // then
            assertThat(response.name()).isEqualTo("Иванов Java");
            verify(writer).save(any(Resume.class));
        }

        @Test
        @DisplayName("Проверяет суточный лимит по ключу resume-upload:<uid> и бакету resumeUpload после детектора")
        void checksRateLimitAfterFormat() {
            // given
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenReturn(Resume.Format.PDF);
            when(writer.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            service.upload(USER_ID, FILENAME, PDF_BYTES);

            // then
            InOrder order = inOrder(resumeRepository, formatDetector, rateLimiter, storage, writer);
            order.verify(resumeRepository).countByUserId(USER_ID);
            order.verify(formatDetector).detect(PDF_BYTES, FILENAME);
            order.verify(rateLimiter).check(RATE_LIMIT_KEY, UPLOAD_BUCKET);
            order.verify(storage).write(eq(USER_ID), any(UUID.class), eq(PDF_BYTES));
            order.verify(writer).save(any(Resume.class));
        }

        @Test
        @DisplayName("Пробрасывает TooManyRequestsException от лимитера и не пишет файл и не зовёт writer")
        void propagatesRateLimit() {
            // given
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenReturn(Resume.Format.PDF);
            doThrow(new TooManyRequestsException("Too many requests"))
                    .when(rateLimiter).check(RATE_LIMIT_KEY, UPLOAD_BUCKET);

            // when / then
            assertThatThrownBy(() -> service.upload(USER_ID, FILENAME, PDF_BYTES))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage("Too many requests");
            verifyNoInteractions(storage, writer);
        }

        @Test
        @DisplayName("Удаляет файл и пробрасывает ConflictException, когда writer повторной проверкой отклонил загрузку")
        void deletesFileWhenWriterRejectsLimit() {
            // given
            RuntimeException conflict = new ConflictException("Resume limit reached");
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenReturn(Resume.Format.PDF);
            when(writer.save(any(Resume.class))).thenThrow(conflict);

            // when / then
            assertThatThrownBy(() -> service.upload(USER_ID, FILENAME, PDF_BYTES)).isSameAs(conflict);
            ArgumentCaptor<UUID> writtenId = ArgumentCaptor.forClass(UUID.class);
            verify(storage).write(eq(USER_ID), writtenId.capture(), eq(PDF_BYTES));
            verify(storage).delete(USER_ID, writtenId.getValue());
        }

        @Test
        @DisplayName("Удаляет файл с тем же resumeId и пробрасывает то же исключение, когда writer.save падает")
        void deletesFileWhenSaveFails() {
            // given
            RuntimeException saveError = new IllegalStateException("db down");
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenReturn(Resume.Format.PDF);
            when(writer.save(any(Resume.class))).thenThrow(saveError);

            // when / then
            assertThatThrownBy(() -> service.upload(USER_ID, FILENAME, PDF_BYTES)).isSameAs(saveError);
            ArgumentCaptor<UUID> writtenId = ArgumentCaptor.forClass(UUID.class);
            verify(storage).write(eq(USER_ID), writtenId.capture(),
                    eq(PDF_BYTES));
            verify(storage).delete(USER_ID, writtenId.getValue());
        }

        @Test
        @DisplayName("Пробрасывает исходное исключение, когда и storage.delete падает, удаление - в suppressed")
        void keepsOriginalErrorWhenDeleteAlsoFails() {
            // given
            RuntimeException saveError = new IllegalStateException("db down");
            RuntimeException deleteError = new UncheckedIOException(new IOException("disk"));
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenReturn(Resume.Format.PDF);
            when(writer.save(any(Resume.class))).thenThrow(saveError);
            doThrow(deleteError).when(storage).delete(any(UUID.class), any(UUID.class));

            // when / then
            assertThatThrownBy(() -> service.upload(USER_ID, FILENAME, PDF_BYTES))
                    .isSameAs(saveError)
                    .hasSuppressedException(deleteError);
        }
    }

    @Nested
    @DisplayName("UploadSingleFlight")
    class UploadSingleFlight {
        private static final long TIMEOUT_SECONDS = 10;

        @Test
        @DisplayName("Запускает SingleFlight с ключом из userId и hex sha256 содержимого")
        void usesUserIdAndSha256AsKey() throws NoSuchAlgorithmException {
            // given
            String hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PDF_BYTES));
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenReturn(Resume.Format.PDF);
            when(writer.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            service.upload(USER_ID, FILENAME, PDF_BYTES);

            // then
            verify(singleFlight).run(eq(new SingleFlight.Key("resume.upload", List.of(USER_ID, hex))), any());
        }

        @Test
        @DisplayName("Два одновременных одинаковых запроса дают один вызов writer и один и тот же ответ")
        void collapsesConcurrentIdenticalUploads() throws Exception {
            // given
            CountDownLatch saveStarted = new CountDownLatch(1);
            CountDownLatch releaseSave = new CountDownLatch(1);
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenReturn(Resume.Format.PDF);
            when(writer.save(any(Resume.class))).thenAnswer(invocation -> {
                saveStarted.countDown();
                if (!releaseSave.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("save was not released");
                }
                return invocation.getArgument(0);
            });
            CompletableFuture<ResumeResponse> leaderResult = new CompletableFuture<>();
            CompletableFuture<ResumeResponse> joinerResult = new CompletableFuture<>();

            // when
            uploadInThread(leaderResult);
            assertThat(saveStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Thread joiner = uploadInThread(joinerResult);
            awaitWaiting(joiner);
            releaseSave.countDown();

            // then
            ResumeResponse leader = leaderResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ResumeResponse joined = joinerResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(joined).isSameAs(leader);
            verify(writer, times(1)).save(any(Resume.class));
            verify(storage, times(1)).write(eq(USER_ID), any(UUID.class), eq(PDF_BYTES));
        }

        private Thread uploadInThread(CompletableFuture<ResumeResponse> result) {
            return Thread.ofPlatform().start(() -> {
                try {
                    result.complete(service.upload(USER_ID, FILENAME, PDF_BYTES));
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                }
            });
        }

        private void awaitWaiting(Thread thread) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (thread.getState() != Thread.State.WAITING) {
                assertThat(thread.getState()).isNotEqualTo(Thread.State.TERMINATED);
                assertThat(System.nanoTime()).isLessThan(deadline);
                Thread.sleep(5);
            }
        }
    }

    @Nested
    @DisplayName("List")
    class ListResumes {

        @Test
        @DisplayName("Маппит сущности репозитория в ответы в том же порядке")
        void mapsEntitiesInOrder() {
            // given
            Resume newer = aResume("Новое");
            Resume older = aResume("Старое");
            when(resumeRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(newer, older));

            // when
            List<ResumeResponse> result = service.list(USER_ID);

            // then
            assertThat(result).containsExactly(
                    new ResumeResponse(newer.getId(), "Новое", Resume.Format.PDF, "Новое.pdf", 100,
                            newer.getCreatedAt()),
                    new ResumeResponse(older.getId(), "Старое", Resume.Format.PDF, "Старое.pdf", 100,
                            older.getCreatedAt()));
        }

        @Test
        @DisplayName("Возвращает пустой список, когда резюме нет")
        void returnsEmptyWhenNone() {
            // given
            when(resumeRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

            // when / then
            assertThat(service.list(USER_ID)).isEmpty();
        }
    }
}
