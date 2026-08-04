package ru.workbit.content.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.content.model.DictStatus;
import ru.workbit.content.model.ProfessionDict;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfessionDictRepository extends JpaRepository<@NotNull ProfessionDict, @NotNull UUID> {

    List<ProfessionDict> findTop20ByStatusOrderByUsageCountDesc(DictStatus status);

    Optional<ProfessionDict> findByMatchKey(String matchKey);

    @Query(value = """
            SELECT * FROM content.profession_dict
            WHERE status = 'APPROVED'
              AND name ILIKE '%' || :query || '%'
            ORDER BY (name ILIKE :query || '%') DESC, usage_count DESC, name
            LIMIT :limit
            """, nativeQuery = true)
    List<ProfessionDict> suggest(String query, int limit);

    /**
     * Названия словаря, у которых есть общее значащее слово с вводом: их показывают нормализатору,
     * чтобы он предложил уже принятое название вместо синонима-двойника.
     */
    @Query(value = """
            SELECT name FROM content.profession_dict
            WHERE EXISTS (
                SELECT 1 FROM unnest(string_to_array(match_key, ' ')) AS t(token)
                WHERE t.token IN (:tokens)
            )
            ORDER BY usage_count DESC, name
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findCandidateNames(Collection<String> tokens, int limit);

    @Query(value = """
            INSERT INTO content.profession_dict (name, match_key, usage_count) VALUES (:name, :matchKey, 1)
            ON CONFLICT (match_key) DO UPDATE SET usage_count = profession_dict.usage_count + 1
            RETURNING id
            """, nativeQuery = true)
    UUID upsertAndIncrementUsage(String name, String matchKey);
}
