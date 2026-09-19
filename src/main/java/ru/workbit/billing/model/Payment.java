package ru.workbit.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

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
        PACK_50(50, new BigDecimal("690.00"), "Пакет 50 лимитов", true),
        PACK_200(200, new BigDecimal("2290.00"), "Пакет 200 лимитов", true),
        PACK_500(500, new BigDecimal("4990.00"), "Пакет 500 лимитов", true),
        @Deprecated
        PLAN_PRO(400, new BigDecimal("790.00"), "Тариф «Про» на 30 дней", false),
        @Deprecated
        PLAN_MAX(1000, new BigDecimal("1490.00"), "Тариф «Макс» на 30 дней", false);

        private final int limits;
        private final BigDecimal price;
        private final String label;
        private final boolean purchasable;

        Product(int limits, BigDecimal price, String label, boolean purchasable) {
            this.limits = limits;
            this.price = price;
            this.label = label;
            this.purchasable = purchasable;
        }

        public String getDescription() {
            return label + " — Workbit";
        }
    }

    public enum Status {
        PENDING, PAID, FAILED
    }
}
