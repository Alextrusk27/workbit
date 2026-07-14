package ru.workbit.vacancy.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.vacancy.model.VacancySnapshot;

import java.util.UUID;

public interface VacancySnapshotRepository extends JpaRepository<@NotNull VacancySnapshot, @NotNull UUID> {
}
