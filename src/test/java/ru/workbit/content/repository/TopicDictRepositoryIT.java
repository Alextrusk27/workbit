package ru.workbit.content.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.content.model.DictStatus;
import ru.workbit.content.model.ProfessionDict;
import ru.workbit.content.model.TopicDict;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TopicDictRepositoryIT")
class TopicDictRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private TopicDictRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private ProfessionDict aProfession(String name) {
        return ProfessionDict.builder()
                .name(name)
                .build();
    }

    private TopicDict aTopic(UUID professionId, String name) {
        return TopicDict.builder()
                .professionId(professionId)
                .name(name)
                .build(); // status=AUTO, usageCount=0 — @Builder.Default
    }

    private TopicDict aTopic(UUID professionId, String name, int usageCount) {
        return TopicDict.builder()
                .professionId(professionId)
                .name(name)
                .usageCount(usageCount)
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("UniqueNamePerProfession")
    class UniqueNamePerProfession {

        @Test
        @DisplayName("Дубль имени темы в другом регистре у той же профессии нарушает уникальность при flush")
        void throwsOnDuplicateNameSameProfessionDifferentCase() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            em.persistAndFlush(aTopic(profession.getId(), "Spring Core"));

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(aTopic(profession.getId(), "spring core")))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Одинаковое имя темы у разных профессий допустимо")
        void allowsSameNameForDifferentProfessions() {
            // given
            var professionA = em.persistAndFlush(aProfession("Java Developer"));
            var professionB = em.persistAndFlush(aProfession("Python Developer"));

            // when
            var topicA = em.persistAndFlush(aTopic(professionA.getId(), "Databases"));
            var topicB = em.persistAndFlush(aTopic(professionB.getId(), "Databases"));

            // then
            assertThat(topicA.getId()).isNotEqualTo(topicB.getId());
            assertThat(repository.findById(topicA.getId())).isPresent();
            assertThat(repository.findById(topicB.getId())).isPresent();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CheckConstraintStatus")
    class CheckConstraintStatus {

        @Test
        @DisplayName("Статус вне AUTO/APPROVED нарушает CHECK-констрейнт")
        void throwsWhenStatusInvalid() {
            // given
            var profession = em.persistAndFlush(aProfession("Check Status Profession"));

            // when / then — Java-энум DictStatus не позволяет собрать невалидное значение,
            // поэтому CHECK проверяем нативной вставкой в обход маппинга
            assertThatThrownBy(() -> em.getEntityManager()
                    .createNativeQuery("INSERT INTO content.topic_dict (id, profession_id, name, status) "
                            + "VALUES (gen_random_uuid(), :professionId, 'Bad Status Topic', 'BOGUS')")
                    .setParameter("professionId", profession.getId())
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
            var profession = em.persistAndFlush(aProfession("Uuid Gen Profession"));
            var topic = aTopic(profession.getId(), "Uuid Gen Topic");
            assertThat(topic.getId()).isNull();

            // when
            var saved = em.persistFlushFind(topic);

            // then
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("Дефолты status=AUTO и usageCount=0 применяются из @Builder.Default")
        void defaultsAreApplied() {
            // given
            var profession = em.persistAndFlush(aProfession("Defaults Profession"));

            // when
            var saved = em.persistFlushFind(aTopic(profession.getId(), "Defaults Topic"));

            // then
            assertThat(saved.getStatus()).isEqualTo(DictStatus.AUTO);
            assertThat(saved.getUsageCount()).isZero();
            assertThat(saved.getCreated()).isNotNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Suggest")
    class Suggest {

        @Test
        @DisplayName("Возвращаются только темы указанной профессии")
        void returnsOnlyTopicsOfGivenProfession() {
            // given
            var professionA = em.persistAndFlush(aProfession("Zzz Backend Developer"));
            var professionB = em.persistAndFlush(aProfession("Zzz Frontend Developer"));
            var topicA = em.persistAndFlush(aTopic(professionA.getId(), "Databases Basics"));
            em.persistAndFlush(aTopic(professionB.getId(), "Databases Basics"));

            // when
            var result = repository.suggest(professionA.getName(), "databases", 10);

            // then
            assertThat(result).extracting(TopicDict::getId).containsExactly(topicA.getId());
        }

        @Test
        @DisplayName("Профессия резолвится регистронезависимо")
        void resolvesProfessionCaseInsensitively() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Backend Developer"));
            var topic = em.persistAndFlush(aTopic(profession.getId(), "Rest Api Basics"));

            // when
            var result = repository.suggest("zzz BACKEND developer", "rest", 10);

            // then
            assertThat(result).extracting(TopicDict::getId).containsExactly(topic.getId());
        }

        @Test
        @DisplayName("Неизвестная профессия — пустой список")
        void unknownProfessionReturnsEmptyList() {
            // when
            var result = repository.suggest("Zzz Nonexistent Profession", "any", 10);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Prefix-совпадения идут раньше substring-совпадений независимо от usage_count")
        void prefixMatchesRankBeforeSubstringMatches() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Ranking Profession"));
            em.persistAndFlush(aTopic(profession.getId(), "Backend Dev Guru", 100));
            em.persistAndFlush(aTopic(profession.getId(), "Dev Ninja", 1));

            // when
            var result = repository.suggest(profession.getName(), "dev", 10);

            // then
            assertThat(result).extracting(TopicDict::getName)
                    .containsExactly("Dev Ninja", "Backend Dev Guru");
        }

        @Test
        @DisplayName("При равном типе совпадения — порядок по usage_count DESC")
        void sameMatchTypeOrderedByUsageCountDesc() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Usage Profession"));
            em.persistAndFlush(aTopic(profession.getId(), "Qwe One", 1));
            em.persistAndFlush(aTopic(profession.getId(), "Qwe Two", 5));

            // when
            var result = repository.suggest(profession.getName(), "qwe", 10);

            // then
            assertThat(result).extracting(TopicDict::getName)
                    .containsExactly("Qwe Two", "Qwe One");
        }
    }
}
