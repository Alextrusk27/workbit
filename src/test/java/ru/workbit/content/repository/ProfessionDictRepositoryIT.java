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
}
