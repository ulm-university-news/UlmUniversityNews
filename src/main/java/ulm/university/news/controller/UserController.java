package ulm.university.news.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.Moderator;
import ulm.university.news.data.User;
import ulm.university.news.data.enums.TokenType;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ServerException;
import ulm.university.news.util.exceptions.TokenAlreadyExistsException;

import java.util.List;

import static ulm.university.news.util.Constants.*;


/**
 * The UserController handles requests concerning the user resources. It offers methods to query user account data as
 * well as methods to create new user accounts or update existing ones.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class UserController extends AccessController{

    /** An instance of the Logger class which performs logging for the UserController class. */
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    /**
     * Creates an instance of the UserController class.
     */
    public UserController(){

    }

    /**
     * Create a new user account in the system. This method takes the data which have been received with the request
     * and validates it. A unique access token is generated for the new user which acts as an identifier for this user.
     * If the user account creation is successful, the user object with all corresponding data is returned.
     *
     * @param user The user object which contains the data from the request.
     * @return The user object with the data of the created user account.
     * @throws ServerException If user account creation failed.
     */
    public User createUser(User user) throws ServerException {
        // Perform checks on the received data. If the data is incomplete the user can't be created.
        if(user == null || user.getName() == null || user.getPlatform() == null || user.getPushAccessToken() == null){
            logger.error(LOG_SERVER_EXCEPTION, 400, USER_DATA_INCOMPLETE, "Incomplete data record. The given user " +
                    "object is: " + user + ".");
            throw new ServerException(400, USER_DATA_INCOMPLETE);
        }
        else if(!user.getName().matches(ACCOUNT_NAME_PATTERN)){
            logger.error(LOG_SERVER_EXCEPTION, 400, USER_NAME_INVALID, "Invalid username. The given username is: " +
                    user.getName() + ".");
            throw new ServerException(400, USER_NAME_INVALID);
        }

        // Create the accessToken which will identify the new user in the system.
        user.createUserToken();

        boolean successful =false;
        while(successful == false){
            try {
                userDBM.storeUser(user);
                successful = true;
            } catch (TokenAlreadyExistsException e) {
                logger.info("The created user access token has been rejected. Create a new access token.");
                // Create a new access token for the user.
                user.createUserToken();
            } catch (DatabaseException e) {
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Creation of user account" +
                        " failed.");
                throw new ServerException(500, DATABASE_FAILURE);
            }
        }

        return user;
    }


    /**
     * Returns the data of all user accounts which exist in the system.
     *
     * @param accessToken The access token of the requestor.
     * @return Returns a list of user objects.
     * @throws ServerException If the authorization of the requestor fails or the requestor is not allowed to perform
     * the operation. Furthermore, a failure of the database also causes a ServerException.
     */
    public List<User> getUsers(String accessToken) throws ServerException {
        List<User> users = null;
        TokenType tokenType= verifyAccessToken(accessToken);

        // Check the given access token.
        if(tokenType == TokenType.USER){
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, "User is not allowed to perform the requested " +
                    "operation.");
            throw new ServerException(403, USER_FORBIDDEN);
        }
        else if(tokenType == TokenType.INVALID){
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }

        try {
            // Get the moderator which is identified by the access token and check whether he is an administrator.
            Moderator moderator = moderatorDBM.getModeratorByToken(accessToken);
            if(moderator.isAdmin() == false){
                logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Moderator is not allowed to perform " +
                        "the requested operation.");
                throw new ServerException(403, MODERATOR_FORBIDDEN);
            }

            // Get the user account data from the database.
            users = userDBM.getUsers();

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return users;
    }

    /**
     * Get the data to the user account identified by the given id.
     *
     * @param accessToken The access token of the requestor
     * @param userId The id of the user account which should be retrieved.
     * @return The user object with the data of the user account.
     * @throws ServerException If the requstor is unauthorized, the user resource has not been found or a database
     * failure has occurred.
     */
    public User getUser(String accessToken, int userId) throws ServerException {
        User user = null;
        TokenType tokenType = verifyAccessToken(accessToken);

        if(tokenType == TokenType.INVALID){
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }

        try {
            // Query the user data from the database.
            user = userDBM.getUser(userId);
            if(user == null){
                logger.error(LOG_SERVER_EXCEPTION, 404, USER_NOT_FOUND, "User resource with id " + userId + " not " +
                        "found.");
                throw new ServerException(404, USER_NOT_FOUND);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
        return user;
    }

    /**
     * Performs an update on the data of the user account which is identified by the given id and the access token.
     * The user object contains the fields which should be updated and the new values. As far as no data conditions
     * are harmed, the fields will be updated in the database.
     *
     * @param accessToken The access token of the requestor.
     * @param userId The id of the user account which should be updated.
     * @param user The user object with the new data values which have been transmitted with the request.
     * @return The user object with the updated data values.
     * @throws ServerException If the new data values harm certain conditions, the user is not authorized or
     * doesn't have the required permissions and if a database failure occurs.
     */
    public User changeUser(String accessToken, int userId, User user) throws ServerException {
        User userDB = null;

        if(user == null){
            // TODO user USER_DATA_INCOMPLETE here or rather something like USER_PATCH_DATA_INVALID?
            logger.error(LOG_SERVER_EXCEPTION, 400, USER_DATA_INCOMPLETE, "No valid data sent with patch request.");
            throw new ServerException(400, USER_DATA_INCOMPLETE);
        }

        TokenType tokenType = verifyAccessToken(accessToken);
        // Perform access token validation.
        if(tokenType == TokenType.MODERATOR){
            logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Moderator is not allowed to perform " +
                    "the requested operation.");
            throw new ServerException(403, MODERATOR_FORBIDDEN);
        }
        else if(tokenType == TokenType.INVALID){
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }

        try {
            // Note, at this point it is known that the access token is valid, so userDB won't be null.
            userDB = userDBM.getUserByToken(accessToken);

            // User is only allowed to change the data of his own account.
            if(userDB.getId() != userId){
                logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, "User is only allowed to change his own " +
                        "account.");
                throw new ServerException(403, USER_FORBIDDEN);
            }

            // Determine what needs to be updated and update the corresponding fields in the database.
            userDB = updateUser(user, userDB);
            userDBM.updateUser(userDB);

            //TODO notify all participants of groups in which the user is participant.

        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure. Update of user account data " +
                    "failed.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return userDB;
    }

    /**
     * Compares the user object with the received data from the request with the user object taken from the
     * database. Updates the database object with the new data which has been received through the request. Note that
     * some fields cannot be changed, so if some changes to these fields are described, they will be ignored.
     *
     * @param user The user object which contains the data from the request.
     * @param userDB The user object which contains the data from the database.
     * @return Returns an updated version of the user object taken from the database.
     * @throws ServerException If some data based conditions were harmed.
     */
    private User updateUser(User user, User userDB) throws ServerException{
        String newName = user.getName();
        if(newName != null){
            // Update name if conditions are met.
            if(user.getName().matches(ACCOUNT_NAME_PATTERN)){
                userDB.setName(newName);
            }
            else{
                logger.error(LOG_SERVER_EXCEPTION, 400, USER_NAME_INVALID, "Invalid username. The given username is: " +
                        user.getName() + ".");
                throw new ServerException(400, USER_NAME_INVALID);
            }
        }

        // TODO validation of push token necessary?
        String newPushToken = user.getPushAccessToken();
        if(newPushToken != null){
            userDB.setPushAccessToken(newPushToken);
        }

//        if(user.getPlatform() != null){
//            userDBM.setPlatform(user.getPlatform());
//        }

        return userDB;
    }

}
