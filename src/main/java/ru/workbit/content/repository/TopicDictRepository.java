package ru.workbit.content.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.content.model.TopicDict;

import java.util.List;
import java.util.UUID;

public interface TopicDictRepository extends JpaRepository<@NotNull TopicDict, @NotNull UUID> {

    @Query(value = """
            SELECT t.* FROM content.topic_dict t
            JOIN content.profession_dict p ON p.id = t.profession_id
            WHERE lower(p.name) = lower(:profession)
              AND t.status = 'APPROVED'
              AND t.name ILIKE '%' || :query || '%'
            ORDER BY (t.name ILIKE :query || '%') DESC, t.usage_count DESC, t.name
            LIMIT :limit
            """, nativeQuery = true)
    List<TopicDict> suggest(String profession, String query, int limit);

    @Query(value = """
            INSERT INTO content.topic_dict (profession_id, name, usage_count) VALUES (:professionId, :name, 1)
            ON CONFLICT (profession_id, lower(name)) DO UPDATE SET usage_count = topic_dict.usage_count + 1
            RETURNING id
            """, nativeQuery = true)
    UUID upsertAndIncrementUsage(UUID professionId, String name);
}
