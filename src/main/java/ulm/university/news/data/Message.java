package ulm.university.news.data;

import ulm.university.news.data.enums.Priority;

import java.time.ZonedDateTime;

/**
 * The Message class represents a general message. A message contains a message text and was created at a certain
 * date and time. Each message is sent with a defined priority.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Message {

    /** The unique id of the message. */
    protected int id;
    /** The text of the message. */
    protected String text;
    /** The date and time when the message was created. */
    protected ZonedDateTime creationDate;
    /** The priority of the message. */
    protected Priority priority;

    /**
     * Creates an instance of the Message class.
     */
    public Message(){

    }

    /**
     * Creates an instance of the Message class.
     *
     * @param text The text of the message.
     * @param creationDate The date and time when the message was created.
     * @param priority The priority of the message.
     */
    public Message(String text, ZonedDateTime creationDate, Priority priority){
        this.text = text;
        this.creationDate = creationDate;
        this.priority = priority;
    }

    /**
     * Creates an instance of the Message class.
     *
     * @param id The id of the message.
     * @param text The text of the message.
     * @param creationDate The date and time when the message was created.
     * @param priority The priority of the message.
     */
    public Message(int id, String text, ZonedDateTime creationDate, Priority priority){
        this.id = id;
        this.text = text;
        this.creationDate = creationDate;
        this.priority = priority;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", text='" + text + '\'' +
                ", creationDate=" + creationDate +
                ", priority=" + priority +
                '}';
    }
}
