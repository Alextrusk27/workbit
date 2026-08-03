package ru.workbit.content.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.content.model.SkillDict;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillDictRepository extends JpaRepository<@NotNull SkillDict, @NotNull UUID> {

    Optional<SkillDict> findByProfessionIdAndMatchKey(UUID professionId, String matchKey);

    @Query(value = """
            SELECT s.* FROM content.skill_dict s
            JOIN content.profession_dict p ON p.id = s.profession_id
            WHERE p.match_key = :professionKey
              AND s.status = 'APPROVED'
              AND s.name ILIKE '%' || :query || '%'
            ORDER BY (s.name ILIKE :query || '%') DESC, s.usage_count DESC, s.name
            LIMIT :limit
            """, nativeQuery = true)
    List<SkillDict> suggest(String professionKey, String query, int limit);

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

    /**
     * Название навыка с таким ключом у любой профессии: навык вводится раньше профессии, поэтому
     * ищем по всему словарю — иначе новая профессия завела бы двойника уже известного навыка.
     */
    @Query(value = """
            SELECT name FROM content.skill_dict
            WHERE match_key = :matchKey
            ORDER BY usage_count DESC, name
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findNameByMatchKey(String matchKey);

    /**
     * Названия словаря, у которых есть общее значащее слово с вводом: их показывают нормализатору,
     * чтобы он предложил уже принятое название вместо синонима-двойника.
     */
    @Query(value = """
            SELECT min(name) FROM content.skill_dict
            WHERE EXISTS (
                SELECT 1 FROM unnest(string_to_array(match_key, ' ')) AS t(token)
                WHERE t.token IN (:tokens)
            )
            GROUP BY match_key
            ORDER BY sum(usage_count) DESC, min(name)
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findCandidateNames(Collection<String> tokens, int limit);

    @Query(value = """
            INSERT INTO content.skill_dict (profession_id, name, match_key, usage_count)
            VALUES (:professionId, :name, :matchKey, 1)
            ON CONFLICT (profession_id, match_key) DO UPDATE SET usage_count = skill_dict.usage_count + 1
            RETURNING id
            """, nativeQuery = true)
    UUID upsertAndIncrementUsage(UUID professionId, String name, String matchKey);
}
