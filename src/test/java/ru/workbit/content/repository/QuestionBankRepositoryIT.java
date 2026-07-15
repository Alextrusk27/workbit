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
import ru.workbit.content.model.TopicDict;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.Profession;
import ru.workbit.interview.model.TrainingQuestion;
import ru.workbit.interview.model.TrainingSession;

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
                .build();
    }

    private TopicDict aTopic(UUID professionId, String name) {
        return TopicDict.builder()
                .professionId(professionId)
                .name(name)
                .build();
    }

    private BankQuestion aBankQuestion(UUID professionId, UUID topicId, List<String> levels, String text) {
        return BankQuestion.builder()
                .professionId(professionId)
                .topicId(topicId)
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
                .profession(Profession.JAVA_DEV)
                .level(Level.JUNIOR)
                .build();
    }

    private TrainingQuestion aQuestion(TrainingSession session, UUID bankQuestionId, int orderIndex) {
        return TrainingQuestion.builder()
                .trainingSession(session)
                .bankQuestionId(bankQuestionId)
                .questionText("Вопрос " + orderIndex)
                .orderIndex(orderIndex)
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("SampleUnseen")
    class SampleUnseen {

        @Test
        @DisplayName("Возвращает вопросы только запрошенной профессии")
        void filtersByProfession() {
            // given
            var professionA = em.persistAndFlush(aProfession("Java Developer"));
            var professionB = em.persistAndFlush(aProfession("Python Developer"));
            var questionA = em.persistAndFlush(
                    aBankQuestion(professionA.getId(), null, List.of("JUNIOR"), "Вопрос по Java"));
            em.persistAndFlush(aBankQuestion(professionB.getId(), null, List.of("JUNIOR"), "Вопрос по Python"));

            // when
            var result = repository.sampleUnseen(professionA.getId(), null, "JUNIOR", UUID.randomUUID(), 10);

            // then
            assertThat(result).extracting(BankQuestion::getId).containsExactly(questionA.getId());
        }

        @Test
        @DisplayName("topicId=null возвращает только общие вопросы, не тематические")
        void topicIdNullReturnsOnlyGeneralQuestions() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var topic = em.persistAndFlush(aTopic(profession.getId(), "Spring Core"));
            var generalQuestion = em.persistAndFlush(
                    aBankQuestion(profession.getId(), null, List.of("JUNIOR"), "Общий вопрос"));
            em.persistAndFlush(aBankQuestion(profession.getId(), topic.getId(), List.of("JUNIOR"), "Тематический вопрос"));

            // when
            var result = repository.sampleUnseen(profession.getId(), null, "JUNIOR", UUID.randomUUID(), 10);

            // then
            assertThat(result).extracting(BankQuestion::getId).containsExactly(generalQuestion.getId());
        }

        @Test
        @DisplayName("topicId задан — возвращает только вопросы этой темы")
        void topicIdSetReturnsOnlyThatTopicQuestions() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var topicA = em.persistAndFlush(aTopic(profession.getId(), "Spring Core"));
            var topicB = em.persistAndFlush(aTopic(profession.getId(), "Collections"));
            var topicAQuestion = em.persistAndFlush(
                    aBankQuestion(profession.getId(), topicA.getId(), List.of("JUNIOR"), "Вопрос по Spring"));
            em.persistAndFlush(aBankQuestion(profession.getId(), topicB.getId(), List.of("JUNIOR"), "Вопрос по коллекциям"));
            em.persistAndFlush(aBankQuestion(profession.getId(), null, List.of("JUNIOR"), "Общий вопрос"));

            // when
            var result = repository.sampleUnseen(profession.getId(), topicA.getId(), "JUNIOR", UUID.randomUUID(), 10);

            // then
            assertThat(result).extracting(BankQuestion::getId).containsExactly(topicAQuestion.getId());
        }

        @Test
        @DisplayName("Уровень через ANY: вопрос с levels [JUNIOR, MIDDLE] находится для обоих, но не для SENIOR")
        void levelMatchesViaAnyArray() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var question = em.persistAndFlush(
                    aBankQuestion(profession.getId(), null, List.of("JUNIOR", "MIDDLE"), "Многоуровневый вопрос"));

            // when
            var forJunior = repository.sampleUnseen(profession.getId(), null, "JUNIOR", UUID.randomUUID(), 10);
            var forMiddle = repository.sampleUnseen(profession.getId(), null, "MIDDLE", UUID.randomUUID(), 10);
            var forSenior = repository.sampleUnseen(profession.getId(), null, "SENIOR", UUID.randomUUID(), 10);

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
            var question = em.persistAndFlush(
                    aBankQuestion(profession.getId(), null, List.of("JUNIOR"), "Уже виденный вопрос"));
            var user = em.persistAndFlush(aUser("seen@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(aQuestion(session, question.getId(), 1));

            // when
            var result = repository.sampleUnseen(profession.getId(), null, "JUNIOR", user.getId(), 10);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Не исключает вопрос, увиденный другим пользователем")
        void includesSeenQuestionsForDifferentUser() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var question = em.persistAndFlush(
                    aBankQuestion(profession.getId(), null, List.of("JUNIOR"), "Вопрос другого пользователя"));
            var owner = em.persistAndFlush(aUser("owner@example.com"));
            var otherUser = em.persistAndFlush(aUser("other@example.com"));
            var session = em.persistAndFlush(aSession(owner.getId()));
            em.persistAndFlush(aQuestion(session, question.getId(), 1));

            // when
            var result = repository.sampleUnseen(profession.getId(), null, "JUNIOR", otherUser.getId(), 10);

            // then
            assertThat(result).extracting(BankQuestion::getId).containsExactly(question.getId());
        }

        @Test
        @DisplayName("Ограничивает выборку значением limit меньше доступного числа вопросов")
        void respectsLimit() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            for (int i = 0; i < 5; i++) {
                em.persistAndFlush(aBankQuestion(profession.getId(), null, List.of("JUNIOR"), "Вопрос " + i));
            }

            // when
            var result = repository.sampleUnseen(profession.getId(), null, "JUNIOR", UUID.randomUUID(), 2);

            // then
            assertThat(result).hasSize(2);
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
            var bad = aBankQuestion(profession.getId(), null, List.of(), "Вопрос без уровней");

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Значение вне JUNIOR/MIDDLE/SENIOR/LEAD нарушает CHECK-констрейнт chk_bank_levels")
        void throwsWhenLevelValueInvalid() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            var bad = aBankQuestion(profession.getId(), null, List.of("EXPERT"), "Вопрос с невалидным уровнем");

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
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
            var question = aBankQuestion(profession.getId(), null, List.of("MIDDLE", "SENIOR"), "Вопрос с массивом уровней");

            // when
            var saved = em.persistFlushFind(question);

            // then
            assertThat(saved.getLevels()).containsExactlyInAnyOrder("MIDDLE", "SENIOR");
        }
    }
}
