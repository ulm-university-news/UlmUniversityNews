package ulm.university.news.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.Channel;
import ulm.university.news.data.Lecture;
import ulm.university.news.data.Moderator;
import ulm.university.news.data.enums.ChannelType;
import ulm.university.news.manager.database.ChannelDatabaseManager;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ServerException;

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
     * Creates a new channel and adds the creator to its responsible moderators.
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
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_TERM, "Invalid term.");
            throw new ServerException(400, CHANNEL_INVALID_TERM);
        }
        // TODO Other (e.g. contacts) input validation? Verify length only!

        // Perform special checks for the received data of the lecture subclass.
        if (channel.getType() == ChannelType.LECTURE) {
                Lecture lecture = (Lecture) channel;
                if (lecture.getLecturer() == null || lecture.getFaculty() == null) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_DATA_INCOMPLETE, "Channel data is incomplete.");
                    throw new ServerException(400, CHANNEL_DATA_INCOMPLETE);
                }
        }
        // TODO Input validation of subclass fields? Verify length only!

        // Check if requestor is a valid moderator.
        Moderator moderatorDB = verifyModeratorAccess(accessToken);

        // Initialize remaining channel fields.
        channel.computeCreationDate();
        channel.setModificationDate(channel.getCreationDate());

        try {
            channelDBM.storeChannel(channel, moderatorDB.getId());
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Creation of channel failed.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return channel;
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

        // Check if requestor is a valid moderator.
        Moderator moderatorRequestorDB = verifyModeratorAccess(accessToken);

        try {
            // Only a responsible moderator is allowed to add other moderators.
            if(!channelDBM.isResponsibleForChannel(channelId, moderatorRequestorDB.getId())){
                logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "This moderator is not allowed to " +
                        "perform the requested operation.");
                throw new ServerException(403, MODERATOR_FORBIDDEN);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Could not check if moderator" +
                    " is responsible for channel.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        try {
            channelDBM.addModeratorToChannel(channelId, moderatorCtrl.getModeratorIdByName(moderatorName));
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Could not add moderator to " +
                    "channel.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }
}
