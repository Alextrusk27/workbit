package ru.workbit.vacancy.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.vacancy.model.VacancySnapshot;

public interface VacancySnapshotRepository extends JpaRepository<VacancySnapshot, UUID> {

    @Query("SELECT v.id FROM VacancySnapshot v WHERE v.sourceId = :sourceId")
    List<UUID> findIdsBySourceId(String sourceId);
}
