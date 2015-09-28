package ulm.university.news.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.*;
import ulm.university.news.data.enums.ChannelType;
import ulm.university.news.manager.database.ChannelDatabaseManager;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ServerException;

import java.util.List;
import java.util.regex.Pattern;

import static ulm.university.news.util.Constants.*;

/**
 * The ChannelController handles requests concerning the channel resources. It offers methods to query channel data
 * as well as methods to create new channels or update existing ones. This class also handles request regarding
 * announcements and reminders.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class ChannelController extends AccessController {

    /** The logger instance for ChannelController. */
    private static final Logger logger = LoggerFactory.getLogger(ChannelController.class);

    /** Instance of the ChannelDatabaseManager class. */
    protected ChannelDatabaseManager channelDBM = new ChannelDatabaseManager();

    /** Instance of the ModeratorController class. */
    private ModeratorController moderatorCtrl = new ModeratorController();

    /**
     * Validates received channel data. Creates a new channel and adds the creator to its responsible moderators.
     *
     * @param accessToken The access token of the requestor.
     * @param channel The channel object including the data of the new channel.
     * @return The newly created channel object.
     * @throws ServerException If the authorization of the requestor fails or the requestor isn't allowed to perform
     * the operation. Furthermore, a failure of the database also causes a ServerException.
     */
    public Channel createChannel(String accessToken, Channel channel) throws ServerException {
        // Perform checks on the received data. If the data isn't accurate the channel can't be created.
        if (channel.getName() == null || channel.getType() == null || channel.getContacts() == null) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_DATA_INCOMPLETE, "Channel data is incomplete.");
            throw new ServerException(400, CHANNEL_DATA_INCOMPLETE);
        } else if (!Pattern.compile(NAME_PATTERN).matcher(channel.getName()).matches()) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_NAME, "Channel name is invalid.");
            throw new ServerException(400, CHANNEL_INVALID_NAME);
        } else if(channel.getTerm() != null && !channel.getTerm().matches(TERM_PATTERN)){
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_TERM, "Term is invalid.");
            throw new ServerException(400, CHANNEL_INVALID_TERM);
        } else if(channel.getContacts().length() > CHANNEL_CONTACTS_MAX_LENGTH){
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_CONTACTS, "Contacts to long.");
            throw new ServerException(400, CHANNEL_INVALID_CONTACTS);
        } else if(channel.getDescription() != null && channel.getDescription().length() > DESCRIPTION_MAX_LENGTH){
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_DESCRIPTION, "Description to long.");
            throw new ServerException(400, CHANNEL_INVALID_DESCRIPTION);
        } else if(channel.getLocations() != null && channel.getLocations().length() > CHANNEL_LOCATIONS_MAX_LENGTH){
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_LOCATIONS, "Locations to long.");
            throw new ServerException(400, CHANNEL_INVALID_LOCATIONS);
        } else if(channel.getDates() != null && channel.getDates().length() > CHANNEL_DATES_MAX_LENGTH){
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_DATES, "Dates to long.");
            throw new ServerException(400, CHANNEL_INVALID_DATES);
        }

        // Perform special checks for the received data of the Lecture subclass.
        if (channel.getType() == ChannelType.LECTURE) {
                Lecture lecture = (Lecture) channel;
                if (lecture.getLecturer() == null || lecture.getFaculty() == null) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_DATA_INCOMPLETE, "Lecture data is incomplete.");
                    throw new ServerException(400, CHANNEL_DATA_INCOMPLETE);
                } else if(lecture.getLecturer().length() > CHANNEL_CONTACTS_MAX_LENGTH){
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_LECTURER, "Lecturer to long.");
                    throw new ServerException(400, CHANNEL_INVALID_LECTURER);
                } else if(lecture.getAssistant() != null && lecture.getAssistant().length() >
                        CHANNEL_CONTACTS_MAX_LENGTH){
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_ASSISTANT, "Assistant to long.");
                    throw new ServerException(400, CHANNEL_INVALID_ASSISTANT);
                }
        }

        // Perform special checks for the received data of the Sports subclass.
        if (channel.getType() == ChannelType.SPORTS) {
            Sports sports = (Sports) channel;
            if(sports.getCost() != null && sports.getCost().length() > CHANNEL_COST_MAX_LENGTH){
                logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_COST, "Costs to long.");
                throw new ServerException(400, CHANNEL_INVALID_COST);
            } else if(sports.getNumberOfParticipants() != null && sports.getNumberOfParticipants().length() >
                    CHANNEL_PARTICIPANTS_MAX_LENGTH){
                logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_PARTICIPANTS, "Number of participants to long.");
                throw new ServerException(400, CHANNEL_INVALID_PARTICIPANTS);
            }
        }

        // Perform special checks for the received data of the Event subclass.
        if (channel.getType() == ChannelType.EVENT) {
            Event event = (Event) channel;
            if(event.getCost() != null && event.getCost().length() > CHANNEL_COST_MAX_LENGTH){
                logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_COST, "Costs to long.");
                throw new ServerException(400, CHANNEL_INVALID_COST);
            } else if(event.getOrganizer() != null && event.getOrganizer().length() >
                    CHANNEL_CONTACTS_MAX_LENGTH){
                logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_ORGANIZER, "Organizer to long.");
                throw new ServerException(400, CHANNEL_INVALID_ORGANIZER);
            }
        }

        // Check if requestor is a valid moderator.
        Moderator moderatorDB = verifyModeratorAccess(accessToken);

        // Initialize remaining channel fields.
        channel.computeCreationDate();
        channel.setModificationDate(channel.getCreationDate());

        try {
            channelDBM.storeChannel(channel, moderatorDB.getId());
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return channel;
    }

    /**
     * Verifies that the requestor is a valid moderator, the channel with the given id exists and the moderator is
     * responsible for the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel.
     * @return The moderator (requestor) if he is responsible for the channel.
     * @throws ServerException If the authorization of the requestor fails, the requestor isn't allowed to perform
     * the operation, the channel wasn't found or the moderator isn't responsible for the channel. Furthermore, a
     * failure of the database also causes a ServerException.
     */
    private Moderator verifyResponsibleModerator(String accessToken, int channelId) throws ServerException {
        // Check if requestor is a valid moderator.
        Moderator moderatorRequestorDB = verifyModeratorAccess(accessToken);
        try {
            // Check if channel with given id exits.
            if(!channelDBM.isValidChannelId(channelId)) {
                logger.error(LOG_SERVER_EXCEPTION, 404, CHANNEL_NOT_FOUND, "Channel id not found in database.");
                throw new ServerException(404, CHANNEL_NOT_FOUND);
            }
            // Check if moderator is responsible for the channel.
            if(!channelDBM.isResponsibleForChannel(channelId, moderatorRequestorDB.getId())){
                logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "This moderator is not allowed to " +
                        "perform the requested operation.");
                throw new ServerException(403, MODERATOR_FORBIDDEN);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
        return moderatorRequestorDB;
    }

    /**
     * Adds a moderator identified by name to a channel identified by id. Afterwards the moderator is registered as
     * responsible moderator for the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the moderator should be added.
     * @param moderatorName The name of the moderator who should be added to the channel.
     * @throws ServerException If the authorization of the requestor fails or the requestor isn't allowed to perform
     * the operation. Furthermore, a failure of the database also causes a ServerException.
     */
    public void addModeratorToChannel(String accessToken, int channelId, String moderatorName) throws ServerException {
        // Check weather the moderator name is set or not.
        if(moderatorName == null) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_DATA_INCOMPLETE, "Moderator name isn't set.");
            throw new ServerException(400, CHANNEL_DATA_INCOMPLETE);
        }

        // Only a responsible moderator is allowed to add other moderators.
        verifyResponsibleModerator(accessToken, channelId);

        try {
            channelDBM.addModeratorToChannel(channelId, moderatorCtrl.getModeratorIdByName(moderatorName));
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * Removes a moderator identified by id from a channel identified by id. Afterwards the moderator is no longer a
     * responsible moderator for the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel for which the moderator is responsible.
     * @param moderatorId The id of the moderator who should be removed as responsible moderator from the channel.
     * @throws ServerException If the authorization of the requestor fails or the requestor isn't allowed to perform
     * the operation. Furthermore, a failure of the database also causes a ServerException.
     */
    public void removeModeratorFromChannel(String accessToken, int channelId, int moderatorId) throws
            ServerException {
        try {
            // Check if there is more than one responsible moderator for the channel.
            List<Moderator> responsibleModerators = getResponsibleModerators(accessToken, channelId);
            if(responsibleModerators.size() == 1) {
                logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Can't remove moderator from channel " +
                        "because there has to be at least on responsible moderator per channel.");
                throw new ServerException(403, MODERATOR_FORBIDDEN);
            }
            // Perform no further checks, just try to delete the specified entry. If not found, nothing happens.
            channelDBM.removeModeratorFromChannel(channelId, moderatorId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * Gets all moderators who are responsible for a channel with the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel for which the moderators are responsible.
     * @throws ServerException If the authorization of the requestor fails or the requestor isn't allowed to perform
     * the operation. Furthermore, a failure of the database also causes a ServerException.
     */
    public List<Moderator> getResponsibleModerators(String accessToken, int channelId) throws ServerException {
        // Check if requestor is a valid responsible moderator of the channel.
        verifyResponsibleModerator(accessToken, channelId);
        try {
            return channelDBM.getResponsibleModerators(channelId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * Adds a user to a channel. Afterwards the user is registered as subscriber of the channel. Subscribers will
     * receive announcements of the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the user should be added.
     * @throws ServerException If the authorization of the requestor fails, the requestor isn't allowed to perform
     * the operation or the channel wasn't found. Furthermore, a failure of the database also causes a ServerException.
     */
    public void subscribeChannel(String accessToken, int channelId) throws ServerException {
        // Check if requestor is a valid user.
        User userDB = verifyUserAccess(accessToken);
        try {
            // Check if channel with given id exits.
            if(!channelDBM.isValidChannelId(channelId)) {
                logger.error(LOG_SERVER_EXCEPTION, 404, CHANNEL_NOT_FOUND, "Channel id not found in database.");
                throw new ServerException(404, CHANNEL_NOT_FOUND);
            }
            channelDBM.addSubscriberToChannel(channelId, userDB.getId());
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * Removes a user from a channel. Afterwards the user is no longer a subscriber of the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the user is subscribed.
     * @param userId The id of the user who should be removed as subscriber from the channel.
     * @throws ServerException If the authorization of the requestor fails, the requestor isn't allowed to perform
     * the operation or the channel wasn't found. Furthermore, a failure of the database also causes a ServerException.
     */
    public void unsubscribeChannel(String accessToken, int channelId, int userId) throws ServerException {
        // Check if requestor is a valid user.
        User userDB = verifyUserAccess(accessToken);
        try {
            // Check if requestor id is another user id as the one which should be unsubscribed.
            if(userDB.getId() != userId) {
                logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, "User is not allowed to perform the requested" +
                        " operation.");
                throw new ServerException(403, USER_FORBIDDEN);
            }
            // Perform no further checks, just try to delete the specified entry. If not found, nothing happens.
            channelDBM.removeSubscriberFromChannel(channelId, userId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * Gets all users who are subscribed to a channel with the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the users are subscribed.
     * @throws ServerException If the authorization of the requestor fails or the requestor isn't allowed to perform
     * the operation. Furthermore, a failure of the database also causes a ServerException.
     */
    public List<User> getSubscribers(String accessToken, int channelId) throws ServerException {
        // Check if requestor is a valid moderator.
        verifyResponsibleModerator(accessToken, channelId);
        try {
            return channelDBM.getSubscribers(channelId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }
}
