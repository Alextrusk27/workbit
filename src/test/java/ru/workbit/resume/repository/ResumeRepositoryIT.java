package ru.workbit.resume.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.User;
import ru.workbit.resume.model.Resume;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ResumeRepositoryIT")
class ResumeRepositoryIT extends AbstractPostgresIT {

    private static final int MAX_SIZE_BYTES = 5_242_880;

    private static final String INSERT_NATIVE = "INSERT INTO resume.document "
            + "(id, user_id, name, original_filename, format, size_bytes) "
            + "VALUES (gen_random_uuid(), :userId, 'Резюме', 'cv.pdf', :format, :size)";

    @Autowired
    private ResumeRepository repository;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .build();
    }

    private Resume aResume(UUID userId, String name) {
        return Resume.builder()
                .userId(userId)
                .name(name)
                .originalFilename("cv.pdf")
                .format(Resume.Format.PDF)
                .sizeBytes(1024)
                .build();
    }

    private Statistics statistics() {
        return em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    private void setCreatedAt(UUID id, Instant createdAt) {
        em.getEntityManager().createNativeQuery("UPDATE resume.document SET created_at = :t WHERE id = :id")
                .setParameter("t", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    private int insertNative(UUID userId, String format, int size) {
        return em.getEntityManager().createNativeQuery(INSERT_NATIVE)
                .setParameter("userId", userId)
                .setParameter("format", format)
                .setParameter("size", size)
                .executeUpdate();
    }

    // =========================================================================

    @Nested
    @DisplayName("Save")
    class Save {

        @Test
        @DisplayName("Вставляет строку одним INSERT без предварительного SELECT и возвращает тот же экземпляр")
        void insertsWithoutSelect() {
            // given
            var user = em.persistAndFlush(aUser("resume-save-no-select@example.com"));
            var resume = aResume(user.getId(), "Основное");
            Statistics statistics = statistics();
            statistics.clear();

            // when
            var saved = repository.save(resume);
            repository.flush();

            // then
            assertThat(saved).isSameAs(resume);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            assertThat(statistics.getEntityLoadCount()).isZero();
            assertThat(statistics.getEntityInsertCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Читает обратно все поля, загруженная сущность не считается новой")
        void readsAllFieldsBack() {
            // given
            var user = em.persistAndFlush(aUser("resume-save-read@example.com"));
            var resume = Resume.builder()
                    .userId(user.getId())
                    .name("Backend")
                    .originalFilename("Иванов Иван.docx")
                    .format(Resume.Format.DOCX)
                    .sizeBytes(2048)
                    .build();
            repository.saveAndFlush(resume);
            em.clear();

            // when
            var found = repository.findById(resume.getId()).orElseThrow();

            // then
            assertThat(found).isNotSameAs(resume);
            assertThat(found.getUserId()).isEqualTo(user.getId());
            assertThat(found.getName()).isEqualTo("Backend");
            assertThat(found.getOriginalFilename()).isEqualTo("Иванов Иван.docx");
            assertThat(found.getFormat()).isEqualTo(Resume.Format.DOCX);
            assertThat(found.getSizeBytes()).isEqualTo(2048);
            assertThat(found.getCreatedAt()).isCloseTo(resume.getCreatedAt(), within(1, ChronoUnit.MICROS));
            assertThat(found.isNew()).isFalse();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindAllByUserIdOrderByCreatedAtDesc")
    class FindAllByUserIdOrderByCreatedAtDesc {

        @Test
        @DisplayName("Возвращает только резюме пользователя, новые сверху")
        void returnsOwnNewestFirst() {
            // given
            var user = em.persistAndFlush(aUser("resume-list-owner@example.com"));
            var other = em.persistAndFlush(aUser("resume-list-other@example.com"));
            var now = Instant.now();
            var oldest = repository.save(aResume(user.getId(), "Старое"));
            var newest = repository.save(aResume(user.getId(), "Новое"));
            var middle = repository.save(aResume(user.getId(), "Среднее"));
            var foreign = repository.save(aResume(other.getId(), "Чужое"));
            repository.flush();
            setCreatedAt(oldest.getId(), now.minusSeconds(7200));
            setCreatedAt(newest.getId(), now);
            setCreatedAt(middle.getId(), now.minusSeconds(3600));
            setCreatedAt(foreign.getId(), now.minusSeconds(60));
            em.flush();
            em.clear();

            // when
            var result = repository.findAllByUserIdOrderByCreatedAtDesc(user.getId());

            // then
            assertThat(result).extracting(Resume::getId)
                    .containsExactly(newest.getId(), middle.getId(), oldest.getId());
        }

        @Test
        @DisplayName("Возвращает пустой список, когда у пользователя нет резюме")
        void returnsEmptyWhenNone() {
            // given
            var user = em.persistAndFlush(aUser("resume-list-empty@example.com"));

            // when
            var result = repository.findAllByUserIdOrderByCreatedAtDesc(user.getId());

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindByIdAndUserId")
    class FindByIdAndUserId {

        @Test
        @DisplayName("Находит резюме владельца")
        void findsOwn() {
            // given
            var user = em.persistAndFlush(aUser("resume-find-own@example.com"));
            var resume = repository.saveAndFlush(aResume(user.getId(), "Моё"));
            em.clear();

            // when
            var found = repository.findByIdAndUserId(resume.getId(), user.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(resume.getId());
        }

        @Test
        @DisplayName("Не находит чужое резюме")
        void doesNotFindForeign() {
            // given
            var owner = em.persistAndFlush(aUser("resume-find-owner@example.com"));
            var stranger = em.persistAndFlush(aUser("resume-find-stranger@example.com"));
            var resume = repository.saveAndFlush(aResume(owner.getId(), "Чужое"));
            em.clear();

            // when
            var found = repository.findByIdAndUserId(resume.getId(), stranger.getId());

            // then
            assertThat(found).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "     "})
        @DisplayName("Название из одних пробелов или пустое нарушает chk_document_name")
        void rejectsBlankName(String name) {
            // given
            var user = em.persistAndFlush(aUser("resume-chk-name@example.com"));
            var resume = aResume(user.getId(), name);

            // when / then
            assertThatThrownBy(() -> repository.saveAndFlush(resume))
                    .hasMessageContaining("chk_document_name");
        }

        @Test
        @DisplayName("Неизвестный формат нарушает chk_document_format")
        void rejectsUnknownFormat() {
            // given
            var user = em.persistAndFlush(aUser("resume-chk-format@example.com"));

            // when / then
            assertThatThrownBy(() -> insertNative(user.getId(), "RTF", 1024))
                    .hasMessageContaining("chk_document_format");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, MAX_SIZE_BYTES + 1})
        @DisplayName("Размер вне диапазона 1..5242880 нарушает chk_document_size")
        void rejectsSizeOutOfRange(int size) {
            // given
            var user = em.persistAndFlush(aUser("resume-chk-size-" + size + "@example.com"));

            // when / then
            assertThatThrownBy(() -> insertNative(user.getId(), "PDF", size))
                    .hasMessageContaining("chk_document_size");
        }

        @ParameterizedTest
        @ValueSource(ints = {1, MAX_SIZE_BYTES})
        @DisplayName("Граничные размеры 1 и 5242880 допустимы")
        void acceptsBoundarySizes(int size) {
            // given
            var user = em.persistAndFlush(aUser("resume-chk-bound-" + size + "@example.com"));

            // when
            var inserted = insertNative(user.getId(), "PDF", size);

            // then
            assertThat(inserted).isEqualTo(1);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Cascade")
    class Cascade {

        @Test
        @DisplayName("Удаление пользователя каскадно удаляет его резюме и не трогает чужие")
        void deletingUserCascadesToResumes() {
            // given
            var user = em.persistAndFlush(aUser("resume-cascade-owner@example.com"));
            var other = em.persistAndFlush(aUser("resume-cascade-other@example.com"));
            var mine = repository.save(aResume(user.getId(), "Моё 1"));
            var mine2 = repository.save(aResume(user.getId(), "Моё 2"));
            var foreign = repository.save(aResume(other.getId(), "Чужое"));
            repository.flush();

            // when
            em.getEntityManager().createNativeQuery("DELETE FROM auth.users WHERE id = :id")
                    .setParameter("id", user.getId())
                    .executeUpdate();
            em.flush();
            em.clear();

            // then
            assertThat(repository.findById(mine.getId())).isEmpty();
            assertThat(repository.findById(mine2.getId())).isEmpty();
            assertThat(repository.findAllByUserIdOrderByCreatedAtDesc(user.getId())).isEmpty();
            assertThat(repository.findById(foreign.getId())).isPresent();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CountByUserId")
    class CountByUserId {

        @Test
        @DisplayName("Возвращает 0 для пользователя без резюме")
        void returnsZeroWhenNone() {
            // given
            var user = em.persistAndFlush(aUser("resume-count-empty@example.com"));

            // when
            var count = repository.countByUserId(user.getId());

            // then
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Считает только резюме своего пользователя")
        void countsOnlyOwn() {
            // given
            var user = em.persistAndFlush(aUser("resume-count-owner@example.com"));
            var other = em.persistAndFlush(aUser("resume-count-other@example.com"));
            repository.save(aResume(user.getId(), "Моё 1"));
            repository.save(aResume(user.getId(), "Моё 2"));
            repository.save(aResume(other.getId(), "Чужое"));
            repository.flush();

            // when
            var count = repository.countByUserId(user.getId());

            // then
            assertThat(count).isEqualTo(2);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("LockUser")
    class LockUser {

        @Test
        @DisplayName("Выполняется в транзакции без ошибок и берёт advisory-блокировку")
        void acquiresAdvisoryLock() {
            // given
            var user = em.persistAndFlush(aUser("resume-lock-simple@example.com"));

            // when
            repository.lockUser(user.getId());

            // then
            var held = em.getEntityManager().createNativeQuery("SELECT count(*) FROM pg_locks "
                            + "WHERE locktype = 'advisory' AND granted AND pid = pg_backend_pid()")
                    .getSingleResult();
            assertThat(((Number) held).longValue()).isEqualTo(1);
        }

        @Test
        @DisplayName("Повторный вызов в той же транзакции не блокируется")
        void isReentrantWithinTransaction() {
            // given
            var user = em.persistAndFlush(aUser("resume-lock-reentrant@example.com"));
            repository.lockUser(user.getId());

            // when / then
            assertThat(catchThrowable(() -> repository.lockUser(user.getId()))).isNull();
        }
    }

    // =========================================================================

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("LockUserConcurrency")
    class LockUserConcurrency {

        private static final long TIMEOUT_SECONDS = 10;

        private final ExecutorService executor = Executors.newFixedThreadPool(3);

        private TransactionTemplate tx;

        private UUID userId;

        private UUID otherUserId;

        @BeforeEach
        void setUp() {
            tx = new TransactionTemplate(transactionManager);
            userId = tx.execute(status -> em.persistAndFlush(
                    aUser("resume-lock-a-" + UUID.randomUUID() + "@example.com")).getId());
            otherUserId = tx.execute(status -> em.persistAndFlush(
                    aUser("resume-lock-b-" + UUID.randomUUID() + "@example.com")).getId());
        }

        @AfterEach
        void tearDown() {
            executor.shutdownNow();
            jdbcTemplate.update("DELETE FROM auth.users WHERE id IN (?, ?)", userId, otherUserId);
        }

        @Test
        @DisplayName("Транзакция ждёт блокировку того же пользователя, не мешает другому и продолжает после коммита")
        void serializesSameUserOnly() throws Exception {
            // given
            var holderLocked = new CountDownLatch(1);
            var releaseA = new CountDownLatch(1);
            var waiterAcquired = new CountDownLatch(1);
            Future<?> a = executor.submit(() -> tx.executeWithoutResult(status -> {
                repository.lockUser(userId);
                holderLocked.countDown();
                await(releaseA);
            }));
            assertThat(holderLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            // when
            Future<?> b = executor.submit(() -> tx.executeWithoutResult(status -> {
                repository.lockUser(userId);
                waiterAcquired.countDown();
            }));
            awaitWaitingAdvisoryLock();
            Future<?> other = executor.submit(
                    () -> tx.executeWithoutResult(status -> repository.lockUser(otherUserId)));

            // then
            other.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(waiterAcquired.getCount()).isEqualTo(1);
            assertThat(b.isDone()).isFalse();

            releaseA.countDown();
            a.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            b.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(waiterAcquired.getCount()).isZero();
        }

        @Test
        @DisplayName("Повторный счёт под блокировкой видит строку, закоммиченную предыдущим держателем")
        void countUnderLockSeesCommittedRow() throws Exception {
            // given
            var holderLocked = new CountDownLatch(1);
            var releaseA = new CountDownLatch(1);
            Future<?> a = executor.submit(() -> tx.executeWithoutResult(status -> {
                repository.lockUser(userId);
                holderLocked.countDown();
                await(releaseA);
                repository.saveAndFlush(aResume(userId, "Из A"));
            }));
            assertThat(holderLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<Long> b = executor.submit(() -> tx.execute(status -> {
                repository.lockUser(userId);
                return repository.countByUserId(userId);
            }));
            awaitWaitingAdvisoryLock();

            // when
            releaseA.countDown();
            a.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // then
            assertThat(b.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(1);
        }

        private void awaitWaitingAdvisoryLock() throws InterruptedException {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (System.nanoTime() < deadline) {
                var waiting = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_locks "
                        + "WHERE locktype = 'advisory' AND NOT granted AND database = "
                        + "(SELECT oid FROM pg_database WHERE datname = current_database())", Long.class);
                if (waiting != null && waiting > 0) {
                    return;
                }
                Thread.sleep(50);
            }
            throw new AssertionError("Вторая транзакция не встала в ожидание advisory-блокировки");
        }

        private void await(CountDownLatch latch) {
            try {
                if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError("Латч не освобождён за " + TIMEOUT_SECONDS + " с");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
