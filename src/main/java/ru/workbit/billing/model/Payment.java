package ru.workbit.billing.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment", schema = "billing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "inv_id", nullable = false, updatable = false)
    private int invId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant created = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    @Getter
    public enum Product {
        PLAN_PRO(BillingAccount.Plan.PRO, new BigDecimal("790.00"), "Тариф «Про» на 30 дней"),
        PLAN_MAX(BillingAccount.Plan.MAX, new BigDecimal("1290.00"), "Тариф «Макс» на 30 дней");

        private final BillingAccount.Plan plan;
        private final BigDecimal price;
        private final String label;

        Product(BillingAccount.Plan plan, BigDecimal price, String label) {
            this.plan = plan;
            this.price = price;
            this.label = label;
        }

        public String getDescription() {
            return label + " — Workbit";
        }
    }

    public enum Status {
        PENDING, PAID, FAILED
    }
}
