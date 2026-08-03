package ru.workbit.content.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.content.model.DictStatus;
import ru.workbit.content.model.SkillDict;

import java.util.List;
import java.util.UUID;

public interface SkillDictRepository extends JpaRepository<@NotNull SkillDict, @NotNull UUID> {

    boolean existsByProfessionIdAndNameIgnoreCaseAndStatus(UUID professionId, String name, DictStatus status);

    @Query(value = """
            SELECT s.* FROM content.skill_dict s
            JOIN content.profession_dict p ON p.id = s.profession_id
            WHERE lower(p.name) = lower(:profession)
              AND s.status = 'APPROVED'
              AND s.name ILIKE '%' || :query || '%'
            ORDER BY (s.name ILIKE :query || '%') DESC, s.usage_count DESC, s.name
            LIMIT :limit
            """, nativeQuery = true)
    List<SkillDict> suggest(String profession, String query, int limit);

    @Query(value = """
            SELECT min(name) FROM content.skill_dict
            WHERE status = 'APPROVED'
              AND name ILIKE '%' || :query || '%'
            GROUP BY lower(name)
            ORDER BY (min(name) ILIKE :query || '%') DESC, sum(usage_count) DESC, min(name)
            LIMIT :limit
            """, nativeQuery = true)
    List<String> suggestAcrossProfessions(String query, int limit);

    @Query(value = """
            SELECT min(name) FROM content.skill_dict
            WHERE status = 'APPROVED'
            GROUP BY lower(name)
            ORDER BY sum(usage_count) DESC, min(name)
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findTopNames(int limit);

    @Query(value = """
            INSERT INTO content.skill_dict (profession_id, name, usage_count) VALUES (:professionId, :name, 1)
            ON CONFLICT (profession_id, lower(name)) DO UPDATE SET usage_count = skill_dict.usage_count + 1
            RETURNING id
            """, nativeQuery = true)
    UUID upsertAndIncrementUsage(UUID professionId, String name);
}
