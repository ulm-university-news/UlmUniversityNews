package ulm.university.news.data;

import ulm.university.news.data.enums.Priority;

import java.time.ZonedDateTime;

/**
 * The ConversationMessage class represents a message which is sent within a conversation. The message belongs to a
 * certain conversation and is sent by a user. The message contains the id of the author and the id of the
 * conversation.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class ConversationMessage extends Message {

    /** The id of the user who is the author of the message. */
    private int authorUser;
    /** The id of the conversation to which the message belongs. */
    private int conversationId;

    /**
     * Creates an instance of the ConversationMessage class.
     */
    public ConversationMessage(){
        super();
    }

    /**
     * Creates an instance of the ConversationMessage class.
     *
     * @param text The text of the message.
     * @param messageNumber The number of the message regarding the given conversation.
     * @param priority The priority of the message.
     * @param creationDate The date and time when the message was created.
     * @param authorUser The id of the user who is the author of the message.
     * @param conversationId The id of the conversation to which the message belongs.
     */
    public ConversationMessage(String text, int messageNumber, Priority priority, ZonedDateTime creationDate, int
            authorUser, int conversationId){
        super(text, messageNumber, creationDate, priority);
        this.authorUser = authorUser;
        this.conversationId = conversationId;
    }

    /**
     * Creates an instance of the ConversationMessage class.
     *
     * @param id The id of the message.
     * @param text The text of the message.
     * @param messageNumber The number of the message regarding the given conversation.
     * @param priority The priority of the message.
     * @param creationDate The date and time when the message was created.
     * @param authorUser The id of the user who is the author of the message.
     * @param conversationId The id of the conversation to which the message belongs.
     */
    public ConversationMessage(int id, String text, int messageNumber, Priority priority, ZonedDateTime creationDate,
                               int authorUser, int conversationId){
        super(id, text, messageNumber, creationDate, priority);
        this.authorUser = authorUser;
        this.conversationId = conversationId;
    }

    public int getAuthorUser() {
        return authorUser;
    }

    public void setAuthorUser(int authorUser) {
        this.authorUser = authorUser;
    }

    public int getConversationId() {
        return conversationId;
    }

    public void setConversationId(int conversationId) {
        this.conversationId = conversationId;
    }

    @Override
    public String toString() {
        return "ConversationMessage{" +
                "authorUser=" + authorUser +
                ", conversationId=" + conversationId +
                "} " + super.toString();
    }
}
