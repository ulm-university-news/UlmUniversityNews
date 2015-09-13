package ulm.university.news.controller;

import ulm.university.news.data.User;
import ulm.university.news.manager.database.ModeratorDatabaseManager;
import ulm.university.news.manager.database.UserDatabaseManager;
import ulm.university.news.util.DatabaseException;
import ulm.university.news.util.ServerException;
import ulm.university.news.util.TokenAlreadyExistsException;
import ulm.university.news.util.TokenType;

import java.util.List;


/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class UserController {

    /** An instance of the UserDatabaseManager. */
    UserDatabaseManager userDBManager = new UserDatabaseManager();
    ModeratorDatabaseManager moderatorDBManager = new ModeratorDatabaseManager();
    AccessController accessController = new AccessController();

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
        if(user.getName() == null || user.getPlatform() == null || user.getPushAccessToken() == null){
            // TODO send 400 Bad Request and abort execution
        }

        // Create the accessToken which will identify the new user in the system.
        user.createUserToken();

        boolean successful =false;
        while(successful == false){
            try {
                userDBManager.storeUser(user);
                successful = true;
            } catch (TokenAlreadyExistsException e) {
                // TODO logging
                System.out.println("Need to create a new access token for the user!");
                // Create a new access token for the user.
                user.createUserToken();
            } catch (DatabaseException e) {
                // TODO Logging
                e.printStackTrace();
                throw new ServerException(500, 1000, "Database failure. Creation of user account failed.");
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
        TokenType tokenType= accessController.verifyAccessToken(accessToken);

        if(tokenType == TokenType.USER){
            throw new ServerException(403,0, "User is not allowed to perform the requested operation.");
        }
        else if(tokenType == TokenType.INVALID){
            throw new ServerException(401, 0, "To perform this operation a valid access token needs to be provided.");
        }

//        Moderator moderator = moderatorDB.getModeratorByToken(accessToken);
//        if(moderator.isAdmin() == false){
//            throw new ServerException(403, 0, "Moderator is not allowed to perform the requested operation.");
//        }
//
//        try {
//            users = userDBManager.getUsers();
//        } catch (DatabaseException e) {
//            //TODO Logging
//            e.printStackTrace();
//            throw new ServerException(500, 0, "Database failure.");
//        }

        return users;
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
     * @throws ServerException If the new data values have harmed certain conditions, the user is not authorized or
     * doesn't have the required permissions and if a database failure has occurred.
     */
    public User changeUser(String accessToken, int userId, User user) throws ServerException {
        User userDB = null;
        TokenType tokenType = accessController.verifyAccessToken(accessToken);

        if(tokenType == TokenType.MODERATOR){
            throw new ServerException(403,0, "Moderator is not allowed to perform the requested operation.");
        }
        else if(tokenType == TokenType.INVALID){
            throw new ServerException(401, 0, "To perform this operation a valid access token needs to be provided.");
        }

        try {
            userDB = userDBManager.getUserByToken(accessToken);

            if(userDB.getId() != userId){
                throw new ServerException(403, 0, "User is only allowed to change his own account.");
            }

            userDB = updateUser(user, userDB);
            userDBManager.updateUser(userDB);

        } catch (DatabaseException e) {
            // TODO Logging
            e.printStackTrace();
            throw new ServerException(500, 0, "Database failure. Update user account failed.");
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
            if(user.getName().length() <= 35){
                userDB.setName(newName);
            }
            else{
                throw new ServerException(400,0, "Name exceeded maximum length.");
            }
        }
        String newPushToken = user.getPushAccessToken();
        if(newPushToken != null){
            userDB.setPushAccessToken(newPushToken);
        }
//        if(user.getPlatform() != null){
//            userDB.setPlatform(user.getPlatform());
//        }

        return userDB;
    }

}
