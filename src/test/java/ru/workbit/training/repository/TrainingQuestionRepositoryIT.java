package ru.workbit.training.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    private TrainingQuestion aQuestion(TrainingSession session, int orderIndex) {
        return aQuestion(session, orderIndex, null);
    }

    private TrainingQuestion aQuestion(TrainingSession session, int orderIndex, UUID bankQuestionId) {
        return TrainingQuestion.builder()
                .trainingSession(session)
                .bankQuestionId(bankQuestionId)
                .text("Вопрос " + orderIndex)
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

    private BankQuestion aBankQuestion(UUID professionId, UUID skillId) {
        return BankQuestion.builder()
                .professionId(professionId)
                .skillId(skillId)
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
            var skill = em.persistAndFlush(aSkill(profession.getId(), "Set Null Skill"));
            var bankQuestion = em.persistAndFlush(aBankQuestion(profession.getId(), skill.getId()));
            var question = em.persistAndFlush(aQuestion(session, 1, bankQuestion.getId()));

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
        @DisplayName("Два вопроса с одинаковым order_index в одной сессии — нарушение уникальности при flush")
        void throwsWhenSameOrderIndexInSameSession() {
            // given
            var user = em.persistAndFlush(aUser("dup-order@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(aQuestion(session, 1));

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(aQuestion(session, 1)))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Одинаковый order_index в разных сессиях допустим")
        void allowsSameOrderIndexForDifferentSessions() {
            // given
            var user = em.persistAndFlush(aUser("same-order-diff-session@example.com"));
            var sessionA = em.persistAndFlush(aSession(user.getId()));
            var sessionB = em.persistAndFlush(aSession(user.getId()));

            // when
            var questionA = em.persistAndFlush(aQuestion(sessionA, 1));
            var questionB = em.persistAndFlush(aQuestion(sessionB, 1));

            // then
            assertThat(questionA.getId()).isNotNull();
            assertThat(questionB.getId()).isNotNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("OrderIndexRange")
    class OrderIndexRange {

        @ParameterizedTest
        @ValueSource(ints = {0, 51})
        @DisplayName("order_index вне диапазона 1..50 нарушает CHECK-констрейнт chk_question_order_index")
        void throwsWhenOrderIndexOutOfRange(int invalidOrderIndex) {
            // given
            var user = em.persistAndFlush(aUser("order-range-" + invalidOrderIndex + "@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var bad = aQuestion(session, invalidOrderIndex);

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Границы диапазона 1 и 50 допустимы")
        void allowsBoundaryOrderIndexValues() {
            // given
            var user = em.persistAndFlush(aUser("order-range-boundary@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));

            // when
            var low = em.persistAndFlush(aQuestion(session, 1));
            var high = em.persistAndFlush(aQuestion(session, 50));

            // then
            assertThat(low.getId()).isNotNull();
            assertThat(high.getId()).isNotNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("AnsweredConsistency")
    class AnsweredConsistency {

        @Test
        @DisplayName("answered=true с answer_text=null нарушает CHECK-констрейнт")
        void throwsWhenAnsweredTrueWithNullAnswerText() {
            // given
            var user = em.persistAndFlush(aUser("chk-answer-text@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var bad = TrainingQuestion.builder()
                    .trainingSession(session)
                    .text("Вопрос")
                    .orderIndex(1)
                    .answered(true)
                    .answeredAt(Instant.now())
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("answered=true с answered_at=null нарушает CHECK-констрейнт")
        void throwsWhenAnsweredTrueWithNullAnsweredAt() {
            // given
            var user = em.persistAndFlush(aUser("chk-answered-at@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var bad = TrainingQuestion.builder()
                    .trainingSession(session)
                    .text("Вопрос")
                    .orderIndex(1)
                    .answered(true)
                    .answerText("Ответ")
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("answered=true с заполненными answer_text и answered_at сохраняется без ошибок")
        void savesWhenAnsweredTrueWithBothFieldsSet() {
            // given
            var user = em.persistAndFlush(aUser("chk-answered-ok@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var good = answered(aQuestion(session, 1), Instant.now());

            // when
            var saved = em.persistFlushFind(good);

            // then
            assertThat(saved.isAnswered()).isTrue();
            assertThat(saved.getAnswerText()).isEqualTo("Ответ");
            assertThat(saved.getAnsweredAt()).isNotNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("SessionLevelCheck")
    class SessionLevelCheck {

        @Test
        @DisplayName("Уровень NOEXP у сессии проходит CHECK-констрейнт chk_session_level")
        void noexpLevelIsAllowed() {
            // given
            var user = em.persistAndFlush(aUser("noexp-level@example.com"));
            var session = TrainingSession.builder()
                    .userId(user.getId())
                    .skill("Основы Java")
                    .profession("Java-разработчик")
                    .level(TrainingSession.Level.NOEXP)
                    .build();

            // when
            var saved = em.persistFlushFind(session);

            // then
            assertThat(saved.getLevel()).isEqualTo(TrainingSession.Level.NOEXP);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindNextUnanswered")
    class FindNextUnanswered {

        @Test
        @DisplayName("Возвращает вопрос с наименьшим order_index среди неотвеченных")
        void returnsLowestOrderIndexUnanswered() {
            // given
            var user = em.persistAndFlush(aUser("next-unanswered@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(answered(aQuestion(session, 1), Instant.now()));
            var next = em.persistAndFlush(aQuestion(session, 2));
            em.persistAndFlush(aQuestion(session, 3));

            // when
            var result = repository.findNextUnanswered(session.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(next.getId());
        }

        @Test
        @DisplayName("Возвращает empty, когда все вопросы отвечены")
        void returnsEmptyWhenAllAnswered() {
            // given
            var user = em.persistAndFlush(aUser("next-unanswered-empty@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(answered(aQuestion(session, 1), Instant.now()));

            // when / then
            assertThat(repository.findNextUnanswered(session.getId())).isEmpty();
        }

        @Test
        @DisplayName("Учитывает только вопросы запрошенной сессии")
        void scopesToRequestedSessionOnly() {
            // given
            var user = em.persistAndFlush(aUser("next-unanswered-scope@example.com"));
            var sessionA = em.persistAndFlush(aSession(user.getId()));
            var sessionB = em.persistAndFlush(aSession(user.getId()));
            var questionA = em.persistAndFlush(aQuestion(sessionA, 1));
            em.persistAndFlush(aQuestion(sessionB, 1));

            // when
            var result = repository.findNextUnanswered(sessionA.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(questionA.getId());
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CountBySessionIds")
    class CountBySessionIds {

        @Test
        @DisplayName("Считает total и answered раздельно по каждой сессии, не путая значения между ними")
        void countsTotalAndAnsweredPerSessionSeparately() {
            // given
            var user = em.persistAndFlush(aUser("counts-multi@example.com"));
            var sessionA = em.persistAndFlush(aSession(user.getId()));
            var sessionB = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(answered(aQuestion(sessionA, 1), Instant.now()));
            em.persistAndFlush(aQuestion(sessionA, 2));
            em.persistAndFlush(aQuestion(sessionA, 3));
            em.persistAndFlush(answered(aQuestion(sessionB, 1), Instant.now()));
            em.persistAndFlush(answered(aQuestion(sessionB, 2), Instant.now()));

            // when
            var result = repository.countBySessionIds(List.of(sessionA.getId(), sessionB.getId()));

            // then
            assertThat(result).hasSize(2);
            var countsA = result.stream()
                    .filter(c -> c.getSessionId().equals(sessionA.getId()))
                    .findFirst()
                    .orElseThrow();
            var countsB = result.stream()
                    .filter(c -> c.getSessionId().equals(sessionB.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(countsA.getTotal()).isEqualTo(3);
            assertThat(countsA.getAnswered()).isEqualTo(1);
            assertThat(countsB.getTotal()).isEqualTo(2);
            assertThat(countsB.getAnswered()).isEqualTo(2);
        }

        @Test
        @DisplayName("Сессия, где отвечены все вопросы — answered равен total")
        void allAnsweredSessionHasAnsweredEqualToTotal() {
            // given
            var user = em.persistAndFlush(aUser("counts-all-answered@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(answered(aQuestion(session, 1), Instant.now()));
            em.persistAndFlush(answered(aQuestion(session, 2), Instant.now()));

            // when
            var result = repository.countBySessionIds(List.of(session.getId()));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTotal()).isEqualTo(2);
            assertThat(result.get(0).getAnswered()).isEqualTo(2);
        }

        @Test
        @DisplayName("Сессия без единого отвеченного вопроса — answered равен 0, не null")
        void noneAnsweredSessionHasAnsweredZero() {
            // given
            var user = em.persistAndFlush(aUser("counts-none-answered@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(aQuestion(session, 1));
            em.persistAndFlush(aQuestion(session, 2));

            // when
            var result = repository.countBySessionIds(List.of(session.getId()));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTotal()).isEqualTo(2);
            assertThat(result.get(0).getAnswered()).isEqualTo(0);
        }

        @Test
        @DisplayName("Сессия без единого вопроса не попадает в результат")
        void sessionWithoutQuestionsIsAbsentFromResult() {
            // given
            var user = em.persistAndFlush(aUser("counts-no-questions@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));

            // when
            var result = repository.countBySessionIds(List.of(session.getId()));

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Id чужой сессии в списке не подмешивает лишних строк")
        void doesNotMixInRowsForSessionNotRequested() {
            // given
            var user = em.persistAndFlush(aUser("counts-foreign@example.com"));
            var requestedSession = em.persistAndFlush(aSession(user.getId()));
            var foreignSession = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(aQuestion(requestedSession, 1));
            em.persistAndFlush(aQuestion(foreignSession, 1));
            em.persistAndFlush(aQuestion(foreignSession, 2));

            // when
            var result = repository.countBySessionIds(List.of(requestedSession.getId()));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSessionId()).isEqualTo(requestedSession.getId());
            assertThat(result.get(0).getTotal()).isEqualTo(1);
        }

        @Test
        @DisplayName("Пустой список id — фактическое поведение: пустой результат без ошибки")
        void emptySessionIdsListReturnsEmptyResult() {
            // given
            var user = em.persistAndFlush(aUser("counts-empty-list@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            em.persistAndFlush(aQuestion(session, 1));

            // when
            var result = repository.countBySessionIds(List.of());

            // then
            assertThat(result).isEmpty();
        }
    }
}
