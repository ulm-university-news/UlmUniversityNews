package ulm.university.news.data;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import ulm.university.news.data.enums.Priority;

import java.time.ZonedDateTime;

import static ulm.university.news.util.Constants.TIME_ZONE;

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
    Integer interval;
    /** Defines if the next Reminder event should be ignored. */
    Boolean ignore;
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
     * Constructor which sets the given attributes.
     *
     * @param id The unique reminder id.
     * @param creationDate The date on which the reminder was created.
     * @param modificationDate The date on which the reminder was modified.
     * @param startDate The date on which the reminder should fire for the first time.
     * @param endDate The date on which the reminder should fire for the last time.
     * @param interval The interval in seconds on which the reminder should fire.
     * @param ignore Indicates weather the next reminder event should be ignored or not.
     * @param channelId The id of the channel which is associated with the reminder.
     * @param authorModerator The id of the moderator which links to the author of the reminder.
     * @param title The title of the announcement.
     * @param text The text of the announcement.
     * @param priority The priority of the announcement.
     */
    public Reminder(int id, ZonedDateTime creationDate, ZonedDateTime modificationDate, ZonedDateTime startDate,
                    ZonedDateTime endDate, Integer interval, Boolean ignore, int channelId, int authorModerator, String
                            title, String text, Priority priority) {
        this.id = id;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
        this.startDate = startDate;
        this.endDate = endDate;
        this.interval = interval;
        this.ignore = ignore;
        this.channelId = channelId;
        this.authorModerator = authorModerator;
        this.title = title;
        this.text = text;
        this.priority = priority;
    }

    @Override
    public String toString() {
        return "Reminder{" +
                "id=" + id +
                ", creationDate=" + creationDate +
                ", modificationDate=" + modificationDate +
                ", startDate=" + startDate +
                ", nextDate=" + nextDate +
                ", endDate=" + endDate +
                ", interval=" + interval +
                ", ignore=" + ignore +
                ", channelId=" + channelId +
                ", authorModerator=" + authorModerator +
                ", title='" + title + '\'' +
                ", text='" + text + '\'' +
                ", priority=" + priority +
                '}';
    }

    /**
     * Check if the reminders next date is after the reminders end date and if the reminders end date is in the past.
     *
     * @return true if Reminder is expired.
     */
    @JsonIgnore
    public boolean isExpired() {
        return nextDate.isAfter(endDate) || endDate.isBefore(ZonedDateTime.now(TIME_ZONE));
    }

    /**
     * Computes and sets the date on which the next ReminderTask should start.
     */
    public void computeNextDate() {
        // If interval is 0, it's a one time reminder, so marked reminder as expired.
        if(interval == 0){
            nextDate = endDate.plusSeconds(1);
        }else{
            nextDate = nextDate.plusSeconds(interval);
        }

    }

    /**
     * Computes and sets the date on which the fist ReminderTask should start.
     */
    public void computeFirstNextDate() {
        // Set first next date to start date.
        if (nextDate == null) {
            nextDate = startDate;
        }
        // If interval is 0, the first next date is the start date.
        if (interval == 0) {
            return;
        }
        // The next date has to be in the future.
        while (nextDate.isBefore(ZonedDateTime.now(TIME_ZONE))) {
            nextDate = nextDate.plusSeconds(interval);
        }
    }

    /**
     * Computes the creation date of the Reminder. If the creation date was already set, this method does nothing.
     */
    public void computeCreationDate() {
        if (creationDate == null) {
            creationDate = ZonedDateTime.now(TIME_ZONE);
        }
    }

    /**
     * Computes the modification date of the Reminder.
     */
    public void computeModificationDate() {
        modificationDate = ZonedDateTime.now(TIME_ZONE);
    }

    /**
     * Checks if the interval value is one of the allowed values.
     *
     * @return true if interval is valid.
     */
    @JsonIgnore
    public boolean isValidInterval() {
        // 0 is a valid interval. It means that there is no interval, it's a one time reminder.
        if (interval == 0) {
            return true;
        } else
            // If start date equals end date it's a one time reminder, so interval has to be 0.
            if (startDate.equals(endDate) && interval != 0) {
                return false;
            } else
                // Check if the interval is a multiple of a day (86400s = 24h * 60m * 60s).
                if (interval % 86400 != 0) {
                    return false;
                } else {
                    // Check if interval is at least one day and no more than 28 days (4 weeks).
                    if (interval < 86400 || interval > 2419200) {
                        return false;
                    }
                }
        // All checks passed. Interval is valid.
        return true;
    }

    /**
     * Checks if the start and end date is valid.
     *
     * @return true if dates are valid.
     */
    @JsonIgnore
    public boolean isValidDates() {
        // Check if the start date is after the end date.
        if (startDate.isAfter(endDate)) {
            return false;
            // The end date has to be in the future.
        } else if (endDate.isBefore(ZonedDateTime.now(TIME_ZONE))) {
            return false;
        }
        //All checks passed. Dates are valid.
        return true;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // Make sure that date is serialized correctly.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    // Make sure that date is serialized correctly.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public ZonedDateTime getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(ZonedDateTime modificationDate) {
        this.modificationDate = modificationDate;
    }

    // Make sure that date is serialized correctly.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    @JsonIgnore
    public ZonedDateTime getNextDate() {
        return nextDate;
    }

    // Make sure that date is serialized correctly.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public Integer getInterval() {
        return interval;
    }

    public void setInterval(Integer interval) {
        this.interval = interval;
    }

    public Boolean isIgnore() {
        return ignore;
    }

    public void setIgnore(Boolean ignore) {
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