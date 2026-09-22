package ru.workbit.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.billing.service.EmailHasher;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("WelcomeGrantRepositoryIT")
class WelcomeGrantRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private WelcomeGrantRepository repository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("Повторная вставка того же хеша возвращает 0 и не трогает существующую строку")
    void secondInsertOfSameHashReturnsZero() {
        // given
        String hash = "welcome-grant-repeat-hash";
        Instant now = Instant.now();
        assertThat(repository.insertIfAbsent(hash, now)).isEqualTo(1);

        // when
        int inserted = repository.insertIfAbsent(hash, now.plusSeconds(3600));

        // then
        assertThat(inserted).isZero();
        assertThat(repository.findById(hash)).isPresent();
    }

    @Test
    @DisplayName("Хеш из миграции (pgcrypto) совпадает с хешем EmailHasher на том же секрете")
    void sqlAndJavaHashesMatch() {
        // given — засев welcome_grant считает хеш в SQL, а приложение — в Java:
        // разойдись ключ или кодировка, каждый засеянный пользователь получил бы вторую двадцатку
        String email = "welcome-grant-vector@example.com";

        // when
        String sqlHash = (String) em.getEntityManager()
                .createNativeQuery("SELECT encode(hmac(:email, :secret, 'sha256'), 'base64')")
                .setParameter("email", email)
                .setParameter("secret", EMAIL_HASH_SECRET)
                .getSingleResult();

        // then
        assertThat(sqlHash).isEqualTo(new EmailHasher(EMAIL_HASH_SECRET).hash(email));
    }
}
