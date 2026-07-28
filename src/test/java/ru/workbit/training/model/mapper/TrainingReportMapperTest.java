package ru.workbit.training.model.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.workbit.training.dto.TrainingReportResponse;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingReport;
import ru.workbit.training.model.TrainingSession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("TrainingReportMapperTest")
class TrainingReportMapperTest {

    private TrainingReportMapper mapper;

    @BeforeEach
    void setUp() {
        var reportMapper = new TrainingReportMapperImpl();
        ReflectionTestUtils.setField(reportMapper, "trainingQuestionMapper", new TrainingQuestionMapperImpl());
        mapper = reportMapper;
    }

    private TrainingQuestion aQuestion(String text, int orderIndex) {
        return TrainingQuestion.builder()
                .id(UUID.randomUUID())
                .text(text)
                .orderIndex(orderIndex)
                .build();
    }

    @Nested
    @DisplayName("ToResponse")
    class ToResponse {

        @Test
        @DisplayName("Переносит поля отчёта, сессии и берёт вопросы ИМЕННО из третьего параметра, а не из session.getQuestions()")
        void takesQuestionsFromParameterNotFromSession() {
            // given
            var reportId = UUID.randomUUID();
            var sessionId = UUID.randomUUID();
            var generatedAt = Instant.now();
            var report = TrainingReport.builder()
                    .id(reportId)
                    .avgScore(4.2)
                    .overallFeedback("Итоговый фидбэк по тренировке")
                    .generatedAt(generatedAt)
                    .build();

            var sessionQuestion = aQuestion("Вопрос из session.getQuestions() - не должен попасть в ответ", 1);
            var session = TrainingSession.builder()
                    .id(sessionId)
                    .profession("Java-разработчик")
                    .topic("Spring Boot")
                    .level(TrainingSession.Level.MIDDLE)
                    .questions(List.of(sessionQuestion))
                    .build();

            var paramQuestion1 = aQuestion("Вопрос номер один из параметра", 1);
            var paramQuestion2 = aQuestion("Вопрос номер два из параметра", 2);
            List<TrainingQuestion> paramQuestions = List.of(paramQuestion1, paramQuestion2);

            // when
            TrainingReportResponse dto = mapper.toResponse(report, session, paramQuestions);

            // then
            assertThat(dto.reportId()).isEqualTo(reportId);
            assertThat(dto.sessionId()).isEqualTo(sessionId);
            assertThat(dto.profession()).isEqualTo("Java-разработчик");
            assertThat(dto.topic()).isEqualTo("Spring Boot");
            assertThat(dto.level()).isEqualTo(TrainingSession.Level.MIDDLE);
            assertThat(dto.avgScore()).isEqualTo(4.2);
            assertThat(dto.overallFeedback()).isEqualTo("Итоговый фидбэк по тренировке");
            assertThat(dto.generatedAt()).isEqualTo(generatedAt);

            assertThat(dto.questions())
                    .hasSize(2)
                    .extracting(q -> q.questionId(), q -> q.questionText())
                    .containsExactly(
                            tuple(paramQuestion1.getId(), "Вопрос номер один из параметра"),
                            tuple(paramQuestion2.getId(), "Вопрос номер два из параметра")
                    );
        }
    }
}
