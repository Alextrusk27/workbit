package ru.workbit.vacancy.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import ru.workbit.exception.NotFoundException;
import ru.workbit.exception.VacancyFetchException;
import ru.workbit.vacancy.client.HhClient;
import ru.workbit.vacancy.dto.HhVacancyResponse;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancyPreviewResponse;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.dto.VacancyStatusResponse;
import ru.workbit.vacancy.dto.VacancyStatusesResponse;
import ru.workbit.vacancy.model.VacancySnapshot;
import ru.workbit.vacancy.model.mapper.VacancyMapper;
import ru.workbit.vacancy.repository.VacancySnapshotRepository;

@Slf4j
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
        VacancyStatusResponse.Status status = getCachedStatus(vacancyId);
        return new VacancyStatusResponse(status != null ? status : fetchAndCacheStatus(vacancyId));
    }

    public VacancyStatusesResponse getStatuses(List<String> urls) {
        Map<String, String> vacancyIds = new LinkedHashMap<>();
        urls.forEach(url -> vacancyIds.put(url, getVacancyId(url)));

        Map<String, VacancyStatusResponse.Status> statuses = new LinkedHashMap<>();
        boolean hhAvailable = true;
        for (Map.Entry<String, String> entry : vacancyIds.entrySet()) {
            VacancyStatusResponse.Status status = getCachedStatus(entry.getValue());
            if (status == null && hhAvailable) {
                try {
                    status = fetchAndCacheStatus(entry.getValue());
                } catch (VacancyFetchException e) {
                    log.warn("hh.ru unavailable, skipping uncached statuses: {}", e.getMessage());
                    hhAvailable = false;
                }
            }
            if (status != null) {
                statuses.put(entry.getKey(), status);
            }
        }
        return new VacancyStatusesResponse(statuses);
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

    public List<UUID> getSnapshotIds(String sourceId) {
        return vacancySnapshotRepository.findIdsBySourceId(sourceId);
    }

    public void deleteSnapshots(Collection<UUID> ids) {
        vacancySnapshotRepository.deleteAllByIdInBatch(ids);
    }

    private HhVacancyResponse getActiveVacancy(String vacancyId) {
        HhVacancyResponse hhVacancy = hhClient.getHhVacancy(vacancyId);
        if (hhVacancy.archived()) {
            throw new NotFoundException("Vacancy %s not found or archived".formatted(vacancyId));
        }
        return hhVacancy;
    }

    private VacancyStatusResponse.Status getCachedStatus(String vacancyId) {
        CachedStatus cached = statusCache.get(vacancyId);
        if (cached == null || cached.checkedAt().isBefore(Instant.now().minus(STATUS_TTL))) {
            return null;
        }
        return cached.status();
    }

    private VacancyStatusResponse.Status fetchAndCacheStatus(String vacancyId) {
        VacancyStatusResponse.Status status = fetchStatus(vacancyId);
        statusCache.put(vacancyId, new CachedStatus(status, Instant.now()));
        return status;
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
