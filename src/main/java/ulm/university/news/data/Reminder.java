package ulm.university.news.data;

import ulm.university.news.data.enums.Priority;

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
     * @param id              The unique Reminder id.
     * @param startDate       The date on which the Reminder should fire for the first time.
     * @param endDate         The date on which the Reminder should fire for the last time.
     * @param interval        The interval in seconds on which the Reminder should fire.
     * @param channelId       The id of the Channel which is associated with the Reminder.
     * @param authorModerator The id of the Moderator which links to the author of the Reminder.
     * @param title           The title of the Announcement.
     * @param text            The text of the Announcement.
     * @param priority        The priority of the Announcement.
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
        // Check if the start date is after the end date.
        if (startDate.isAfter(endDate)) {
            return false;
        } else {
            // Check if the interval is a multiple of a day (86400s = 24h * 60m * 60s).
            if(interval % 86400 != 0) {
                return false;
            } else {
                // Check if interval is at least one day and no more than 28 days (4 weeks).
                if(interval < 86400 || interval > 2419200) {
                    return false;
                }
            }
        }
        // All checks passed. Reminder is valid.
        return true;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public ZonedDateTime getModificationDate() {
        return modificationDate;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getNextDate() {
        return nextDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public boolean isIgnore() {
        return ignore;
    }

    public void setIgnore(boolean ignore) {
        this.ignore = ignore;
    }

    public int getChannelId() {
        return channelId;
    }

    public void setChannelId(int channelId) {
        this.channelId = channelId;
    }

    public int getAuthorModerator() {
        return authorModerator;
    }

    public void setAuthorModerator(int authorModerator) {
        this.authorModerator = authorModerator;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }
}