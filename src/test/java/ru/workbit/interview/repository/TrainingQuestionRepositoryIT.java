package ru.workbit.interview.repository;

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
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.TrainingQuestion;
import ru.workbit.interview.model.TrainingSession;

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
                .level(Level.JUNIOR)
                .build();
    }

    private TrainingQuestion aQuestion(TrainingSession session, TrainingQuestion parent, int orderIndex) {
        return aQuestion(session, parent, orderIndex, null);
    }

    private TrainingQuestion aQuestion(TrainingSession session, TrainingQuestion parent, int orderIndex, UUID bankQuestionId) {
        return TrainingQuestion.builder()
                .trainingSession(session)
                .parentQuestion(parent)
                .bankQuestionId(bankQuestionId)
                .questionText("Вопрос " + orderIndex)
                .orderIndex(orderIndex)
                .build();
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
            var question = em.persistAndFlush(aQuestion(session, null, 1, bankQuestion.getId()));

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
            assertThat(reloaded.get().getQuestionText()).isEqualTo("Вопрос 1");
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
            em.persistAndFlush(aQuestion(session, null, 1));

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(aQuestion(session, null, 1)))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Одинаковый order_index при разных parent_question_id допустим")
        void allowsSameOrderIndexForDifferentParents() {
            // given
            var user = em.persistAndFlush(aUser("same-order-diff-parent@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var parentA = em.persistAndFlush(aQuestion(session, null, 1));
            var parentB = em.persistAndFlush(aQuestion(session, null, 2));

            // when
            var childA = em.persistAndFlush(aQuestion(session, parentA, 1));
            var childB = em.persistAndFlush(aQuestion(session, parentB, 1));

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
            var parent = em.persistAndFlush(aQuestion(session, null, 1));
            var followUp = em.persistAndFlush(aQuestion(session, parent, 1));

            // when — физическое удаление родителя нативным SQL, чтобы проверить реальный
            // ON DELETE CASCADE в БД, минуя JPA-кеш
            em.getEntityManager()
                    .createNativeQuery("DELETE FROM interview.training_question WHERE id = :id")
                    .setParameter("id", parent.getId())
                    .executeUpdate();
            em.flush();
            em.clear();

            // then
            assertThat(repository.findById(parent.getId())).isEmpty();
            assertThat(repository.findById(followUp.getId())).isEmpty();
        }
    }
}
