package ulm.university.news.controller;

import org.apache.commons.validator.routines.EmailValidator;
import ulm.university.news.data.Moderator;
import ulm.university.news.manager.database.ModeratorDatabaseManager;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ServerException;
import ulm.university.news.util.exceptions.TokenAlreadyExistsException;

import java.util.regex.Pattern;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class ModeratorController {

    /** A pattern which describes the valid form of a user or moderator name. */
    private static final String NAME_PATTERN = "^[a-zA-Z0-9_-]{3,35}$";

    /** A pattern which describes the valid form of a user or moderator name. */
    private static final String PASSWORD_PATTERN = "^[a-zA-Z0-9]{3,35}$";

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
        System.out.println(moderator.getPassword());
        // Perform checks on the received data. If the data isn't accurate the moderator can't be created.
        // In case of inaccuracy, send 400 Bad Request and abort execution.
        if (moderator.getName() == null || moderator.getPassword() == null || moderator.getEmail() == null ||
                moderator.getFirstName() == null || moderator.getLastName() == null ||
                moderator.getMotivation() == null) {
            throw new ServerException(400, 0, "Moderator data is incomplete.");
        } else if (!Pattern.compile(NAME_PATTERN).matcher(moderator.getName()).matches()) {
            throw new ServerException(400, 0, "Name is invalid.");
        } else if (!EmailValidator.getInstance().isValid(moderator.getEmail())) {
            throw new ServerException(400, 0, "Email address is invalid.");
        } else if (!Pattern.compile(PASSWORD_PATTERN).matcher(moderator.getName()).matches()) {
            throw new ServerException(400, 0, "Password is invalid.");
        }

        // Create the accessToken which will identify the new moderator in the system.
        moderator.createModeratorToken();

        // Initialize remaining fields.
        moderator.setLocked(true);
        moderator.setAdmin(false);
        moderator.setDeleted(false);

        boolean successful = false;
        while (!successful) {
            try {
                moderatorDB.storeModerator(moderator);
                successful = true;
            } catch (TokenAlreadyExistsException e) {
                // TODO logging
                System.out.println("Need to create a new access token for the moderator!");
                // Create a new access token for the moderator.
                moderator.createModeratorToken();
            } catch (DatabaseException e) {
                // TODO Logging
                e.printStackTrace();
                throw new ServerException(500, 1000, "Database failure. Creation of moderator account failed.");
            }
        }

        return moderator;
    }

    /**
     * Performs an update on the data of the moderator account which is identified by the given id and the access token.
     * The moderator object contains the fields which should be updated and the new values. As far as no data
     * and access conditions are harmed, the fields will be updated in the database.
     *
     * @param accessToken The access token of the requestor.
     * @param moderatorId The id of the moderator account which should be updated.
     * @param moderator The moderator object with the new data values which have been transmitted with the request.
     * @return The moderator object with the updated data values.
     * @throws ServerException If the new data values have harmed certain conditions, the moderator is not authorized,
     * doesn't have the required permissions or if a database failure has occurred.
     */
    public Moderator changeModerator(String accessToken, int moderatorId, Moderator moderator) throws ServerException {
        // TODO
        return null;
    }


}
