package ulm.university.news.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.Group;
import ulm.university.news.data.User;
import ulm.university.news.data.enums.TokenType;
import ulm.university.news.manager.database.GroupDatabaseManager;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ServerException;

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
    private GroupDatabaseManager groupDB = new GroupDatabaseManager();

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
     * @throws ServerException If the creation of the group fails.
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
                        "Probably the description exceeded the size. The size of the description is: "
                        + group.getDescription().length() + ".");
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
            groupDB.storeGroup(group);

            // Password should not be returned in response.
            group.setPassword(null);

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return group;
    }

}
