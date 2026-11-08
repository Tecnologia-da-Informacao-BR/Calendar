package br.com.calendar.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, String> {

    Optional<Reminder> findByTask_IdAndOccurrenceStartsAt(
            String taskId,
            Instant occurrenceStartsAt
    );
}