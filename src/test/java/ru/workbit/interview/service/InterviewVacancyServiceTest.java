package ru.workbit.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.InterviewAttemptResponse;
import ru.workbit.interview.dto.InterviewVacancyDetailResponse;
import ru.workbit.interview.dto.InterviewVacancyResponse;
import ru.workbit.interview.dto.RecommendedTrainingResponse;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.repository.InterviewSessionRepository;
import ru.workbit.training.dto.TrainingTopicMatch;
import ru.workbit.training.model.TrainingSession;
import ru.workbit.training.service.TrainingService;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.service.VacancyService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewVacancyServiceTest")
class InterviewVacancyServiceTest {

    @Mock
    InterviewSessionRepository interviewSessionRepository;
    @Mock
    VacancyService vacancyService;
    @Mock
    TrainingService trainingService;

    @InjectMocks
    InterviewVacancyService interviewVacancyService;

    private static InterviewSession aSession(UUID id, UUID userId, InterviewSession.Status status,
                                              UUID vacancySnapshotId, InterviewReport report) {
        return InterviewSession.builder()
                .id(id)
                .userId(userId)
                .vacancySnapshotId(vacancySnapshotId)
                .status(status)
                .report(report)
                .build();
    }

    private static InterviewReport aReport(Double avgScore, InterviewReport.OfferProbability offer, String weakestSkill) {
        return InterviewReport.builder()
                .avgScore(avgScore)
                .offerProbability(offer)
                .weakestSkill(weakestSkill)
                .build();
    }

    private static VacancySnapshotView aView(String sourceId, String name) {
        return new VacancySnapshotView(sourceId, name, "Работодатель", "https://hh.ru/vacancy/" + sourceId, "Опыт");
    }

    @Nested
    @DisplayName("GetAll")
    class GetAll {

        private final UUID userId = UUID.randomUUID();

        @Test
        @DisplayName("Группирует сессии по вакансии (новые вакансии первыми), поля - из последней сессии и лучшего отчёта")
        void groupsAndOrdersVacanciesByNewestSessionFirst() {
            // given
            UUID snapA = UUID.randomUUID();
            UUID snapB = UUID.randomUUID();

            InterviewSession sessionACompleted = aSession(UUID.randomUUID(), userId, InterviewSession.Status.COMPLETED,
                    snapA, aReport(3.0, InterviewReport.OfferProbability.LOW, null));
            InterviewSession sessionANew = aSession(UUID.randomUUID(), userId, InterviewSession.Status.IN_PROGRESS,
                    snapA, null);
            InterviewSession sessionB = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    snapB, null);

            // repository already returns newest-first: B, then A's newer attempt, then A's older attempt
            when(interviewSessionRepository.findAllByUserIdOrderByCreatedDesc(userId))
                    .thenReturn(List.of(sessionB, sessionANew, sessionACompleted));

            VacancySnapshotView viewA = aView("111", "Java-разработчик");
            VacancySnapshotView viewB = aView("222", "Python-разработчик");
            when(vacancyService.getSnapshotViews(List.of(snapB, snapA, snapA)))
                    .thenReturn(Map.of(snapA, viewA, snapB, viewB));

            // when
            List<InterviewVacancyResponse> result = interviewVacancyService.getAll(userId);

            // then
            assertThat(result).hasSize(2);

            InterviewVacancyResponse vacancyBResponse = result.get(0);
            assertThat(vacancyBResponse.vacancyId()).isEqualTo("222");
            assertThat(vacancyBResponse.vacancyName()).isEqualTo("Python-разработчик");
            assertThat(vacancyBResponse.status()).isEqualTo(InterviewSession.Status.CREATED);
            assertThat(vacancyBResponse.completedCount()).isZero();
            assertThat(vacancyBResponse.bestScore()).isNull();
            assertThat(vacancyBResponse.bestOffer()).isNull();
            assertThat(vacancyBResponse.lastActivity()).isEqualTo(sessionB.getCreated());

            InterviewVacancyResponse vacancyAResponse = result.get(1);
            assertThat(vacancyAResponse.vacancyId()).isEqualTo("111");
            assertThat(vacancyAResponse.vacancyName()).isEqualTo("Java-разработчик");
            assertThat(vacancyAResponse.status()).isEqualTo(InterviewSession.Status.IN_PROGRESS);
            assertThat(vacancyAResponse.completedCount()).isEqualTo(1);
            assertThat(vacancyAResponse.bestScore()).isEqualTo(3.0);
            assertThat(vacancyAResponse.bestOffer()).isEqualTo(InterviewReport.OfferProbability.LOW);
            assertThat(vacancyAResponse.lastActivity()).isEqualTo(sessionANew.getCreated());
        }

