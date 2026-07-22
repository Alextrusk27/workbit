package ru.workbit.vacancy.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.workbit.vacancy.dto.HhVacancyResponse;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancyPreviewResponse;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.model.VacancySnapshot;

import java.util.Locale;

@Mapper(componentModel = "spring")
public interface VacancyMapper {

    @Mapping(target = "employer", source = "response.employer.name")
    @Mapping(target = "experience", source = "response.experience.name")
    @Mapping(target = "description", source = "description")
    VacancyData toVacancyData(HhVacancyResponse response, VacancySnapshot.Source source, String sourceId, String url, String description);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fetchedAt", ignore = true)
    VacancySnapshot toSnapshot(VacancyData data);

    @Mapping(target = "employer", source = "response.employer.name")
    @Mapping(target = "experience", source = "response.experience.name")
    VacancyPreviewResponse toPreview(HhVacancyResponse response, String url);

    VacancySnapshotView toSnapshotView(VacancySnapshot snapshot);

    default String toSkillName(HhVacancyResponse.KeySkill keySkill) {
        return keySkill.name();
    }

    default String toSalaryLabel(HhVacancyResponse.Salary salary) {
        if (salary == null) {
            return null;
        }
        String currency = switch (salary.currency()) {
            case "RUR" -> "₽";
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> salary.currency();
        };
        if (salary.from() != null && salary.to() != null) {
            return "%s - %s %s".formatted(formatAmount(salary.from()), formatAmount(salary.to()), currency);
        }
        return salary.from() != null
                ? "от %s %s".formatted(formatAmount(salary.from()), currency)
                : "до %s %s".formatted(formatAmount(salary.to()), currency);
    }

    private static String formatAmount(int value) {
        return String.format(Locale.US, "%,d", value).replace(",", " ");
    }
}
