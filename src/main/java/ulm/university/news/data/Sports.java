package ulm.university.news.data;

import ulm.university.news.data.enums.ChannelType;

import java.time.ZonedDateTime;

/**
 * The Sports class is a sub class of Channel. This class adds fields to describe a Sports group.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Sports extends Channel {
    /** The cost of participation of the Sports group. */
    String cost;
    /** The number of participants of the Sports group. */
    String numberOfParticipants;

    public Sports() {
    }

    public Sports(int id, String name, String description, ChannelType type, ZonedDateTime creationDate, ZonedDateTime
            modificationDate, String term, String locations, String dates, String contacts, String website, String
                          cost, String numberOfParticipants) {
        super(id, name, description, type, creationDate, modificationDate, term, locations, dates, contacts, website);
        this.cost = cost;
        this.numberOfParticipants = numberOfParticipants;
    }

    public String getCost() {
        return cost;
    }

    public void setCost(String cost) {
        this.cost = cost;
    }

    public String getNumberOfParticipants() {
        return numberOfParticipants;
    }

    public void setNumberOfParticipants(String numberOfParticipants) {
        this.numberOfParticipants = numberOfParticipants;
    }
}
