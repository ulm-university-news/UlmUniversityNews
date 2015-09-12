package ulm.university.news.controller;

import ulm.university.news.data.User;
import ulm.university.news.manager.database.UserDatabaseManager;
import ulm.university.news.util.DatabaseException;
import ulm.university.news.util.ServerException;
import ulm.university.news.util.TokenAlreadyExistsException;


/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class UserController {

    /** An instance of the UserDatabaseManager. */
    UserDatabaseManager userDB = new UserDatabaseManager();

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
                userDB.storeUser(user);
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

}
