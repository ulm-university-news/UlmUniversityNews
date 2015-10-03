package ulm.university.news.manager.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.*;
import ulm.university.news.data.enums.ChannelType;
import ulm.university.news.data.enums.Faculty;
import ulm.university.news.data.enums.Platform;
import ulm.university.news.util.Constants;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ServerException;

import java.sql.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static ulm.university.news.util.Constants.*;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class ChannelDatabaseManager extends DatabaseManager {

    /** The logger instance for ChannelDatabaseManager. */
    private static final Logger logger = LoggerFactory.getLogger(ChannelDatabaseManager.class);

    /**
     * Creates an instance of the ChannelDatabaseManager class.
     */
    public ChannelDatabaseManager() {
    }

    /**
     * Stores the new channel (and its subclass) in the database. Then the creator is added as responsible moderator
     * by storing the link between the channel id and the moderator id.
     *
     * @param channel The channel object which contains the channel data.
     * @param moderatorId The id of the moderator who wants to create the channel.
     * @throws DatabaseException If the data could not be stored in the database due to database failure.
     * @throws ServerException If channel name already exists.
     */
    public void storeChannel(Channel channel, int moderatorId) throws DatabaseException, ServerException {
        logger.debug("Start with channel:{} and moderatorId:{}.", channel, moderatorId);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            // Start transaction.
            con.setAutoCommit(false);

            String storeChannelQuery =
                    "INSERT INTO Channel (Name, Description, Type, Term, Locations, Contacts, Website, CreationDate, " +
                            "ModificationDate, Dates) VALUES (?,?,?,?,?,?,?,?,?,?); ";

            PreparedStatement storeChannelStmt = con.prepareStatement(storeChannelQuery);
            storeChannelStmt.setString(1, channel.getName());
            storeChannelStmt.setString(2, channel.getDescription());
            storeChannelStmt.setInt(3, channel.getType().ordinal());
            storeChannelStmt.setString(4, channel.getTerm());
            storeChannelStmt.setString(5, channel.getLocations());
            storeChannelStmt.setString(6, channel.getContacts());
            storeChannelStmt.setString(7, channel.getWebsite());
            storeChannelStmt.setTimestamp(8, Timestamp.from(channel.getCreationDate().toInstant()));
            storeChannelStmt.setTimestamp(9, Timestamp.from(channel.getModificationDate().toInstant()));
            storeChannelStmt.setString(10, channel.getDates());

            storeChannelStmt.execute();

            // Retrieve auto incremented id of the database record.
            String getIdQuery = "SELECT LAST_INSERT_ID();";

            Statement getIdStmt = con.createStatement();
            ResultSet getIdRs = getIdStmt.executeQuery(getIdQuery);
            if (getIdRs.next()) {
                channel.setId(getIdRs.getInt(1));
            }
            logger.info("Stored channel with id:{}.", channel.getId());

            // Store data of the channels subclass.
            switch (channel.getType()) {
                case LECTURE:
                    Lecture lecture = (Lecture) channel;
                    String storeLectureQuery =
                            "INSERT INTO Lecture (Faculty, StartDate, EndDate, Lecturer, Assistant, Channel_Id) " +
                                    "VALUES (?,?,?,?,?,?); ";

                    PreparedStatement storeLectureStmt = con.prepareStatement(storeLectureQuery);
                    storeLectureStmt.setInt(1, lecture.getFaculty().ordinal());
                    storeLectureStmt.setString(2, lecture.getStartDate());
                    storeLectureStmt.setString(3, lecture.getEndDate());
                    storeLectureStmt.setString(4, lecture.getLecturer());
                    storeLectureStmt.setString(5, lecture.getAssistant());
                    storeLectureStmt.setInt(6, channel.getId());

                    storeLectureStmt.execute();
                    logger.info("Stored lecture.");
                    break;
                case EVENT:
                    Event event = (Event) channel;
                    String storeEventQuery =
                            "INSERT INTO Event (Cost, Organizer, Channel_Id) " +
                                    "VALUES (?,?,?); ";

                    PreparedStatement storeEventStmt = con.prepareStatement(storeEventQuery);
                    storeEventStmt.setString(1, event.getCost());
                    storeEventStmt.setString(2, event.getOrganizer());
                    storeEventStmt.setInt(3, channel.getId());

                    storeEventStmt.execute();
                    logger.info("Stored event.");
                    break;
                case SPORTS:
                    Sports sports = (Sports) channel;
                    String storeSportQuery =
                            "INSERT INTO Sports (Cost, NumberOfParticipants, Channel_Id) " +
                                    "VALUES (?,?,?); ";

                    PreparedStatement storeSportStmt = con.prepareStatement(storeSportQuery);
                    storeSportStmt.setString(1, sports.getCost());
                    storeSportStmt.setString(2, sports.getNumberOfParticipants());
                    storeSportStmt.setInt(3, channel.getId());

                    storeSportStmt.execute();
                    logger.info("Stored sports.");
                    break;
                default:
                    // There is no subclass for channel type OTHER and STUDENT_GROUP, so storing is already complete.
                    break;
            }

            // Add moderator (creator of the channel) to responsible moderators.
            String addModeratorQuery =
                    "INSERT INTO ModeratorChannel (Moderator_Id, Channel_Id, Active) " +
                            "VALUES (?,?,?);";

            PreparedStatement addModeratorStmt = con.prepareStatement(addModeratorQuery);
            addModeratorStmt.setInt(1, moderatorId);
            addModeratorStmt.setInt(2, channel.getId());
            addModeratorStmt.setBoolean(3, true);  // It is an active moderator.

            addModeratorStmt.executeUpdate();
            logger.info("Added the moderator with id {} as responsible for the channel with id {}.", moderatorId,
                    channel.getId());

            // End transaction.
            con.commit();

            storeChannelStmt.close();
            getIdStmt.close();
            addModeratorStmt.close();
        } catch (SQLException e) {
            try {
                logger.warn("Need to rollback the transaction.");
                con.rollback();
            } catch (SQLException e1) {
                logger.warn("Rollback failed.");
                logger.error(Constants.LOG_SQL_EXCEPTION, e1.getSQLState(), e1.getErrorCode(), e1.getMessage());
            }
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Check if the uniqueness of the channel name was harmed.
            if (e.getErrorCode() == 1062 && e.getMessage().contains("Name_UNIQUE")) {
                logger.error("Uniqueness of the channel name was harmed. Cannot store channel.");
                logger.error(LOG_SERVER_EXCEPTION, 409, CHANNEL_NAME_ALREADY_EXISTS, "Channel name already exits.");
                throw new ServerException(409, CHANNEL_NAME_ALREADY_EXISTS);
            }
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Gets all requested channels from the database.
     *
     * @param moderatorId Get only channels for which the moderator with the given id is responsible.
     * @param lastUpdated Get only channels with a newer modification data as the last updated date.
     * @return All requested channels.
     */
    public List<Channel> getChannels(Integer moderatorId, ZonedDateTime lastUpdated) throws DatabaseException {
        logger.debug("Start with moderatorId:{} and lastUpdated:{}.", moderatorId, lastUpdated);
        Connection con = null;
        List<Channel> channels = new ArrayList<Channel>();
        try {
            con = getDatabaseConnection();

            // Create proper SQL statement.
            String query = "SELECT * FROM Channel";
            PreparedStatement getChannelsStmt;
            if (moderatorId != null && lastUpdated != null) {
                query += " AS c INNER JOIN ModeratorChannel AS mc ON c.Id=mc.Channel_Id " +
                        "WHERE mc.Moderator_Id=? AND ModificationDate>?;";
                getChannelsStmt = con.prepareStatement(query);
                getChannelsStmt.setInt(1, moderatorId);
                getChannelsStmt.setTimestamp(2, Timestamp.from(lastUpdated.toInstant()));
            } else if (moderatorId != null) {
                query += " AS c INNER JOIN ModeratorChannel AS mc ON c.Id=mc.Channel_Id WHERE mc.Moderator_Id=?;";
                getChannelsStmt = con.prepareStatement(query);
                getChannelsStmt.setInt(1, moderatorId);
            } else if (lastUpdated != null) {
                query += " WHERE ModificationDate>?;";
                getChannelsStmt = con.prepareStatement(query);
                getChannelsStmt.setTimestamp(1, Timestamp.from(lastUpdated.toInstant()));
            } else {
                query += ";";
                getChannelsStmt = con.prepareStatement(query);
            }
            logger.debug("SQL query:{}", query);
            ResultSet getChannelsRs = getChannelsStmt.executeQuery();

            // Create fields before while loop, not within every pass.
            String name, description, term, locations, dates, contacts, website, startDate, endDate, lecturer,
                    assistant, cost, organizer, participants;
            ChannelType type;
            Faculty faculty;
            ZonedDateTime creationDate, modificationDate;
            // Get channel data from database.
            while (getChannelsRs.next()) {
                int id = getChannelsRs.getInt("Id");
                name = getChannelsRs.getString("Name");
                description = getChannelsRs.getString("Description");
                type = ChannelType.values[getChannelsRs.getInt("Type")];
                creationDate = getChannelsRs.getTimestamp("CreationDate").toLocalDateTime().atZone(Constants.TIME_ZONE);
                modificationDate = getChannelsRs.getTimestamp("ModificationDate").toLocalDateTime().atZone(Constants
                        .TIME_ZONE);
                term = getChannelsRs.getString("Term");
                locations = getChannelsRs.getString("Locations");
                dates = getChannelsRs.getString("Dates");
                contacts = getChannelsRs.getString("Contacts");
                website = getChannelsRs.getString("Website");

                // If necessary get additional channel data and create corresponding channel subclass.
                PreparedStatement getSubclassStmt;
                ResultSet getSubclassRs;
                switch (type) {
                    case LECTURE:
                        query = "SELECT * FROM Lecture WHERE Channel_Id=?;";
                        getSubclassStmt = con.prepareStatement(query);
                        getSubclassStmt.setInt(1, id);
                        getSubclassRs = getSubclassStmt.executeQuery();
                        if (getSubclassRs.next()) {
                            faculty = Faculty.values[getSubclassRs.getInt("Faculty")];
                            startDate = getSubclassRs.getString("StartDate");
                            endDate = getSubclassRs.getString("EndDate");
                            lecturer = getSubclassRs.getString("Lecturer");
                            assistant = getSubclassRs.getString("Assistant");
                            Lecture lecture = new Lecture(id, name, description, type, creationDate,
                                    modificationDate, term, locations, dates, contacts, website, faculty, startDate,
                                    endDate, lecturer, assistant);
                            channels.add(lecture);
                        }
                        break;
                    case EVENT:
                        query = "SELECT * FROM Event WHERE Channel_Id=?;";
                        getSubclassStmt = con.prepareStatement(query);
                        getSubclassStmt.setInt(1, id);
                        getSubclassRs = getSubclassStmt.executeQuery();
                        if (getSubclassRs.next()) {
                            cost = getSubclassRs.getString("Cost");
                            organizer = getSubclassRs.getString("Organizer");
                            Event event = new Event(id, name, description, type, creationDate,
                                    modificationDate, term, locations, dates, contacts, website, cost, organizer);
                            channels.add(event);
                        }
                        break;
                    case SPORTS:
                        query = "SELECT * FROM Sports WHERE Channel_Id=?;";
                        getSubclassStmt = con.prepareStatement(query);
                        getSubclassStmt.setInt(1, id);
                        getSubclassRs = getSubclassStmt.executeQuery();
                        if (getSubclassRs.next()) {
                            cost = getSubclassRs.getString("Cost");
                            participants = getSubclassRs.getString("NumberOfParticipants");
                            Sports sports = new Sports(id, name, description, type, creationDate,
                                    modificationDate, term, locations, dates, contacts, website, cost, participants);
                            channels.add(sports);
                        }
                        break;
                    default:
                        // There is no subclass for channel type OTHER and STUDENT_GROUP, so create channel object.
                        Channel channel = new Channel(id, name, description, type, creationDate, modificationDate, term,
                                locations, dates, contacts, website);
                        channels.add(channel);
                }
            }
            getChannelsStmt.close();
        } catch (SQLException e) {
            // Throw back DatabaseException to the Controller.
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End with channels:{}.", channels);
        return channels;
    }

    /**
     * Gets the requested channel from the database.
     *
     * @param channelId The id of the channel.
     * @return The channel with the specified channel id.
     */
    public Channel getChannel(int channelId) throws DatabaseException {
        logger.debug("Start with channelId:{}.", channelId);
        Connection con = null;
        Channel channel = null;
        try {
            con = getDatabaseConnection();

            String query = "SELECT * FROM Channel WHERE Id=?";
            PreparedStatement getChannelsStmt = con.prepareStatement(query);
            getChannelsStmt.setInt(1, channelId);
            ResultSet getChannelsRs = getChannelsStmt.executeQuery();

            // Create fields before while loop, not within every pass.
            String name, description, term, locations, dates, contacts, website, startDate, endDate, lecturer,
                    assistant, cost, organizer, participants;
            ChannelType type;
            Faculty faculty;
            ZonedDateTime creationDate, modificationDate;
            // Get channel data from database.
            if (getChannelsRs.next()) {
                int id = getChannelsRs.getInt("Id");
                name = getChannelsRs.getString("Name");
                description = getChannelsRs.getString("Description");
                type = ChannelType.values[getChannelsRs.getInt("Type")];
                creationDate = getChannelsRs.getTimestamp("CreationDate").toLocalDateTime().atZone(Constants.TIME_ZONE);
                modificationDate = getChannelsRs.getTimestamp("ModificationDate").toLocalDateTime().atZone(Constants
                        .TIME_ZONE);
                term = getChannelsRs.getString("Term");
                locations = getChannelsRs.getString("Locations");
                dates = getChannelsRs.getString("Dates");
                contacts = getChannelsRs.getString("Contacts");
                website = getChannelsRs.getString("Website");

                // If necessary get additional channel data and create corresponding channel subclass.
                PreparedStatement getSubclassStmt;
                ResultSet getSubclassRs;
                switch (type) {
                    case LECTURE:
                        query = "SELECT * FROM Lecture WHERE Channel_Id=?;";
                        getSubclassStmt = con.prepareStatement(query);
                        getSubclassStmt.setInt(1, id);
                        getSubclassRs = getSubclassStmt.executeQuery();
                        if (getSubclassRs.next()) {
                            faculty = Faculty.values[getSubclassRs.getInt("Faculty")];
                            startDate = getSubclassRs.getString("StartDate");
                            endDate = getSubclassRs.getString("EndDate");
                            lecturer = getSubclassRs.getString("Lecturer");
                            assistant = getSubclassRs.getString("Assistant");
                            channel = new Lecture(id, name, description, type, creationDate,
                                    modificationDate, term, locations, dates, contacts, website, faculty, startDate,
                                    endDate, lecturer, assistant);
                        }
                        break;
                    case EVENT:
                        query = "SELECT * FROM Event WHERE Channel_Id=?;";
                        getSubclassStmt = con.prepareStatement(query);
                        getSubclassStmt.setInt(1, id);
                        getSubclassRs = getSubclassStmt.executeQuery();
                        if (getSubclassRs.next()) {
                            cost = getSubclassRs.getString("Cost");
                            organizer = getSubclassRs.getString("Organizer");
                            channel = new Event(id, name, description, type, creationDate,
                                    modificationDate, term, locations, dates, contacts, website, cost, organizer);
                        }
                        break;
                    case SPORTS:
                        query = "SELECT * FROM Sports WHERE Channel_Id=?;";
                        getSubclassStmt = con.prepareStatement(query);
                        getSubclassStmt.setInt(1, id);
                        getSubclassRs = getSubclassStmt.executeQuery();
                        if (getSubclassRs.next()) {
                            cost = getSubclassRs.getString("Cost");
                            participants = getSubclassRs.getString("NumberOfParticipants");
                            channel = new Sports(id, name, description, type, creationDate,
                                    modificationDate, term, locations, dates, contacts, website, cost, participants);
                        }
                        break;
                    default:
                        // There is no subclass for channel type OTHER and STUDENT_GROUP, so create channel object.
                        channel = new Channel(id, name, description, type, creationDate, modificationDate, term,
                                locations, dates, contacts, website);
                }
            }
            getChannelsStmt.close();
        } catch (SQLException e) {
            // Throw back DatabaseException to the Controller.
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End with channel:{}.", channel);
        return channel;
    }

    /**
     * Get all channels which are managed by the moderator with the given id. The channel objects contain a list of all
     * their responsible moderators and subscribers.
     *
     * @param moderatorId The moderator id who is responsible for the requested channels.
     * @return The requested channels including lists of responsible moderators and subscribers.
     * @throws DatabaseException If a database failure occurs.
     */
    public List<Channel> getChannelsOfModerator(int moderatorId) throws DatabaseException {
        logger.debug("Start with moderatorId:{}.", moderatorId);
        Connection con = null;
        List<Channel> channels = new ArrayList<Channel>();
        try {
            con = getDatabaseConnection();

            // First: Get channels for which the moderator is responsible.
            String query = "SELECT Channel_Id FROM ModeratorChannel WHERE Moderator_Id=? AND Active=?;";
            PreparedStatement getChannelsStmt = con.prepareStatement(query);
            getChannelsStmt.setInt(1, moderatorId);
            getChannelsStmt.setBoolean(2, true);
            ResultSet getChannelsRs = getChannelsStmt.executeQuery();

            Channel channel;
            while (getChannelsRs.next()) {
                channel = new Channel();
                channel.setId(getChannelsRs.getInt("Channel_Id"));
                logger.debug("Channel_Id:{}", channel.getId());

                // Second: Get all moderators (id only) who are responsible for this channel.
                query = "SELECT Moderator_Id FROM ModeratorChannel WHERE Channel_Id=? AND Active=?;";

                PreparedStatement getModeratorsStmt = con.prepareStatement(query);
                getModeratorsStmt.setInt(1, channel.getId());
                getModeratorsStmt.setBoolean(2, true);
                ResultSet getModeratorsRs = getModeratorsStmt.executeQuery();

                Moderator moderator;
                List<Moderator> moderators = new ArrayList<>();
                while (getModeratorsRs.next()) {
                    // Only set the moderator id.
                    moderator = new Moderator();
                    moderator.setId(getModeratorsRs.getInt("Moderator_Id"));
                    logger.debug("Moderator_Id:{}", moderator.getId());
                    moderators.add(moderator);
                }
                // Add list of moderators to the channel object.
                channel.setModerators(moderators);
                getModeratorsStmt.close();

                // Third: Get all subscribers of this channel.
                query = "SELECT u.Id, u.Name, u.PushAccessToken, u.Platform " +
                        "FROM User AS u INNER JOIN UserChannel AS uc " +
                        "ON u.Id=uc.User_Id WHERE uc.Channel_Id=?;";

                PreparedStatement getSubscribersStmt = con.prepareStatement(query);
                getSubscribersStmt.setInt(1, channel.getId());
                ResultSet getSubscribersRs = getSubscribersStmt.executeQuery();

                User user;
                List<User> subscribers = new ArrayList<User>();
                while (getSubscribersRs.next()) {
                    user = new User();
                    user.setId(getSubscribersRs.getInt("Id"));
                    user.setName(getSubscribersRs.getString("Name"));
                    user.setPushAccessToken(getSubscribersRs.getString("PushAccessToken"));
                    user.setPlatform(Platform.values[getSubscribersRs.getInt("Platform")]);
                    logger.debug("user:{}", user);
                    subscribers.add(user);
                }
                channel.setSubscribers(subscribers);
                getSubscribersStmt.close();

                // Add channel with responsible moderators and subscribers to channel list.
                channels.add(channel);
            }
            getChannelsStmt.close();
        } catch (SQLException e) {
            // Throw back DatabaseException to the Controller.
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End with channels:{}.", channels);
        return channels;
    }

    /**
     * Adds the moderator with the given id to the channel with the given id as responsible moderator.
     *
     * @param channelId The id of the channel to which the moderator should be added.
     * @param moderatorId The id of the moderator who should be added to the channel.
     * @throws DatabaseException If the data could not be stored in the database due to a database failure.
     */
    public void addModeratorToChannel(int channelId, int moderatorId) throws DatabaseException {
        logger.debug("Start with channelId:{} and moderatorId:{}.", channelId, moderatorId);
        Connection con = null;
        try {
            con = getDatabaseConnection();

            // Add a moderator as responsible to a channel.
            String addModeratorQuery =
                    "INSERT INTO ModeratorChannel (Moderator_Id, Channel_Id, Active) " +
                            "VALUES (?,?,?);";

            PreparedStatement addModeratorStmt = con.prepareStatement(addModeratorQuery);
            addModeratorStmt.setInt(1, moderatorId);
            addModeratorStmt.setInt(2, channelId);
            addModeratorStmt.setBoolean(3, true);  // It is an active moderator.

            addModeratorStmt.executeUpdate();
            logger.info("Added the moderator with id {} as responsible for the channel with id {}.", moderatorId,
                    channelId);
            addModeratorStmt.close();
        } catch (SQLException e) {
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Check if primary key (moderatorId + channelId) already exists.
            if (e.getErrorCode() == 1062 && e.getMessage().contains("PRIMARY")) {
                logger.info("Double primary key. Moderator already in link table. Set moderator to active.");
                // If there is already an entry, just set active to true no matter what value it had previously.
                try {
                    String updateActiveQuery =
                            "UPDATE ModeratorChannel " +
                                    "SET Active=? " +
                                    "WHERE Moderator_Id=? AND Channel_Id=?;";

                    PreparedStatement updateActiveStmt = con.prepareStatement(updateActiveQuery);
                    updateActiveStmt.setBoolean(1, true);
                    updateActiveStmt.setInt(2, moderatorId);
                    updateActiveStmt.setInt(3, channelId);

                    updateActiveStmt.executeUpdate();
                    logger.info("Set the active field for the moderator with id {} to true. The moderator is an " +
                            "active responsible moderator of the channel with id {}.", moderatorId, channelId);
                    updateActiveStmt.close();
                } catch (SQLException e1) {
                    // Throw back DatabaseException to the Controller.
                    throw new DatabaseException("Database failure. Couldn't set active field to true.");
                }
            } else {
                // Throw back DatabaseException to the Controller.
                throw new DatabaseException("Database failure.");
            }
        } finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Removes the moderator with the given id as responsible moderator from the channel with the given id.
     *
     * @param channelId The id of the channel for which the moderators is responsible.
     * @param moderatorId The id of the moderator who should be removed from the channel.
     * @throws DatabaseException If the data could not be deleted from the database due to a database failure.
     */
    public void removeModeratorFromChannel(int channelId, int moderatorId) throws DatabaseException {
        logger.debug("Start with channelId:{} and moderatorId:{}.", channelId, moderatorId);
        Connection con = null;
        try {
            con = getDatabaseConnection();

            // Set the moderator as inactive for the channel.
            String removeModeratorQuery =
                    "UPDATE ModeratorChannel SET Active=? WHERE Moderator_Id=? AND Channel_Id=?;";

            PreparedStatement removeModeratorStmt = con.prepareStatement(removeModeratorQuery);
            removeModeratorStmt.setBoolean(1, false);
            removeModeratorStmt.setInt(2, moderatorId);
            removeModeratorStmt.setInt(3, channelId);

            int rowsAffected = removeModeratorStmt.executeUpdate();
            if (rowsAffected == 1) {
                logger.info("Set the moderator with id {} to inactive for the channel with id {}.", moderatorId,
                        channelId);
            }
            removeModeratorStmt.close();
        } catch (SQLException e) {
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Removes the moderator with the given id as responsible moderator from all channels.
     *
     * @param moderatorId The id of the moderator who should be removed from the channels.
     * @throws DatabaseException If the data could not be deleted from the database due to a database failure.
     */
    public void removeModeratorFromChannels(int moderatorId) throws DatabaseException {
        logger.debug("Start with moderatorId:{}.", moderatorId);
        Connection con = null;
        try {
            con = getDatabaseConnection();

            // Set the moderator as inactive for the channel.
            String removeModeratorQuery =
                    "UPDATE ModeratorChannel SET Active=? WHERE Moderator_Id=?;";

            PreparedStatement removeModeratorStmt = con.prepareStatement(removeModeratorQuery);
            removeModeratorStmt.setBoolean(1, false);
            removeModeratorStmt.setInt(2, moderatorId);

            int rowsAffected = removeModeratorStmt.executeUpdate();
            if (rowsAffected > 1) {
                logger.info("Set the moderator with id {} to inactive for {} channel(s).", moderatorId, rowsAffected);
            }
            removeModeratorStmt.close();
        } catch (SQLException e) {
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Deletes the channel identified by given channel id from the database. This method deletes the channel
     * superclass and subclass. Furthermore it deletes the links between moderators and users and the channel.
     * Finally announcements and reminders of the channel are deleted.
     *
     * @param channelId The channel which should be deleted.
     * @throws DatabaseException If not all the data could be deleted from the database.
     */
    public void deleteChannel(int channelId) throws DatabaseException {
        logger.debug("Start with channelId:{}.", channelId);
        Connection con = null;
        try {
            con = getDatabaseConnection();

            // Delete the entry in the channel table.
            String query = "DELETE FROM Channel WHERE Id=?;";
            PreparedStatement removeChannelStmt = con.prepareStatement(query);
            removeChannelStmt.setInt(1, channelId);
            int rowsAffected = removeChannelStmt.executeUpdate();
            if (rowsAffected == 1) {
                logger.info("Removed the channel with id {} and all linked entries from database.", channelId);
            }

            /*
            Note: MySQL will take care of deletion of other entries which are linked to the channel thanks to the
            defined foreign keys with ON DELETE CASCADE.
            */

            removeChannelStmt.close();
        } catch (SQLException e) {
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Gets all moderators who are responsible for a channel with the given id from the database.
     *
     * @param channelId The id of the channel.
     * @return All moderators who are responsible for the channel.
     */
    public List<Moderator> getResponsibleModerators(int channelId) throws DatabaseException {
        logger.debug("Start with channelId:{}.", channelId);
        Connection con = null;
        List<Moderator> moderators = new ArrayList<Moderator>();
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT m.Id, m.Name, m.FirstName, m.LastName, m.Email " +
                            "FROM Moderator AS m INNER JOIN ModeratorChannel AS mc ON " +
                            "m.Id=mc.Moderator_Id WHERE mc.Channel_Id=? AND mc.Active=?;";

            PreparedStatement getResponsibleStmt = con.prepareStatement(query);
            getResponsibleStmt.setInt(1, channelId);
            getResponsibleStmt.setBoolean(2, true);

            ResultSet getResponsibleRs = getResponsibleStmt.executeQuery();
            while (getResponsibleRs.next()) {
                // Only set those values which should be returned to the requestor.
                Moderator moderator = new Moderator();
                moderator.setId(getResponsibleRs.getInt("Id"));
                moderator.setName(getResponsibleRs.getString("Name"));
                moderator.setFirstName(getResponsibleRs.getString("FirstName"));
                moderator.setLastName(getResponsibleRs.getString("LastName"));
                moderator.setEmail(getResponsibleRs.getString("Email"));
                moderators.add(moderator);
            }
            getResponsibleStmt.close();
        } catch (SQLException e) {
            // Throw back DatabaseException to the Controller.
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End with moderators:{}.", moderators);
        return moderators;
    }

    /**
     * Gets all moderators who are deleted but once were responsible for the channel with the given id.
     *
     * @param channelId The id of the channel.
     * @return All deleted moderators of the specified channel.
     * @throws DatabaseException If a database failure occurs.
     */
    public List<Moderator> getDeletedModeratorsByChannel(int channelId) throws DatabaseException {
        logger.debug("Start with channelId:{}.", channelId);
        Connection con = null;
        List<Moderator> moderators = new ArrayList<Moderator>();
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT m.Id " +
                            "FROM Moderator AS m INNER JOIN ModeratorChannel AS mc ON " +
                            "m.Id=mc.Moderator_Id WHERE mc.Channel_Id=? AND m.Deleted=?;";

            PreparedStatement getResponsibleStmt = con.prepareStatement(query);
            getResponsibleStmt.setInt(1, channelId);
            getResponsibleStmt.setBoolean(2, true);

            ResultSet getResponsibleRs = getResponsibleStmt.executeQuery();
            while (getResponsibleRs.next()) {
                // Only needed values.
                Moderator moderator = new Moderator();
                moderator.setId(getResponsibleRs.getInt("Id"));
                moderators.add(moderator);
            }
            getResponsibleStmt.close();
        } catch (SQLException e) {
            // Throw back DatabaseException to the Controller.
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End with moderators:{}.", moderators);
        return moderators;
    }

    /**
     * Adds the user with the given id as subscriber to the channel with the given id.
     *
     * @param channelId The id of the channel to which the user should be added.
     * @param userId The id of the user who should be added to the channel.
     * @throws DatabaseException If the data could not be stored in the database due to a database failure.
     */
    public void addSubscriberToChannel(int channelId, int userId) throws DatabaseException {
        logger.debug("Start with channelId:{} and userId:{}.", channelId, userId);
        Connection con = null;
        try {
            con = getDatabaseConnection();

            // Add a user as subscriber to a channel.
            String addUserQuery =
                    "INSERT INTO UserChannel (User_Id, Channel_Id) " +
                            "VALUES (?,?);";

            PreparedStatement addUserStmt = con.prepareStatement(addUserQuery);
            addUserStmt.setInt(1, userId);
            addUserStmt.setInt(2, channelId);

            addUserStmt.executeUpdate();
            logger.info("Added the user with id {} as subscriber to the channel with id {}.", userId, channelId);
            addUserStmt.close();
        } catch (SQLException e) {
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Check if primary key (userId + channelId) already exists.
            if (e.getErrorCode() == 1062 && e.getMessage().contains("PRIMARY")) {
                // If there is already an entry, do nothing.
                logger.info("Double primary key. User already in link table. Do nothing.");
            } else {
                // Throw back DatabaseException to the Controller.
                throw new DatabaseException("Database failure.");
            }
        } finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Removes the user with the given id as subscriber from the channel with the given id.
     *
     * @param channelId The id of the channel to which the user is subscribed.
     * @param userId The id of the user who should be removed from the channel.
     * @throws DatabaseException If the data could not be deleted from the database due to a database failure.
     */
    public void removeSubscriberFromChannel(int channelId, int userId) throws DatabaseException {
        logger.debug("Start with channelId:{} and userId:{}.", channelId, userId);
        Connection con = null;
        try {
            con = getDatabaseConnection();

            // Remove a user as subscriber from a channel.
            String removeUserQuery =
                    "DELETE FROM UserChannel WHERE User_Id=? AND Channel_Id=?;";

            PreparedStatement removeUserStmt = con.prepareStatement(removeUserQuery);
            removeUserStmt.setInt(1, userId);
            removeUserStmt.setInt(2, channelId);

            int rowsAffected = removeUserStmt.executeUpdate();
            if (rowsAffected == 1) {
                logger.info("Removed the user with id {} as subscriber from the channel with id {}.", userId, channelId);
            }
            removeUserStmt.close();
        } catch (SQLException e) {
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Gets all moderators who are responsible for a channel with the given id from the database.
     *
     * @param channelId The id of the channel.
     * @return All moderators who are responsible for the channel.
     */
    public List<User> getSubscribers(int channelId) throws DatabaseException {
        logger.debug("Start with channelId:{}.", channelId);
        Connection con = null;
        List<User> users = new ArrayList<User>();
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT u.Id, u.Name, u.PushAccessToken, u.Platform " +
                            "FROM User AS u INNER JOIN UserChannel AS uc " +
                            "ON u.Id=uc.User_Id WHERE uc.Channel_Id=?;";

            PreparedStatement getSubscribersStmt = con.prepareStatement(query);
            getSubscribersStmt.setInt(1, channelId);

            ResultSet getSubscribersRs = getSubscribersStmt.executeQuery();
            while (getSubscribersRs.next()) {
                // Only set those values which should be returned to the requestor.
                User user = new User();
                user.setId(getSubscribersRs.getInt("Id"));
                user.setName(getSubscribersRs.getString("Name"));
                user.setPushAccessToken(getSubscribersRs.getString("PushAccessToken"));
                user.setPlatform(Platform.values[getSubscribersRs.getInt("Platform")]);
                users.add(user);
            }
            getSubscribersStmt.close();
        } catch (SQLException e) {
            // Throw back DatabaseException to the Controller.
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End with users:{}.", users);
        return users;
    }

    /**
     * Checks whether the moderator identified by the given moderator id is responsible for the channel identified by
     * the given channel id or not.
     *
     * @param channelId The id of the channel.
     * @param moderatorId The id of the moderator.
     * @return true if the moderator is responsible for the channel.
     */
    public boolean isResponsibleForChannel(int channelId, int moderatorId) throws DatabaseException {
        logger.debug("Start with moderatorId:{} and channelId:{}.", moderatorId, channelId);
        Connection con = null;
        boolean responsible = false;
        try {
            con = getDatabaseConnection();
            String query = "SELECT * FROM ModeratorChannel WHERE Moderator_Id=? AND Channel_Id=? AND Active=?;";

            PreparedStatement getResponsibleStmt = con.prepareStatement(query);
            getResponsibleStmt.setInt(1, moderatorId);
            getResponsibleStmt.setInt(2, channelId);
            getResponsibleStmt.setBoolean(3, true);

            ResultSet getResponsibleRs = getResponsibleStmt.executeQuery();
            if (getResponsibleRs.next()) {
                responsible = true;
            }
            getResponsibleStmt.close();
        } catch (SQLException e) {
            // Throw back DatabaseException to the Controller.
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End with responsible:{}.", responsible);
        return responsible;
    }

    /**
     * Checks if moderator has still one or more entries in the ModeratorChannel table.
     *
     * @param moderatorId The id of the moderator.
     * @return true if the moderator is still in the database and linked to one or more channels.
     * @throws DatabaseException If a database failure occurs.
     */
    public boolean isModeratorStillNeeded(int moderatorId) throws DatabaseException {
        logger.debug("Start with moderatorId:{}.", moderatorId);
        Connection con = null;
        boolean needed = false;
        try {
            con = getDatabaseConnection();
            String query = "SELECT * FROM ModeratorChannel WHERE Moderator_Id=?;";

            PreparedStatement getResponsibleStmt = con.prepareStatement(query);
            getResponsibleStmt.setInt(1, moderatorId);

            ResultSet getResponsibleRs = getResponsibleStmt.executeQuery();
            if (getResponsibleRs.next()) {
                needed = true;
            }
            getResponsibleStmt.close();
        } catch (SQLException e) {
            // Throw back DatabaseException to the Controller.
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End with needed:{}.", needed);
        return needed;
    }

    /**
     * Checks whether a channel identified by the given channel id exists in the database or not.
     *
     * @param channelId The id of the Channel.
     * @return true if the channel with the given id exists in the database.
     */
    public boolean isValidChannelId(int channelId) throws DatabaseException {
        logger.debug("Start with channelId:{}.", channelId);
        Connection con = null;
        boolean valid = false;
        try {
            con = getDatabaseConnection();
            String query = "SELECT * FROM Channel WHERE Id=?;";

            PreparedStatement getValidStmt = con.prepareStatement(query);
            getValidStmt.setInt(1, channelId);

            ResultSet getValidRs = getValidStmt.executeQuery();
            if (getValidRs.next()) {
                valid = true;
            }
            getValidStmt.close();
        } catch (SQLException e) {
            // Throw back DatabaseException to the Controller.
            logger.error(LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new DatabaseException("Database failure.");
        } finally {
            returnConnection(con);
        }
        logger.debug("End with valid:{}.", valid);
        return valid;
    }
}
