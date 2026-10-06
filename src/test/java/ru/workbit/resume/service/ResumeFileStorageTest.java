package ru.workbit.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.workbit.resume.config.ResumeProperties;

@DisplayName("ResumeFileStorageTest")
class ResumeFileStorageTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESUME_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final byte[] CONTENT = {1, 2, 3, 4, 5};

    @TempDir
    Path tempDir;

    private Path root;
    private ResumeFileStorage storage;

    @BeforeEach
    void setUp() {
        root = tempDir.resolve("resumes");
        storage = new ResumeFileStorage(new ResumeProperties(root));
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("Создаёт корневой каталог, если его нет")
        void createsRootWhenMissing() {
            // given
            Path missingRoot = tempDir.resolve("a").resolve("b");

            // when
            new ResumeFileStorage(new ResumeProperties(missingRoot));

            // then
            assertThat(missingRoot).isDirectory();
        }

        @Test
        @DisplayName("Не падает, если корневой каталог уже существует")
        void acceptsExistingRoot() {
            // given
            assertThat(root).isDirectory();

            // when / then
            assertThatCode(() -> new ResumeFileStorage(new ResumeProperties(root))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Бросает IllegalStateException, когда корень не создать")
        void throwsWhenRootCannotBeCreated() throws IOException {
            // given
            Path blocker = Files.createFile(tempDir.resolve("blocker"));
            ResumeProperties properties = new ResumeProperties(blocker.resolve("storage"));

            // when / then
            assertThatThrownBy(() -> new ResumeFileStorage(properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Cannot create resume storage dir")
                    .hasCauseInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("Write")
    class Write {

        @Test
        @DisplayName("Кладёт файл в {root}/{userId}/{resumeId} с теми же байтами")
        void writesFileWithSameBytes() throws IOException {
            // when
            storage.write(USER_ID, RESUME_ID, CONTENT);

            // then
            Path file = root.resolve(USER_ID.toString()).resolve(RESUME_ID.toString());
            assertThat(file).isRegularFile();
            assertThat(Files.readAllBytes(file)).isEqualTo(CONTENT);
        }

        @Test
        @DisplayName("Не оставляет временных файлов рядом")
        void leavesNoTempFiles() throws IOException {
            // when
            storage.write(USER_ID, RESUME_ID, CONTENT);

            // then
            try (Stream<Path> files = Files.list(root.resolve(USER_ID.toString()))) {
                assertThat(files.map(path -> path.getFileName().toString()))
                        .containsExactly(RESUME_ID.toString());
            }
        }

        @Test
        @DisplayName("Перезаписывает содержимое при повторной записи того же id")
        void overwritesExistingFile() throws IOException {
            // given
            byte[] updated = {9, 8, 7};
            storage.write(USER_ID, RESUME_ID, CONTENT);

            // when
            storage.write(USER_ID, RESUME_ID, updated);

            // then
            Path userDir = root.resolve(USER_ID.toString());
            assertThat(Files.readAllBytes(userDir.resolve(RESUME_ID.toString()))).isEqualTo(updated);
            try (Stream<Path> files = Files.list(userDir)) {
                assertThat(files).hasSize(1);
            }
        }

        @Test
        @DisplayName("Бросает UncheckedIOException, когда на месте папки пользователя обычный файл")
        void throwsWhenUserDirIsRegularFile() throws IOException {
            // given
            Files.createFile(root.resolve(USER_ID.toString()));

            // when / then
            assertThatThrownBy(() -> storage.write(USER_ID, RESUME_ID, CONTENT))
                    .isInstanceOf(UncheckedIOException.class);
        }

        @Test
        @DisplayName("Удаляет временный файл и бросает UncheckedIOException, когда переименование не удалось")
        void cleansUpTempFileWhenMoveFails() throws IOException {
            // given
            Path userDir = root.resolve(USER_ID.toString());
            Path blockingDir = Files.createDirectories(userDir.resolve(RESUME_ID.toString()));
            Files.createFile(blockingDir.resolve("child"));

            // when / then
            assertThatThrownBy(() -> storage.write(USER_ID, RESUME_ID, CONTENT))
                    .isInstanceOf(UncheckedIOException.class);
            try (Stream<Path> files = Files.list(userDir)) {
                assertThat(files.map(path -> path.getFileName().toString())).containsExactly(RESUME_ID.toString());
            }
        }
    }

    @Nested
    @DisplayName("Read")
    class Read {

        @Test
        @DisplayName("Читает те же байты, что записаны через write")
        void readsWrittenBytes() {
            // given
            storage.write(USER_ID, RESUME_ID, CONTENT);

            // when
            Optional<byte[]> result = storage.read(USER_ID, RESUME_ID);

            // then
            assertThat(result).hasValue(CONTENT);
        }

        @Test
        @DisplayName("Возвращает empty, когда файла нет, а папка пользователя есть")
        void returnsEmptyWhenFileMissing() {
            // given
            storage.write(USER_ID, UUID.fromString("33333333-3333-3333-3333-333333333333"), CONTENT);

            // when
            Optional<byte[]> result = storage.read(USER_ID, RESUME_ID);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Возвращает empty, когда нет папки пользователя")
        void returnsEmptyWhenUserDirMissing() {
            // when
            Optional<byte[]> result = storage.read(USER_ID, RESUME_ID);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Бросает UncheckedIOException, когда на месте файла каталог")
        void throwsWhenPathIsDirectory() throws IOException {
            // given
            Files.createDirectories(root.resolve(USER_ID.toString()).resolve(RESUME_ID.toString()));

            // when / then
            assertThatThrownBy(() -> storage.read(USER_ID, RESUME_ID))
                    .isInstanceOf(UncheckedIOException.class);
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("Удаляет файл")
        void deletesFile() {
            // given
            storage.write(USER_ID, RESUME_ID, CONTENT);

            // when
            storage.delete(USER_ID, RESUME_ID);

            // then
            assertThat(root.resolve(USER_ID.toString()).resolve(RESUME_ID.toString())).doesNotExist();
        }

        @Test
        @DisplayName("Не трогает другие файлы пользователя")
        void keepsOtherFiles() {
            // given
            UUID otherId = UUID.fromString("33333333-3333-3333-3333-333333333333");
            storage.write(USER_ID, RESUME_ID, CONTENT);
            storage.write(USER_ID, otherId, CONTENT);

            // when
            storage.delete(USER_ID, RESUME_ID);

            // then
            assertThat(root.resolve(USER_ID.toString()).resolve(otherId.toString())).isRegularFile();
        }

        @Test
        @DisplayName("Не падает, когда файла нет, а папка пользователя есть")
        void doesNothingWhenFileMissing() {
            // given
            storage.write(USER_ID, UUID.fromString("33333333-3333-3333-3333-333333333333"), CONTENT);

            // when / then
            assertThatCode(() -> storage.delete(USER_ID, RESUME_ID)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Не падает, когда нет и папки пользователя")
        void doesNothingWhenUserDirMissing() {
            // when / then
            assertThatCode(() -> storage.delete(USER_ID, RESUME_ID)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Бросает UncheckedIOException, когда удалить не получается")
        void throwsWhenDeleteFails() throws IOException {
            // given
            Path notEmptyDir = Files.createDirectories(root.resolve(USER_ID.toString()).resolve(RESUME_ID.toString()));
            Files.createFile(notEmptyDir.resolve("child"));

            // when / then
            assertThatThrownBy(() -> storage.delete(USER_ID, RESUME_ID))
                    .isInstanceOf(UncheckedIOException.class);
        }
    }

    @Nested
    @DisplayName("DeleteUser")
    class DeleteUser {
        private static final UUID OTHER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

        @Test
        @DisplayName("Удаляет папку пользователя со всеми файлами, вложенной папкой и временным файлом")
        void deletesUserDirWithAllContent() throws IOException {
            // given
            storage.write(USER_ID, RESUME_ID, CONTENT);
            storage.write(USER_ID, UUID.fromString("33333333-3333-3333-3333-333333333333"), CONTENT);
            Path userDir = root.resolve(USER_ID.toString());
            Files.createFile(userDir.resolve(RESUME_ID + "-leftover.tmp"));
            Path nested = Files.createDirectories(userDir.resolve("nested"));
            Files.write(nested.resolve("child"), CONTENT);

            // when
            storage.deleteUser(USER_ID);

            // then
            assertThat(userDir).doesNotExist();
            assertThat(root).isDirectory();
        }

        @Test
        @DisplayName("Не трогает папку другого пользователя")
        void keepsOtherUserDir() throws IOException {
            // given
            storage.write(USER_ID, RESUME_ID, CONTENT);
            storage.write(OTHER_USER_ID, RESUME_ID, CONTENT);

            // when
            storage.deleteUser(USER_ID);

            // then
            Path otherFile = root.resolve(OTHER_USER_ID.toString()).resolve(RESUME_ID.toString());
            assertThat(otherFile).isRegularFile();
            assertThat(Files.readAllBytes(otherFile)).isEqualTo(CONTENT);
        }

        @Test
        @DisplayName("Не падает при повторном вызове")
        void isIdempotent() {
            // given
            storage.write(USER_ID, RESUME_ID, CONTENT);
            storage.deleteUser(USER_ID);

            // when / then
            assertThatCode(() -> storage.deleteUser(USER_ID)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Не падает, когда у пользователя нет папки")
        void doesNothingWhenUserDirMissing() {
            // when / then
            assertThatCode(() -> storage.deleteUser(USER_ID)).doesNotThrowAnyException();
            assertThat(root).isEmptyDirectory();
        }
    }
}
