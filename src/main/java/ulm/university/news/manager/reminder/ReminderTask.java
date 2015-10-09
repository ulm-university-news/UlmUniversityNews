package ulm.university.news.manager.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.controller.ChannelController;
import ulm.university.news.data.Reminder;

/**
 * The ReminderTask is executed every time the next date of a Reminder has come. The task produces an Announcement and
 * schedules its next execution.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class ReminderTask implements Runnable {

    /** The logger instance for ReminderTask. */
    private static final Logger logger = LoggerFactory.getLogger(ReminderTask.class);

    /** Instance of the ChannelController class. */
    private ChannelController channelCtrl = new ChannelController();

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
    @Override
    public void run() {
        processReminder();
    }

    /**
     * Decides whether an Announcement should be created or not and computes the date on which the ReminderTask
     * should run next. An expired Reminder will be removed.
     */
    private void processReminder() {
        logger.info("Event of reminder with id {} has been fired.", reminder.getId());
        if (reminder.isExpired()) {
            // The Reminder is expired so stop its production of announcements.
            logger.debug("Expired. No announcement has been created. Reminder will be deactivated.");
            ReminderManager.removeReminder(reminder.getId());
        } else {
            if (reminder.isIgnore()) {
                // Ignore the production of an announcement this time and reset the ignore flag.
                reminder.setIgnore(false);
                logger.debug("Ignored. No announcement has been created. Ignore flag will be reset.");
                channelCtrl.resetReminderIgnore(reminder.getId());
            } else {
                // Create an announcement.
                logger.debug("Valid. Announcement has been created.");
                channelCtrl.createAnnouncementFromReminder(reminder);
            }
            // Compute the reminders next execution date.
            logger.debug("Compute reminder next date.");
            reminder.computeNextDate();

            // Check again if the reminder is expired to detect its possible expiration before waiting the whole
            // interval for its next (expired) execution.
            if (reminder.isExpired()) {
                // The reminder is expired so stop its production of announcements.
                logger.debug("Expired. Reminder will be deactivated.");
                ReminderManager.removeReminder(reminder.getId());
            }
        }
    }
}
