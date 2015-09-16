package ulm.university.news.controller;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.Moderator;
import ulm.university.news.data.enums.Language;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ModeratorNameAlreadyExistsException;
import ulm.university.news.util.exceptions.ServerException;
import ulm.university.news.util.exceptions.TokenAlreadyExistsException;

import java.util.regex.Pattern;

import static ulm.university.news.util.Constants.*;

/**
 * The ModeratorController handles requests concerning the moderator resources. It offers methods to query moderator
 * account data as well as methods to create new moderator accounts or update existing ones. This class also handles
 * requests of administrators.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class ModeratorController extends AccessController {

    /** The logger instance for ModeratorController. */
    private static final Logger logger = LoggerFactory.getLogger(ModeratorController.class);

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
        logger.debug("Start with moderator:{}.", moderator);
        // Perform checks on the received data. If the data isn't accurate the moderator can't be created.
        // In case of inaccuracy, send 400 Bad Request and abort execution.
        if (moderator.getName() == null || moderator.getPassword() == null || moderator.getEmail() == null ||
                moderator.getFirstName() == null || moderator.getLastName() == null ||
                moderator.getMotivation() == null) {
            logger.error(LOG_SERVER_EXCEPTION, 400, MODERATOR_DATA_INCOMPLETE, "Moderator data is incomplete.");
            throw new ServerException(400, MODERATOR_DATA_INCOMPLETE);
        } else if (!Pattern.compile(NAME_PATTERN).matcher(moderator.getName()).matches()) {
            logger.error(LOG_SERVER_EXCEPTION, 400, MODERATOR_INVALID_NAME, "Name is invalid.");
            throw new ServerException(400, MODERATOR_INVALID_NAME);
        } else if (!EmailValidator.getInstance().isValid(moderator.getEmail())) {
            logger.error(LOG_SERVER_EXCEPTION, 400, MODERATOR_INVALID_EMAIL, "Email address is invalid.");
            throw new ServerException(400, MODERATOR_INVALID_EMAIL);
        } else if (!Pattern.compile(PASSWORD_PATTERN).matcher(moderator.getPassword()).matches()) {
            logger.error(LOG_SERVER_EXCEPTION, 400, MODERATOR_INVALID_PASSWORD, "Password is invalid.");
            throw new ServerException(400, MODERATOR_INVALID_PASSWORD);
        }

        // Initialize remaining fields.
        moderator.setLocked(true);
        moderator.setAdmin(false);
        moderator.setDeleted(false);
        if (moderator.getLanguage() == null) {
            moderator.setLanguage(Language.ENGLISH);
        }

        // Create the accessToken which will identify the new moderator in the system.
        moderator.createModeratorToken();

        // Encrypt the given password.
        moderator.encryptPassword();

        boolean successful = false;
        while (!successful) {
            try {
                moderatorDB.storeModerator(moderator);
                successful = true;
            } catch (TokenAlreadyExistsException e) {
                logger.info(e.getMessage());
                // Create a new access token for the moderator.
                moderator.createModeratorToken();
            } catch (ModeratorNameAlreadyExistsException e) {
                logger.info(e.getMessage());
                logger.error(LOG_SERVER_EXCEPTION, 500, MODERATOR_NAME_ALREADY_EXISTS, "Moderator name already exits.");
                throw new ServerException(500, MODERATOR_NAME_ALREADY_EXISTS);
            } catch (DatabaseException e) {
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Creation of moderator " +
                        "account failed.");
                throw new ServerException(500, DATABASE_FAILURE);
            }
        }

        // Do not return the encrypted password to the requestor.
        moderator.setPassword(null);

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
