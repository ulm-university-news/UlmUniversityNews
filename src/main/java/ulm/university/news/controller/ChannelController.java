package ulm.university.news.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.*;
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
        } else if (!Pattern.compile(CHANNEL_NAME_PATTERN).matcher(channel.getName()).matches()) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_NAME, "Channel name is invalid.");
            throw new ServerException(400, CHANNEL_INVALID_NAME);
        } else if(channel.getTerm() != null && !channel.getTerm().matches(TERM_PATTERN)){
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_TERM, "Invalid term.");
            throw new ServerException(400, CHANNEL_INVALID_TERM);
        }
        // TODO Other (e.g. contacts) input validation?

        // Perform special checks for the received data of the lecture subclass.
        if (channel.getType() == ChannelType.LECTURE) {
                Lecture lecture = (Lecture) channel;
                if (lecture.getLecturer() == null || lecture.getFaculty() == null) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_DATA_INCOMPLETE, "Channel data is incomplete.");
                    throw new ServerException(400, CHANNEL_DATA_INCOMPLETE);
                }
        }
        // TODO Input validation of subclass fields?

        // Check if requestor is a valid moderator.
        Moderator moderatorRequestorDB = verifyModeratorAccess(accessToken);

        // Initialize remaining channel fields.
        channel.computeCreationDate();
        channel.setModificationDate(channel.getCreationDate());

        try {
            channelDBM.storeChannel(channel, moderatorRequestorDB.getId());
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Creation of channel failed.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return channel;
    }
}
