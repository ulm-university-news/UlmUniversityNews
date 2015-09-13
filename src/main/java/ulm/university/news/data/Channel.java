package ulm.university.news.data;

import ulm.university.news.util.ChannelType;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Channel {
    /** The unique id of the Channel. */
    int id;
    /** The name of the Channel. */
    String name;
    /** The description of the Channel. */
    String description;
    /** The type of the Channel. */
    ChannelType type;
    /** The date on which the Channel was created. */
    ZonedDateTime creationDate;
    /** The date on which the Channel was modified. */
    ZonedDateTime modificationDate;
    /** The term to which the Channel corresponds. */
    String term;
    /** The locations which belong to the Channel. */
    String locations;
    /** Dates which belong to the Channel. */
    String dates;
    /** Contact persons who belong to the Channel. */
    String contacts;
    /** The website of the Channel. */
    String website;
    /** Why? */
    boolean deleted;
    /** A list of all Announcements of the Channel. */
    List<Announcement> announcements;
    /** A list of all Reminders of the Channel. */
    List<Reminder> reminders;
    /** A list of all Moderators of the Channel. */
    List<Moderator> moderators;
}
