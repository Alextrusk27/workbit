package ru.workbit.content.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.content.model.TopicDict;

import java.util.UUID;

public interface TopicDictRepository extends JpaRepository<@NotNull TopicDict, @NotNull UUID> {
}
