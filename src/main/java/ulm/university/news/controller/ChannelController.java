package ulm.university.news.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.*;
import ulm.university.news.data.enums.ChannelType;
import ulm.university.news.data.enums.TokenType;
import ulm.university.news.manager.database.ChannelDatabaseManager;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ServerException;

import java.time.ZonedDateTime;
import java.util.List;

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
    private ModeratorController moderatorCtrl;

    /**
     * Constructor of ChannelController. Creates a new ModeratorController and passes itself as reference. This
     * prevents infinite mutual constructor invocation of ChannelController and ModeratorController.
     */
    public ChannelController() {
        if (moderatorCtrl == null) {
            moderatorCtrl = new ModeratorController(this);
        }
    }

    /**
     * Constructor of ChannelController. Sets the given ModeratorController as local instance. This
     * prevents infinite mutual constructor invocation of ChannelController and ModeratorController.
     */
    public ChannelController(ModeratorController moderatorCtrl) {
        this.moderatorCtrl = moderatorCtrl;
    }

    /**
     * Creates a new channel and adds the creator to its responsible moderators. Validates received channel data.
     *
     * @param accessToken The access token of the requestor.
     * @param channel The channel object including the data of the new channel.
     * @return The newly created channel object.
     * @throws ServerException If the authorization of the requestor fails or the requestor isn't allowed to perform
     * the operation. Furthermore, invalid channel data and a failure of the database also causes a ServerException.
     */
    public Channel createChannel(String accessToken, Channel channel) throws ServerException {
        // Perform checks on the received channel data. If the data isn't accurate the channel can't be created.
        if (channel.getName() == null || channel.getType() == null || channel.getContacts() == null) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_DATA_INCOMPLETE, "Channel data is incomplete.");
            throw new ServerException(400, CHANNEL_DATA_INCOMPLETE);
        }
        verifyChannelData(channel);

        // Perform special checks for the received data of the channel subclass.
        if (channel.getType() == ChannelType.LECTURE) {
            Lecture lecture = (Lecture) channel;
            if (lecture.getLecturer() == null || lecture.getFaculty() == null) {
                logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_DATA_INCOMPLETE, "Lecture data is incomplete.");
                throw new ServerException(400, CHANNEL_DATA_INCOMPLETE);
            }
        }
        verifyChannelSubclassData(channel);

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
     * Validates received channel data. Changes the channel data according to the given channel object.
     *
     * @param accessToken The access token of the requestor.
     * @param channel The channel object including the changed data of the channel.
     * @return The changed channel object.
     * @throws ServerException If the authorization of the requestor fails or the requestor isn't allowed to perform
     * the operation. Furthermore, invalid channel data and a failure of the database also causes a ServerException.
     */
    public Channel changeChannel(String accessToken, int channelId, Channel channel) throws ServerException {
        // Perform checks on the received channel data. If the data isn't accurate the channel can't be changed.
        verifyChannelData(channel);

        // Check if requestor is a valid moderator.
        Moderator moderatorRequestorDB = verifyModeratorAccess(accessToken);

        Channel channelDB;
        try {
            // Get channel with given id from database.
            channelDB = channelDBM.getChannel(channelId);
            // Check if channel exists in database.
            if (channelDB == null) {
                logger.error(LOG_SERVER_EXCEPTION, 404, CHANNEL_NOT_FOUND, "Channel not found in database.");
                throw new ServerException(404, CHANNEL_NOT_FOUND);
            }
            // Check if moderator is responsible for the channel.
            if (!channelDBM.isResponsibleForChannel(channelId, moderatorRequestorDB.getId())) {
                logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "This moderator is not allowed to " +
                        "perform the requested operation.");
                throw new ServerException(403, MODERATOR_FORBIDDEN);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        boolean changed = false;
        // Check if at least one field of channel will be changed.
        if (channel.getName() != null || channel.getDescription() != null || channel.getTerm() != null || channel
                .getLocations() != null || channel.getContacts() != null || channel.getDates() != null || channel
                .getWebsite() != null) {
            changed = true;
        }

        if (changed) {
            // Update channel fields.
            channelDB = updateChannel(channel, channelDB);
        }

        // Update modification date.
        channelDB.setModificationDate(ZonedDateTime.now(TIME_ZONE));

        // Ignore given channel subclass data if it's not the subclass of the channel in the database.
        if (channelDB.getType() == channel.getType()) {
            // Perform special checks for the received data of the channel subclass.
            verifyChannelSubclassData(channel);
            // Update channel subclass fields.
            channelDB = updateChannelSubclass(channel, channelDB, changed);
            try {
                channelDBM.updateChannelWithSubclass(channelDB);
            } catch (DatabaseException e) {
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
                throw new ServerException(500, DATABASE_FAILURE);
            }
        } else {
            if (!changed) {
                logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_DATA_INCOMPLETE, "Channel PATCH data is incomplete.");
                throw new ServerException(400, CHANNEL_DATA_INCOMPLETE);
            }
            try {
                channelDBM.updateChannel(channelDB);
            } catch (DatabaseException e) {
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
                throw new ServerException(500, DATABASE_FAILURE);
            }
        }
        return channelDB;
    }

    /**
     * Updates several fields of the channel object retrieved from the database. Only the following fields can be
     * changed: name, description, term, locations, contact, modificationDate, dates and website.
     *
     * @param channel The channel object with the updated data values.
     * @param channelDB The channel object from the database.
     * @return The complete updated channel object.
     */
    private Channel updateChannel(Channel channel, Channel channelDB) {
        // Only update fields which are set.
        if (channel.getName() != null) {
            channelDB.setName(channel.getName());
        }
        if (channel.getDescription() != null) {
            channelDB.setDescription(channel.getDescription());
        }
        if (channel.getTerm() != null) {
            channelDB.setTerm(channel.getTerm());
        }
        if (channel.getLocations() != null) {
            channelDB.setLocations(channel.getLocations());
        }
        if (channel.getContacts() != null) {
            channelDB.setContacts(channel.getContacts());
        }
        if (channel.getDates() != null) {
            channelDB.setDates(channel.getDates());
        }
        if (channel.getWebsite() != null) {
            channelDB.setWebsite(channel.getWebsite());
        }
        return channelDB;
    }

    /**
     * Updates several fields of the channel subclass object retrieved from the database. Only the following fields
     * can be changed. Lecture: startDate, endDate, lecturer and assistant. Sports: cost and numberOfParticipants.
     * Event: cost and organizer.
     *
     * @param channel The channel object with the updated data values.
     * @param channelDB The channel object from the database.
     * @param changed Indicates if one or more fields of the channel superclass were changed.
     * @return The complete updated channel object.
     * @throws ServerException If channel PATCH data is incomplete.
     */
    private Channel updateChannelSubclass(Channel channel, Channel channelDB, boolean changed) throws ServerException {
        // Only update fields which are set.
        switch (channelDB.getType()) {
            case LECTURE:
                Lecture lecture = (Lecture) channel;
                Lecture lectureDB = (Lecture) channelDB;
                if (!changed && lecture.getStartDate() == null && lecture.getEndDate() == null && lecture.getLecturer()
                        == null && lecture.getAssistant() == null) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_DATA_INCOMPLETE, "Channel PATCH data is " +
                            "incomplete.");
                    throw new ServerException(400, CHANNEL_DATA_INCOMPLETE);
                }
                if (lecture.getStartDate() != null) {
                    lectureDB.setStartDate(lecture.getStartDate());
                }
                if (lecture.getEndDate() != null) {
                    lectureDB.setEndDate(lecture.getEndDate());
                }
                if (lecture.getLecturer() != null) {
                    lectureDB.setLecturer(lecture.getLecturer());
                }
                if (lecture.getAssistant() != null) {
                    lectureDB.setAssistant(lecture.getAssistant());
                }
                break;
            case SPORTS:
                Sports sports = (Sports) channel;
                Sports sportsDB = (Sports) channelDB;
                if (!changed && sports.getCost() == null && sports.getNumberOfParticipants() == null) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_DATA_INCOMPLETE, "Channel PATCH data is " +
                            "incomplete.");
                    throw new ServerException(400, CHANNEL_DATA_INCOMPLETE);
                }
                if (sports.getCost() != null) {
                    sportsDB.setCost(sports.getCost());
                }
                if (sports.getNumberOfParticipants() != null) {
                    sportsDB.setNumberOfParticipants(sports.getNumberOfParticipants());
                }
                break;
            case EVENT:
                Event event = (Event) channel;
                Event eventDB = (Event) channelDB;
                if (!changed && event.getCost() == null && event.getOrganizer() == null) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_DATA_INCOMPLETE, "Channel PATCH data is " +
                            "incomplete.");
                    throw new ServerException(400, CHANNEL_DATA_INCOMPLETE);
                }
                if (event.getCost() != null) {
                    eventDB.setCost(event.getCost());
                }
                if (event.getOrganizer() != null) {
                    eventDB.setOrganizer(event.getOrganizer());
                }
                break;
        }
        return channelDB;
    }

    /**
     * Perform various checks on any possible channel data.
     *
     * @param channel The channel object with the data to check.
     * @throws ServerException If channel data isn't valid.
     */
    private void verifyChannelData(Channel channel) throws ServerException {
        // Perform checks on the received data. If the data isn't accurate the channel can't be created.
        if (channel.getName() != null && !channel.getName().matches(NAME_PATTERN)) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_NAME, "Channel name is invalid.");
            throw new ServerException(400, CHANNEL_INVALID_NAME);
        } else if (channel.getTerm() != null && !channel.getTerm().matches(TERM_PATTERN)) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_TERM, "Term is invalid.");
            throw new ServerException(400, CHANNEL_INVALID_TERM);
        } else if (channel.getContacts() != null && channel.getContacts().length() > CHANNEL_CONTACTS_MAX_LENGTH) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_CONTACTS, "Contacts to long.");
            throw new ServerException(400, CHANNEL_INVALID_CONTACTS);
        } else if (channel.getDescription() != null && channel.getDescription().length() > DESCRIPTION_MAX_LENGTH) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_DESCRIPTION, "Description to long.");
            throw new ServerException(400, CHANNEL_INVALID_DESCRIPTION);
        } else if (channel.getLocations() != null && channel.getLocations().length() > CHANNEL_LOCATIONS_MAX_LENGTH) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_LOCATIONS, "Locations to long.");
            throw new ServerException(400, CHANNEL_INVALID_LOCATIONS);
        } else if (channel.getDates() != null && channel.getDates().length() > CHANNEL_DATES_MAX_LENGTH) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_DATES, "Dates to long.");
            throw new ServerException(400, CHANNEL_INVALID_DATES);
        } if (channel.getWebsite() != null && channel.getWebsite().length() > CHANNEL_WEBSITE_MAX_LENGTH) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_WEBSITE, "Website to long.");
            throw new ServerException(400, CHANNEL_INVALID_WEBSITE);
        }
    }

    /**
     * Perform various checks on any possible channel subclass data.
     *
     * @param channel The channel object with the subclass data to check.
     * @throws ServerException If channel data isn't valid.
     */
    private void verifyChannelSubclassData(Channel channel) throws ServerException {
        switch (channel.getType()) {
            case LECTURE:
                // Perform special checks for the received data of the Lecture subclass.
                Lecture lecture = (Lecture) channel;
                if (lecture.getLecturer() != null && lecture.getLecturer().length() > CHANNEL_CONTACTS_MAX_LENGTH) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_LECTURER, "Lecturer to long.");
                    throw new ServerException(400, CHANNEL_INVALID_LECTURER);
                } else if (lecture.getAssistant() != null && lecture.getAssistant().length() >
                        CHANNEL_CONTACTS_MAX_LENGTH) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_ASSISTANT, "Assistant to long.");
                    throw new ServerException(400, CHANNEL_INVALID_ASSISTANT);
                } else if (lecture.getStartDate() != null && lecture.getStartDate().length() > CHANNEL_DATE_MAX_LENGTH) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_START_DATE, "Start date to long.");
                    throw new ServerException(400, CHANNEL_INVALID_START_DATE);
                } else if (lecture.getEndDate() != null && lecture.getEndDate().length() > CHANNEL_DATE_MAX_LENGTH) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_END_DATE, "End date to long.");
                    throw new ServerException(400, CHANNEL_INVALID_END_DATE);
                }
                break;
            case SPORTS:
                // Perform special checks for the received data of the Sports subclass.
                Sports sports = (Sports) channel;
                if (sports.getCost() != null && sports.getCost().length() > CHANNEL_COST_MAX_LENGTH) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_COST, "Costs to long.");
                    throw new ServerException(400, CHANNEL_INVALID_COST);
                } else if (sports.getNumberOfParticipants() != null && sports.getNumberOfParticipants().length() >
                        CHANNEL_PARTICIPANTS_MAX_LENGTH) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_PARTICIPANTS, "Number of participants to long.");
                    throw new ServerException(400, CHANNEL_INVALID_PARTICIPANTS);
                }
                break;
            case EVENT:
                // Perform special checks for the received data of the Event subclass.
                Event event = (Event) channel;
                if (event.getCost() != null && event.getCost().length() > CHANNEL_COST_MAX_LENGTH) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_COST, "Costs to long.");
                    throw new ServerException(400, CHANNEL_INVALID_COST);
                } else if (event.getOrganizer() != null && event.getOrganizer().length() >
                        CHANNEL_CONTACTS_MAX_LENGTH) {
                    logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_ORGANIZER, "Organizer to long.");
                    throw new ServerException(400, CHANNEL_INVALID_ORGANIZER);
                }
                break;
        }
    }

    /**
     * Gets all the existing channel data. The requested channels can be restricted to a specific selection by the
     * given params: moderatorId and lastUpdated.
     *
     * @param accessToken The access token of the requestor.
     * @param moderatorId Get only channels for which the moderator with the given id is responsible.
     * @param lastUpdated Get only channels with a newer modification data as the last updated date.
     * @return A list with all requested channel objects.
     * @throws ServerException If the authorization of the requestor fails or the requestor isn't allowed to perform
     * the operation. Furthermore, a failure of the database also causes a ServerException.
     */
    public List<Channel> getChannels(String accessToken, Integer moderatorId, ZonedDateTime lastUpdated) throws
            ServerException {
        TokenType tokenType = verifyAccessToken(accessToken);

        // Check if there is a valid access token provided.
        if (tokenType == TokenType.INVALID) {
            String errMsg = "To perform this operation a valid access token needs to be provided.";
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, errMsg);
            throw new ServerException(401, TOKEN_INVALID);
        } else if (tokenType == TokenType.MODERATOR) {
            // The requestor is a valid moderator.
            try {
                Moderator moderatorDB = moderatorDBM.getModeratorByToken(accessToken);
                return channelDBM.getChannels(moderatorId, lastUpdated);
            } catch (DatabaseException e) {
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
                throw new ServerException(500, DATABASE_FAILURE);
            }
        } else if (tokenType == TokenType.USER) {
            // The requestor is a valid user.
            try {
                // Ignore parameter moderatorId because requestor is a user.
                return channelDBM.getChannels(null, lastUpdated);
            } catch (DatabaseException e) {
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
                throw new ServerException(500, DATABASE_FAILURE);
            }
        }
        return null;
    }


    /**
     * Delivers the channel data of a specific channel identified by id.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel.
     * @return The requested channel object.
     * @throws ServerException If the authorization of the requestor fails or the requestor isn't allowed to perform
     * the operation. Furthermore, a failure of the database also causes a ServerException.
     */
    public Channel getChannel(String accessToken, int channelId) throws ServerException {
        // Check if there is a valid access token provided.
        TokenType tokenType = verifyAccessToken(accessToken);
        if (tokenType == TokenType.INVALID) {
            String errMsg = "To perform this operation a valid access token needs to be provided.";
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, errMsg);
            throw new ServerException(401, TOKEN_INVALID);
        }
        try {
            // Requestor is a valid user or a moderator. Both are allowed to perform this operation.
            Channel channel = channelDBM.getChannel(channelId);
            if (channel == null) {
                logger.error(LOG_SERVER_EXCEPTION, 404, CHANNEL_NOT_FOUND, "Channel not found in database.");
                throw new ServerException(404, CHANNEL_NOT_FOUND);
            } else {
                return channel;
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * Deletes a channel from the database. Afterwards, it's no longer available on the server.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel which should be deleted.
     * @throws ServerException If the authorization of the requestor fails, the requestor isn't allowed to perform
     * the operation or the channel with given id isn't found in the database. Furthermore, a failure of the database
     * also causes a ServerException.
     */
    public void deleteChannel(String accessToken, int channelId) throws ServerException {
        // Check if requestor is a valid moderator and responsible for the given channel.
        verifyResponsibleModerator(accessToken, channelId);

        List<Moderator> deletedModerators = null;
        List<User> subscribers = null;
        try {
            // Get all deleted moderators of the specified channel for later deletion.
            deletedModerators = channelDBM.getDeletedModeratorsByChannel(channelId);
            // Get all subscribers of the specified channel for later notification.
            subscribers = channelDBM.getSubscribers(channelId);
            // Delete channel and all entries which are linked to it.
            channelDBM.deleteChannel(channelId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // TODO notifySubscribers(channelId, subscribers, CHANNEL_DELETED)

        // Attempt to delete moderators who are marked as deleted but are still in the database.
        if (deletedModerators != null) {
            for (Moderator deletedModerator : deletedModerators) {
                moderatorCtrl.deleteModerator(deletedModerator.getId());
            }
        }
    }

    /**
     * Gets all channels which are managed by the moderator with the given id. The channel objects contain a list of all
     * their responsible moderators and subscribers.
     *
     * @param moderatorId The moderator id who is responsible for the requested channels.
     * @return The requested channels including lists of responsible moderators and subscribers.
     * @throws ServerException If a database failure occurs.
     */
    public List<Channel> getChannelsOfModerator(int moderatorId) throws ServerException {
        try {
            return channelDBM.getChannelsOfModerator(moderatorId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * Removes the moderator with the given id as responsible moderator from all channels.
     *
     * @param moderatorId The id of the moderator who should be removed from the channels.
     * @throws ServerException If the data could not be deleted from the database due to a database failure.
     */
    public void removeModeratorFromChannels(int moderatorId) throws ServerException {
        try {
            channelDBM.removeModeratorFromChannels(moderatorId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
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
            if (!channelDBM.isValidChannelId(channelId)) {
                logger.error(LOG_SERVER_EXCEPTION, 404, CHANNEL_NOT_FOUND, "Channel id not found in database.");
                throw new ServerException(404, CHANNEL_NOT_FOUND);
            }
            // Check if moderator is responsible for the channel.
            if (!channelDBM.isResponsibleForChannel(channelId, moderatorRequestorDB.getId())) {
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
        if (moderatorName == null) {
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
            if (responsibleModerators.size() == 1) {
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
            if (!channelDBM.isValidChannelId(channelId)) {
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
            if (userDB.getId() != userId) {
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

    /**
     * Checks if moderator is still linked to one or more channels in the database.
     *
     * @param moderatorId The id of the moderator.
     * @return true if the moderator is still in the database and linked to one or more channels.
     * @throws ServerException If a database failure occurs.
     */
    public boolean isModeratorStillNeeded(int moderatorId) throws ServerException {
        try {
            return channelDBM.isModeratorStillNeeded(moderatorId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * Creates a new announcement. Validates received announcement data.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel in which the announcement should be crated.
     * @param announcement The announcement object including the data of the new announcement.
     * @return The newly created announcement object.
     * @throws ServerException If the authorization of the requestor fails or the requestor isn't allowed to perform
     * the operation. Furthermore, invalid announcement data and a failure of the database also causes a
     * ServerException.
     */
    public Announcement createAnnouncement(String accessToken, int channelId, Announcement announcement) throws
            ServerException {
        // Perform checks on the received data. If the data isn't accurate the announcement can't be created.
        if (announcement.getText() == null || announcement.getPriority() == null || announcement.getTitle() == null) {
            logger.error(LOG_SERVER_EXCEPTION, 400, ANNOUNCEMENT_DATA_INCOMPLETE, "Incomplete announcement data.");
            throw new ServerException(400, ANNOUNCEMENT_DATA_INCOMPLETE);
        } else if (announcement.getText().length() > MESSAGE_MAX_LENGTH) {
            logger.error(LOG_SERVER_EXCEPTION, 400, ANNOUNCEMENT_INVALID_TEXT, "Announcement text to long.");
            throw new ServerException(400, ANNOUNCEMENT_INVALID_TEXT);
        } else if (announcement.getTitle().length() > ANNOUNCEMENT_TITLE_MAX_LENGTH) {
            logger.error(LOG_SERVER_EXCEPTION, 400, ANNOUNCEMENT_INVALID_TITLE, "Announcement title to long.");
            throw new ServerException(400, ANNOUNCEMENT_INVALID_TITLE);
        }

        // Check if requestor is a valid moderator and responsible for the channel.
        Moderator moderatorDB = verifyResponsibleModerator(accessToken, channelId);

        // Set creation date, author and channel id of the announcement.
        announcement.computeCreationDate();
        announcement.setAuthorModerator(moderatorDB.getId());
        announcement.setChannelId(channelId);

        try {
            channelDBM.storeAnnouncement(announcement);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
        return announcement;
    }
}
