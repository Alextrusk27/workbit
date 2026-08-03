package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.InterviewAttemptResponse;
import ru.workbit.interview.dto.InterviewVacancyDetailResponse;
import ru.workbit.interview.dto.InterviewVacancyResponse;
import ru.workbit.interview.dto.RecommendedTrainingResponse;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.repository.InterviewSessionRepository;
import ru.workbit.training.dto.TrainingSkillMatch;
import ru.workbit.training.service.TrainingService;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.service.VacancyService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewVacancyService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final VacancyService vacancyService;
    private final TrainingService trainingService;

    public List<InterviewVacancyResponse> getAll(UUID userId) {
        List<InterviewSession> sessions = interviewSessionRepository.findAllByUserIdOrderByCreatedDesc(userId);
        Map<UUID, VacancySnapshotView> views = vacancyService.getSnapshotViews(
                sessions.stream().map(InterviewSession::getVacancySnapshotId).toList());

        Map<String, List<InterviewSession>> byVacancy = new LinkedHashMap<>();
        for (InterviewSession session : sessions) {
            byVacancy.computeIfAbsent(views.get(session.getVacancySnapshotId()).sourceId(), k -> new ArrayList<>())
                    .add(session);
        }

        return byVacancy.entrySet().stream()
                .map(e -> toVacancyResponse(e.getKey(), e.getValue(), views))
                .toList();
    }

    public InterviewVacancyDetailResponse get(String vacancyId, UUID userId) {
        List<InterviewSession> sessions = findSessions(vacancyId, userId);
        VacancySnapshotView view = vacancyService.getSnapshotView(sessions.getLast().getVacancySnapshotId());
        return new InterviewVacancyDetailResponse(
                vacancyId,
                view.name(),
                view.employer(),
                view.url(),
                view.experience(),
                sessions.stream().map(InterviewVacancyService::toAttempt).toList(),
                recommendations(sessions, userId));
    }

    @Transactional
    public void delete(String vacancyId, UUID userId) {
        List<InterviewSession> sessions = findSessions(vacancyId, userId);
        interviewSessionRepository.deleteAllByIdInBatch(
                sessions.stream().map(InterviewSession::getId).toList());
        vacancyService.deleteSnapshots(
                sessions.stream().map(InterviewSession::getVacancySnapshotId).toList());
    }

    private static InterviewVacancyResponse toVacancyResponse(String vacancyId, List<InterviewSession> sessions,
                                                              Map<UUID, VacancySnapshotView> views) {
        InterviewSession latest = sessions.getFirst();
        VacancySnapshotView view = views.get(latest.getVacancySnapshotId());
        InterviewReport best = bestReport(sessions);
        return new InterviewVacancyResponse(
                vacancyId,
                view.name(),
                view.employer(),
                view.url(),
                view.experience(),
                latest.getStatus(),
                (int) sessions.stream().filter(s -> s.getStatus() == InterviewSession.Status.COMPLETED).count(),
                best == null ? null : best.getAvgScore(),
                best == null ? null : best.getOfferProbability(),
                latest.getCreated());
    }

    private static InterviewReport bestReport(List<InterviewSession> sessions) {
        return sessions.stream()
                .map(InterviewSession::getReport)
                .filter(Objects::nonNull)
                .max(Comparator.comparingDouble(InterviewReport::getAvgScore))
                .orElse(null);
    }

    private static InterviewAttemptResponse toAttempt(InterviewSession session) {
        InterviewReport report = session.getReport();
        return new InterviewAttemptResponse(
                session.getId(),
                session.getStatus(),
                session.getCreated(),
                session.getCompletedAt(),
                report == null ? null : report.getAvgScore(),
                report == null ? null : report.getOfferProbability());
    }

    private List<InterviewSession> findSessions(String vacancyId, UUID userId) {
        List<UUID> snapshotIds = vacancyService.getSnapshotIds(vacancyId);
        List<InterviewSession> sessions = snapshotIds.isEmpty()
                ? List.of()
                : interviewSessionRepository.findAllByUserIdAndVacancySnapshotIdInOrderByCreatedAsc(userId, snapshotIds);
        if (sessions.isEmpty()) {
            log.warn("User {} has no interviews for vacancy {}", userId, vacancyId);
            throw new NotFoundException("Vacancy not found");
        }
        return sessions;
    }

    private List<RecommendedTrainingResponse> recommendations(List<InterviewSession> sessions, UUID userId) {
        Map<String, WeakSkill> bySkill = new LinkedHashMap<>();
        for (InterviewSession session : sessions) {
            InterviewReport report = session.getReport();
            if (report != null && report.getWeakestSkill() != null) {
                bySkill.put(report.getWeakestSkill().toLowerCase(),
                        new WeakSkill(report.getWeakestSkill(), report.getAvgScore()));
            }
        }
        if (bySkill.isEmpty()) {
            return List.of();
        }

        Map<String, TrainingSkillMatch> matches = trainingService.findLatestBySkills(userId, bySkill.keySet())
                .stream()
                .collect(Collectors.toMap(m -> m.skill().toLowerCase(), Function.identity(), (a, b) -> a));

        return bySkill.values().stream()
                .sorted(Comparator.comparingDouble(WeakSkill::score))
                .map(skill -> toRecommendation(skill, matches.get(skill.name().toLowerCase())))
                .toList();
    }

    private static RecommendedTrainingResponse toRecommendation(WeakSkill skill, TrainingSkillMatch match) {
        if (match == null) {
            return new RecommendedTrainingResponse(skill.name(), skill.score(), null, null, null, null, null);
        }
        return new RecommendedTrainingResponse(
                skill.name(),
                skill.score(),
                match.sessionId(),
                match.status().name(),
                match.avgScore(),
                match.answeredCount(),
                match.totalQuestions());
    }

    private record WeakSkill(String name, double score) {
    }
}
