package ulm.university.news.data;

import ulm.university.news.util.Priority;

import java.time.ZonedDateTime;

import static ulm.university.news.util.Constants.*;

/**
 * The Reminder class provides information which are necessary to produce Announcement messages at certain,
 * periodic times.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Reminder {
    /** The unique Reminder id. */
    int id;
    /** The date on which the Reminder was created. */
    ZonedDateTime creationDate;
    /** The date on which the Reminder was modified. */
    ZonedDateTime modificationDate;
    /** The date on which the Reminder should fire for the first time. */
    ZonedDateTime startDate;
    /** The date on which the Reminder should fire the next time. */
    ZonedDateTime nextDate;
    /** The date on which the Reminder should fire for the last time. */
    ZonedDateTime endDate;
    /** The interval in seconds on which the Reminder should fire. */
    int interval;
    /** Defines if the next Reminder event should be ignored. */
    boolean ignore;
    /** The id of the Channel which is associated with the Reminder. */
    int channelId;
    /** The id of the Moderator which links to the author of the Reminder. */
    int authorModerator;
    /** The title of the Announcement. */
    String title;
    /** The text of the Announcement. */
    String text;
    /** The priority of the Announcement. */
    Priority priority;

    /**
     * Empty constructor. Needed values are set with corresponding set methods. Useful for Reminder update.
     */
    public Reminder() {
    }

    /**
     * Constructor which sets the given attributes and computes the remaining fields.
     *
     * @param id The unique Reminder id.
     * @param startDate The date on which the Reminder should fire for the first time.
     * @param endDate The date on which the Reminder should fire for the last time.
     * @param interval The interval in seconds on which the Reminder should fire.
     * @param channelId The id of the Channel which is associated with the Reminder.
     * @param authorModerator The id of the Moderator which links to the author of the Reminder.
     * @param title The title of the Announcement.
     * @param text The text of the Announcement.
     * @param priority The priority of the Announcement.
     */
    public Reminder(int id, ZonedDateTime startDate, ZonedDateTime endDate, int interval, int channelId, int
            authorModerator, String title, String text, Priority priority) {
        this.id = id;
        this.startDate = startDate;
        this.nextDate = startDate;
        this.endDate = endDate;
        this.interval = interval;
        this.ignore = false;
        this.channelId = channelId;
        this.authorModerator = authorModerator;
        this.title = title;
        this.text = text;
        this.priority = priority;
        computeCreationDate();
        computeFirstNextDate();
    }

    /**
     * Check if the Reminders next date is after the Reminders end date.
     *
     * @return true if Reminder is expired
     */
    public boolean isExpired() {
        return nextDate.isAfter(endDate);
    }

    /**
     * Computes and sets the date on which the next ReminderTask should start.
     */
    public void computeNextDate() {
        nextDate = nextDate.plusSeconds(interval);
    }

    /**
     * Computes and sets the date on which the fist ReminderTask should start.
     */
    public void computeFirstNextDate() {
        // The next date has to be in the future.
        while (nextDate.isBefore(ZonedDateTime.now(TIME_ZONE))) {
            nextDate = nextDate.plusSeconds(interval);
        }
    }

    /**
     * Computes the creation date of the Reminder. If the creation date was already set, this method does nothing.
     */
    private void computeCreationDate() {
        if (creationDate != null) {
            creationDate = ZonedDateTime.now(TIME_ZONE);
            // The first modification date is equal to the creation date.
            modificationDate = creationDate;
        }
    }

    /**
     * Computes the modification date of the Reminder.
     */
    public void computeModificationDate() {
        modificationDate = ZonedDateTime.now(TIME_ZONE);
    }

    /**
     * Checks if the interval value is one of the allowed values and if the end date is after the start date.
     *
     * @return true if Reminder is valid
     */
    public boolean isValid() {
        // TODO
        if (endDate.isAfter(startDate)) {
            return false;
        }
        return true;
    }
}