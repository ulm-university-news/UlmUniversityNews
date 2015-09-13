package ulm.university.news.controller;

import ulm.university.news.data.Moderator;
import ulm.university.news.manager.database.ModeratorDatabaseManager;
import ulm.university.news.util.DatabaseException;
import ulm.university.news.util.ServerException;
import ulm.university.news.util.TokenAlreadyExistsException;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class ModeratorController {

    /** An instance of the ModeratorDatabaseManager. */
    ModeratorDatabaseManager moderatorDB = new ModeratorDatabaseManager();

    /**
     * Create a new moderator account in the system. This method takes the data which have been received with the
     * request and validates it. A unique access token is generated for the new moderator which acts as an identifier
     * for this moderator. If the moderator account creation is successful, the moderator object with all corresponding
     * data is returned.
     *
     * @param moderator The Moderator object which contains the data from the request.
     * @return The moderator object with the data of the created moderator account.
     * @throws ServerException If moderator account creation failed.
     */
    public Moderator createModerator(Moderator moderator) throws ServerException {
        // Perform checks on the received data. If the data is incomplete the moderator can't be created.
        if(moderator.getEmail() == null || moderator.getPassword() == null || moderator.getName() == null ||
                moderator.getFirstName() == null || moderator.getLastName() == null ||
                moderator.getMotivation() == null){
            // Send 400 Bad Request and abort execution.
            throw new ServerException(400, 0, "Incomplete moderator data. Creation of moderator account failed.");
        }

        // Create the accessToken which will identify the new moderator in the system.
        moderator.createModeratorToken();

        boolean successful =false;
        while(!successful){
            try {
                moderatorDB.storeModerator(moderator);
                successful = true;
            } catch (TokenAlreadyExistsException e) {
                // TODO logging
                System.out.println("Need to create a new access token for the user!");
                // Create a new access token for the moderator.
                moderator.createModeratorToken();
            } catch (DatabaseException e) {
                // TODO Logging
                e.printStackTrace();
                throw new ServerException(500, 1000, "Database failure. Creation of user account failed.");
            }
        }

        return moderator;
    }
}
