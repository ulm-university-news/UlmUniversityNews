package ulm.university.news.data;

import java.util.List;

/**
 * The Option class represents an option of a ballot. An option always belongs to a ballot. The ballot can have
 * multiple options for which users can vote. The option consists of a text and can contain a list of ids of users
 * who have voted for the specific option.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Option {

    /** The unique id of the option. */
    private int id;
    /** The text of the option. */
    private String text;
    /** A list of ids of users who have voted for this option in the corresponding ballot. */
    private List<Integer> voters;

    /**
     * Creates an instance of the Option class.
     */
    public Option(){

    }

    /**
     * Creates an instance of the Option class.
     *
     * @param text The text of the option.
     */
    public Option(String text){
        this.text = text;
    }

    /**
     * Creates an instance of the Option class.
     *
     * @param id The id of the option.
     * @param text The text of the option.
     */
    public Option(int id, String text){
        this.id = id;
        this.text = text;
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

    public List<Integer> getVoters() {
        return voters;
    }

    public void setVoters(List<Integer> voters) {
        this.voters = voters;
    }

    @Override
    public String toString() {
        return "Option{" +
                "id=" + id +
                ", text='" + text + '\'' +
                ", voters=" + voters +
                '}';
    }
}
