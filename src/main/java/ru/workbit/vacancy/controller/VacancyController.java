package ru.workbit.vacancy.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.workbit.vacancy.dto.VacancyPreviewResponse;
import ru.workbit.vacancy.service.VacancyService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vacancies")
public class VacancyController {
    private final VacancyService vacancyService;

    @GetMapping("/preview")
    public VacancyPreviewResponse preview(@RequestParam String url) {
        return vacancyService.preview(url);
    }
}
