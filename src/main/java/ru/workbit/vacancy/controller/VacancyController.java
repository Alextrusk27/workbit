package ru.workbit.vacancy.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import ru.workbit.util.annotation.Loggable;
import ru.workbit.vacancy.dto.VacancyPreviewResponse;
import ru.workbit.vacancy.dto.VacancyStatusResponse;
import ru.workbit.vacancy.dto.VacancyStatusesRequest;
import ru.workbit.vacancy.dto.VacancyStatusesResponse;
import ru.workbit.vacancy.service.VacancyService;

@RestController
@RequiredArgsConstructor
public class VacancyController implements VacancyApi {
    private final VacancyService vacancyService;

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public VacancyPreviewResponse preview(String url) {
        return vacancyService.preview(url);
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public VacancyStatusResponse status(String url) {
        return vacancyService.getStatus(url);
    }

    @Override
    @Loggable(level = "DEBUG", logArgs = true)
    public VacancyStatusesResponse statuses(VacancyStatusesRequest request) {
        return vacancyService.getStatuses(request.urls());
    }
}
