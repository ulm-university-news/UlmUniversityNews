package ulm.university.news.manager.reminder;

import ulm.university.news.data.Reminder;

/**
 * The ReminderTask is executed every time the next date of a Reminder has come. The task produces an Announcement and
 * schedules its next execution.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class ReminderTask implements Runnable {

    /** The Reminder which produces an Announcement. */
    Reminder reminder;

    /**
     * Create a ReminderTask with the given Reminder.
     *
     * @param reminder The Reminder which should produce an Announcement.
     */
    public ReminderTask(Reminder reminder) {
        this.reminder = reminder;
    }

    /**
     * This method runs when the ReminderTask has started.
     */
    public void run()
    {
        processReminder();
    }

    /**
     * Decides whether an Announcement should be created or not and computes the date on which the ReminderTask
     * should run next. An expired Reminder will be removed.
     */
    private void processReminder() {
        if(reminder.isExpired()) {
            // The Reminder is expired so stop its production of Announcements.
            ReminderManager.removeReminder(reminder.getId());
        } else {
            if(reminder.isIgnore()) {
                // Ignore the production of an Announcement this time and reset the ignore flag.
                reminder.setIgnore(false);
                System.out.println("Reminder " + reminder.getId() + ": No announcement created.");
                // TODO: Store changes in database.
                // ChannelController.resetReminderIgnore()
            } else {
                // TODO: Produce an Announcement.
                // ChannelController.createAnnouncementFromReminder(reminder);
            }
            // Compute the Reminders next execution date.
            reminder.computeNextDate();

            // Check again if the Reminder is expired to detect its possible expiration before waiting the whole
            // interval for its next (expired) execution.
            if(reminder.isExpired()) {
                // The Reminder is expired so stop its production of Announcements.
                ReminderManager.removeReminder(reminder.getId());
            }
        }
    }
}