        @Test
        @DisplayName("Лучший отчёт выбирается по максимальному avgScore, а не по порядку сессий")
        void picksBestReportByMaxAvgScoreRegardlessOfOrder() {
            // given
            UUID snap = UUID.randomUUID();
            InterviewSession newest = aSession(UUID.randomUUID(), userId, InterviewSession.Status.COMPLETED,
                    snap, aReport(2.0, InterviewReport.OfferProbability.LOW, null));
            InterviewSession middle = aSession(UUID.randomUUID(), userId, InterviewSession.Status.COMPLETED,
                    snap, aReport(4.5, InterviewReport.OfferProbability.HIGH, null));
            InterviewSession oldest = aSession(UUID.randomUUID(), userId, InterviewSession.Status.COMPLETED,
                    snap, aReport(3.0, InterviewReport.OfferProbability.MEDIUM, null));

            when(interviewSessionRepository.findAllByUserIdOrderByCreatedDesc(userId))
                    .thenReturn(List.of(newest, middle, oldest));
            VacancySnapshotView view = aView("333", "Data Scientist");
            when(vacancyService.getSnapshotViews(List.of(snap, snap, snap)))
                    .thenReturn(Map.of(snap, view));

            // when
            List<InterviewVacancyResponse> result = interviewVacancyService.getAll(userId);

            // then
            assertThat(result).hasSize(1);
            InterviewVacancyResponse response = result.getFirst();
            assertThat(response.completedCount()).isEqualTo(3);
            assertThat(response.bestScore()).isEqualTo(4.5);
            assertThat(response.bestOffer()).isEqualTo(InterviewReport.OfferProbability.HIGH);
            assertThat(response.lastActivity()).isEqualTo(newest.getCreated());
        }

