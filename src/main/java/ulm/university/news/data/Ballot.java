package ulm.university.news.data;

import java.util.List;

/**
 * The Ballot class represents a ballot. A ballot belongs to a group and offers the participants several options for
 * which each participant can vote. Depending on the properties of the ballot participants can vote for either one
 * option per ballot or for multiple options. The participants can display the result of the voting. However,
 * depending on the properties the votes can be public or anonymous. If the votes are anonymous, the participants can
 * only determine how many votes an option has received. If the votes are public, it can also be determined which
 * participant has voted for a certain option.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Ballot {

    /** The unique id of the ballot. */
    private int id;
    /** The title of the ballot. */
    private String title;
    /** The description of the ballot. */
    private String description;
    /** The id of the user who assumes the role of the administrator for the ballot. */
    private int admin;
    /** The closed field indicates whether the ballot is closed or open. If the ballot is closed, no further votes
     * can be placed and the ballot data can't be changed. */
    private Boolean closed;
    /** The multipleChoice field indicates whether the participants of a group can vote for multiple options of a
     * ballot. If multiple choice is disabled, the participants can only vote for one option per ballot. */
    private Boolean multipleChoice;
    /** The publicVotes field indicates whether the participants of the group can see which participants have voted
     * for certain options of the ballot. If public votes are disabled, the participants can only determine how many
     * votes a option has received, but not which participants have voted for it. */
    private Boolean publicVotes;
    /** A list of options for the ballot. */
    private List<Option> options;

    /**
     * Creates an instance of the Ballot class.
     */
    public Ballot(){

    }

    /**
     * Creates an instance of the Ballot class.
     *
     * @param title The title of the ballot.
     * @param description The description of the ballot.
     * @param admin The id of the user who is the administrator of the ballot.
     * @param closed Field which indicates whether the ballot is open or closed.
     * @param multipleChoice Field which indicates whether multiple choice is enabled for the ballot.
     * @param publicVotes Field which indicates whether the votes for this ballot are public.
     */
    public Ballot(String title, String description, int admin, Boolean closed, Boolean multipleChoice, Boolean
                  publicVotes){
        this.title = title;
        this.description = description;
        this.admin = admin;
        this.closed = closed;
        this.multipleChoice = multipleChoice;
        this.publicVotes = publicVotes;
    }

    /**
     * Creates an instance of the Ballot class.
     *
     * @param id The unique id of the ballot.
     * @param title The title of the ballot.
     * @param description The description of the ballot.
     * @param admin The id of the user who is the administrator of the ballot.
     * @param closed Field which indicates whether the ballot is open or closed.
     * @param multipleChoice Field which indicates whether multiple choice is enabled for the ballot.
     * @param publicVotes Field which indicates whether the votes for this ballot are public.
     */
    public Ballot(int id, String title, String description, int admin, Boolean closed, Boolean multipleChoice, Boolean
            publicVotes){
        this.id = id;
        this.title = title;
        this.description = description;
        this.admin = admin;
        this.closed = closed;
        this.multipleChoice = multipleChoice;
        this.publicVotes = publicVotes;
    }

    /**
     * Checks if the user with the specified id is the administrator for the ballot.
     *
     * @param userId The id of the user.
     * @return Returns true if the user who is identified by the given id is the ballot administrator, false otherwise.
     */
    public boolean isBallotAdmin(int userId){
        if(this.getAdmin() == userId){
            return true;
        }
        return false;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getAdmin() {
        return admin;
    }

    public void setAdmin(int admin) {
        this.admin = admin;
    }

    public Boolean getClosed() {
        return closed;
    }

    public void setClosed(Boolean closed) {
        this.closed = closed;
    }

    public Boolean getMultipleChoice() {
        return multipleChoice;
    }

    public void setMultipleChoice(Boolean multipleChoice) {
        this.multipleChoice = multipleChoice;
    }

    public Boolean getPublicVotes() {
        return publicVotes;
    }

    public void setPublicVotes(Boolean publicVotes) {
        this.publicVotes = publicVotes;
    }

    public List<Option> getOptions() {
        return options;
    }

    public void setOptions(List<Option> options) {
        this.options = options;
    }

    @Override
    public String toString() {
        return "Ballot{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", admin=" + admin +
                ", closed=" + closed +
                ", multipleChoice=" + multipleChoice +
                ", publicVotes=" + publicVotes +
                ", options=" + options +
                '}';
    }
}
