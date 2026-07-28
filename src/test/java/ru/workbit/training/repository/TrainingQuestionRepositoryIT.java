package ru.workbit.training.repository;

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
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingSession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TrainingQuestionRepositoryIT")
class TrainingQuestionRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private TrainingQuestionRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .password("hashed_password")
                .build();
    }

    private TrainingSession aSession(UUID userId) {
        return TrainingSession.builder()
                .userId(userId)
                .profession("Java-разработчик")
                .level(TrainingSession.Level.JUNIOR)
                .build();
    }

    private TrainingQuestion aMainQuestion(TrainingSession session, int orderIndex) {
        return aMainQuestion(session, orderIndex, null);
    }

    private TrainingQuestion aMainQuestion(TrainingSession session, int orderIndex, UUID bankQuestionId) {
        return TrainingQuestion.builder()
                .trainingSession(session)
                .bankQuestionId(bankQuestionId)
                .text("Вопрос " + orderIndex)
                .orderIndex(orderIndex)
                .build();
    }

    private TrainingQuestion aFollowUpQuestion(TrainingSession session, UUID parentQuestionId, int orderIndex) {
        return TrainingQuestion.builder()
                .trainingSession(session)
                .parentQuestionId(parentQuestionId)
                .followUp(true)
                .text("Уточнение " + orderIndex)
                .orderIndex(orderIndex)
                .build();
    }

    private TrainingQuestion answered(TrainingQuestion question, Instant answeredAt) {
        question.setAnswered(true);
        question.setAnswerText("Ответ");
        question.setAnsweredAt(answeredAt);
        return question;
    }

    private ProfessionDict aProfession(String name) {
        return ProfessionDict.builder()
                .name(name)
                .build();
    }

    private BankQuestion aBankQuestion(UUID professionId) {
        return BankQuestion.builder()
                .professionId(professionId)
                .levels(List.of("JUNIOR"))
                .text("Вопрос из банка")
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("OnDeleteSetNull")
    class OnDeleteSetNull {

        @Test
        @DisplayName("Удаление вопроса банка обнуляет bank_question_id, не трогая саму строку")
        void deletingBankQuestionSetsBankQuestionIdNull() {
            // given
            var user = em.persistAndFlush(aUser("set-null@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var profession = em.persistAndFlush(aProfession("Set Null Profession"));
            var bankQuestion = em.persistAndFlush(aBankQuestion(profession.getId()));
            var question = em.persistAndFlush(aMainQuestion(session, 1, bankQuestion.getId()));

            // when — физическое удаление вопроса банка нативным SQL, чтобы проверить реальный
            // ON DELETE SET NULL в БД, минуя JPA-кеш
            em.getEntityManager()
                    .createNativeQuery("DELETE FROM content.question_bank WHERE id = :id")
                    .setParameter("id", bankQuestion.getId())
                    .executeUpdate();
            em.flush();
            em.clear();

            // then
            var reloaded = repository.findById(question.getId());
            assertThat(reloaded).isPresent();
            assertThat(reloaded.get().getBankQuestionId()).isNull();
            assertThat(reloaded.get().getText()).isEqualTo("Вопрос 1");
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("UniqueOrder")
    class UniqueOrder {

        @Test
        @DisplayName("Два вопроса с parent=NULL и одинаковым order_index в одной сессии — нарушение (NULLS NOT DISTINCT)")
        void throwsWhenSameParentNullAndSameOrderIndexInSession() {
            // given
            var user = em.persistAndFlush(aUser("dup-order@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(aMainQuestion(session, 1));

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(aMainQuestion(session, 1)))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Одинаковый order_index при разных parent_question_id допустим")
        void allowsSameOrderIndexForDifferentParents() {
            // given
            var user = em.persistAndFlush(aUser("same-order-diff-parent@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var parentA = em.persistAndFlush(aMainQuestion(session, 1));
            var parentB = em.persistAndFlush(aMainQuestion(session, 2));

            // when
            var childA = em.persistAndFlush(aFollowUpQuestion(session, parentA.getId(), 1));
            var childB = em.persistAndFlush(aFollowUpQuestion(session, parentB.getId(), 1));

            // then
            assertThat(childA.getId()).isNotNull();
            assertThat(childB.getId()).isNotNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CascadeDeleteParent")
    class CascadeDeleteParent {

        @Test
        @DisplayName("Удаление родительского вопроса каскадно удаляет follow-up")
        void deletingParentCascadesFollowUp() {
            // given
            var user = em.persistAndFlush(aUser("cascade-parent@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var parent = em.persistAndFlush(aMainQuestion(session, 1));
            var followUp = em.persistAndFlush(aFollowUpQuestion(session, parent.getId(), 1));

            // when — физическое удаление родителя нативным SQL, чтобы проверить реальный
            // ON DELETE CASCADE в БД, минуя JPA-кеш
            em.getEntityManager()
                    .createNativeQuery("DELETE FROM training.question WHERE id = :id")
                    .setParameter("id", parent.getId())
                    .executeUpdate();
            em.flush();
            em.clear();

            // then
            assertThat(repository.findById(parent.getId())).isEmpty();
            assertThat(repository.findById(followUp.getId())).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FollowUpParentConstraint")
    class FollowUpParentConstraint {

        @Test
        @DisplayName("follow_up=true без parent_question_id — нарушение констрейнта")
        void throwsWhenFollowUpTrueWithoutParent() {
            // given
            var user = em.persistAndFlush(aUser("chk-followup-no-parent@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var bad = TrainingQuestion.builder()
                    .trainingSession(session)
                    .followUp(true)
                    .text("Уточнение без родителя")
                    .orderIndex(1)
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("follow_up=false с заполненным parent_question_id — нарушение констрейнта")
        void throwsWhenFollowUpFalseWithParent() {
            // given
            var user = em.persistAndFlush(aUser("chk-followup-with-parent@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var parent = em.persistAndFlush(aMainQuestion(session, 1));
            var bad = TrainingQuestion.builder()
                    .trainingSession(session)
                    .parentQuestionId(parent.getId())
                    .followUp(false)
                    .text("Не уточнение, но с родителем")
                    .orderIndex(1)
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindNextUnansweredMain")
    class FindNextUnansweredMain {

        @Test
        @DisplayName("Возвращает мейн-вопрос с наименьшим order_index, игнорируя уточнения")
        void returnsLowestOrderIndexUnansweredMain() {
            // given
            var user = em.persistAndFlush(aUser("next-main@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var main1 = em.persistAndFlush(answered(aMainQuestion(session, 1), Instant.now()));
            var main2 = em.persistAndFlush(aMainQuestion(session, 2));
            em.persistAndFlush(aMainQuestion(session, 3));
            em.persistAndFlush(aFollowUpQuestion(session, main1.getId(), 1));

            // when
            var result = repository.findNextUnansweredMain(session.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(main2.getId());
        }

        @Test
        @DisplayName("Возвращает empty, когда все мейн-вопросы отвечены")
        void returnsEmptyWhenAllMainAnswered() {
            // given
            var user = em.persistAndFlush(aUser("next-main-empty@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(answered(aMainQuestion(session, 1), Instant.now()));

            // when / then
            assertThat(repository.findNextUnansweredMain(session.getId())).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindNextUnansweredFollowUp")
    class FindNextUnansweredFollowUp {

        @Test
        @DisplayName("Возвращает уточняющий вопрос с наименьшим order_index, игнорируя мейн-вопросы")
        void returnsLowestOrderIndexUnansweredFollowUp() {
            // given
            var user = em.persistAndFlush(aUser("next-followup@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var parent = em.persistAndFlush(aMainQuestion(session, 1));
            em.persistAndFlush(answered(aFollowUpQuestion(session, parent.getId(), 1), Instant.now()));
            var followUp2 = em.persistAndFlush(aFollowUpQuestion(session, parent.getId(), 2));

            // when
            var result = repository.findNextUnansweredFollowUp(session.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(followUp2.getId());
        }

        @Test
        @DisplayName("Возвращает empty, когда уточняющих вопросов нет")
        void returnsEmptyWhenNoFollowUpQuestions() {
            // given
            var user = em.persistAndFlush(aUser("next-followup-empty@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(aMainQuestion(session, 1));

            // when / then
            assertThat(repository.findNextUnansweredFollowUp(session.getId())).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindLastAnsweredUnchecked")
    class FindLastAnsweredUnchecked {

        @Test
        @DisplayName("Возвращает последний отвеченный непроверенный вопрос по answered_at DESC")
        void returnsMostRecentlyAnsweredUnchecked() {
            // given
            var user = em.persistAndFlush(aUser("last-unchecked@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var now = Instant.now();
            em.persistAndFlush(answered(aMainQuestion(session, 1), now.minusSeconds(60)));
            var newer = em.persistAndFlush(answered(aMainQuestion(session, 2), now));

            // when
            var result = repository.findLastAnsweredWithoutFollowUpCheck(session.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(newer.getId());
        }

        @Test
        @DisplayName("Игнорирует вопросы с follow_up_checked=true")
        void ignoresCheckedQuestions() {
            // given
            var user = em.persistAndFlush(aUser("last-unchecked-ignores-checked@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var now = Instant.now();
            var checked = answered(aMainQuestion(session, 1), now);
            checked.setFollowUpChecked(true);
            em.persistAndFlush(checked);
            var unchecked = em.persistAndFlush(answered(aMainQuestion(session, 2), now.minusSeconds(60)));

            // when
            var result = repository.findLastAnsweredWithoutFollowUpCheck(session.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(unchecked.getId());
        }

        @Test
        @DisplayName("Возвращает empty, когда нет отвеченных непроверенных вопросов")
        void returnsEmptyWhenNoAnsweredUncheckedQuestions() {
            // given
            var user = em.persistAndFlush(aUser("last-unchecked-empty@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(aMainQuestion(session, 1));

            // when / then
            assertThat(repository.findLastAnsweredWithoutFollowUpCheck(session.getId())).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindAllByParentQuestionIdOrderByOrderIndex")
    class FindAllByParentQuestionIdOrderByOrderIndex {

        @Test
        @DisplayName("Возвращает уточнения родителя, отсортированные по order_index")
        void returnsFollowUpsOrderedByOrderIndex() {
            // given
            var user = em.persistAndFlush(aUser("followups-by-parent@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var parent = em.persistAndFlush(aMainQuestion(session, 1));
            var followUp2 = em.persistAndFlush(aFollowUpQuestion(session, parent.getId(), 2));
            var followUp1 = em.persistAndFlush(aFollowUpQuestion(session, parent.getId(), 1));

            // when
            var result = repository.findAllByParentQuestionIdOrderByOrderIndex(parent.getId());

            // then
            assertThat(result).extracting(TrainingQuestion::getId)
                    .containsExactly(followUp1.getId(), followUp2.getId());
        }

        @Test
        @DisplayName("Возвращает пустой список, когда у родителя нет уточнений")
        void returnsEmptyListWhenNoFollowUps() {
            // given
            var user = em.persistAndFlush(aUser("followups-by-parent-empty@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var parent = em.persistAndFlush(aMainQuestion(session, 1));

            // when / then
            assertThat(repository.findAllByParentQuestionIdOrderByOrderIndex(parent.getId())).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CountByParentQuestionId")
    class CountByParentQuestionId {

        @Test
        @DisplayName("Возвращает количество уточнений родителя")
        void returnsCountOfFollowUps() {
            // given
            var user = em.persistAndFlush(aUser("count-followups@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var parent = em.persistAndFlush(aMainQuestion(session, 1));
            em.persistAndFlush(aFollowUpQuestion(session, parent.getId(), 1));
            em.persistAndFlush(aFollowUpQuestion(session, parent.getId(), 2));

            // when / then
            assertThat(repository.countByParentQuestionId(parent.getId())).isEqualTo(2);
        }

        @Test
        @DisplayName("Возвращает 0, когда у родителя нет уточнений")
        void returnsZeroWhenNoFollowUps() {
            // given
            var user = em.persistAndFlush(aUser("count-followups-empty@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var parent = em.persistAndFlush(aMainQuestion(session, 1));

            // when / then
            assertThat(repository.countByParentQuestionId(parent.getId())).isZero();
        }
    }
}
