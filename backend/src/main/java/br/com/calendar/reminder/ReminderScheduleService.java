package br.com.calendar.reminder;

import br.com.calendar.task.Task;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class ReminderScheduleService {

    private final ReminderRepository reminderRepository;
    private final ReminderScheduleCalculator reminderScheduleCalculator;

    public ReminderScheduleService(ReminderRepository reminderRepository, ReminderScheduleCalculator reminderScheduleCalculator) {
        this.reminderRepository = reminderRepository;
        this.reminderScheduleCalculator = reminderScheduleCalculator;
    }

    public void scheduleForNewTask(Task task) {
        Instant occurrenceStarts = task.getStartsAt();
        Integer reminderMinutesBefore = task.getReminderMinutesBefore();
        if (occurrenceStarts == null || reminderMinutesBefore == null) {
            return;
        }
        Instant scheduleAt = reminderScheduleCalculator.calculateScheduledAt(occurrenceStarts, reminderMinutesBefore);
        Optional<Reminder> existingReminder = reminderRepository.findByTask_IdAndOccurrenceStartsAt(task.getId(),occurrenceStarts);


        if (existingReminder.isPresent()) {
            Reminder foundReminder = existingReminder.get();

            if (foundReminder.getStatus() == ReminderStatus.PENDING){
                foundReminder.setScheduledAt(scheduleAt);
                reminderRepository.save(foundReminder);
            }
            return;
        }
        Reminder reminder = new Reminder();
        reminder.setTask(task);
        reminder.setUser(task.getUser());
        reminder.setOccurrenceStartsAt(occurrenceStarts);
        reminder.setScheduledAt(scheduleAt);
        reminderRepository.save(reminder);
    }
}