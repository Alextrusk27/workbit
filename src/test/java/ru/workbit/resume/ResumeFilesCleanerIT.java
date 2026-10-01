package ru.workbit.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import ru.workbit.auth.UsersDeletedEvent;
import ru.workbit.auth.model.User;
import ru.workbit.auth.repository.UserJPARepository;
import ru.workbit.auth.service.AccountCleanupService;
import ru.workbit.auth.service.AuthService;
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
    private AuthService authService;

    @Autowired
    private AccountCleanupService accountCleanupService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : userIds) {
            jdbcTemplate.update("DELETE FROM auth.users WHERE id = ?", id);
        }
    }

    private UUID aUser() {
        var user = userRepository.save(User.builder()
                .email("cleaner-" + UUID.randomUUID() + "@example.com")
                .build());
        userIds.add(user.getId());
        return user.getId();
    }

    private void markWarnedLongAgo(UUID ownerId) {
        jdbcTemplate.update(
                "UPDATE auth.users SET deletion_warned_at = now() - interval '31 days', last_seen = now() WHERE id = ?",
                ownerId);
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

    private Path dirOf(UUID ownerId) {
        return properties.storageDir().resolve(ownerId.toString());
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

    @Nested
    @DisplayName("OnUsersDeleted")
    class OnUsersDeleted {

        @Test
        @DisplayName("Удаляет папку пользователя целиком после удаления аккаунта")
        void deletesUserDirectoryAfterAccountDeletion() {
            // given
            var owner = aUser();
            var first = aResumeWithFile(owner);
            var second = aResumeWithFile(owner);
            var directory = dirOf(owner);
            assertThat(fileOf(owner, first.getId())).exists();
            assertThat(fileOf(owner, second.getId())).exists();

            // when
            authService.deleteUser(owner);

            // then
            assertThat(userRepository.findById(owner)).isEmpty();
            assertThat(resumeRepository.countByUserId(owner)).isZero();
            awaitFileDeleted(directory);
            assertThat(directory).doesNotExist();
        }

        @Test
        @DisplayName("Ночная очистка удаляет папку пользователя с истёкшим предупреждением и не трогает активного")
        void cleanupDeletesDirectoryOfExpiredUserOnly() {
            // given
            var expired = aUser();
            var active = aUser();
            aResumeWithFile(expired);
            aResumeWithFile(active);
            markWarnedLongAgo(expired);
            var expiredDirectory = dirOf(expired);
            var activeDirectory = dirOf(active);

            // when
            accountCleanupService.cleanupInactiveAccounts();

            // then
            assertThat(userRepository.findById(expired)).isEmpty();
            assertThat(userRepository.findById(active)).isPresent();
            awaitFileDeleted(expiredDirectory);
            assertThat(expiredDirectory).doesNotExist();
            assertThat(activeDirectory).isDirectory();
            assertThat(resumeRepository.countByUserId(active)).isEqualTo(1);
        }

        @Test
        @DisplayName("Оставляет папку на месте, когда транзакция удаления пользователя откатилась")
        void keepsDirectoryOnRollback() {
            // given
            var rolledBack = aUser();
            var first = aResumeWithFile(rolledBack);
            var second = aResumeWithFile(rolledBack);
            var committed = aUser();
            aResumeWithFile(committed);

            // when
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                userRepository.deleteById(rolledBack);
                eventPublisher.publishEvent(new UsersDeletedEvent(List.of(rolledBack)));
                status.setRollbackOnly();
            });
            authService.deleteUser(committed);
            awaitFileDeleted(dirOf(committed));

            // then
            assertThat(userRepository.findById(rolledBack)).isPresent();
            assertThat(resumeRepository.countByUserId(rolledBack)).isEqualTo(2);
            assertThat(fileOf(rolledBack, first.getId())).exists();
            assertThat(fileOf(rolledBack, second.getId())).exists();
            assertThat(userRepository.findById(committed)).isEmpty();
        }

        @Test
        @DisplayName("Не считает ошибкой отсутствие папки пользователя на диске")
        void succeedsWhenDirectoryIsMissing() {
            // given
            var owner = aUser();
            assertThat(dirOf(owner)).doesNotExist();

            // when
            var thrown = catchThrowable(() -> authService.deleteUser(owner));

            // then
            assertThat(thrown).isNull();
            assertThat(userRepository.findById(owner)).isEmpty();
        }
    }
}
