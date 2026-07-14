package ru.workbit.interview.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.jdbc.Sql;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.interview.model.BankQuestion;
import ru.workbit.interview.model.Category;
import ru.workbit.interview.model.Level;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("QuestionBankRepositoryIT")
class QuestionBankRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private QuestionBankRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private BankQuestion aQuestion(Category category, Level level, String text) {
        var question = BankQuestion.builder()
                .id(UUID.randomUUID())
                .category(category)
                .level(level)
                .text(text)
                .build();
        return em.persistAndFlush(question);
    }

    // =========================================================================

    @Nested
    @DisplayName("PickRandomByLevel")
    class PickRandomByLevel {

        @Test
        @DisplayName("Возвращает только вопросы запрошенного уровня и не больше quantity")
        void returnsOnlyRequestedLevelWithinQuantity() {
            // given
            aQuestion(Category.JAVA_CORE, Level.MIDDLE, "Вопрос 1");
            aQuestion(Category.SPRING, Level.MIDDLE, "Вопрос 2");
            aQuestion(Category.CONCURRENCY, Level.MIDDLE, "Вопрос 3");
            aQuestion(Category.SQL_JPA, Level.MIDDLE, "Вопрос 4");
            aQuestion(Category.JAVA_CORE, Level.SENIOR, "Вопрос 5");

            // when
            List<BankQuestion> result = repository.pickRandomByLevel(Level.MIDDLE, 3);

            // then
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(q -> q.getLevel() == Level.MIDDLE);
        }

        @Test
        @DisplayName("Возвращает все вопросы уровня, когда их меньше quantity")
        void returnsAllAvailableWhenFewerThanQuantity() {
            // given
            aQuestion(Category.JAVA_CORE, Level.JUNIOR, "Вопрос 1");
            aQuestion(Category.SPRING, Level.JUNIOR, "Вопрос 2");
            aQuestion(Category.JAVA_CORE, Level.MIDDLE, "Вопрос 3");

            // when
            List<BankQuestion> result = repository.pickRandomByLevel(Level.JUNIOR, 10);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(q -> q.getLevel() == Level.JUNIOR);
        }

        @Test
        @DisplayName("Возвращает пустой список, когда вопросов запрошенного уровня нет")
        void returnsEmptyWhenNoQuestionsOfLevel() {
            // given
            aQuestion(Category.JAVA_CORE, Level.MIDDLE, "Вопрос 1");

            // when
            List<BankQuestion> result = repository.pickRandomByLevel(Level.LEAD, 5);

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("DataSqlSeed")
    class DataSqlSeed {

        @Test
        @DisplayName("data.sql накатывается на реальную схему question_bank без ошибок и наполняет банк вопросов")
        @Sql("/data.sql")
        void seedsQuestionBankFromDataSql() {
            // when
            List<BankQuestion> all = repository.findAll();

            // then
            assertThat(all).isNotEmpty();
        }
    }
}
