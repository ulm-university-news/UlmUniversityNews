package ulm.university.news.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.Ballot;
import ulm.university.news.data.Group;
import ulm.university.news.data.Moderator;
import ulm.university.news.data.User;
import ulm.university.news.data.enums.GroupType;
import ulm.university.news.data.enums.TokenType;
import ulm.university.news.manager.database.GroupDatabaseManager;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ServerException;

import java.util.List;

import static ulm.university.news.util.Constants.*;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class GroupController extends AccessController {

    /** An instance of the Logger class which performs logging for the GroupController class. */
    private static final Logger logger = LoggerFactory.getLogger(GroupController.class);

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
        // Check if the requestor is a valid user. Only users are allowed to perform this operation.
        User user = verifyUserAccess(accessToken);

        // Check if the received data is valid.
        if (group == null || group.getName() == null || group.getGroupType() == null ||
                group.getPassword() == null || group.getGroupAdmin() == 0) {
            String errMsg = "Incomplete data record. The given group object is " + group + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_DATA_INCOMPLETE, errMsg);
            throw new ServerException(400, GROUP_DATA_INCOMPLETE);
        } else if (!group.getName().matches(GROUP_NAME_PATTERN)) {
            String errMsg = "Invalid group name. The given name is " + group.getName() + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_NAME, errMsg);
            throw new ServerException(400, GROUP_INVALID_NAME);
        } else if (!group.getPassword().matches(GROUP_PASSWORD_PATTERN)) {
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

        // Check if the group admin is set correctly.
        if (group.getGroupAdmin() != user.getId()) {
            logger.warn("The Id of the groupAdmin and the requestor don't match. The user who makes the creation " +
                    "request for the group should be entered as the group admin. The group admin is set to the " +
                    "id of the requestor.");
            group.setGroupAdmin(user.getId());
        }

        // Prepare the password for being stored in the database.
        group.encryptPassword();

        //Compute the creation date of the group and also set it as the modification date.
        group.computeCreationDate();
        group.setModificationDate(group.getCreationDate());

        try {
            // Store group in database.
            groupDBM.storeGroup(group);

            // Password should not be returned in response.
            group.setPassword(null);

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

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
        List<Group> groups = null;
        /* Verify access token and check whether the requestor is allowed to perform the operation.
           Users and system administrators can perform this operation. */
        TokenType tokenType = verifyAccessToken(accessToken);
        if(tokenType == TokenType.INVALID){
            String errMsg = "To perform this operation a valid access token needs to be provided.";
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, errMsg);
            throw new ServerException(401, TOKEN_INVALID);
        }

        try {
            if (tokenType == TokenType.MODERATOR) {
                Moderator moderator = moderatorDBM.getModeratorByToken(accessToken);
                // Besides users, only administrators have the permission to perform this operation.
                if(moderator.isAdmin() == false){
                    String errMsg = "Moderator is not allowed to perform the requested operation.";
                    logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, errMsg);
                    throw new ServerException(403, MODERATOR_FORBIDDEN);
                }else{
                    logger.info("Administrator with id {} requests groups.", moderator.getId());
                }
            }

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
        Group group = null;
        /* Verify access token and check whether the requestor is allowed to perform the operation.
           Users and system administrators can perform this operation. */
        TokenType tokenType = verifyAccessToken(accessToken);
        if(tokenType == TokenType.INVALID){
            String errMsg = "To perform this operation a valid access token needs to be provided.";
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, errMsg);
            throw new ServerException(401, TOKEN_INVALID);
        }

        try {
            if (tokenType == TokenType.MODERATOR) {
                Moderator moderator = moderatorDBM.getModeratorByToken(accessToken);
                // Besides users, only administrators have the permission to perform this operation.
                if(moderator.isAdmin() == false){
                    String errMsg = "Moderator is not allowed to perform the requested operation.";
                    logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, errMsg);
                    throw new ServerException(403, MODERATOR_FORBIDDEN);
                }else{
                    logger.info("Administrator with id {} requests group with id {}.", moderator.getId(), groupId);
                }
            }

            // Get the group from the database.
            group = groupDBM.getGroup(groupId, withParticipants);
            if(group == null){
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

            // The password should not be returned to the requestor, so set it to null.
            group.setPassword(null);

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

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
        User user = verifyUserAccess(accessToken);

        Group groupDB = null;
        try {
            // Get the data of the group from the database. The group should already contain the list of participants.
            groupDB = groupDBM.getGroup(groupId, true);
            if(groupDB == null){
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

            // Check if the user is allowed to execute the update operation.
            if(user.getId() != groupDB.getGroupAdmin()){
                String errMsg = "The user is not the group administrator of this group. The user is thus not allowed " +
                        "to change the group data.";
                logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
                throw new ServerException(403, USER_FORBIDDEN);
            }

            // Determine what needs to be updated and update the corresponding fields in the database.
            groupDB = updateGroup(group, groupDB);
            groupDBM.updateGroup(groupDB);

            List<User> participants = groupDB.getParticipants();
            // TODO send notification to participants

            // Don't return the list of participants in the response.
            groupDB.setParticipants(null);
            // Don't return the password in the response.
            groupDB.setPassword(null);

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return groupDB;
    }

    /**
     * Compares the group object with the received data from the request with the group object taken from the
     * database. Updates the database object with the new data which has been received through the request. Note that
     * some fields cannot be changed, so if some changes to these fields are described, they will be ignored.
     *
     * @param group The group object which contains the data from the request.
     * @param groupDB The group object which contains the data from the database.
     * @return Returns an updated version of the group object taken from the database.
     * @throws ServerException If some data based conditions were harmed.
     * @throws DatabaseException If the validation of some data against the database fails.
     */
    private Group updateGroup(Group group, Group groupDB) throws ServerException, DatabaseException {
        String newName = group.getName();
        if(newName != null){
            // Update the name if conditions are met.
            if(newName.matches(GROUP_NAME_PATTERN)){
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
            if(newPassword.matches(GROUP_PASSWORD_PATTERN)){
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
            if(groupDBM.isActiveParticipant(groupDB.getId(), newGroupAdmin)){
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
     * Deletes the group with the specified id. All corresponding subresources like conversations and ballots are
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
            String errMsg = "To perform this operation a valid access token needs to be provided.";
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, errMsg);
            throw new ServerException(401, TOKEN_INVALID);
        }

        try {
            // Request the affected group. The group should contain a list of all its participants.
            Group groupDB = groupDBM.getGroup(groupId, true);
            if(groupDB == null){
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

            // Check whether the requestor has the permission to delete the group.
            if(tokenType == TokenType.USER){
                // Note that user cannot be null here as the access token has been verified.
                User user = userDBM.getUserByToken(accessToken);
                logger.info("User with id {} wants to delete the group with id {}.", user.getId(), groupId);

                // Check if user is group administrator of this group.
                if(user.getId() != groupDB.getGroupAdmin()){
                    String errMsg = "The user with id "+ user.getId() + " is not the group administrator of this " +
                            "group. The user is thus not allowed to change the group data.";
                    logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
                    throw new ServerException(403, USER_FORBIDDEN);
                }
            }
            else if (tokenType == TokenType.MODERATOR) {
                // TODO Check moderator for null?
                Moderator moderator = moderatorDBM.getModeratorByToken(accessToken);
                // Besides group administrators, only administrators have the permission to perform this operation.
                if (moderator.isAdmin() == false) {
                    String errMsg = "Moderator is not allowed to perform the requested operation.";
                    logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, errMsg);
                    throw new ServerException(403, MODERATOR_FORBIDDEN);
                } else {
                    logger.info("Administrator with id {} wants to delete the group with id {}.", moderator.getId(),
                            groupId);
                }
            }

            // Get all participants of the group.
            List<User> participants = groupDB.getParticipants();

            // Delete the group and all corresponding resources.
            groupDBM.deleteGroup(groupId);

            // TODO notify participants about deleted group

        }catch (DatabaseException e){
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
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
        User user = verifyUserAccess(accessToken);

        try {
            // Request the group from the database. The group object should already contain a list of its participants.
            Group groupDB = groupDBM.getGroup(groupId, true);
            if(groupDB == null){
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

            // Check if the user has provided the correct password in order to join the group.
            boolean verified = groupDB.verifyPassword(password);
            if(verified == false){
                String errMessage = "The user didn't provide the correct password to be able to join the group.";
                logger.error(LOG_SERVER_EXCEPTION, 403, GROUP_INCORRECT_PASSWORD, errMessage);
                throw new ServerException(403, GROUP_INCORRECT_PASSWORD);
            }

            // If the password is verified, the user can be added to the group.
            groupDBM.addParticipantToGroup(groupId, user.getId());

            // Get the participants of the group. Note that in this list the new participant is not contained.
            List<User> participants = groupDB.getParticipants();

            // TODO Send a notification to the participants to notify them about the new user.

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
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
        User user = verifyUserAccess(accessToken);

        try {
            // Check if group exists.
            if(!groupDBM.isValidGroup(groupId)){
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

            // Check if user is active participant of the group.
            boolean activeParticipant = groupDBM.isActiveParticipant(groupId, user.getId());
            if(activeParticipant == false){
                String errMsg = "The user needs to be an active participant of the group to perform this operation.";
                logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
                throw new ServerException(403, USER_FORBIDDEN);
            }

            // Get the participants from the database.
            users = groupDBM.getParticipants(groupId);

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
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

        try {
            // Request the group object. The group object should already contain a list of the participants
            Group groupDB = groupDBM.getGroup(groupId, true);
            if(groupDB == null){
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

            // Check if the user which is identified by the participantId is an active participant of the group.
            boolean activeParticipant = groupDBM.isActiveParticipant(groupId, participantId);
            if(activeParticipant==false){
                String errMsg = "The user with the id " + participantId + " is not found in the group with id " +
                        groupId + ". The user is not an active participant of the group.";
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
               group administrator exclusively is allowed to remove other participants from the group.  */
            if(requestor.getId() != participantId && !isAdmin){
                String errMsg = "The user with id " + requestor.getId() + " requested to remove the user with the id " +
                        participantId + " from the group. The request is rejected as the requestor is not the group " +
                        "administrator of the group.";
                logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
                throw new ServerException(403, USER_FORBIDDEN);
            }

            // Perform the removal. The user will be set to inactive for the group.
            groupDBM.removeParticipantFromGroup(groupId, participantId);

            // Notify participants depending on whether the user has left or the user was removed from the group.
            List<User> participants = groupDB.getParticipants();
            if(participantId == requestor.getId()){
                logger.info("The user with id {} has left the group with id {}.", requestor.getId(), groupId);
                // TODO send notification with type GROUP_PARTICIPANT_LEFT
                // TODO remove the participant with participantId from the participants list first?
            }
            else{
                logger.info("The user with id {} has been removed from the group with id {} by the group " +
                        "administrator", participantId, groupId);
                // TODO send notification with type GROUP_PARTICIPANT_REMOVED
            }

            // TODO updateConversationAndBallotAdmin(groupDB, participantId)

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    private void updateConversationAndBallotAdmin(Group groupDB, int participantId){
        // TODO
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
        // Check if the requestor is a valid user. Only users are allowed to perform this operation.
        User requestor = verifyUserAccess(accessToken);

        // Validate the received data.
        if(ballot == null || ballot.getTitle() == null || ballot.getMultipleChoice() == null ||
                ballot.getPublicVotes() == null){
            String errMsg = "Incomplete data record. The given ballot object is " + ballot + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, BALLOT_DATA_INCOMPLETE, errMsg);
            throw new ServerException(400, BALLOT_DATA_INCOMPLETE);
        }
        else if(!ballot.getTitle().matches(BALLOT_TITLE_PATTERN)){
            String errMsg = "Invalid ballot title. The given title is " + ballot.getTitle() + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, BALLOT_INVALID_TITLE, errMsg);
            throw new ServerException(400, BALLOT_INVALID_TITLE);
        }
        else if(ballot.getDescription() != null && !ballot.getDescription().matches(DESCRIPTION_PATTERN)){
            String errMsg = "Invalid description for ballot. Probably the description exceeded the size or the " +
                    "description contains any special chars which are not supported. The size of the description " +
                    "is: " + ballot.getDescription().length() + ". The description is: " + ballot.getDescription()
                    + ".";
            logger.error(LOG_SERVER_EXCEPTION, 400, BALLOT_INVALID_DESCRIPTION, errMsg);
            throw new ServerException(400, BALLOT_INVALID_DESCRIPTION);
        }

        try {
            // Request the affected group from the database. The group should already contain the list of participants.
            Group groupDB = groupDBM.getGroup(groupId, true);
            if(groupDB == null){
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

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
                if(groupDB.isGroupAdmin(requestor.getId()) == false){
                    String errMsg = "The requestor, i.e. the user with id " + requestor.getId() + ", is not the tutor" +
                            " (group administrator) of the tutorial group with id " + groupId + ". The user is thus " +
                            "not allowed to create a ballot for this group.";
                    logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
                    throw new ServerException(403, USER_FORBIDDEN);
                }
            }

            // Set the requestor id as the ballot administrator for this ballot.
            ballot.setAdmin(requestor.getId());

            // Store the ballot in the database.
            groupDBM.storeBallot(ballot, groupId);

            // Notify participants about a new ballot.
            List<User> participants = groupDB.getParticipants();
            // TODO send notification

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return ballot;
    }

    /**
     * Returns a list of ballots which belong to the given group with the specified id. It can be defined whether the
     * ballot objects should contain their corresponding sub-resources like the options and the voters for each option.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group.
     * @param subresources Indicates whether the ballot objects should contain the corresponding options and a list
     *                     of voters who voted for each option.
     * @return A list of ballot objects. The list can also be empty.
     * @throws ServerException If the requestor is not allowed to execute the operation, the group is not found or
     * the ballots can not be retrieved due to a database failure.
     */
    public List<Ballot> getBallots(String accessToken, int groupId, boolean subresources) throws ServerException {
        List<Ballot> ballots = null;

        /* Check if requestor is a valid user. Only users (more precisely participants of the group) are allowed to
           perform this operation. */
        User requestor = verifyUserAccess(accessToken);
        try {
            boolean isActiveParticipant = groupDBM.isActiveParticipant(groupId, requestor.getId());
            if(!isActiveParticipant){
                String errMsg = "The requestor, i.e. the user with id " + requestor.getId() + " is not an active " +
                        "participant of the group with id " + groupId + ". The request is rejected.";
                logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
                throw new ServerException(403, USER_FORBIDDEN);
            }

            // Check if group exists.
            if(!groupDBM.isValidGroup(groupId)){
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

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
        try {
            boolean isActiveParticipant = groupDBM.isActiveParticipant(groupId, requestor.getId());
            if(!isActiveParticipant){
                String errMsg = "The requestor, i.e. the user with id " + requestor.getId() + " is not an active " +
                        "participant of the group with id " + groupId + ". The request is rejected.";
                logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
                throw new ServerException(403, USER_FORBIDDEN);
            }

            // Check if group exists.
            if(!groupDBM.isValidGroup(groupId)){
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

            // Get the ballot from the database.
            ballot = groupDBM.getBallot(groupId, ballotId);
            if(ballot == null){
                String errMsg = "The ballot with id " + ballotId + " could not be found in the group with id " +
                        groupId + ".";
                logger.error(LOG_SERVER_EXCEPTION, 404, BALLOT_NOT_FOUND);
                throw new ServerException(404, BALLOT_NOT_FOUND);
            }

        } catch (DatabaseException e){
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
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
        Ballot ballotDB = null;
        /* Check if requestor is a valid user. The user needs to be an active participant of the group and needs to
           be the administrator of the affected ballot. */
        User requestor = verifyUserAccess(accessToken);
        try {
            // First, get the group from the database. The group should already contain a list of all participants.
            Group groupDB = groupDBM.getGroup(groupId, true);
            if(groupDB == null){
                String errMsg = "The group with the id " + groupId + " could not be found.";
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, errMsg);
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

            // Check if the requestor is an active participant of the group. Otherwise reject the request.
            if(!groupDB.isValidParticipant(requestor.getId())){
                String errMsg = "The user with id " + requestor.getId() + " is not an active participant of the group" +
                        " with id " + groupId + ". The request is rejected.";
                logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
                throw new ServerException(403, USER_FORBIDDEN);
            }

            // Second, get the ballot object from the database.
            ballotDB = groupDBM.getBallot(groupId, ballotId);
            if(ballotDB == null){
                String errMsg = "The ballot with id " + ballotId + " could not be found in the group with id " +
                        groupId + ".";
                logger.error(LOG_SERVER_EXCEPTION, 404, BALLOT_NOT_FOUND);
                throw new ServerException(404, BALLOT_NOT_FOUND);
            }

            // Check if the requestor is the administrator of the ballot. Otherwise reject the request.
            if(!ballotDB.isBallotAdmin(requestor.getId())){
                String errMsg = "The requestor, i.e. the user with id " + requestor.getId() + ", is not the " +
                        "administrator of the ballot with id " + ballotId + ". The request is rejected.";
                logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, errMsg);
                throw new ServerException(403, USER_FORBIDDEN);
            }

            // Determine what needs to be updated and update the corresponding fields in the database.
            ballotDB = updateBallot(ballot, ballotDB);
            groupDBM.updateBallot(ballotDB);

            // Notify the participants of the group about the changed ballot.
            List<User> participants = groupDB.getParticipants();
            // TODO notify participants.

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
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
            if(newTitle.matches(BALLOT_TITLE_PATTERN)){
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
                        "is: " +ballot.getDescription().length()+ ". The description is: " +ballot.getDescription()+
                        ".";
                logger.error(LOG_SERVER_EXCEPTION, 400, BALLOT_INVALID_DESCRIPTION, errMsg);
                throw new ServerException(400, BALLOT_INVALID_DESCRIPTION);
            }
        }

        // Update the closed status if necessary.
        boolean closed = ballot.getClosed();
        if(closed == true && !ballotDB.getClosed() ){
            logger.info("The ballot with id {} is getting closed.", ballotDB.getId());
            ballotDB.setClosed(closed);
        }
        else if(closed == false && ballotDB.getClosed()){
            logger.info("The ballot with id {} is getting opened again.", ballotDB.getId());
            ballotDB.setClosed(closed);
        }

        return ballotDB;
    }

}
