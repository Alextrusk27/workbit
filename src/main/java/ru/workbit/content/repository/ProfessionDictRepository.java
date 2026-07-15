package ru.workbit.content.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.content.model.ProfessionDict;

import java.util.List;
import java.util.UUID;

public interface ProfessionDictRepository extends JpaRepository<@NotNull ProfessionDict, @NotNull UUID> {

    List<ProfessionDict> findTop20ByOrderByUsageCountDesc();

    @Query(value = """
            SELECT * FROM content.profession_dict
            WHERE name ILIKE '%' || :query || '%'
            ORDER BY (name ILIKE :query || '%') DESC, usage_count DESC, name
            LIMIT :limit
            """, nativeQuery = true)
    List<ProfessionDict> suggest(String query, int limit);

    @Query(value = """
            INSERT INTO content.profession_dict (name, usage_count) VALUES (:name, 1)
            ON CONFLICT (lower(name)) DO UPDATE SET usage_count = profession_dict.usage_count + 1
            RETURNING id
            """, nativeQuery = true)
    UUID upsertAndIncrementUsage(String name);
}
