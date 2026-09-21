package ru.workbit.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "account", schema = "billing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingAccount {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private int limits;

    @Column(name = "limits_expire_at")
    private Instant limitsExpireAt;

    @Column(name = "paid_at")
    private Instant paidAt;
}
