package ru.workbit.content.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.User;
import ru.workbit.content.model.BankQuestion;
import ru.workbit.content.model.ProfessionDict;
import ru.workbit.content.model.SkillDict;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingSession;
import ru.workbit.util.DictText;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("QuestionBankRepositoryIT")
class QuestionBankRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private QuestionBankRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private ProfessionDict aProfession(String name) {
        return ProfessionDict.builder()
                .name(name)
                .matchKey(DictText.matchKey(name))
                .build();
    }

    private SkillDict aSkill(UUID professionId, String name) {
        return SkillDict.builder()
                .professionId(professionId)
                .name(name)
                .matchKey(DictText.matchKey(name))
                .build();
    }

    private BankQuestion aBankQuestion(UUID professionId, UUID skillId, List<String> levels, String text) {
        return BankQuestion.builder()
                .professionId(professionId)
                .skillId(skillId)
                .levels(levels)
                .text(text)
                .build();
    }

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .password("hashed_password")
                .build();
    }

    private TrainingSession aSession(UUID userId) {
        return TrainingSession.builder()
                .userId(userId)
                .skill("Spring Core")
                .profession("Java-разработчик")
                .level(TrainingSession.Level.JUNIOR)
                .build();
    }

    private TrainingQuestion aQuestion(TrainingSession session, UUID bankQuestionId, int orderIndex) {
        return TrainingQuestion.builder()
                .trainingSession(session)
                .bankQuestionId(bankQuestionId)
                .text("Вопрос " + orderIndex)
                .orderIndex(orderIndex)
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("SampleUnseen")
    class SampleUnseen {

        @Test
        @DisplayName("Возвращает вопросы только запрошенной профессии и навыка")
        void returnsOnlyQuestionsOfGivenProfessionAndSkill() {
            // given
            var professionA = em.persistAndFlush(aProfession("Java Developer"));
            var professionB = em.persistAndFlush(aProfession("Python Developer"));
            var skillA = em.persistAndFlush(aSkill(professionA.getId(), "Collections"));
            var skillB = em.persistAndFlush(aSkill(professionB.getId(), "Generators"));
            var questionA = em.persistAndFlush(
                    aBankQuestion(professionA.getId(), skillA.getId(), List.of("JUNIOR"), "Вопрос по Java"));
            em.persistAndFlush(aBankQuestion(professionB.getId(), skillB.getId(), List.of("JUNIOR"), "Вопрос по Python"));

            // when
            var result = repository.sampleUnseen(professionA.getId(), skillA.getId(), "JUNIOR", UUID.randomUUID(), 10);

            // then
            assertThat(result).extracting(BankQuestion::getId).containsExactly(questionA.getId());
        }

        @Test
        @DisplayName("Не возвращает вопросы другого навыка той же профессии")
        void doesNotReturnQuestionsOfAnotherSkillSameProfession() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var skillA = em.persistAndFlush(aSkill(profession.getId(), "Spring Core"));
            var skillB = em.persistAndFlush(aSkill(profession.getId(), "Collections"));
            var skillAQuestion = em.persistAndFlush(
                    aBankQuestion(profession.getId(), skillA.getId(), List.of("JUNIOR"), "Вопрос по Spring"));
            em.persistAndFlush(aBankQuestion(profession.getId(), skillB.getId(), List.of("JUNIOR"), "Вопрос по коллекциям"));

            // when
            var result = repository.sampleUnseen(profession.getId(), skillA.getId(), "JUNIOR", UUID.randomUUID(), 10);

            // then
            assertThat(result).extracting(BankQuestion::getId).containsExactly(skillAQuestion.getId());
        }

        @Test
        @DisplayName("Не возвращает вопрос, если professionId не соответствует профессии переданного skillId")
        void returnsEmptyWhenProfessionDoesNotMatchSkillOwner() {
            // given
            var professionA = em.persistAndFlush(aProfession("Java Developer"));
            var professionB = em.persistAndFlush(aProfession("Python Developer"));
            var skillA = em.persistAndFlush(aSkill(professionA.getId(), "Spring Core"));
            em.persistAndFlush(aBankQuestion(professionA.getId(), skillA.getId(), List.of("JUNIOR"), "Вопрос по Spring"));

            // when — skillA принадлежит professionA, но передан professionB
            var result = repository.sampleUnseen(professionB.getId(), skillA.getId(), "JUNIOR", UUID.randomUUID(), 10);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Уровень через ANY: вопрос с levels [JUNIOR, MIDDLE] находится для обоих, но не для SENIOR")
        void levelMatchesViaAnyArray() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var skill = em.persistAndFlush(aSkill(profession.getId(), "Spring Core"));
            var question = em.persistAndFlush(
                    aBankQuestion(profession.getId(), skill.getId(), List.of("JUNIOR", "MIDDLE"), "Многоуровневый вопрос"));

            // when
            var forJunior = repository.sampleUnseen(profession.getId(), skill.getId(), "JUNIOR", UUID.randomUUID(), 10);
            var forMiddle = repository.sampleUnseen(profession.getId(), skill.getId(), "MIDDLE", UUID.randomUUID(), 10);
            var forSenior = repository.sampleUnseen(profession.getId(), skill.getId(), "SENIOR", UUID.randomUUID(), 10);

            // then
            assertThat(forJunior).extracting(BankQuestion::getId).containsExactly(question.getId());
            assertThat(forMiddle).extracting(BankQuestion::getId).containsExactly(question.getId());
            assertThat(forSenior).isEmpty();
        }

        @Test
        @DisplayName("Исключает вопрос, увиденный этим пользователем в прошлой сессии")
        void excludesSeenQuestionsForSameUser() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var skill = em.persistAndFlush(aSkill(profession.getId(), "Spring Core"));
            var question = em.persistAndFlush(
                    aBankQuestion(profession.getId(), skill.getId(), List.of("JUNIOR"), "Уже виденный вопрос"));
            var user = em.persistAndFlush(aUser("seen@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(aQuestion(session, question.getId(), 1));

            // when
            var result = repository.sampleUnseen(profession.getId(), skill.getId(), "JUNIOR", user.getId(), 10);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Не исключает вопрос, увиденный другим пользователем")
        void includesSeenQuestionsForDifferentUser() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var skill = em.persistAndFlush(aSkill(profession.getId(), "Spring Core"));
            var question = em.persistAndFlush(
                    aBankQuestion(profession.getId(), skill.getId(), List.of("JUNIOR"), "Вопрос другого пользователя"));
            var owner = em.persistAndFlush(aUser("owner@example.com"));
            var otherUser = em.persistAndFlush(aUser("other@example.com"));
            var session = em.persistAndFlush(aSession(owner.getId()));
            em.persistAndFlush(aQuestion(session, question.getId(), 1));

            // when
            var result = repository.sampleUnseen(profession.getId(), skill.getId(), "JUNIOR", otherUser.getId(), 10);

            // then
            assertThat(result).extracting(BankQuestion::getId).containsExactly(question.getId());
        }

        @Test
        @DisplayName("Ограничивает выборку значением limit меньше доступного числа вопросов")
        void respectsLimit() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var skill = em.persistAndFlush(aSkill(profession.getId(), "Spring Core"));
            for (int i = 0; i < 5; i++) {
                em.persistAndFlush(aBankQuestion(profession.getId(), skill.getId(), List.of("JUNIOR"), "Вопрос " + i));
            }

            // when
            var result = repository.sampleUnseen(profession.getId(), skill.getId(), "JUNIOR", UUID.randomUUID(), 2);

            // then
            assertThat(result).hasSize(2);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("SkillIdRequired")
    class SkillIdRequired {

        @Test
        @DisplayName("Вставка вопроса банка с skill_id=NULL нарушает NOT NULL-констрейнт")
        void throwsWhenSkillIdIsNull() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var bad = BankQuestion.builder()
                    .professionId(profession.getId())
                    .levels(List.of("JUNIOR"))
                    .text("Вопрос без навыка")
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CheckConstraintLevels")
    class CheckConstraintLevels {

        @Test
        @DisplayName("Пустой массив levels нарушает CHECK-констрейнт chk_bank_levels")
        void throwsWhenLevelsEmpty() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var skill = em.persistAndFlush(aSkill(profession.getId(), "Spring Core"));
            var bad = aBankQuestion(profession.getId(), skill.getId(), List.of(), "Вопрос без уровней");

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Значение вне NOEXP/JUNIOR/MIDDLE/SENIOR нарушает CHECK-констрейнт chk_bank_levels")
        void throwsWhenLevelValueInvalid() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var skill = em.persistAndFlush(aSkill(profession.getId(), "Spring Core"));
            var bad = aBankQuestion(profession.getId(), skill.getId(), List.of("EXPERT"), "Вопрос с невалидным уровнем");

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Уровень NOEXP проходит CHECK-констрейнт chk_bank_levels")
        void allowsNoexpLevel() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var skill = em.persistAndFlush(aSkill(profession.getId(), "Spring Core"));
            var question = aBankQuestion(profession.getId(), skill.getId(), List.of("NOEXP"), "Вопрос для новичка без опыта");

            // when
            var saved = em.persistFlushFind(question);

            // then
            assertThat(saved.getLevels()).containsExactly("NOEXP");
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CascadeDeleteSkill")
    class CascadeDeleteSkill {

        @Test
        @DisplayName("Удаление навыка каскадно удаляет вопросы банка, привязанные к нему")
        void deletingSkillCascadesBankQuestions() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var skill = em.persistAndFlush(aSkill(profession.getId(), "Spring Core"));
            var question = em.persistAndFlush(
                    aBankQuestion(profession.getId(), skill.getId(), List.of("JUNIOR"), "Вопрос по Spring"));

            // when — физическое удаление навыка нативным SQL, чтобы проверить реальный
            // ON DELETE CASCADE в БД, минуя JPA-кеш
            em.getEntityManager()
                    .createNativeQuery("DELETE FROM content.skill_dict WHERE id = :id")
                    .setParameter("id", skill.getId())
                    .executeUpdate();
            em.flush();
            em.clear();

            // then
            assertThat(repository.findById(question.getId())).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("SaveAndRead")
    class SaveAndRead {

        @Test
        @DisplayName("Round-trip массива levels: данные читаются из БД идентично сохранённым")
        void roundTripLevelsArray() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var skill = em.persistAndFlush(aSkill(profession.getId(), "Spring Core"));
            var question = aBankQuestion(profession.getId(), skill.getId(), List.of("MIDDLE", "SENIOR"), "Вопрос с массивом уровней");

            // when
            var saved = em.persistFlushFind(question);

            // then
            assertThat(saved.getLevels()).containsExactlyInAnyOrder("MIDDLE", "SENIOR");
        }
    }
}
