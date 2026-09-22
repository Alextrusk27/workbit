package ru.workbit;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class AbstractPostgresIT {

    // singleton-контейнер: стартуем один раз на JVM и не останавливаем.
    // @Container/@Testcontainers останавливают контейнер после каждого класса,
    // и при нескольких IT-классах Spring переиспользует кэшированный контекст
    // со старым (уже мёртвым) JDBC URL → connection timeout. Ryuk удалит контейнер при завершении JVM.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }

    // Секрет хеша email без дефолта в application.yml — иначе прод при забытой
    // переменной молча засеет хеши известным ключом. Значение здесь общее для Flyway и
    // EmailHasher: SQL и Java должны давать один хеш на одном адресе.
    protected static final String EMAIL_HASH_SECRET = "test-email-hash-secret-not-used-in-production";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.flyway.placeholders.email_hash_secret", () -> EMAIL_HASH_SECRET);
        r.add("app.email.hash-secret", () -> EMAIL_HASH_SECRET);
    }
}
