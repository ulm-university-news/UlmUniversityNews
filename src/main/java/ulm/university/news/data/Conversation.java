package ulm.university.news.data;

import java.util.List;

/**
 * The Conversation class represents a conversation. A conversation belongs to a group and contains messages. The
 * messages can be sent from participants of the group and are distributed among the participants. A conversation is
 * a collection basin for messages which are accumulated under a certain topic, i.e. the topic of the conversation.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Conversation {

    /** The unique id of the conversation. */
    private int id;
    /** The title of the conversation. */
    private String title;
    /** The closed field indicates whether the conversation is closed. If a conversation is closed then no further
     * messages can be sent into this conversation. */
    private Boolean closed;
    /** The id of the user who is the administrator of the conversation. */
    private int admin;
    /** A list of conversation messages which belong to this conversation. */
    private List<ConversationMessage> conversationMessages;

    /**
     * Creates an instance of the Conversation class.
     */
    public Conversation(){

    }

    /**
     * Creates an instance of the Conversation class.
     *
     * @param title The title of the conversation.
     * @param closed Indicates whether the conversation is closed or open.
     * @param admin The id of the user who is the administrator of the conversation.
     */
    public Conversation(String title, Boolean closed, int admin){
        this.title = title;
        this.closed = closed;
        this.admin = admin;
    }

    /**
     * Creates an instance of the Conversation class.
     *
     * @param id The id of the conversation.
     * @param title The title of the conversation.
     * @param closed Indicates whether the conversation is closed or open.
     * @param admin The id of the user who is the administrator of the conversation.
     */
    public Conversation(int id, String title, Boolean closed, int admin){
        this.id = id;
        this.title = title;
        this.closed = closed;
        this.admin = admin;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getClosed() {
        return closed;
    }

    public void setClosed(Boolean closed) {
        this.closed = closed;
    }

    public int getAdmin() {
        return admin;
    }

    public void setAdmin(int admin) {
        this.admin = admin;
    }

    public List<ConversationMessage> getConversationMessages() {
        return conversationMessages;
    }

    public void setConversationMessages(List<ConversationMessage> conversationMessages) {
        this.conversationMessages = conversationMessages;
    }

    @Override
    public String toString() {
        return "Conversation{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", closed=" + closed +
                ", admin=" + admin +
                ", conversationMessages=" + conversationMessages +
                '}';
    }
}
