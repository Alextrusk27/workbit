package ru.workbit.content.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.content.model.BankQuestion;
import ru.workbit.content.model.DictStatus;
import ru.workbit.content.model.ProfessionDict;
import ru.workbit.content.model.TopicDict;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ProfessionDictRepositoryIT")
class ProfessionDictRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private ProfessionDictRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private ProfessionDict aProfession(String name) {
        return ProfessionDict.builder()
                .name(name)
                .build(); // status=AUTO, usageCount=0 — @Builder.Default
    }

    private ProfessionDict aProfession(String name, int usageCount) {
        return ProfessionDict.builder()
                .name(name)
                .usageCount(usageCount)
                .build();
    }

    private ProfessionDict anApprovedProfession(String name, int usageCount) {
        return ProfessionDict.builder()
                .name(name)
                .usageCount(usageCount)
                .status(DictStatus.APPROVED)
                .build();
    }

    private TopicDict aTopic(UUID professionId, String name) {
        return TopicDict.builder()
                .professionId(professionId)
                .name(name)
                .build();
    }

    private BankQuestion aBankQuestion(UUID professionId, UUID topicId) {
        return BankQuestion.builder()
                .professionId(professionId)
                .topicId(topicId)
                .levels(List.of("JUNIOR"))
                .text("Что такое SOLID?")
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("UniqueName")
    class UniqueName {

        @Test
        @DisplayName("Дубль имени в другом регистре нарушает уникальность при flush")
        void throwsOnDuplicateNameDifferentCase() {
            // given
            em.persistAndFlush(aProfession("Java Developer"));

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(aProfession("java developer")))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Разные имена сохраняются без ошибок")
        void allowsDifferentNames() {
            // given / when
            var a = em.persistAndFlush(aProfession("Backend Developer"));
            var b = em.persistAndFlush(aProfession("Frontend Developer"));

            // then
            assertThat(a.getId()).isNotEqualTo(b.getId());
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CheckConstraintStatus")
    class CheckConstraintStatus {

        @Test
        @DisplayName("Статус вне AUTO/APPROVED нарушает CHECK-констрейнт")
        void throwsWhenStatusInvalid() {
            // given / when / then — Java-энум DictStatus не позволяет собрать невалидное
            // значение, поэтому CHECK проверяем нативной вставкой в обход маппинга
            assertThatThrownBy(() -> em.getEntityManager()
                    .createNativeQuery("INSERT INTO content.profession_dict (id, name, status) "
                            + "VALUES (gen_random_uuid(), 'Bad Status Profession', 'BOGUS')")
                    .executeUpdate())
                    .isInstanceOf(Exception.class);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("SaveAndRead")
    class SaveAndRead {

        @Test
        @DisplayName("UUID генерируется автоматически при сохранении")
        void uuidIsGeneratedOnSave() {
            // given
            var profession = aProfession("Uuid Gen Profession");
            assertThat(profession.getId()).isNull();

            // when
            var saved = em.persistFlushFind(profession);

            // then
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("Дефолты status=AUTO и usageCount=0 применяются из @Builder.Default")
        void defaultsAreApplied() {
            // given / when
            var saved = em.persistFlushFind(aProfession("Defaults Profession"));

            // then
            assertThat(saved.getStatus()).isEqualTo(DictStatus.AUTO);
            assertThat(saved.getUsageCount()).isZero();
            assertThat(saved.getCreated()).isNotNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CascadeDelete")
    class CascadeDelete {

        @Test
        @DisplayName("Удаление профессии каскадно удаляет её темы и вопросы банка")
        void cascadeDeleteRemovesTopicsAndBankQuestions() {
            // given
            var profession = em.persistAndFlush(aProfession("Cascade Profession"));
            var topic = em.persistAndFlush(aTopic(profession.getId(), "Cascade Topic"));
            var topicalQuestion = em.persistAndFlush(aBankQuestion(profession.getId(), topic.getId()));
            var generalQuestion = em.persistAndFlush(aBankQuestion(profession.getId(), null));

            // when — физическое удаление профессии нативным SQL, чтобы проверить реальный
            // ON DELETE CASCADE в БД, минуя JPA-кеш
            em.getEntityManager()
                    .createNativeQuery("DELETE FROM content.profession_dict WHERE id = :id")
                    .setParameter("id", profession.getId())
                    .executeUpdate();
            em.flush();
            em.clear();

            // then
            assertThat(repository.findById(profession.getId())).isEmpty();
            assertThat(em.find(TopicDict.class, topic.getId())).isNull();
            assertThat(em.find(BankQuestion.class, topicalQuestion.getId())).isNull();
            assertThat(em.find(BankQuestion.class, generalQuestion.getId())).isNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Suggest")
    class Suggest {

        @Test
        @DisplayName("Регистронезависимый поиск по подстроке")
        void findsBySubstringCaseInsensitive() {
            // given
            em.persistAndFlush(anApprovedProfession("Zzz Backend Engineer", 0));

            // when
            var result = repository.suggest("BACKEND", 10);

            // then
            assertThat(result).extracting(ProfessionDict::getName).contains("Zzz Backend Engineer");
        }

        @Test
        @DisplayName("AUTO-запись не попадает в выдачу, даже если имя матчится")
        void autoStatusRecordIsExcluded() {
            // given
            em.persistAndFlush(aProfession("Zzz Auto Engineer"));

            // when
            var result = repository.suggest("auto engineer", 10);

            // then
            assertThat(result).extracting(ProfessionDict::getName).doesNotContain("Zzz Auto Engineer");
        }

        @Test
        @DisplayName("Prefix-совпадения идут раньше substring-совпадений независимо от usage_count")
        void prefixMatchesRankBeforeSubstringMatches() {
            // given
            em.persistAndFlush(anApprovedProfession("Backend Dev Guru", 100));
            em.persistAndFlush(anApprovedProfession("Dev Ninja", 1));

            // when
            var result = repository.suggest("dev", 10);

            // then
            assertThat(result).extracting(ProfessionDict::getName)
                    .containsExactly("Dev Ninja", "Backend Dev Guru");
        }

        @Test
        @DisplayName("При равном типе совпадения — порядок по usage_count DESC")
        void sameMatchTypeOrderedByUsageCountDesc() {
            // given
            em.persistAndFlush(anApprovedProfession("Qwe One", 1));
            em.persistAndFlush(anApprovedProfession("Qwe Two", 5));

            // when
            var result = repository.suggest("qwe", 10);

            // then
            assertThat(result).extracting(ProfessionDict::getName)
                    .containsExactly("Qwe Two", "Qwe One");
        }

        @Test
        @DisplayName("Limit обрезает количество результатов")
        void limitCutsResults() {
            // given
            em.persistAndFlush(anApprovedProfession("Lim One", 1));
            em.persistAndFlush(anApprovedProfession("Lim Two", 2));
            em.persistAndFlush(anApprovedProfession("Lim Three", 3));

            // when
            var result = repository.suggest("lim", 2);

            // then
            assertThat(result).extracting(ProfessionDict::getName)
                    .containsExactly("Lim Three", "Lim Two");
        }

        @Test
        @DisplayName("Экранированный литерал \\% матчит буквальный процент, а не как wildcard")
        void escapedPercentMatchesLiteral() {
            // given
            em.persistAndFlush(anApprovedProfession("100% Off Sale", 0));
            em.persistAndFlush(anApprovedProfession("100 Percent Off", 0));

            // when — репозиторий ждёт уже экранированный ввод (экранирование делает сервис)
            var result = repository.suggest("100\\%", 10);

            // then
            assertThat(result).extracting(ProfessionDict::getName).containsExactly("100% Off Sale");
        }

        @Test
        @DisplayName("Неэкранированный % работает как wildcard и матчит всё")
        void unescapedPercentMatchesEverything() {
            // given
            em.persistAndFlush(anApprovedProfession("Wild One", 0));
            em.persistAndFlush(anApprovedProfession("Wild Two", 0));

            // when
            var result = repository.suggest("%", 50);

            // then
            assertThat(result).extracting(ProfessionDict::getName)
                    .contains("Wild One", "Wild Two");
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindTop20ByStatusOrderByUsageCountDesc")
    class FindTop20ByStatusOrderByUsageCountDesc {

        @Test
        @DisplayName("Возвращает только APPROVED-профессии, AUTO отфильтрованы")
        void returnsOnlyApprovedProfessions() {
            // given
            em.persistAndFlush(aProfession("Zzz Auto Top Profession"));
            em.persistAndFlush(anApprovedProfession("Zzz Approved Top Profession", 50));

            // when
            var result = repository.findTop20ByStatusOrderByUsageCountDesc(DictStatus.APPROVED);

            // then
            assertThat(result).extracting(ProfessionDict::getName)
                    .contains("Zzz Approved Top Profession")
                    .doesNotContain("Zzz Auto Top Profession");
        }

        @Test
        @DisplayName("Сортирует по usage_count DESC")
        void ordersByUsageCountDesc() {
            // given
            em.persistAndFlush(anApprovedProfession("Zzz High Usage Profession", 100));
            em.persistAndFlush(anApprovedProfession("Zzz Mid Usage Profession", 50));
            em.persistAndFlush(anApprovedProfession("Zzz Low Usage Profession", 10));

            // when
            var result = repository.findTop20ByStatusOrderByUsageCountDesc(DictStatus.APPROVED);

            // then
            assertThat(result).extracting(ProfessionDict::getName)
                    .containsSubsequence("Zzz High Usage Profession", "Zzz Mid Usage Profession", "Zzz Low Usage Profession");
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindByNameIgnoreCase")
    class FindByNameIgnoreCase {

        @Test
        @DisplayName("Находит запись при другом регистре запроса")
        void findsRecordWithDifferentCase() {
            // given
            var saved = em.persistAndFlush(anApprovedProfession("Golang Developer", 0));

            // when
            var result = repository.findByNameIgnoreCase("GOLANG developer");

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(saved.getId());
        }

        @Test
        @DisplayName("Возвращает empty для отсутствующего имени")
        void returnsEmptyForMissingName() {
            // when
            var result = repository.findByNameIgnoreCase("Zzz Nonexistent Profession Name");

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("UpsertAndIncrementUsage")
    class UpsertAndIncrementUsage {

        @Test
        @DisplayName("Новое имя создаёт профессию со status AUTO и usage_count 1")
        void newNameCreatesRowWithDefaults() {
            // when
            var returnedId = repository.upsertAndIncrementUsage("Golang Engineer");

            // then
            var saved = repository.findById(returnedId).orElseThrow();
            assertThat(saved.getName()).isEqualTo("Golang Engineer");
            assertThat(saved.getStatus()).isEqualTo(DictStatus.AUTO);
            assertThat(saved.getUsageCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Повторный вызов с тем же именем в другом регистре инкрементит usage_count и не меняет каноническое имя")
        void repeatedCallDifferentCaseIncrementsUsageKeepsCanonicalName() {
            // given
            var firstId = repository.upsertAndIncrementUsage("Rust Engineer");

            // when
            var secondId = repository.upsertAndIncrementUsage("rust engineer");

            // then
            assertThat(secondId).isEqualTo(firstId);
            var saved = repository.findById(firstId).orElseThrow();
            assertThat(saved.getName()).isEqualTo("Rust Engineer");
            assertThat(saved.getUsageCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Конфликт с сид-профессией инкрементит usage_count, не трогая status и каноническое имя")
        void conflictsWithSeededProfessionKeepsApprovedStatusAndCanonicalName() {
            // given — сид из schema.sql: ('Java-разработчик', 'APPROVED'), usage_count по умолчанию 0
            var seedId = (UUID) em.getEntityManager()
                    .createNativeQuery("SELECT id FROM content.profession_dict WHERE lower(name) = 'java-разработчик'")
                    .getSingleResult();

            // when
            var returnedId = repository.upsertAndIncrementUsage("java-разработчик");

            // then
            assertThat(returnedId).isEqualTo(seedId);
            var saved = repository.findById(returnedId).orElseThrow();
            assertThat(saved.getName()).isEqualTo("Java-разработчик");
            assertThat(saved.getStatus()).isEqualTo(DictStatus.APPROVED);
            assertThat(saved.getUsageCount()).isEqualTo(1);
        }
    }
}
