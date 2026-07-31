package ru.workbit.vacancy.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.vacancy.model.VacancySnapshot;

import java.util.List;
import java.util.UUID;

public interface VacancySnapshotRepository extends JpaRepository<@NotNull VacancySnapshot, @NotNull UUID> {

    @Query("SELECT v.id FROM VacancySnapshot v WHERE v.sourceId = :sourceId")
    List<UUID> findIdsBySourceId(@NotNull String sourceId);
}
