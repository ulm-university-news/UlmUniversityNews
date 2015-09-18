package ulm.university.news.controller;

import org.eclipse.persistence.sessions.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        // Verify access token and check whether the requestor is allowed to perform the operation.
        TokenType tokenType = verifyAccessToken(accessToken);
        if(tokenType == TokenType.INVALID){
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }
        else if(tokenType == TokenType.MODERATOR){
            logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Moderator is not allowed to perform the " +
                    "requested operation.");
            throw new ServerException(403, MODERATOR_FORBIDDEN);
        }

        try {
            User user = userDBM.getUserByToken(accessToken);

            // Check if the received data is valid.
            if(group == null || group.getName() == null || group.getGroupType() == null ||
                    group.getPassword() == null || group.getGroupAdmin() == 0){
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_DATA_INCOMPLETE, "Incomplete data record. The given  " +
                        "group object is " + group + ".");
                throw new ServerException(400, GROUP_DATA_INCOMPLETE);
            }
            else if(!group.getName().matches(GROUP_NAME_PATTERN)){
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_NAME, "Invalid group name. The given name is " +
                        group.getName() + ".");
                throw new ServerException(400, GROUP_INVALID_NAME);
            }
            else if(!group.getPassword().matches(GROUP_PASSWORD_PATTERN)){
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_PASSWORD, "Invalid group password.");
                throw new ServerException(400, GROUP_INVALID_PASSWORD);
            }
            else if(group.getDescription() != null && !group.getDescription().matches(DESCRIPTION_PATTERN)){
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_DESCRIPTION, "Invalid description for group. " +
                        "Probably the description exceeded the size or the description contains any special chars " +
                        "which are not supported. The size of the description is: " + group.getDescription().length()
                        + ". The description is: " + group.getDescription() + ".");
                throw new ServerException(400, GROUP_INVALID_DESCRIPTION);
            }
            else if(group.getTerm() != null && !group.getTerm().matches(TERM_PATTERN)){
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_TERM, "Invalid term. The given term is " +
                        group.getTerm() + ".");
                throw new ServerException(400, GROUP_INVALID_TERM);
            }

            // Check if the group admin is set correctly.
            if(group.getGroupAdmin() != user.getId()){
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
        // Verify access token and check whether the requestor is allowed to perform the operation.
        TokenType tokenType = verifyAccessToken(accessToken);
        if(tokenType == TokenType.INVALID){
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }

        try {
            if (tokenType == TokenType.MODERATOR) {
                Moderator moderator = moderatorDBM.getModeratorByToken(accessToken);
                // Besides users, only administrators have the permission to perform this operation.
                if(moderator.isAdmin() == false){
                    logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Moderator is not allowed to perform" +
                            " the requested operation.");
                    throw new ServerException(403, MODERATOR_FORBIDDEN);
                }else{
                    logger.info("Administrator with id {} requests groups.", moderator.getId());
                }
            }

            // Get the groups from the database.
            groups = groupDBM.getGroups(groupName,groupType);
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
        // Verify access token and check whether the requestor is allowed to perform the operation.
        TokenType tokenType = verifyAccessToken(accessToken);
        if(tokenType == TokenType.INVALID){
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }

        try {
            if (tokenType == TokenType.MODERATOR) {
                Moderator moderator = moderatorDBM.getModeratorByToken(accessToken);
                // Besides users, only administrators have the permission to perform this operation.
                if(moderator.isAdmin() == false){
                    logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Moderator is not allowed to perform" +
                            " the requested operation.");
                    throw new ServerException(403, MODERATOR_FORBIDDEN);
                }else{
                    logger.info("Administrator with id {} requests group with id {}.", moderator.getId(), groupId);
                }
            }

            // Get the group from the database.
            group = groupDBM.getGroup(groupId, withParticipants);
            if(group == null){
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, "The group with the id " + groupId + " could" +
                        " not be found.");
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
        Group groupDB = null;
        if(group == null){
            logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_DATA_INCOMPLETE, "No valid data sent with patch request.");
            throw new ServerException(400, GROUP_DATA_INCOMPLETE);
        }

        // Verify access token and check whether the requestor is allowed to perform the operation.
        TokenType tokenType = verifyAccessToken(accessToken);
        if(tokenType == TokenType.INVALID){
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }
        else if(tokenType == TokenType.MODERATOR){
            logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Moderator is not allowed to perform the " +
                    "requested operation.");
            throw new ServerException(403, MODERATOR_FORBIDDEN);
        }

        try {
            User user = userDBM.getUserByToken(accessToken);

            // Get the data of the group from the database. The group should already contain the list of participants.
            groupDB = groupDBM.getGroup(groupId, true);
            if(groupDB == null){
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, "The group with the id " + groupId + " could" +
                        " not be found.");
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

            if(user.getId() != groupDB.getGroupAdmin()){
                logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, "The user is not the group administrator of " +
                        "this group. The user is thus not allowed to change the group data.");
                throw new ServerException(403, USER_FORBIDDEN);
            }

            // Determine what needs to be updated and update the corresponding fields in the database.
            groupDB = updateGroup(group, groupDB);
            groupDBM.updateGroup(groupDB);

            List<User> participants = groupDB.getParticipants();
            // TODO send notification to participants

            // Don't return the list of participants in the resonse.
            groupDB.setParticipants(null);

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
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_NAME, "Invalid group name. The given name is " +
                        group.getName() + ".");
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
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_DESCRIPTION, "Invalid description for group. " +
                        "Probably the description exceeded the size or the description contains any special chars " +
                        "which are not supported. The size of the description is: " + group.getDescription().length()
                        + ". The description is: " + group.getDescription() + ".");
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
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_TERM, "Invalid term. The given term is " +
                        group.getTerm() + ".");
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
                logger.error(LOG_SERVER_EXCEPTION, 400, GROUP_INVALID_GROUP_ADMIN, "Invalid group admin. The given " +
                        "id for the new group admin represents a user who is no active participant of the group and " +
                        "thus can't be set as the group admin of this group. The id is " + newGroupAdmin + ".");
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
        // Verify access token.
        TokenType tokenType = verifyAccessToken(accessToken);
        if(tokenType == TokenType.INVALID){
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }

        try {
            // Request the affected group. The group should contain a list of all its participants.
            Group groupDB = groupDBM.getGroup(groupId, true);
            if(groupDB == null){
                logger.error(LOG_SERVER_EXCEPTION, 404, GROUP_NOT_FOUND, "The group with the id " + groupId + " could" +
                        " not be found.");
                throw new ServerException(404, GROUP_NOT_FOUND);
            }

            // Check whether the requestor has the permission to delete the group.
            if(tokenType == TokenType.USER){
                // Note that user cannot be null here as the access token has been verified.
                User user = userDBM.getUserByToken(accessToken);
                logger.info("User with id {} wants to delete the group with id {}.", user.getId(), groupId);

                // Check if user is group administrator of this group.
                if(user.getId() != groupDB.getGroupAdmin()){
                    logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, "The user with id "+ user.getId() + " is " +
                            "not the group administrator of this group. The user is thus not allowed to change the " +
                            "group data.");
                    throw new ServerException(403, USER_FORBIDDEN);
                }
            }
            else if (tokenType == TokenType.MODERATOR) {
                // TODO Check moderator for null?
                Moderator moderator = moderatorDBM.getModeratorByToken(accessToken);
                // Besides group administrators, only administrators have the permission to perform this operation.
                if (moderator.isAdmin() == false) {
                    logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Moderator is not allowed to perform" +
                            " the requested operation.");
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
        // Verify access token and check whether the requestor is allowed to perform the operation.
        TokenType tokenType = verifyAccessToken(accessToken);
        if(tokenType == TokenType.INVALID){
            String errMsg = "To perform this operation a valid access token needs to be provided.";
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, errMsg);
            throw new ServerException(401, TOKEN_INVALID);
        }
        else if(tokenType == TokenType.MODERATOR){
            String errMsg = "Moderator is not allowed to perform the requested operation.";
            logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, errMsg);
            throw new ServerException(403, MODERATOR_FORBIDDEN);
        }

        try {
            User user = userDBM.getUserByToken(accessToken);

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
        // Verify access token and check whether the requestor is allowed to perform the operation.
        TokenType tokenType = verifyAccessToken(accessToken);
        if(tokenType == TokenType.INVALID){
            String errMsg = "To perform this operation a valid access token needs to be provided.";
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, errMsg);
            throw new ServerException(401, TOKEN_INVALID);
        }
        else if(tokenType == TokenType.MODERATOR){
            String errMsg = "Moderator is not allowed to perform the requested operation.";
            logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, errMsg);
            throw new ServerException(403, MODERATOR_FORBIDDEN);
        }

        try {
            User user = userDBM.getUserByToken(accessToken);

            // TODO check if the group exists?

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

}
