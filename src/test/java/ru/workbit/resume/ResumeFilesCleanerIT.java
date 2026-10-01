package ru.workbit.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.User;
import ru.workbit.auth.repository.UserJPARepository;
import ru.workbit.resume.config.ResumeProperties;
import ru.workbit.resume.model.Resume;
import ru.workbit.resume.repository.ResumeRepository;
import ru.workbit.resume.service.ResumeFileStorage;
import ru.workbit.resume.service.ResumeService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long-enough-for-hmac",
        "jwt.expiration=3600000",
        "app.mail.from-name=Workbit",
        "app.mail.from-mail=noreply@workbit.ru",
        "app.mail.base-url=https://workbit.ru",
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "llm.gateway.base-url=http://localhost"
})
@DisplayName("ResumeFilesCleanerIT")
class ResumeFilesCleanerIT extends AbstractPostgresIT {

    private static final long TIMEOUT_SECONDS = 10;
    private static final byte[] CONTENT = "%PDF-1.4\ncontent".getBytes();

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private UserJPARepository userRepository;

    @Autowired
    private ResumeFileStorage storage;

    @Autowired
    private ResumeProperties properties;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @AfterEach
    void tearDown() {
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM auth.users WHERE id = ?", userId);
        }
    }

    private UUID aUser() {
        var user = userRepository.save(User.builder()
                .email("cleaner-" + UUID.randomUUID() + "@example.com")
                .build());
        userId = user.getId();
        return userId;
    }

    private Resume aResumeWithFile(UUID ownerId) {
        var resume = resumeRepository.saveAndFlush(Resume.builder()
                .userId(ownerId)
                .name("Резюме")
                .originalFilename("cv.pdf")
                .format(Resume.Format.PDF)
                .sizeBytes(CONTENT.length)
                .build());
        storage.write(ownerId, resume.getId(), CONTENT);
        return resume;
    }

    private Resume aResumeWithoutFile(UUID ownerId) {
        return resumeRepository.saveAndFlush(Resume.builder()
                .userId(ownerId)
                .name("Без файла")
                .originalFilename("cv.pdf")
                .format(Resume.Format.PDF)
                .sizeBytes(CONTENT.length)
                .build());
    }

    private Path fileOf(UUID ownerId, UUID resumeId) {
        return properties.storageDir().resolve(ownerId.toString()).resolve(resumeId.toString());
    }

    private void awaitFileDeleted(Path file) {
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS)).until(() -> Files.notExists(file));
    }

    @Nested
    @DisplayName("OnResumeDeleted")
    class OnResumeDeleted {

        @Test
        @DisplayName("Удаляет файл после коммита удаления резюме")
        void deletesFileAfterCommit() throws Exception {
            // given
            var owner = aUser();
            var resume = aResumeWithFile(owner);
            var file = fileOf(owner, resume.getId());
            assertThat(file).exists();

            // when
            resumeService.delete(owner, resume.getId());

            // then
            assertThat(resumeRepository.findById(resume.getId())).isEmpty();
            awaitFileDeleted(file);
            assertThat(file).doesNotExist();
        }

        @Test
        @DisplayName("Оставляет файл на месте, когда транзакция удаления откатилась")
        void keepsFileOnRollback() throws Exception {
            // given
            var owner = aUser();
            var rolledBack = aResumeWithFile(owner);
            var committed = aResumeWithFile(owner);
            var rolledBackFile = fileOf(owner, rolledBack.getId());
            var committedFile = fileOf(owner, committed.getId());

            // when
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                resumeRepository.delete(rolledBack);
                eventPublisher.publishEvent(new ResumeDeletedEvent(owner, rolledBack.getId()));
                status.setRollbackOnly();
            });
            resumeService.delete(owner, committed.getId());
            awaitFileDeleted(committedFile);

            // then
            assertThat(resumeRepository.findById(rolledBack.getId())).isPresent();
            assertThat(rolledBackFile).exists();
            assertThat(resumeRepository.findById(committed.getId())).isEmpty();
        }

        @Test
        @DisplayName("Не считает ошибкой отсутствие файла на диске")
        void succeedsWhenFileIsMissing() {
            // given
            var owner = aUser();
            var resume = aResumeWithoutFile(owner);
            assertThat(fileOf(owner, resume.getId())).doesNotExist();

            // when
            var thrown = catchThrowable(() -> resumeService.delete(owner, resume.getId()));

            // then
            assertThat(thrown).isNull();
            assertThat(resumeRepository.findById(resume.getId())).isEmpty();
        }
    }
}
