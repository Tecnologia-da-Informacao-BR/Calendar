package br.com.calendar.reminder;

/**
 * Summary of one scheduler run.
 *
 * @param recovered abandoned claims returned to PENDING
 * @param ready     reminders that became eligible for delivery
 * @param skipped   reminders whose event had already started
 * @param canceled  reminders whose task was deleted or completed
 */
public record ReminderProcessingResult(int recovered, int ready, int skipped, int canceled) {

    public static final ReminderProcessingResult EMPTY = new ReminderProcessingResult(0, 0, 0, 0);

    public boolean isEmpty() {
        return recovered == 0 && ready == 0 && skipped == 0 && canceled == 0;
    }
}
