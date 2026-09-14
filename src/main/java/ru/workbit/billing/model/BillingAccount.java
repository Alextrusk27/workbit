package ru.workbit.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan;

    @Column(name = "plan_expires_at")
    private Instant planExpiresAt;

    @Column(name = "plan_interviews_left", nullable = false)
    private int planInterviewsLeft;

    @Column(name = "plan_trainings_left", nullable = false)
    private int planTrainingsLeft;

    @Getter
    public enum Plan {
        FREE(1, 3, false),
        PRO(10, 20, false),
        MAX(25, 0, true);

        private final int interviews;
        private final int trainings;
        private final boolean unlimitedTrainings;

        Plan(int interviews, int trainings, boolean unlimitedTrainings) {
            this.interviews = interviews;
            this.trainings = trainings;
            this.unlimitedTrainings = unlimitedTrainings;
        }
    }
}
