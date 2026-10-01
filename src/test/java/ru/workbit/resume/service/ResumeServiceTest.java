package ru.workbit.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.exception.UnprocessableEntityException;
import ru.workbit.resume.dto.ResumeResponse;
import ru.workbit.resume.model.Resume;
import ru.workbit.resume.model.mapper.ResumeMapper;
import ru.workbit.resume.model.mapper.ResumeMapperImpl;
import ru.workbit.resume.repository.ResumeRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResumeServiceTest")
class ResumeServiceTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String FILENAME = "Иванов Java.pdf";
    private static final byte[] PDF_BYTES = "%PDF-1.7 body".getBytes(StandardCharsets.US_ASCII);

    @Mock
    ResumeRepository resumeRepository;

    @Mock
    ResumeWriter writer;

    @Mock
    ResumeFileStorage storage;

    @Mock
    ResumeFormatDetector formatDetector;

    @Spy
    ResumeMapper resumeMapper = new ResumeMapperImpl();

    @InjectMocks
    ResumeService service;

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
        @DisplayName("Пробрасывает UnprocessableEntityException от детектора и не трогает storage и writer")
        void propagatesUnsupportedFormat() {
            // given
            when(formatDetector.detect(PDF_BYTES, FILENAME)).thenThrow(new UnprocessableEntityException("Unsupported format"));

            // when / then
            assertThatThrownBy(() -> service.upload(USER_ID, FILENAME, PDF_BYTES))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage("Unsupported format");
            verifyNoInteractions(storage, writer);
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