        @Test
        @DisplayName("Нет сессий у пользователя - пустой список")
        void returnsEmptyListWhenNoSessions() {
            // given
            when(interviewSessionRepository.findAllByUserIdOrderByCreatedDesc(userId)).thenReturn(List.of());
            when(vacancyService.getSnapshotViews(List.of())).thenReturn(Map.of());

            // when
            List<InterviewVacancyResponse> result = interviewVacancyService.getAll(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Get")
    class Get {

        private final UUID userId = UUID.randomUUID();
        private final String vacancyId = "123456";

        @Test
        @DisplayName("Нет снапшотов вакансии - NotFoundException, дальше по цепочке ничего не дёргается")
        void throwsWhenNoSnapshotIds() {
            // given
            when(vacancyService.getSnapshotIds(vacancyId)).thenReturn(List.of());

            // when / then
            assertThatThrownBy(() -> interviewVacancyService.get(vacancyId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Vacancy not found");
            verifyNoInteractions(interviewSessionRepository, trainingService);
            verify(vacancyService, never()).getSnapshotView(any());
        }

        @Test
        @DisplayName("Снапшоты есть, но сессий пользователя по ним нет - NotFoundException")
        void throwsWhenNoSessionsForSnapshotIds() {
            // given
            UUID snap = UUID.randomUUID();
            when(vacancyService.getSnapshotIds(vacancyId)).thenReturn(List.of(snap));
            when(interviewSessionRepository.findAllByUserIdAndVacancySnapshotIdInOrderByCreatedAsc(userId, List.of(snap)))
                    .thenReturn(List.of());

            // when / then
            assertThatThrownBy(() -> interviewVacancyService.get(vacancyId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Vacancy not found");
            verifyNoInteractions(trainingService);
            verify(vacancyService, never()).getSnapshotView(any());
        }

        @Test
        @DisplayName("Нет ни одного weakestSkill в отчётах - recommendedTrainings пуст, trainingService не вызывается")
        void returnsDetailWithoutRecommendationsWhenNoWeakestSkill() {
            // given
            UUID snap = UUID.randomUUID();
            InterviewSession session1 = aSession(UUID.randomUUID(), userId, InterviewSession.Status.COMPLETED,
                    snap, aReport(3.0, InterviewReport.OfferProbability.MEDIUM, null));
            InterviewSession session2 = aSession(UUID.randomUUID(), userId, InterviewSession.Status.IN_PROGRESS,
                    snap, null);
            when(vacancyService.getSnapshotIds(vacancyId)).thenReturn(List.of(snap));
            when(interviewSessionRepository.findAllByUserIdAndVacancySnapshotIdInOrderByCreatedAsc(userId, List.of(snap)))
                    .thenReturn(List.of(session1, session2));
            VacancySnapshotView view = aView(vacancyId, "Java-разработчик");
            when(vacancyService.getSnapshotView(snap)).thenReturn(view);

            // when
            InterviewVacancyDetailResponse result = interviewVacancyService.get(vacancyId, userId);

            // then
            assertThat(result.vacancyId()).isEqualTo(vacancyId);
            assertThat(result.vacancyName()).isEqualTo("Java-разработчик");
            assertThat(result.interviews()).containsExactly(
                    new InterviewAttemptResponse(session1.getId(), session1.getStatus(), session1.getCreated(),
                            session1.getCompletedAt(), 3.0, InterviewReport.OfferProbability.MEDIUM),
                    new InterviewAttemptResponse(session2.getId(), session2.getStatus(), session2.getCreated(),
                            session2.getCompletedAt(), null, null));
            assertThat(result.recommendedTrainings()).isEmpty();
            verifyNoInteractions(trainingService);
        }

        @Test
        @DisplayName("Несколько разных отстающих навыков - сортировка по возрастанию оценки, матч и отсутствие матча из тренажёра")
        void returnsRecommendationsSortedByScoreWithAndWithoutTrainingMatch() {
            // given
            UUID snap = UUID.randomUUID();
            InterviewSession session1 = aSession(UUID.randomUUID(), userId, InterviewSession.Status.COMPLETED,
                    snap, aReport(4.0, InterviewReport.OfferProbability.HIGH, "SOLID"));
            InterviewSession session2 = aSession(UUID.randomUUID(), userId, InterviewSession.Status.COMPLETED,
                    snap, aReport(2.0, InterviewReport.OfferProbability.LOW, "Многопоточность"));
            when(vacancyService.getSnapshotIds(vacancyId)).thenReturn(List.of(snap));
            when(interviewSessionRepository.findAllByUserIdAndVacancySnapshotIdInOrderByCreatedAsc(userId, List.of(snap)))
                    .thenReturn(List.of(session1, session2));
            VacancySnapshotView view = aView(vacancyId, "Java-разработчик");
            when(vacancyService.getSnapshotView(snap)).thenReturn(view);

            UUID trainingSessionId = UUID.randomUUID();
            TrainingTopicMatch solidMatch = new TrainingTopicMatch(
                    trainingSessionId, "Solid", TrainingSession.Status.IN_PROGRESS, null, 2, 10);
            when(trainingService.findLatestByTopics(userId, Set.of("solid", "многопоточность")))
                    .thenReturn(List.of(solidMatch));

            // when
            InterviewVacancyDetailResponse result = interviewVacancyService.get(vacancyId, userId);

            // then
            assertThat(result.recommendedTrainings()).containsExactly(
                    new RecommendedTrainingResponse("Многопоточность", 2.0, null, null, null, null, null),
                    new RecommendedTrainingResponse("SOLID", 4.0, trainingSessionId, "IN_PROGRESS", null, 2, 10));
        }

        @Test
        @DisplayName("Один и тот же навык в разных отчётах (разный регистр) - используется значение более позднего отчёта")
        void deduplicatesWeakestSkillByLowerCaseKeepingLatestReport() {
            // given
            UUID snap = UUID.randomUUID();
            InterviewSession olderSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.COMPLETED,
                    snap, aReport(4.0, InterviewReport.OfferProbability.HIGH, "Многопоточность"));
            InterviewSession newerSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.COMPLETED,
                    snap, aReport(1.5, InterviewReport.OfferProbability.LOW, "многопоточность"));
            when(vacancyService.getSnapshotIds(vacancyId)).thenReturn(List.of(snap));
            when(interviewSessionRepository.findAllByUserIdAndVacancySnapshotIdInOrderByCreatedAsc(userId, List.of(snap)))
                    .thenReturn(List.of(olderSession, newerSession));
            VacancySnapshotView view = aView(vacancyId, "Java-разработчик");
            when(vacancyService.getSnapshotView(snap)).thenReturn(view);
            when(trainingService.findLatestByTopics(userId, Set.of("многопоточность"))).thenReturn(List.of());

            // when
            InterviewVacancyDetailResponse result = interviewVacancyService.get(vacancyId, userId);

            // then
            assertThat(result.recommendedTrainings()).containsExactly(
                    new RecommendedTrainingResponse("многопоточность", 1.5, null, null, null, null, null));
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        private final UUID userId = UUID.randomUUID();
        private final String vacancyId = "123456";

        @Test
        @DisplayName("Нет сессий по вакансии - NotFoundException, ничего не удаляется")
        void throwsWhenNoSessionsFound() {
            // given
            when(vacancyService.getSnapshotIds(vacancyId)).thenReturn(List.of());

            // when / then
            assertThatThrownBy(() -> interviewVacancyService.delete(vacancyId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Vacancy not found");
            verify(interviewSessionRepository, never()).deleteAllByIdInBatch(any());
            verify(vacancyService, never()).deleteSnapshots(any());
        }

        @Test
        @DisplayName("Сессии найдены - удаляются сессии батчем, затем их снапшоты")
        void deletesSessionsAndSnapshots() {
            // given
            UUID snap1 = UUID.randomUUID();
            UUID snap2 = UUID.randomUUID();
            InterviewSession session1 = aSession(UUID.randomUUID(), userId, InterviewSession.Status.COMPLETED,
                    snap1, aReport(3.0, InterviewReport.OfferProbability.MEDIUM, null));
            InterviewSession session2 = aSession(UUID.randomUUID(), userId, InterviewSession.Status.IN_PROGRESS,
                    snap2, null);
            when(vacancyService.getSnapshotIds(vacancyId)).thenReturn(List.of(snap1, snap2));
            when(interviewSessionRepository.findAllByUserIdAndVacancySnapshotIdInOrderByCreatedAsc(userId, List.of(snap1, snap2)))
                    .thenReturn(List.of(session1, session2));

            // when
            interviewVacancyService.delete(vacancyId, userId);

            // then
            verify(interviewSessionRepository).deleteAllByIdInBatch(List.of(session1.getId(), session2.getId()));
            verify(vacancyService).deleteSnapshots(List.of(snap1, snap2));
        }
    }
}
