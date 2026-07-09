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
import ru.workbit.vacancy.model.VacancySnapshot;
import ru.workbit.vacancy.model.mapper.VacancyMapper;
import ru.workbit.vacancy.repository.VacancySnapshotRepository;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VacancyService {
    private static final Pattern VACANCY_ID = Pattern.compile("hh\\.ru/vacancy/(\\d+)");

    private final HhClient hhClient;

    private final VacancySnapshotRepository vacancySnapshotRepository;
    private final VacancyMapper vacancyMapper;

    public VacancyData fetch(String url) {
        String vacancyId = getVacancyId(url);
        HhVacancyResponse hhVacancy = getActiveVacancy(vacancyId);
        return vacancyMapper.toVacancyData(hhVacancy, Long.parseLong(vacancyId), canonicalUrl(vacancyId),
                sanitize(hhVacancy.description()));
    }

    public VacancyPreviewResponse preview(String url) {
        String vacancyId = getVacancyId(url);
        return vacancyMapper.toPreview(getActiveVacancy(vacancyId), canonicalUrl(vacancyId));
    }

    public VacancyData fromText(String text) {
        return new VacancyData(null, null, null, null, null, null, sanitize(text));
    }

    public UUID saveSnapshot(VacancyData data, String name) {
        return vacancySnapshotRepository.save(vacancyMapper.toSnapshot(data, name)).getId();
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
}
