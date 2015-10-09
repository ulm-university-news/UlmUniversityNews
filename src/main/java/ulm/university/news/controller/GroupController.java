package ulm.university.news.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.*;
import ulm.university.news.data.enums.GroupType;
import ulm.university.news.data.enums.Priority;
import ulm.university.news.data.enums.TokenType;
import ulm.university.news.manager.database.GroupDatabaseManager;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.MessageNumberAlreadyExistsException;
import ulm.university.news.util.exceptions.ServerException;

import java.util.List;
import java.util.Random;

import static ulm.university.news.util.Constants.*;

/**
 * The GroupController handles requests concerning the group resources and the corresponding sub-resources. The
 * sub-resources of the group resource are options and conversations. The GroupController provides methods to
 * retrieve, create, update or delete group resources and their sub-resources.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class GroupController extends AccessController {

    /** An instance of the Logger class which performs logging for the GroupController class. */
    private static final Logger logger = LoggerFactory.getLogger(GroupController.class);

    // Error messages:
    /** A standard error message which can be used if the request is rejected due to an invalid token. */
    private static final String INVALID_TOKEN_ERROR_MSG =
            "To perform this operation a valid access token needs to be provided.";
    /** A standard error message which can be used if the requestor is a moderator and not a user and the operation
     * can only be performed by users. */
    private static final String MODERATOR_FORBIDDEN_ERROR_MSG =
            "Moderator is not allowed to perform the requested  operation.";

    /** Instance of the GroupDatabaseManager class. */
    private GroupDatabaseManager groupDBM = new GroupDatabaseManager();

    /**
     * Creates an instance of the GroupController class.
     */
    public GroupController(){

    }

    /**
     * Creates a new group. The data of the new group is passed within the group object. If the creation process is
     * successful, the created group is returned.
     *
     * @param accessToken The access token of the requestor.
     * @param group The group object which contains the data for the new group.
     * @return The created group with all corresponding data.
     * @throws ServerException If the creation of the group fails due to database failure or invalid data. If the
     * requestor is not allowed to perform the operation, a ServerException is thrown as well.
     */
    public Group createGroup(String accessToken, Group group) throws ServerException {
        // Check if the received data is valid.
        if (group == null || group.getName() == null || group.getGroupType() == null || group.getPassword() == null) {
            String errMsg = "Incomplete data record. The given group object is " + group + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_DATA_INCOMPLETE, errMsg);
            throw new ServerException(400, GROUP_DATA_INCOMPLETE);
        } else if (!group.getName().matches(NAME_PATTERN)) {
            String errMsg = "Invalid group name. The given name is " + group.getName() + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_NAME, errMsg);
            throw new ServerException(400, GROUP_INVALID_NAME);
        } else if (!group.getPassword().matches(PASSWORD_HASH_PATTERN)) {
            String errMsg = "Invalid group password.";
            logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_PASSWORD, errMsg);
            throw new ServerException(400, GROUP_INVALID_PASSWORD);
        } else if (group.getDescription() != null && !group.getDescription().matches(DESCRIPTION_PATTERN)) {
            String errMsg = "Invalid description for group. Probably the description exceeded the size or the " +
                    "description contains any special chars which are not supported. The size of the description " +
                    "is: " + group.getDescription().length() + ". The description is: " + group.getDescription()
                    + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_DESCRIPTION, errMsg);
            throw new ServerException(400, GROUP_INVALID_DESCRIPTION);
        } else if (group.getTerm() != null && !group.getTerm().matches(TERM_PATTERN)) {
            String errMsg = "Invalid term. The given term is " + group.getTerm() + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_TERM, errMsg);
            throw new ServerException(400, GROUP_INVALID_TERM);
        }

        // Check if the requestor is a valid user. Only users are allowed to perform this operation.
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with id {}, requests to create a new group.", requestor.getId());

        // Check if the group admin is set correctly.
        if (group.getGroupAdmin() != requestor.getId()) {
            logger.warn("The Id of the groupAdmin ({}) and the requestor don't match. The user who makes the creation" +
                    " request for the group should be entered as the group admin. The group admin is set to the " +
                    "id of the requestor: {}.", group.getGroupAdmin(), requestor.getId());
            group.setGroupAdmin(requestor.getId());
        }

        // Prepare the password for being stored in the database.
        group.encryptPassword();

        //Compute the creation date of the group and also set it as the modification date.
        group.computeCreationDate();
        group.setModificationDate(group.getCreationDate());

        try {
            // Store group in database.
            groupDBM.storeGroup(group);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Password should not be returned in response.
        group.setPassword(null);

        return group;
    }

    /**
     * Returns a list of groups. Search parameters, the name and the type of the group, can be used to influence the
     * result of the query. If the search parameters are set, the method will only return groups which contain the
     * the specified name string in their name or groups which are of a given type.
     *
     * @param accessToken The access token of the requestor.
     * @param groupName The search parameter for the name of the group.
     * @param groupType The search parameter for the type of the group.
     * @return A list of groups. The list can also be empty.
     * @throws ServerException If the requestor is not allowed to perform the operation or the execution fails due to
     * a database failure.
     */
    public List<Group> getGroups(String accessToken, String groupName, GroupType groupType) throws ServerException {
        List<Group> groups;
        /* Verify access token and check whether the requestor is allowed to perform the operation.
           Users and system administrators can perform this operation. */
        TokenType tokenType = verifyAccessToken(accessToken);
        if(tokenType == TokenType.INVALID){
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, INVALID_TOKEN_ERROR_MSG);
            throw new ServerException(401, TOKEN_INVALID);
        }
        if(tokenType == TokenType.MODERATOR && !isAdministrator(accessToken)) {
            logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, MODERATOR_FORBIDDEN_ERROR_MSG);
            throw new ServerException(403, MODERATOR_FORBIDDEN);
        }
        logger.debug("Groups are requested by a valid user.");

        try {
            // Get the groups from the database.
            groups = groupDBM.getGroups(groupName, groupType);
            logger.info("Returns list of groups with size {}.", groups.size());

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return groups;
    }

    /**
     * Returns the group which is identified by the specified id. It can be defined whether the group object should
     * contain a list of all participants of this group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group which should be returned.
     * @param withParticipants Indicates whether the group object should contain a list of all participants of the
     *                         group.
     * @return The group object.
     * @throws ServerException If the requestor is not allowed to perform the operation or the execution fails due to
     * a database failure.
     */
    public Group getGroup(String accessToken, int groupId, boolean withParticipants) throws ServerException {
        Group group;
        /* Verify access token and check whether the requestor is allowed to perform the operation.
           Users and system administrators can perform this operation. */
        TokenType tokenType = verifyAccessToken(accessToken);
        if (tokenType == TokenType.INVALID) {
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, INVALID_TOKEN_ERROR_MSG);
            throw new ServerException(401, TOKEN_INVALID);
        }
        if (tokenType == TokenType.MODERATOR && !isAdministrator(accessToken)) {
            logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, MODERATOR_FORBIDDEN_ERROR_MSG);
            throw new ServerException(403, MODERATOR_FORBIDDEN);
        }
        logger.debug("The group with id {} is requested by a valid user. WithParticipants parameter is {}.", groupId,
                withParticipants);

        // Get the group from the database.
        group = getGroup(groupId, withParticipants);
        // The password should not be returned to the requestor, so set it to null.
        group.setPassword(null);

        return group;
    }

    /**
     * Performs an update on the data of the group which is identified by the specified id. The group object, which is
     * generated from the request, contains the fields which should be updated and the new values. As far as no data
     * conditions are harmed, the fields will be updated in the database.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group which should be updated.
     * @param group The group object which contains the data from the request.
     * @return Returns an updated version of the group object.
     * @throws ServerException If the new data values harm certain conditions, the user is not authorized or
     * doesn't have the required permissions and if a database failure occurs.
     */
    public Group changeGroup(String accessToken, int groupId, Group group) throws ServerException {
        if(group == null){
            String errMsg = "No valid data sent with patch request.";
            logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_DATA_INCOMPLETE, errMsg);
            throw new ServerException(400, GROUP_DATA_INCOMPLETE);
        }

        // Check if the requestor is a valid user. Only users (here the group administrator) are allowed to perform
        // this operation.
        User requestor = verifyUserAccess(accessToken);

        // Get the data of the group from the database. The group should already contain the list of participants.
        Group groupDB = getGroup(groupId, true);
        // Check if the user is allowed to execute the update operation.
        if(!groupDB.isGroupAdmin(requestor.getId())){
            String errMsg = "The user with id " + requestor.getId() + " is not the group administrator of this group." +
                    " The user is thus not allowed to change the group data.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        try {
            // Determine what needs to be updated and update the corresponding fields in the database.
            groupDB = updateGroup(group, groupDB);
            groupDBM.updateGroup(groupDB);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        List<User> participants = groupDB.getParticipants();
        // TODO send notification to participants

        // Don't return the list of participants in the response.
        groupDB.setParticipants(null);
        // Don't return the password in the response.
        groupDB.setPassword(null);

        return groupDB;
    }

    /**
     * Compares the group object with the received data from the request with the group object taken from the
     * database. Updates the database object with the new data which has been received through the request. Note that
     * some fields cannot be changed, so if some changes to these fields are described, they will be ignored.
     *
     * @param group The group object which contains the data from the request.
     * @param groupDB The group object which contains the data from the database and a list of its participants. The
     *                list of participants needs to be set, otherwise not all conditions can be checked successfully.
     * @return Returns an updated version of the group object taken from the database.
     * @throws ServerException If some data based conditions are harmed.
     */
    private Group updateGroup(Group group, Group groupDB) throws ServerException {
        String newName = group.getName();
        if(newName != null){
            // Update the name if conditions are met.
            if(newName.matches(NAME_PATTERN)){
                groupDB.setName(newName);
            }
            else{
                String errMsg = "Invalid group name. The given name is " + group.getName() + ".";
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_NAME, errMsg);
                throw new ServerException(400, GROUP_INVALID_NAME);
            }
        }

        String newDescription = group.getDescription();
        if(newDescription != null){
            // Update the description if conditions are met.
            if(newDescription.matches(DESCRIPTION_PATTERN)){
                groupDB.setDescription(newDescription);
            }
            else{
                String errMsg = "Invalid description for group. Probably the description exceeded the size or the " +
                        "description contains any special chars which are not supported. The size of the description " +
                        "is: " +group.getDescription().length()+ ". The description is: " +group.getDescription()+ ".";
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_DESCRIPTION, errMsg);
                throw new ServerException(400, GROUP_INVALID_DESCRIPTION);
            }
        }

        String newPassword = group.getPassword();
        if(newPassword != null){
            // Update the password if conditions are met.
            if(newPassword.matches(PASSWORD_HASH_PATTERN)){
                groupDB.setPassword(newPassword);
                // Prepare the password for the storing in the database.
                groupDB.encryptPassword();
            }
            else{
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_PASSWORD, "Invalid group password.");
                throw new ServerException(400, GROUP_INVALID_PASSWORD);
            }
        }

        String newTerm = group.getTerm();
        if(newTerm != null){
            // Update the term if conditions are met.
            if(newTerm.matches(TERM_PATTERN)){
                groupDB.setTerm(newTerm);
            }
            else{
                String errMsg = "Invalid term. The given term is " + group.getTerm() + ".";
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_TERM, errMsg);
                throw new ServerException(400, GROUP_INVALID_TERM);
            }
        }

        int newGroupAdmin = group.getGroupAdmin();
        if(newGroupAdmin != 0){
            // Update the group admin if conditions are met.
            if(groupDB.isValidParticipant(newGroupAdmin)){
                groupDB.setGroupAdmin(newGroupAdmin);
            }
            else{
                String errMsg = "Invalid group admin. The given id for the new group admin represents a user who is " +
                        "no active participant of the group and thus can't be set as the group admin of this group. " +
                        "The id is " + newGroupAdmin + ".";
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_GROUP_ADMIN, errMsg);
                throw new ServerException(400, GROUP_INVALID_GROUP_ADMIN);
            }
        }

        // Update the modification date of the group resource.
        groupDB.updateModificationDate();

        return groupDB;
    }

    /**
     * Deletes the group with the specified id. All corresponding sub-resources like conversations and ballots are
     * deleted as well.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group which should be deleted.
     * @throws ServerException If the requestor is not allowed to delete the group or the deletion has failed due to
     * database failure. The exception is also thrown if the group with the specified id doesn't exist.
     */
    public void deleteGroup(String accessToken, int groupId) throws ServerException {
        /* Verify access token. Only users (the group administrator) and system administrators are allowed to delete a
           group. */
        TokenType tokenType = verifyAccessToken(accessToken);
        if(tokenType == TokenType.INVALID){
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, INVALID_TOKEN_ERROR_MSG);
            throw new ServerException(401, TOKEN_INVALID);
        }

        // Request the affected group. The group should contain a list of all its participants.
        Group groupDB = getGroup(groupId, true);

        try {
            // Check whether the requestor has the permission to delete the group.
            if(tokenType == TokenType.USER){
                // Note that user cannot be null here as the access token has been verified.
                User user = userDBM.getUserByToken(accessToken);
                logger.info("User with id {} wants to delete the group with id {}.", user.getId(), groupId);

                // Check if user is group administrator of this group.
                if(!groupDB.isGroupAdmin(user.getId())){
                    String errMsg = "The user with id "+ user.getId() + " is not the group administrator of this " +
                            "group. The user is thus not allowed to delete the group.";
                    logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
                    throw new ServerException(403, USER_FORBIDDEN);
                }
            }
            else if (tokenType == TokenType.MODERATOR) {
                Moderator moderator = moderatorDBM.getModeratorByToken(accessToken);
                logger.info("Moderator with id {} wants to delete the group with id {}.", moderator.getId(),
                        groupId);
                // Besides group administrators, only administrators have the permission to perform this operation.
                if (!moderator.isAdmin()) {
                    logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, MODERATOR_FORBIDDEN_ERROR_MSG);
                    throw new ServerException(403, MODERATOR_FORBIDDEN);
                }
            }

            // Delete the group and all corresponding resources.
            groupDBM.deleteGroup(groupId);

        }catch (DatabaseException e){
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Get all participants of the group. Notify also the requestor?
        List<User> participants = groupDB.getParticipants();
        // TODO notify participants about deleted group
    }

    /**
     * Adds a user as a participant to a group. The user who is intended to join the group is identified via the
     * access token. This implies that only the requestor can join a group, it is impossible to specify another user
     * id than its own. The requestor also needs to provide the password for the group in order to join it.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group.
     * @param password The password which is read from the request.
     * @throws ServerException If the requestor doesn't have the required permissions, the group is not found or the
     * execution fails due to a database failure.
     */
    public void addParticipant(String accessToken, int groupId, String password) throws ServerException {
        // Check if the requestor is a valid user. Only users are allowed to perform this operation.
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with id {}, wants to join the group with id {}.", requestor.getId()
                , groupId);

        // Request the group from the database. The group object should already contain a list of its participants.
        Group groupDB = getGroup(groupId, true);

        // Check if the user has provided the correct password in order to join the group.
        boolean verified = groupDB.verifyPassword(password);
        if(!verified){
            String errMessage = "The user didn't provide the correct password to be able to join the group.";
            logger.error(LOG_SERVER_EXCEPTION, 403, GROUP_INCORRECT_PASSWORD, errMessage);
            throw new ServerException(403, GROUP_INCORRECT_PASSWORD);
        }

        try {
            // If the password is verified, the user can be added to the group.
            groupDBM.addParticipantToGroup(groupId, requestor.getId());

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Get the participants of the group. Note that in this list the new participant is not contained.
        List<User> participants = groupDB.getParticipants();

        // TODO Send a notification to the participants to notify them about the new user.
    }

    /**
     * Returns all participants of the group which is identified by the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group.
     * @return A list of users who are participants of the group. The list can also be empty.
     * @throws ServerException If the requestor doesn't have the required permissions to perform the operation or the
     * execution fails due to a database failure.
     */
    public List<User> getParticipants(String accessToken, int groupId) throws ServerException {
        List<User> users = null;
        // Check if the requestor is a valid user. Only users are allowed to perform this operation.
        User requestor = verifyUserAccess(accessToken);
        logger.debug("The requestor, i.e. the user with id {}, requests the participants of group with id {}.",
                requestor.getId(), groupId);

        // Check if group exists. If not, the request is rejected.
        verifyGroupExistenceViaDB(groupId);

        // Check if user is active participant of the group. If he isn't, the request is rejected.
        verifyParticipationInGroupViaDB(groupId, requestor.getId());

        try {
            // Get the participants from the database.
            users = groupDBM.getParticipants(groupId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Set the push access token and the platform to null, so they are not returned to the requestor.
        for(User user : users){
            user.setPushAccessToken(null);
            user.setPlatform(null);
        }

        return users;
    }

    /**
     * Removes a participant from the group. The user which is identified by the specified id is removed as an active
     * participant of the group. However, the user remains in the list of the participants as an inactive participant
     * . The user needs to remain in the list to be able to resolve possible dependencies.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group.
     * @param participantId The id of the user who should be removed as a participant from the group.
     * @throws ServerException If the requestor is not allowed to perform the operation, the group or the participant
     * are not found or the execution fails due to a database failure.
     */
    public void deleteParticipant(String accessToken, int groupId, int participantId) throws ServerException {
        // Check if the requestor is a valid user. Only users are allowed to perform this operation.
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with id {}, tries to remove the user with id {} from the group with" +
                " id {}.", requestor.getId(), participantId, groupId);

        // Request the group object. The group object should already contain a list of the participants.
        Group groupDB = getGroup(groupId, true);
        // Check if the user which is identified by the participantId is an active participant of the group.
        boolean activeParticipant = groupDB.isValidParticipant(participantId);
        if(!activeParticipant){
            String errMsg = "The user with the id " + participantId + " is not found in the group with id " +
                    groupId + ". The user is not an active participant of the group and thus can't be removed " +
                    "from the participant list.";
            logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_PARTICIPANT_NOT_FOUND, errMsg);
            throw new ServerException(404, GROUP_PARTICIPANT_NOT_FOUND);
        }

        /* Check if the requestor is the group administrator of this group. The group administrator is not
           allowed to leave the group. */
        boolean isAdmin = groupDB.isGroupAdmin(requestor.getId());
        if(requestor.getId() == participantId && isAdmin){
            String errMsg = "The group administrator requests to leave the group. The request is rejected as the " +
                    "group administrator is not allowed to leave the group. The id of the requestor is " +
                    requestor.getId() + ".";
            logger.error(LOG_SERVER_EXCEPTION, 403, GROUP_ADMIN_NOT_ALLOWED_TO_EXIT, errMsg);
            throw new ServerException(403, GROUP_ADMIN_NOT_ALLOWED_TO_EXIT);
        }

        /* Check if the requestor has another id than the one specified in the request for the participant that
           should be removed. A participant can only remove himself from the group, i.e. leave the group. The
           group administrator exclusively is allowed to remove other participants from the group. */
        if(requestor.getId() != participantId && !isAdmin){
            String errMsg = "The user with id " + requestor.getId() + " requested to remove the user with the id " +
                    participantId + " from the group. The request is rejected as the requestor is not the group " +
                    "administrator of the group.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        try {
            // Perform the removal. The user will be set to inactive for the group.
            groupDBM.removeParticipantFromGroup(groupId, participantId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Notify participants depending on whether the user has left or the user was removed from the group.
        List<User> participants = groupDB.getParticipants();
        if(participantId == requestor.getId()){
            logger.info("The user with id {} has left the group with id {}.", requestor.getId(), groupId);
            // TODO send notification with type GROUP_PARTICIPANT_LEFT
            // TODO remove the participant with participantId from the participants list first? -> remove him
        }
        else{
            logger.info("The user with id {} has been removed from the group with id {} by the group " +
                    "administrator", participantId, groupId);
            // TODO send notification with type GROUP_PARTICIPANT_REMOVED
        }

        // Update all ballots and conversations for which the participant is the administrator.
        updateConversationAndBallotAdmin(groupDB, participantId);
    }

    /**
     * A helper method which updates the administrators of conversations and ballots which have been administered by
     * a specific participant. The administrator of the group is set as the new administrator of the conversations
     * and ballots.
     *
     * @param groupDB The group object of the affected group.
     * @param participantId The id of the participant whose ballots and conversations should get a new administrator.
     * @throws ServerException If the updates fail due to a database failure.
     */
    private void updateConversationAndBallotAdmin(Group groupDB, int participantId) throws ServerException {
        logger.info("Update the conversation and ballot administrators of those ballots and conversations that are " +
                "administered by the user with id {} in group with id {}.", participantId, groupDB.getId());

        // Get the administrator of the group.
        int adminId = groupDB.getGroupAdmin();
        // Get the participants of the group.
        List<User> participants = groupDB.getParticipants();

        try {
            // Request all conversations for which the participant is listed as the administrator.
            List<Conversation> conversations = groupDBM.getConversations(groupDB.getId(), participantId);
            /* Set the group administrator as the new administrator of all conversations which have been administered
               by the participant before. */
            for (Conversation conversation : conversations) {
                conversation.setAdmin(adminId);
                // Update the conversation data record in the database.
                groupDBM.updateConversation(conversation);
                logger.debug("User with id {} is now set as the administrator for the conversation with id {}.",
                        adminId, conversation.getId());

                // Send notification about conversation update.
                // TODO send notification
            }

            // Request all ballots for which the participant is listed as the administrator.
            List<Ballot> ballots = groupDBM.getBallots(groupDB.getId(), participantId);
            /* Set the group administrator as the new administrator of all ballots which have been administered
               by the participant before. */
            for (Ballot ballot : ballots){
                ballot.setAdmin(adminId);
                // Update the ballot data record in the database.
                groupDBM.updateBallot(ballot);
                logger.debug("User with id {} is now set as the administrator for the ballot with id {}.", adminId,
                        ballot.getId());

                // Send notification about ballot update.
                // TODO send notification
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * Creates a new ballot in the group with the specified id. The data of the ballot is defined in the ballot object.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group for which the ballot should be created.
     * @param ballot The ballot object containing the ballot data.
     * @return The ballot object with the data of the created ballot resource.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group is not found or
     * the execution fails due to a database failure.
     */
    public Ballot createBallot(String accessToken, int groupId, Ballot ballot) throws ServerException {
        // Validate the received data.
        if(ballot == null || ballot.getTitle() == null || ballot.getMultipleChoice() == null ||
                ballot.getPublicVotes() == null){
            String errMsg = "Incomplete data record. The given ballot object is " + ballot + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, BALLOT_DATA_INCOMPLETE, errMsg);
            throw new ServerException(400, BALLOT_DATA_INCOMPLETE);
        }
        else if(!ballot.getTitle().matches(NAME_PATTERN)){
            String errMsg = "Invalid ballot title. The given title is " + ballot.getTitle() + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, BALLOT_INVALID_TITLE, errMsg);
            throw new ServerException(400, BALLOT_INVALID_TITLE);
        }
        else if(ballot.getDescription() != null && !ballot.getDescription().matches(DESCRIPTION_PATTERN)){
            String errMsg = "Invalid description for ballot. Probably the description exceeded the size or the " +
                    "description contains any special chars which are not supported. The size of the description " +
                    "is: " + ballot.getDescription().length() + ". The description is: '" + ballot.getDescription()
                    + "'.";
            logger.error(LOG_SERVER_EXCEPTION, 400, BALLOT_INVALID_DESCRIPTION, errMsg);
            throw new ServerException(400, BALLOT_INVALID_DESCRIPTION);
        }

        // Check if the requestor is a valid user. Only users are allowed to perform this operation.
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with id {}, tries to create a new ballot for the group with id {}.",
                requestor.getId(), groupId);

        // Request the affected group from the database. The group should already contain the list of participants.
        Group groupDB = getGroup(groupId, true);
        // Check if the requestor is an active participant of the group. If not, discard the request.
        boolean valid = groupDB.isValidParticipant(requestor.getId());
        if(!valid){
            String errMsg = "The requestor, i.e. the user with id " + requestor.getId() + ", is not an active " +
                    "participant of the group. The user is thus not allowed to create a ballot for this group.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        // Perform further authorization checks depending on the group type.
        GroupType groupType = groupDB.getGroupType();
        if(groupType == GroupType.TUTORIAL){
            // In a tutorial group, only the tutor (group administrator) is allowed to create a ballot.
            if(!groupDB.isGroupAdmin(requestor.getId())){
                String errMsg = "The requestor, i.e. the user with id " + requestor.getId() + ", is not the tutor" +
                        " (group administrator) of the tutorial group with id " + groupId + ". The user is thus " +
                        "not allowed to create a ballot for this group.";
                logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
                throw new ServerException(403, USER_FORBIDDEN);
            }
        }

        // Set the requestor id as the ballot administrator for this ballot.
        ballot.setAdmin(requestor.getId());

        try {
            // Store the ballot in the database.
            groupDBM.storeBallot(ballot, groupId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Notify participants about a new ballot.
        List<User> participants = groupDB.getParticipants();
        // TODO send notification

        return ballot;
    }

    /**
     * Returns a list of ballots which belong to the given group with the specified id. It can be defined whether the
     * ballot objects should contain their corresponding sub-resources like the options and the voters for each option.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group.
     * @param subresources Indicates whether the ballot objects should contain the corresponding options and a list
     *                     of voters per option who voted for each option.
     * @return A list of ballot objects. The list can also be empty.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group is not found or
     * the ballots can not be retrieved due to a database failure.
     */
    public List<Ballot> getBallots(String accessToken, int groupId, boolean subresources) throws ServerException {
        List<Ballot> ballots = null;
        /* Check if requestor is a valid user. Only users (more precisely participants of the group) are allowed to
           perform this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.debug("The requestor, i.e. the user with id {}, requests all ballots for the group with id {}.",
                requestor.getId(), groupId);

        // Check if the requestor is a valid participant of the group. If not, the request is rejected.
        verifyParticipationInGroupViaDB(groupId, requestor.getId());

        // Check if group exists.
        verifyGroupExistenceViaDB(groupId);

        try {
            // Get the ballots from the database.
            ballots = groupDBM.getBallots(groupId, subresources);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return ballots;
    }

    /**
     * Returns the ballot which is identified by the specified id and belongs to the group with the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot.
     * @return The ballot object containing the data of the ballot.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group or the ballot are
     * not found or the ballot could not be retrieved due to a database failure.
     */
    public Ballot getBallot(String accessToken, int groupId, int ballotId) throws ServerException {
        Ballot ballot = null;
        /* Check if requestor is a valid user. Only users (more precisely participants of the group) are allowed to
           perform this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.debug("The requestor, i.e. the user with id {}, requests the ballot with the id {} from the group with" +
                " the id {}.", requestor.getId(), ballotId, groupId);

        // Check if the requestor is a valid participant of the group. If not, the request is rejected.
        verifyParticipationInGroupViaDB(groupId, requestor.getId());

        // Check if group exists. If not, the request is rejected.
        verifyGroupExistenceViaDB(groupId);

        // Get the ballot from the database.
        ballot = getBallot(groupId,ballotId);

        return ballot;
    }

    /**
     * Performs an update on the data of the ballot which is identified by the specified id. The ballot object, which
     * is generated from the request, contains the fields which should be updated and the new values. As far as no data
     * conditions are harmed, the fields will be updated in the database.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the affected ballot.
     * @param ballot The ballot object which contains the data values from the request.
     * @return An updated version of the ballot object taken from the database.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group or the ballot are
     * not found or the update could not be performed due to a database failure.
     */
    public Ballot changeBallot(String accessToken, int groupId, int ballotId, Ballot ballot) throws ServerException {
        if(ballot == null){
            logger.error(LOG_SERVER_EXCEPTION, 400, BALLOT_DATA_INCOMPLETE, "No valid data sent with patch request.");
            throw new ServerException(400, BALLOT_DATA_INCOMPLETE);
        }

        /* Check if requestor is a valid user. The user needs to be an active participant of the group and needs to
           be the administrator of the affected ballot. */
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with id {}, requests to update the ballot with id {} in the group " +
                "with id {}.", requestor.getId(), ballotId, groupId);

        // First, get the group from the database. The group should already contain a list of all participants.
        Group groupDB = getGroup(groupId, true);
        // Check if the requestor is an active participant of the group. Otherwise reject the request.
        if(!groupDB.isValidParticipant(requestor.getId())){
            String errMsg = "The user with id " + requestor.getId() + " is not an active participant of the group" +
                    " with id " + groupId + ". The user is not allowed to change the ballot, the request is rejected.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        // Second, get the ballot object from the database.
        Ballot ballotDB = getBallot(groupId, ballotId);
        // Check if the requestor is the administrator of the ballot. Otherwise reject the request.
        if(!ballotDB.isBallotAdmin(requestor.getId())){
            String errMsg = "The requestor, i.e. the user with id " + requestor.getId() + ", is not the " +
                    "administrator of the ballot with id " + ballotId + ". The request is rejected.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        try {
            // Determine what needs to be updated and update the corresponding fields in the database.
            ballotDB = updateBallot(ballot, ballotDB);
            groupDBM.updateBallot(ballotDB);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Notify the participants of the group about the changed ballot. Also the requestor?
        List<User> participants = groupDB.getParticipants();
        // TODO notify participants.

        return ballotDB;
    }

    /**
     * Compares the ballot object with the received data from the request with the ballot object taken from the
     * database. Updates the database object with the new data which has been received through the request. Note that
     * some fields cannot be changed, so if some changes to these fields are described, they will be ignored.
     *
     * @param ballot The ballot object which contains the data from the request.
     * @param ballotDB The ballot object which contains the data from the database.
     * @return Returns an updated version of the ballot object taken from the database.
     * @throws ServerException If some data based conditions were harmed.
     */
    private Ballot updateBallot(Ballot ballot, Ballot ballotDB) throws ServerException {
        String newTitle = ballot.getTitle();
        if(newTitle != null){
            // Update the title if conditions are met.
            if(newTitle.matches(NAME_PATTERN)){
                ballotDB.setTitle(newTitle);
            }
            else{
                String errMsg = "Invalid ballot title. The given title is " + ballot.getTitle() + ".";
                logger.error(LOG_SERVER_EXCEPTION, 400, BALLOT_INVALID_TITLE, errMsg);
                throw new ServerException(400, BALLOT_INVALID_TITLE);
            }
        }

        String newDescription = ballot.getDescription();
        if(newDescription != null){
            // Update the description if conditions are met.
            if(newDescription.matches(DESCRIPTION_PATTERN)){
                ballotDB.setDescription(newDescription);
            }
            else{
                String errMsg = "Invalid description for ballot. Probably the description exceeded the size or the " +
                        "description contains any special chars which are not supported. The size of the description " +
                        "is: " +ballot.getDescription().length()+ ". The description is: '" +ballot.getDescription()+
                        "'.";
                logger.error(LOG_SERVER_EXCEPTION, 400, BALLOT_INVALID_DESCRIPTION, errMsg);
                throw new ServerException(400, BALLOT_INVALID_DESCRIPTION);
            }
        }

        // Update the closed status if necessary.
        if(ballot.getClosed() != null) {
            boolean closed = ballot.getClosed();
            if (closed && !ballotDB.getClosed()) {
                logger.info("The ballot with id {} is getting closed.", ballotDB.getId());
                ballotDB.setClosed(true);
            } else if (!closed && ballotDB.getClosed()) {
                logger.info("The ballot with id {} is getting opened again.", ballotDB.getId());
                ballotDB.setClosed(false);
            }
        }
        return ballotDB;
    }

    /**
     * Deletes the ballot resource which is identified by the specified id and belongs to the given group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot which should be deleted.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group or the ballot are
     * not found or the deletion fails due to a database failure.
     */
    public void deleteBallot(String accessToken, int groupId, int ballotId) throws ServerException {
        /* Check if requestor is a valid user. The user needs to be an active participant of the group and needs to
           be the administrator of the affected ballot. */
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with id {}, tries to delete the ballot with id {} from the group " +
                "with id {}.", requestor.getId(), ballotId, groupId);

        // First, get the group from the database. The group should already contain a list of all participants.
        Group groupDB = getGroup(groupId, true);
        // Check if the requestor is an active participant of the group. Otherwise reject the request.
        if(!groupDB.isValidParticipant(requestor.getId())){
            String errMsg = "The user with id " + requestor.getId() + " is not an active participant of the group" +
                    " with id " + groupId + ". The user is not allowed to delete the ballot, the request is rejected.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        // Second, get the ballot object from the database.
        Ballot ballotDB = getBallot(groupId, ballotId);
        // Check if the requestor is the administrator of the ballot. Otherwise reject the request.
        if(!ballotDB.isBallotAdmin(requestor.getId())){
            String errMsg = "The requestor, i.e. the user with id " + requestor.getId() + ", is not the " +
                    "administrator of the ballot with id " + ballotId + ". The request is rejected.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }
        logger.info("The administrator of the ballot with id {}, i.e. the user identified by id {}, requests to " +
                "delete the ballot. The ballot will be deleted.", ballotId, requestor.getId());

        try {
            // Delete the ballot.
            groupDBM.deleteBallot(groupId, ballotId);
        } catch (DatabaseException e){
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Notify the participants of the group about the deleted ballot. Also the requestor?
        List<User> participants = groupDB.getParticipants();
        // TODO send notification
    }

    /**
     * Creates a new option for the ballot which is identified by the specified id. The data for the option is
     * provided within the option object.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the option belongs.
     * @param option The option object containing the data for the new option.
     * @return The option object containing all data of the new resource.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group or the ballot are
     * not found, the data of the option is invalid or the option could not be stored in the database due to a
     * database failure.
     */
    public Option createOption(String accessToken, int groupId, int ballotId, Option option) throws ServerException {
        // Validate the received data.
        if (option == null || option.getText() == null) {
            String errMsg = "Incomplete data record. The given option is " + option + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, OPTION_DATA_INCOMPLETE, errMsg);
            throw new ServerException(400, OPTION_DATA_INCOMPLETE);
        }
        else if (option.getText().length() > OPTION_TEXT_MAX_LENGTH) {
            String errMsg = "Invalid option text. The option's text has exceeded the max length.";
            logger.error(LOG_SERVER_EXCEPTION, 400, OPTION_INVALID_TEXT, errMsg);
            throw new ServerException(400, OPTION_TEXT_MAX_LENGTH);
        }

        /* Check if requestor is a valid user. The user needs to be an active participant of the group  (implicitly
           guaranteed as the ballotAdmin needs to be a participant) and needs to be the administrator of the affected
           ballot. */
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with id {}, requests to create a new option for the ballot with id " +
                "{} in group with id {}.", requestor.getId(), ballotId, groupId);

        // Get the group from the database including the participant list. Reject the request if group not found.
        Group groupDB = getGroup(groupId, true);

        // Get the ballot from the database. Reject the request if the ballot is not found.
        Ballot ballotDB = getBallot(groupId, ballotId);

        // Check if the ballot is closed. If its closed, reject the request.
        if(ballotDB.getClosed()){
            String errMsg = "The ballot with id " + ballotId + " is closed. There are thus no changes allowed.";
            logger.error(LOG_SERVER_EXCEPTION, 403, BALLOT_CLOSED, errMsg);
            throw new ServerException(403, BALLOT_CLOSED);
        }

        if(!ballotDB.isBallotAdmin(requestor.getId())){
            String errMsg = "The requestor, i.e. the user with id " + requestor.getId() + ", is not the administrator" +
                    " of the ballot with id " + ballotId + ". The user is thus not allowed to create an option for " +
                    "this ballot. The request is rejected.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        try {
            // Store the option in the database.
            groupDBM.storeOption(ballotId, option);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Notify the participants of the group about the new ballot option.
        List<User> partcipants = groupDB.getParticipants();
        // TODO send notficiation with OPTION_NEW status.

        return option;
    }

    /**
     * Returns a list of options which belong to the ballot with the given id. The ballot belongs to the group which
     * is identified by the specified id.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group.
     * @param ballotId The id of the ballot for which the options should be returned.
     * @return A list of option objects. The list can also be empty.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group or the ballot are
     * not found or the retrieval of the option fails due to a database failure.
     */
    public List<Option> getOptions(String accessToken, int groupId, int ballotId) throws ServerException {
        List<Option> options = null;

        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group, is allowed to
           execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.debug("The requestor, i.e. the user with id {}, requests the options for the ballot with id {} which " +
                "belongs to the group with id {}.", requestor.getId(), ballotId, groupId);

        // Check if the group exists. If not, the request is rejected.
        verifyGroupExistenceViaDB(groupId);

        // Check if the requestor is a participant in the group. If not, the request is rejected.
        verifyParticipationInGroupViaDB(groupId, requestor.getId());

        // Check if the ballot exists within the group.
        verifyBallotExistenceViaDB(groupId, ballotId);

        try {
            options = groupDBM.getOptions(ballotId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return options;
    }

    /**
     * Returns the option with the specified id. The option belongs to the ballot which is identified by the given id
     * . The ballot belongs to the group with the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The id of the option.
     * @return Returns the option object containing the option data.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group, ballot or option
     * are not found or if the retrieval of the option fails due to a database failure.
     */
    public Option getOption(String accessToken, int groupId, int ballotId, int optionId) throws ServerException {
        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group, is allowed to
           execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.debug("The requestor, i.e. the user with id {}, requests the option with id {} for the ballot with id " +
                "{} which belongs to the group with id {}.", requestor.getId(), optionId, ballotId, groupId);

        // Check if the group exists. If not, the request is rejected.
        verifyGroupExistenceViaDB(groupId);

        // Check if the requestor is a participant in the group. If not, the request is rejected.
        verifyParticipationInGroupViaDB(groupId, requestor.getId());

        // Check if the ballot exists within the group. If not, the request is rejected.
        verifyBallotExistenceViaDB(groupId, ballotId);

        // Request the option from the database. If the option is not found, the request is rejected.
        Option option = getOption(ballotId, optionId);

        return option;
    }

    /**
     * Deletes the option with the specified id. The option belongs to the ballot with the given id which in turn
     * belongs to the defined group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The if of the option which should be deleted.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group, ballot or option
     * is not found or the deletion fails due to a database failure.
     */
    public void deleteOption(String accessToken, int groupId, int ballotId, int optionId) throws ServerException {
        /* Check if the requestor is a valid user. Only a user, i.e. the administrator of the ballot, is allowed to
           execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with id {}, requests to delete the option with id {} from the " +
                        "ballot with id {} which belongs to the group with id:{}.", requestor.getId(), optionId, ballotId,
                groupId);

        // Get the group object from the database including a list of its participants. Reject if group not found.
        Group groupDB = getGroup(groupId, true);

        // Request the ballot object. Reject if ballot is not found.
        Ballot ballotDB = getBallot(groupId, ballotId);

        // Checks if the option is a valid option of the ballot. Reject if option not found.
        verifyOptionExistenceViaDB(ballotId, optionId);

        // Check if the ballot is closed. If its closed, reject the request.
        if(ballotDB.getClosed()){
            String errMsg = "The ballot with id " + ballotId + " is closed. There are thus no changes allowed.";
            logger.error(LOG_SERVER_EXCEPTION, 403, BALLOT_CLOSED, errMsg);
            throw new ServerException(403, BALLOT_CLOSED);
        }

        // The requestor needs to be the administrator of the ballot in order to delete it.
        if(!ballotDB.isBallotAdmin(requestor.getId())){
            String errMsg = "The user with the id " + requestor.getId() + " is not the administrator of the " +
                    "ballot with id " + ballotId + ". The user is thus not allowed to delete the option.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        try {
            // Delete the option from the ballot.
            groupDBM.deleteOption(ballotId, optionId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Notify participants of the group about the deleted option.
        List<User> participants = groupDB.getParticipants();
        // TODO send notification. Type BALLOT_OPTION_REMOVED ? -> muss noch definiert werden
    }

    /**
     * Creates a new vote. The requestor votes for the option with the specified id which belongs to the given ballot
     * . The ballot in turn belongs to the given group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The id of the option for which the requestor votes.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group, ballot or option
     * are not found or the vote could not be placed due to a database failure.
     */
    public void createVote(String accessToken, int groupId, int ballotId, int optionId) throws ServerException {
        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group, is allowed to
           execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with id {}, requests to vote for the option with id {} in ballot " +
                "with id {}. The ballot belongs to the group with id {}.", requestor.getId(), optionId, ballotId,
                groupId);

        // Get the group including the list of participants from the database. Reject the request if group is not found.
        Group groupDB = getGroup(groupId, true);
        // Check if the requestor is an active participant of the group. Otherwise reject the request.
        if(!groupDB.isValidParticipant(requestor.getId())){
            String errMsg = "The user with id " + requestor.getId() + " isn't an active participant of the group " +
                    "with id " + groupId + ". The user is not allowed to vote for an option of a ballot of this group.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        // Get the ballot from the database. Reject the request if ballot is not found.
        Ballot ballotDB = getBallot(groupId, ballotId);
        // Check if the ballot is closed. If its closed, reject the request.
        if(ballotDB.getClosed()){
            String errMsg = "The ballot with id " + ballotId + " is closed. There are thus no changes allowed.";
            logger.error(LOG_SERVER_EXCEPTION, 403, BALLOT_CLOSED, errMsg);
            throw new ServerException(403, BALLOT_CLOSED);
        }

        // Verify that the option exists.
        verifyOptionExistenceViaDB(ballotId, optionId);

        try {
            // Check whether the requestor has already voted for this option.
            if(groupDBM.isValidVote(optionId, requestor.getId())){
                boolean multipleChoice = ballotDB.getMultipleChoice();
                if(!multipleChoice){
                    /* If there is no multiple choice enabled, each participant can only take exactly one vote for
                       the ballot. */
                    if(groupDBM.hasAlreadyVoted(ballotId, requestor.getId())){
                        String errMsg = "The user with id " + requestor.getId() + " has already placed a vote for an " +
                                "option within the ballot with id " + ballotId + ". No further vote can be placed.";
                        logger.error(LOG_SERVER_EXCEPTION, 403, BALLOT_USER_HAS_ALREADY_VOTED, errMsg);
                        throw new ServerException(403, BALLOT_USER_HAS_ALREADY_VOTED);
                    }
                }

                // Store the vote in the database.
                groupDBM.storeVote(optionId, requestor.getId());
            }
            else{
                // TODO are we sending this error message?
                String errMsg = "The user with id " + requestor.getId() + " has already voted for option with id " +
                        optionId;
                logger.error(LOG_SERVER_EXCEPTION, 409, OPTION_USER_HAS_ALREADY_VOTED, errMsg);
                throw new ServerException(409, OPTION_USER_HAS_ALREADY_VOTED);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Notify the participants of the group about the new vote. Also the requestor?
        List<User> participants = groupDB.getParticipants();
        // TODO notify participants with type BALLOT_OPTION_VOTE
    }

    /**
     * Returns a list of user objects which have voted for the option with the specified id. The option belongs to
     * the given ballot which in turn belongs to the defined group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The id of the option for which the voters are requested.
     * @return A list of user objects. The list can also be empty.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group, ballot or option
     * are not found or the retrieval of the voters fails due to a database failure.
     */
    public List<User> getVoters(String accessToken, int groupId, int ballotId, int optionId) throws ServerException {
        List<User> users = null;
        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group, is allowed to
           execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.debug("The requestor, i.e. the user with id {}, requests the voters for the option with id {} in the " +
                "ballot with id {}. The ballot belongs to the group with id {}.", requestor.getId(), optionId,
                ballotId, groupId);

        // Check if the group exists and the user is an active participant. If not, reject the request.
        verifyGroupExistenceViaDB(groupId);
        verifyParticipationInGroupViaDB(groupId, requestor.getId());

        // Check if the ballot exists. If not, reject the request.
        verifyBallotExistenceViaDB(groupId, ballotId);

        // Check if the option exists. If not, reject the request.
        verifyOptionExistenceViaDB(ballotId, optionId);

        try {
            // Request the voters for the option.
            users = groupDBM.getVoters(optionId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Set the push access token and the platform to null so that they are not returned to the requestor.
        for (User user : users) {
            user.setPushAccessToken(null);
            user.setPlatform(null);
        }

        return users;
    }

    /**
     * Deletes the vote from the user with the specified id for the defined option. The option belongs to the ballot
     * with the given id which in turn belongs to the defined group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The id of the option.
     * @param userId The id of the user whose vote should be removed.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group, ballot or option
     * are not found or if the vote could not be deleted due to a database failure.
     */
    public void deleteVote(String accessToken, int groupId, int ballotId, int optionId, int userId )  throws
            ServerException {
        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group, is allowed to
           execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with id {}, requests to delete the vote for the option with id {} " +
                "from the ballot with id {}. The ballot belongs to the group with id {}.", requestor.getId(),
                optionId, ballotId, groupId);

        // Check whether the userId matches the requestor's id.
        if(requestor.getId() != userId){
            String errMsg = "A user can only delete votes which have been placed by himself.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        // Get the group including the list of participants from the database. Reject the request if group is not found.
        Group groupDB = getGroup(groupId, true);
        // Check if the requestor is an active participant of the group. Otherwise reject the request.
        if(!groupDB.isValidParticipant(requestor.getId())){
            String errMsg = "The user with id " + requestor.getId() + " isn't an active participant of the group " +
                    "with id " + groupId + ". The user is not allowed to remove a vote for an option of a ballot of " +
                    "this group.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        // Get the ballot from the database. Reject the request if ballot is not found.
        Ballot ballotDB = getBallot(groupId, ballotId);
        // Check if the ballot is closed. If it is closed, reject the request.
        if(ballotDB.getClosed()){
            String errMsg = "The ballot with id " + ballotId + " is closed. There are thus no changes allowed.";
            logger.error(LOG_SERVER_EXCEPTION, 403, BALLOT_CLOSED, errMsg);
            throw new ServerException(403, BALLOT_CLOSED);
        }

        // Check if the option exists. If not, reject the request.
        verifyOptionExistenceViaDB(ballotId, optionId);

        try {
            // TODO check if the user has voted for the option?
            groupDBM.deleteVote(optionId, userId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Notify participants about the deleted vote. Also the requestor?
        List<User> participants = groupDB.getParticipants();
        // TODO send notification with type BALLOT_OPTION_VOTE_REMOVED? Needs to be defined
    }

    /**
     * Creates a new conversation in the group with the specified id. The data of the new conversation is taken from
     * the request and provided in form of a conversation object.
     *
     * @param accessToken The access token of the conversation.
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversation The conversation object containing the data for the conversation.
     * @return The conversation object containing the data of the created conversation resource.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group is not found or
     * the creation of the conversation fails due to a database failure.
     */
    public Conversation createConversation(String accessToken, int groupId, Conversation conversation) throws
            ServerException {
        // Validate the received data.
        if(conversation == null || conversation.getTitle() == null){
            String errMsg = "Incomplete data record. The given conversation object is " + conversation + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, CONVERSATION_DATA_INCOMPLETE, errMsg);
            throw new ServerException(400, CONVERSATION_DATA_INCOMPLETE);
        }
        else if(!conversation.getTitle().matches(NAME_PATTERN)){
            String errMsg = "Invalid conversation title. The given title is " + conversation.getTitle() + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, CONVERSATION_INVALID_TITLE, errMsg);
            throw new ServerException(400, CONVERSATION_INVALID_TITLE);
        }

        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group, is allowed to
           execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with id {}, requests to create a new conversation in the group with" +
                " id {}.", requestor.getId(), groupId);

        // Get the group from the database. The group should already include a list of its participants.
        Group groupDB = getGroup(groupId, true);
        // Check if the requestor is an active participant of the group. Otherwise reject the request.
        if(!groupDB.isValidParticipant(requestor.getId())){
            String errMsg = "The user with id " + requestor.getId() + " isn't an active participant of the group " +
                    "with id " + groupId + ". The user is not allowed to create a conversation for this group";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        // Set the requestor as the administrator for the new conversation.
        conversation.setAdmin(requestor.getId());

        try {
            // Store the conversation in the database.
            groupDBM.storeConversation(groupId, conversation);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Notify the participants about the new conversation. Also the requestor?
        List<User> participants = groupDB.getParticipants();
        // TODO send notification with status CONVERSATION_NEW.

        return conversation;
    }

    /**
     * Returns a list of conversation objects which belong to the group with the specified id. It can be defined
     * whether the single conversation objects should contain a list of their sub-resources, i.e. a list of the
     * conversation messages which belong to the conversation.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group for which the conversations are requested.
     * @param subresources Indicates whether the conversation objects should contain a list of their sub-resources.
     * @return A list of conversation objects. The list can also be empty.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group is not found or
     * the retrieval of the conversations fails due to a database failure.
     */
    public List<Conversation> getConversations(String accessToken, int groupId, boolean subresources) throws
            ServerException {
        List<Conversation> conversations = null;
        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group, is allowed to
           execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.debug("The requestor, i.e. the user with id {}, requests the conversations of the group with id {}.",
                requestor.getId(), groupId);

        // Check if the group exists and the user is an active participant of it. If not, reject the request.
        verifyGroupExistenceViaDB(groupId);
        verifyParticipationInGroupViaDB(groupId, requestor.getId());

        try {
            // Get the conversations from the database.
            conversations = groupDBM.getConversations(groupId, subresources);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return conversations;
    }

    /**
     * Returns the conversation object of the conversation which is identified by the specified id. The conversation
     * belongs to the group with the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation which should be retrieved.
     * @return The conversation object.
     * @throws ServerException If the requestor is not allowed to exectue the operation, the group or the
     * conversation are not found or the retrieval fails due to a database failure.
     */
    public Conversation getConversation(String accessToken, int groupId, int conversationId) throws ServerException {
        Conversation conversation = null;
        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group, is allowed to
           execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.debug("The requestor, i.e. the user with id {}, requests the conversation with id {} of the group with" +
                " id {}.", requestor.getId(), conversationId, groupId);

        // Check if the group exists and the user is an active participant of it. If not, reject the request.
        verifyGroupExistenceViaDB(groupId);
        verifyParticipationInGroupViaDB(groupId, requestor.getId());

        // Get the conversation from the database. Reject the request if the conversation is not found.
        conversation = getConversation(groupId, conversationId);

        return conversation;
    }

    /**
     * Performs an update on the data of the conversation which is identified by the specified id. The conversation
     * object, which is generated from the request, contains the fields which should be updated and the new data
     * values. As far as no data conditions are harmed, the fields will be updated in the database.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation which should be updated.
     * @param conversation The conversation object which contains the data values taken from the request.
     * @return An updated version of the conversation object taken from the database.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group or the
     * conversation are not found or the update fails due to a database failure.
     */
    public Conversation changeConversation(String accessToken, int groupId, int conversationId, Conversation
            conversation) throws ServerException {
        if(conversation == null){
            logger.error(LOG_SERVER_EXCEPTION, 400, CONVERSATION_DATA_INCOMPLETE, "No valid data sent with patch " +
                    "request.");
            throw new ServerException(400, CONVERSATION_DATA_INCOMPLETE);
        }

        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group which is also the
        administrator of the conversation, is allowed to execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with the id {}, requests to update the conversation with id {} in " +
                "the group with id {}.", requestor.getId(), conversationId, groupId);

        // First, get the group from the database. The group should already contain a list of all participants.
        Group groupDB = getGroup(groupId, true);
        // Check if the requestor is an active participant of the group. Otherwise reject the request.
        if(!groupDB.isValidParticipant(requestor.getId())){
            String errMsg = "The user with id " + requestor.getId() + " is not an active participant of the group" +
                    " with id " + groupId + ". The user is not allowed to change the conversation, the request is " +
                    "rejected.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        // Second, get the conversation from the database.
        Conversation conversationDB = getConversation(groupId, conversationId);
        // Check if the requestor is the administrator of the conversation. If not, reject the request.
        if(!conversationDB.isAdmin(requestor.getId())){
            String errMsg = "The user with id " + requestor.getId() + " is not the administrator of the conversation " +
                    "with the id " + conversationId + ". The user is thus not allowed to change the conversation.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        try {
            // Determine what needs to be updated and update the fields in the object taken from the database.
            conversationDB = updateConversation(conversation, conversationDB);
            // Update the values in the database.
            groupDBM.updateConversation(conversationDB);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Notify participants about changed conversation.
        List<User> participants = groupDB.getParticipants();
        // TODO send notification

        return conversationDB;
    }

    /**
     * Compares the conversation object with the received data from the request with the conversation object taken
     * from the database. Updates the database object with the new data which has been received through the request.
     * Note that some fields cannot be changed, so if some changes to these fields are described, they will be ignored.
     *
     * @param conversation The conversation object which contains the data from the request.
     * @param conversationDB The conversation object which contains the data from the database.
     * @return Returns an updated version of the conversation object taken from the database.
     * @throws ServerException If some data based conditions are harmed.
     */
    private Conversation updateConversation(Conversation conversation, Conversation conversationDB) throws
            ServerException {
        String newTitle = conversation.getTitle();
        if(newTitle != null){
            // Update the title if conditions are met.
            if(newTitle.matches(NAME_PATTERN)){
                conversationDB.setTitle(newTitle);
            }
            else{
                String errMsg = "Invalid conversation title. The given title is " + conversation.getTitle() + ".";
                logger.error(LOG_SERVER_EXCEPTION, 400, CONVERSATION_INVALID_TITLE, errMsg);
                throw new ServerException(400, CONVERSATION_INVALID_TITLE);
            }
        }

        Boolean closed = conversation.getClosed();
        if(closed != null){
            // Update the closed field if necessary.
            if(closed == Boolean.TRUE && conversationDB.getClosed() == Boolean.FALSE){
                logger.info("The conversation with id {} is getting closed.", conversationDB.getId());
                conversationDB.setClosed(true);
            }
            else if(closed == Boolean.FALSE && conversationDB.getClosed() == Boolean.TRUE){
                logger.info("The conversation with id {} is getting opend again.", conversationDB.getId());
                conversationDB.setClosed(false);
            }
        }

        return conversationDB;
    }

    /**
     * Deletes the conversation with the specified id which belongs to the defined group.
     *
     * @param accessToken The access token of the reqeustor.
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation that should be deleted.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group or the
     * conversation are not found or the deletion fails due to a database failure.
     */
    public void deleteConversation(String accessToken, int groupId, int conversationId) throws ServerException {
        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group which is also the
        administrator of the conversation, is allowed to execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with the id {}, requests to delete the conversation with id {} in " +
                "the group with id {}.", requestor.getId(), conversationId, groupId);

        // First, get the group from the database. The group should already contain a list of all participants.
        Group groupDB = getGroup(groupId, true);
        // Check if the requestor is an active participant of the group. Otherwise reject the request.
        if(!groupDB.isValidParticipant(requestor.getId())){
            String errMsg = "The user with id " + requestor.getId() + " is not an active participant of the group" +
                    " with id " + groupId + ". The user is not allowed to delete the conversation, the request is " +
                    "rejected.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        // Second, get the conversation from the database.
        Conversation conversationDB = getConversation(groupId, conversationId);
        // Check if the requestor is the administrator of the conversation. If not, reject the request.
        if(!conversationDB.isAdmin(requestor.getId())){
            String errMsg = "The user with id " + requestor.getId() + " is not the administrator of the conversation " +
                    "with the id " + conversationId + ". The user is thus not allowed to delete the conversation.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        try {
            groupDBM.deleteConversation(groupId, conversationId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Send notification about the deleted conversation to the participants.
        List<User> participants = groupDB.getParticipants();
        // TODO send notification
    }

    /**
     * Creates a new message for the specified conversation. The message will be sent into the conversation which is
     * identified by the given id and belongs to the defined group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation.
     * @param conversationMessage The object which contains the data of the message.
     * @return The created message resource.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group or the
     * conversation are not found or the message cannot be created due to a database failure.
     */
    public ConversationMessage createConversationMessage(String accessToken, int groupId, int conversationId,
            ConversationMessage conversationMessage) throws ServerException {
        // Validate the received data.
        if(conversationMessage == null || conversationMessage.getText() == null ||
                conversationMessage.getPriority() == null){
            String errMsg = "Incomplete data record. The given conversationMessage is " + conversationMessage + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, CONVERSATIONMESSAGE_DATA_INCOMPLETE, errMsg);
            throw new ServerException(400, CONVERSATIONMESSAGE_DATA_INCOMPLETE);
        }
        else if(conversationMessage.getText().length() > MESSAGE_MAX_LENGTH){
            String errMsg = "Invalid message text. The text exceeded the max length.";
            logger.error(LOG_SERVER_EXCEPTION, 400, CONVERSATIONMESSAGE_INVALID_TEXT, errMsg);
            throw new ServerException(400, CONVERSATIONMESSAGE_INVALID_TEXT);
        }

        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group, is allowed to
        execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.info("The requestor, i.e. the user with the id {}, requests to create a new message for the " +
                "conversation with id {} within the group with id {}.", requestor.getId(), conversationId, groupId);

        // Get the group from the database including a list of the participants.
        Group groupDB = getGroup(groupId, true);
        if(!groupDB.isValidParticipant(requestor.getId())){
            String errMsg = "The user with id " + requestor.getId() + " is not an active participant of the group " +
                    "with id " + groupId + ". The user is thus not allowed to create a new conversation message";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        // Get the conversation from the database. Reject the request if the conversation is closed.
        Conversation conversationDB = getConversation(groupId, conversationId);
        if(conversationDB.getClosed() == Boolean.TRUE){
            String errMsg = "The conversation with id " + conversationId + " is closed. No message can be sent.";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }

        // Check the priority and adjust it if necessary.
        Priority priority = conversationMessage.getPriority();
        if(priority == Priority.HIGH && groupDB.getGroupType() == GroupType.WORKING){
            // In a working group no messages can be sent with priority high.
            conversationMessage.setPriority(Priority.NORMAL);
        }
        else if(priority == Priority.HIGH && groupDB.getGroupType() == GroupType.TUTORIAL &&
                !groupDB.isGroupAdmin(requestor.getId())){
            // In a tutorial group only the group administrator (tutor) can send a message with priority high.
            conversationMessage.setPriority(Priority.NORMAL);
        }

        // Set relevant fields in the conversationMessage object.
        conversationMessage.setConversationId(conversationId);
        conversationMessage.setAuthorUser(requestor.getId());
        conversationMessage.computeCreationDate();

        int maxAttempts = 5;
        int attempts = 0;
        boolean successful = false;
        // Try to store the conversation message into the database. After a maximum of attempts, abort the method.
        while(!successful && attempts < maxAttempts){
            try {
                // Store the conversation message in the database.
                groupDBM.storeConversationMessage(conversationId, conversationMessage);
                successful = true;
            } catch (DatabaseException e) {
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
                throw new ServerException(500, DATABASE_FAILURE);
            } catch (MessageNumberAlreadyExistsException e) {
                // Try again if the storage has failed due to a duplicate message number.
                attempts++;
                // Sleep a random backoff time.
                Random random = new Random();
                int backOff = random.nextInt(500);
                try {
                    Thread.sleep(backOff);
                } catch (InterruptedException e1) {
                    logger.error("Interrupted Exception: {}.", e1.getMessage());
                }
            }
        }
        if(!successful){
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Storing of conversation message has failed due" +
                    " to duplicate message numbers.");
            throw  new ServerException(500, DATABASE_FAILURE);
        }

        // Send notification about the new conversation message to the participants.
        List<User> participants = groupDB.getParticipants();
        // TODO send notification CONVERSATION_MESSAGE_NEW

        return conversationMessage;
    }

    /**
     * Returns a list of conversation messages which belong to the conversation with the specified id. The result of
     * the method depends on the given message number. The method returns the messages of the conversation which have
     * a higher message number than the specified one.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation for which the messages are requested.
     * @param messageNumber Defines the starting message number. The method returns all messages which have a higher
     *                      message number than the one specified in this parameter.
     * @return A list of conversation messages. The list can also be empty.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group or the
     * conversation are not found or the retrieval of the messages fails due to a database failure.
     */
    public List<ConversationMessage> getConversationMessages(String accessToken, int groupId, int conversationId, int
            messageNumber) throws ServerException {
        List<ConversationMessage> messages = null;

        /* Check if the requestor is a valid user. Only a user, i.e. a participant of the group, is allowed to
        execute this operation. */
        User requestor = verifyUserAccess(accessToken);
        logger.debug("The requestor, i.e. the user with id {}, requests the messages from the conversation with id {}" +
                ". The conversation belongs to the group with id {}.", requestor.getId(), conversationId, groupId);

        // Check if the group exists and the requestor is an active participant. If not, reject the request.
        verifyGroupExistenceViaDB(groupId);
        verifyParticipationInGroupViaDB(groupId, requestor.getId());

        // Check if the conversation exists. If not, reject the request.
        verifyConversationExistenceViaDB(groupId, conversationId);

        try {
            // Request the messages from the database.
            messages = groupDBM.getConversationMessages(conversationId, messageNumber);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return messages;
    }

    /**
     * This method determines in which groups the user with the specified id is an active participant. All
     * participants of these groups are then notified with push notifications that an update of the user account with
     * the specified id has taken place. The method uses the operations offered by the PushManager to distribute the
     * push notifications.
     *
     * @param userId The id of the user whose user account has been changed.
     * @throws ServerException If the notification fails due to a database failure.
     */
    public void notifyGroupParticipantsAboutUserChange(int userId) throws ServerException {
        List<Group> groups;
        try {
            // Get all groups in which the user is a participant.
            groups = groupDBM.getGroupsByParticipant(userId);

            // For each group get the list of participants and send the notification about the changed user account.
            for (Group group : groups) {
                List<User> participants = groupDBM.getParticipants(group.getId());

                // TODO send notification USER_CHANGE with userId and groupId
                // Remove the changed user from the participants list before?
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * A helper method which requests the group with the specified id from the database manager. It can be defined
     * whether the group object should already contain a list of the participants of the group. If the group is not
     * found, the method throws a ServerException.
     *
     * @param groupId The id of the group.
     * @param withParticipants Indicates whether the group object should contain a list of participants.
     * @return Returns the group object of the requested group.
     * @throws ServerException If the group is not found or the retrieval from the database fails.
     */
    private Group getGroup(int groupId, boolean withParticipants) throws ServerException {
        Group group;
        try {
            group = groupDBM.getGroup(groupId, withParticipants);
            if (group == null) {
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }
        } catch (DatabaseException e){
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
        return group;
    }

    /**
     * A helper method which checks whether the user is an active participant of the group using the database. The group
     * is identified by the given id. The method does not supply any return value, it just throws a ServerException
     * if the verification provides a negative result, i.e. the user is not an active participant of the group.
     *
     * @param groupId The id of the group for which the participation should be verified.
     * @param userId The if of the user.
     * @throws ServerException If the user is not an active participant of the group or the verification fails due to
     * a database exception.
     */
    private void verifyParticipationInGroupViaDB(int groupId, int userId) throws ServerException {
        boolean activeParticipant;
        try {
            activeParticipant = groupDBM.isActiveParticipant(groupId, userId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
        if(!activeParticipant){
            String errMsg = "The user needs to be an active participant of the group to perform this operation. The " +
                    "user with id " + userId + " is not an active participant of the group with id " + groupId + ".";
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
            throw new ServerException(403, USER_FORBIDDEN);
        }
    }

    /**
     * A helper method which checks whether the group with the given id exists in the database. The method does not
     * supply any return value, it just throws a ServerException if the group is not found.
     *
     * @param groupId The id of the group.
     * @throws ServerException If the group does not exist or the verification fails due to a database failure.
     */
    private void verifyGroupExistenceViaDB(int groupId) throws ServerException {
        try {
            if(!groupDBM.isValidGroup(groupId)){
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * A helper method which requests the ballot with the specified id from the database manager. If the ballot is
     * not found, the method throws a ServerException.
     *
     * @param groupId The if of the group to which the ballot belongs.
     * @param ballotId The id of the ballot.
     * @return Returns the ballot object from the database.
     * @throws ServerException If the ballot is not found or the retrieval of the ballot fails due to a database
     * failure.
     */
    private Ballot getBallot(int groupId, int ballotId) throws ServerException{
        Ballot ballot;
        try {
            // Get the ballot from the database.
            ballot = groupDBM.getBallot(groupId, ballotId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
        if(ballot == null){
            String errMsg = "The ballot with id " + ballotId + " could not be found in the group with id " +
                    groupId + ".";
            logger.error(LOG_SERVER_EXCEPTION, 404, BALLOT_NOT_FOUND, errMsg);
            throw new ServerException(404, BALLOT_NOT_FOUND);
        }
        return ballot;
    }

    /**
     * A helper method which checks whether the ballot with the specified id exists within the group which is
     * identified by the given id. The method does not supply any return value, it just throws a ServerException if
     * the ballot is not found in the defined group.
     *
     * @param groupId The id of the group.
     * @param ballotId The id of the ballot whose existence should be verified.
     * @throws ServerException If the ballot is not found within the group or the verification fails due to a
     * database failure.
     */
    private void verifyBallotExistenceViaDB(int groupId, int ballotId) throws ServerException {
        try {
            if(!groupDBM.isValidBallot(groupId, ballotId)){
                String errMsg = "The ballot with the id " + ballotId + " could not be found within the group with id " +
                         groupId + ".";
                logger.error(LOG_SERVER_EXCEPTION, 404, BALLOT_NOT_FOUND, errMsg);
                throw new ServerException(404, BALLOT_NOT_FOUND);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * A helper method which requests the option with the specified id from the database manager. If the option is
     * not found, it throws a ServerException.
     *
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The id of the option.
     * @return Returns the option object from the database.
     * @throws ServerException If the option is not found or the retrieval of the option object from the database
     * fails due to a database failure.
     */
    private Option getOption(int ballotId, int optionId) throws ServerException {
        Option option;
        try {
            // Get the option from the database.
            option = groupDBM.getOption(ballotId, optionId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
        if(option == null){
            String errMsg = "The option with id " + optionId + " could not be found within the ballot with id " +
                    ballotId + ".";
            logger.error(LOG_SERVER_EXCEPTION, 404, OPTION_NOT_FOUND, errMsg);
            throw new ServerException(404, OPTION_NOT_FOUND);
        }
        return option;
    }

    /**
     * A helper method which checks whether the option with the specified id exists within the ballot which is
     * identified by the given id. The method does not supply any return value, it just throws a ServerException if
     * the option is not found in the defined ballot.
     *
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The id of the option whose existence should be verified.
     * @throws ServerException If the option is not found within the ballot or the verification fails due to a
     * database failure.
     */
    private void verifyOptionExistenceViaDB(int ballotId, int optionId) throws ServerException {
        try {
            if(!groupDBM.isValidOption(ballotId,optionId)){
                String errMsg = "The given option with id " + optionId + " could not be found for the ballot with id " +
                        ballotId + ".";
                logger.error(LOG_SERVER_EXCEPTION, 404, OPTION_NOT_FOUND, errMsg);
                throw new ServerException(404, OPTION_NOT_FOUND);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * A helper method which requests the converstion with the specified id from the database manager. If the
     * conversation is not found, it throws a ServerException.
     *
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation which should be retrieved.
     * @return The conversation object.
     * @throws ServerException If the conversation is not found within the group or the retrieval fails due to a
     * database exception.
     */
    private Conversation getConversation(int groupId, int conversationId) throws ServerException {
        Conversation conversation;
        try {
            conversation = groupDBM.getConversation(groupId, conversationId);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
        if(conversation == null){
            String errMsg = "The conversation with id " + conversationId + " could not be found within the group with" +
                    " id " + groupId + ".";
            logger.error(LOG_SERVER_EXCEPTION, 404, CONVERSATION_NOT_FOUND, errMsg);
            throw new ServerException(404, CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    /**
     * A helper method which checks whether the conversation with the specified id exists within the group which is
     * identified by the given id. The method does not supply any return value, it just throws a ServerException if
     * the conversation is not found in the defined group.
     *
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation whose existence should be checked.
     * @throws ServerException If the conversation is not found within the group or the verification fails due to a
     * database failure.
     */
    private void verifyConversationExistenceViaDB(int groupId, int conversationId) throws ServerException {
        try {
            if(!groupDBM.isValidConversation(groupId, conversationId)){
                String errMsg = "The given conversation with id " + conversationId + " could not be found for the " +
                        "group with id " + groupId + ".";
                logger.error(LOG_SERVER_EXCEPTION, 404, CONVERSATION_NOT_FOUND, errMsg);
                throw new ServerException(404, CONVERSATION_NOT_FOUND);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

}
