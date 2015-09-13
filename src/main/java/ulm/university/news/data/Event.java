package ulm.university.news.data;

/**
 * The Event class is a sub class of Channel. This class adds fields to describe an Event.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Event extends Channel {
    /** The cost of the Event. */
    String cost;
    /** The person who organizes the Event. */
    String organizer;
}
