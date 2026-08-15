package ru.workbit.billing.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.billing.model.Payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<@NotNull Payment, @NotNull UUID> {

    Optional<Payment> findByInvId(int invId);

    List<Payment> findByStatusAndCreatedBetween(Payment.Status status, Instant from, Instant to);

    @Query(value = "SELECT nextval('billing.payment_inv_id_seq')", nativeQuery = true)
    int nextInvId();

    @Modifying
    @Query(value = """
            UPDATE billing.payment SET status = 'PAID', paid_at = :paidAt
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int markPaid(UUID id, Instant paidAt);
}
