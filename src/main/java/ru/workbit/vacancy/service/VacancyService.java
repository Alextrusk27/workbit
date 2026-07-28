package ru.workbit.vacancy.service;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import ru.workbit.exception.NotFoundException;
import ru.workbit.vacancy.client.HhClient;
import ru.workbit.vacancy.dto.HhVacancyResponse;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancyPreviewResponse;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.dto.VacancyStatusResponse;
import ru.workbit.vacancy.model.VacancySnapshot;
import ru.workbit.vacancy.model.mapper.VacancyMapper;
import ru.workbit.vacancy.repository.VacancySnapshotRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VacancyService {
    private static final Pattern VACANCY_ID = Pattern.compile("hh\\.ru/vacancy/(\\d+)");
    private static final Duration STATUS_TTL = Duration.ofMinutes(30);

    private final HhClient hhClient;

    private final VacancySnapshotRepository vacancySnapshotRepository;
    private final VacancyMapper vacancyMapper;

    private final Map<String, CachedStatus> statusCache = new ConcurrentHashMap<>();

    public VacancyData fetch(String url) {
        String vacancyId = getVacancyId(url);
        HhVacancyResponse hhVacancy = getActiveVacancy(vacancyId);
        return vacancyMapper.toVacancyData(hhVacancy, VacancySnapshot.Source.HH, vacancyId, canonicalUrl(vacancyId),
                sanitize(hhVacancy.description()));
    }

    public VacancyPreviewResponse preview(String url) {
        String vacancyId = getVacancyId(url);
        return vacancyMapper.toPreview(getActiveVacancy(vacancyId), canonicalUrl(vacancyId));
    }

    public VacancyStatusResponse getStatus(String url) {
        String vacancyId = getVacancyId(url);
        CachedStatus cached = statusCache.get(vacancyId);
        if (cached == null || cached.checkedAt().isBefore(Instant.now().minus(STATUS_TTL))) {
            cached = new CachedStatus(fetchStatus(vacancyId), Instant.now());
            statusCache.put(vacancyId, cached);
        }
        return new VacancyStatusResponse(cached.status());
    }

    public UUID saveSnapshot(VacancyData data) {
        return vacancySnapshotRepository.save(vacancyMapper.toSnapshot(data)).getId();
    }

    public VacancySnapshotView getSnapshotView(UUID id) {
        return vacancySnapshotRepository.findById(id)
                .map(vacancyMapper::toSnapshotView)
                .orElseThrow(() -> new NotFoundException("Vacancy snapshot %s not found".formatted(id)));
    }

    public Map<UUID, VacancySnapshotView> getSnapshotViews(Collection<UUID> ids) {
        return vacancySnapshotRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(VacancySnapshot::getId, vacancyMapper::toSnapshotView));
    }

    private HhVacancyResponse getActiveVacancy(String vacancyId) {
        HhVacancyResponse hhVacancy = hhClient.getHhVacancy(vacancyId);
        if (hhVacancy.archived()) {
            throw new NotFoundException("Vacancy %s not found or archived".formatted(vacancyId));
        }
        return hhVacancy;
    }

    private VacancyStatusResponse.Status fetchStatus(String vacancyId) {
        try {
            return hhClient.getHhVacancy(vacancyId).archived()
                    ? VacancyStatusResponse.Status.ARCHIVED
                    : VacancyStatusResponse.Status.ACTIVE;
        } catch (NotFoundException e) {
            return VacancyStatusResponse.Status.NOT_FOUND;
        }
    }

    private String getVacancyId(String url) {
        Matcher m = VACANCY_ID.matcher(url);
        if (!m.find()) {
            throw new IllegalArgumentException("URL is not a hh.ru vacancy link: " + url);
        }
        return m.group(1);
    }

    private String sanitize(String html) {
        return Jsoup.parse(html).text();
    }

    private String canonicalUrl(String vacancyId) {
        return "https://hh.ru/vacancy/" + vacancyId;
    }

    private record CachedStatus(VacancyStatusResponse.Status status, Instant checkedAt) {
    }
}
