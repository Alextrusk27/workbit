package ru.workbit.content.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.content.model.ProfessionDict;

import java.util.List;
import java.util.UUID;

public interface ProfessionDictRepository extends JpaRepository<@NotNull ProfessionDict, @NotNull UUID> {

    List<ProfessionDict> findTop20ByOrderByUsageCountDesc();
}
