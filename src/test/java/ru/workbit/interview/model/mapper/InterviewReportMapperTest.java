package ru.workbit.interview.model.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.workbit.interview.dto.InterviewReportResponse;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("InterviewReportMapperTest")
class InterviewReportMapperTest {

    private InterviewReportMapper mapper;

    @BeforeEach
    void setUp() {
        var reportMapper = new InterviewReportMapperImpl();
        ReflectionTestUtils.setField(reportMapper, "interviewQuestionMapper", new InterviewQuestionMapperImpl());
        mapper = reportMapper;
    }

    private InterviewQuestion aQuestion(String text, int orderIndex) {
        return InterviewQuestion.builder()
                .id(UUID.randomUUID())
                .text(text)
                .orderIndex(orderIndex)
                .build();
    }

    @Nested
    @DisplayName("ToResponse")
    class ToResponse {

        @Test
        @DisplayName("Переносит поля отчёта (включая recommendations и offerProbability), sessionId и вопросы из параметра")
        void mapsAllFieldsWithRecommendations() {
            // given
            var reportId = UUID.randomUUID();
            var sessionId = UUID.randomUUID();
            var generatedAt = Instant.now();
            var report = InterviewReport.builder()
                    .id(reportId)
                    .avgScore(3.8)
                    .offerProbability(InterviewReport.OfferProbability.MEDIUM)
                    .overallFeedback("Итоговый фидбэк по интервью")
                    .recommendations("Подтянуть SQL и алгоритмы")
                    .generatedAt(generatedAt)
                    .build();

            var session = InterviewSession.builder()
                    .id(sessionId)
                    .build();

            var question1 = aQuestion("Вопрос номер один", 1);
            var question2 = aQuestion("Вопрос номер два", 2);
            List<InterviewQuestion> questions = List.of(question1, question2);

            // when
            InterviewReportResponse dto = mapper.toResponse(report, session, questions);

            // then
            assertThat(dto.reportId()).isEqualTo(reportId);
            assertThat(dto.sessionId()).isEqualTo(sessionId);
            assertThat(dto.avgScore()).isEqualTo(3.8);
            assertThat(dto.offerProbability()).isEqualTo(InterviewReport.OfferProbability.MEDIUM);
            assertThat(dto.overallFeedback()).isEqualTo("Итоговый фидбэк по интервью");
            assertThat(dto.recommendations()).isEqualTo("Подтянуть SQL и алгоритмы");
            assertThat(dto.generatedAt()).isEqualTo(generatedAt);

            assertThat(dto.questions())
                    .hasSize(2)
                    .extracting(q -> q.questionId(), q -> q.questionText())
                    .containsExactly(
                            tuple(question1.getId(), "Вопрос номер один"),
                            tuple(question2.getId(), "Вопрос номер два")
                    );
        }

        @Test
        @DisplayName("recommendations остаётся null, когда у отчёта нет рекомендаций")
        void recommendationsNullWhenAbsent() {
            // given
            var report = InterviewReport.builder()
                    .id(UUID.randomUUID())
                    .avgScore(4.5)
                    .offerProbability(InterviewReport.OfferProbability.HIGH)
                    .overallFeedback("Итоговый фидбэк по интервью")
                    .recommendations(null)
                    .generatedAt(Instant.now())
                    .build();
            var session = InterviewSession.builder().id(UUID.randomUUID()).build();

            // when
            InterviewReportResponse dto = mapper.toResponse(report, session, List.of());

            // then
            assertThat(dto.recommendations()).isNull();
            assertThat(dto.offerProbability()).isEqualTo(InterviewReport.OfferProbability.HIGH);
            assertThat(dto.questions()).isEmpty();
        }
    }
}
