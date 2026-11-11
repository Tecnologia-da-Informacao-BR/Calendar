package br.com.calendar.reminder;

import br.com.calendar.common.BaseEntity;
import br.com.calendar.task.Task;
import br.com.calendar.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Getter
@Setter
@ToString
@Entity
@Table(
        name = "reminder",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reminder_task_occurrence",
                        columnNames = {"task_id", "occurrence_starts_at"}
                )
        }
)
public class Reminder extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "occurrence_starts_at", nullable = false)
    private Instant occurrenceStartsAt;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderStatus status = ReminderStatus.PENDING;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Version
    @Column(nullable = false)
    private long version;
}