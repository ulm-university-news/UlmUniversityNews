package ulm.university.news.controller;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.Moderator;
import ulm.university.news.data.enums.Language;
import ulm.university.news.data.enums.TokenType;
import ulm.university.news.manager.email.EmailManager;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ModeratorNameAlreadyExistsException;
import ulm.university.news.util.exceptions.ServerException;
import ulm.university.news.util.exceptions.TokenAlreadyExistsException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;
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
        if (moderator == null || moderator.getName() == null || moderator.getPassword() == null || moderator.getEmail()
                == null || moderator.getFirstName() == null || moderator.getLastName() == null ||
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
                moderatorDBM.storeModerator(moderator);
                successful = true;
            } catch (TokenAlreadyExistsException e) {
                logger.info(e.getMessage());
                // Create a new access token for the moderator.
                moderator.createModeratorToken();
            } catch (ModeratorNameAlreadyExistsException e) {
                logger.info(e.getMessage());
                // TODO 500 Internal Server Error or rather 400 Bad Request or something?
                logger.error(LOG_SERVER_EXCEPTION, 500, MODERATOR_NAME_ALREADY_EXISTS, "Moderator name already exits.");
                throw new ServerException(500, MODERATOR_NAME_ALREADY_EXISTS);
            } catch (DatabaseException e) {
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Creation of moderator " +
                        "account failed.");
                throw new ServerException(500, DATABASE_FAILURE);
            }
        }

        // TODO Send account created email to moderator.

        /// Clear fields which should not be delivered to the requestor.
        moderator.setPassword(null);
        moderator.setServerAccessToken(null);

        return moderator;
    }

    /**
     * Gets the moderator data identified by a given moderator id from the database.
     *
     * @param accessToken The access token of the requestor.
     * @param moderatorId The id of the moderator account which should be delivered.
     * @return The found moderator object.
     * @throws ServerException If the referred resource isn't found, the authorization of the requestor fails or the
     * requestor isn't allowed to perform the operation. Furthermore, a failure of the database also causes a
     * ServerException.
     */
    public Moderator getModerator(String accessToken, int moderatorId) throws ServerException {
        TokenType tokenType = verifyAccessToken(accessToken);

        // Check the given access token.
        if (tokenType == TokenType.USER) {
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, "User is not allowed to perform the requested " +
                    "operation.");
            throw new ServerException(403, USER_FORBIDDEN);
        } else if (tokenType == TokenType.INVALID) {
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }

        boolean isOwnAccountRequested;
        Moderator moderatorDB;
        try {
            // Get moderator (requestor) identified by access token from database.
            moderatorDB = moderatorDBM.getModeratorByToken(accessToken);
            isOwnAccountRequested = moderatorId == moderatorDB.getId();
            // Only an administrator can get another than their own moderator account.
            if (!moderatorDB.isAdmin() && !isOwnAccountRequested) {
                logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Only an administrator is allowed to " +
                        "perform the requested operation.");
                throw new ServerException(403, MODERATOR_FORBIDDEN);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't get moderator " +
                    "account by access token.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        if (!isOwnAccountRequested) {
            try {
                // Get requested moderator identified by id from database.
                moderatorDB = moderatorDBM.getModeratorById(moderatorId);
            } catch (DatabaseException e) {
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't get moderator " +
                        "account by name.");
                throw new ServerException(500, DATABASE_FAILURE);
            }

            // Verify moderator account exists.
            if (moderatorDB == null) {
                logger.error(LOG_SERVER_EXCEPTION, 404, MODERATOR_NOT_FOUND, "Moderator account not found.");
                throw new ServerException(404, MODERATOR_NOT_FOUND);
            }
        }

        // Clear fields which should not be delivered to the requestor.
        moderatorDB.setPassword(null);
        moderatorDB.setServerAccessToken(null);

        return moderatorDB;
    }

    /**
     * Get the moderator data of all existing moderator accounts from the database. The requested accounts can be
     * restricted to a specific selection.
     *
     * @param accessToken The access token of the requestor.
     * @param isLocked Defines weather just locked or unlocked accounts are requested.
     * @param isAdmin Defines weather just admin accounts are requested or not.
     * @return A list with all found moderator objects.
     * @throws ServerException ServerException If the authorization of the requestor fails or the requestor isn't
     * allowed to perform the operation. Furthermore, a failure of the database also causes a ServerException.
     */
    public List<Moderator> getModerators(String accessToken, Boolean isLocked, Boolean isAdmin) throws
            ServerException {
        TokenType tokenType = verifyAccessToken(accessToken);

        // Check the given access token.
        if (tokenType == TokenType.USER) {
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, "User is not allowed to perform the requested " +
                    "operation.");
            throw new ServerException(403, USER_FORBIDDEN);
        } else if (tokenType == TokenType.INVALID) {
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }

        List<Moderator> moderators;
        try {
            // Get moderator (requestor) identified by access token from database.
            Moderator moderatorDB = moderatorDBM.getModeratorByToken(accessToken);
            // Only an administrator can get all moderator accounts.
            if (!moderatorDB.isAdmin()) {
                logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Only an administrator is allowed to " +
                        "perform the requested operation.");
                throw new ServerException(403, MODERATOR_FORBIDDEN);
            }
            // Get all requested moderator accounts.
            moderators = moderatorDBM.getModerators(isLocked, isAdmin);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't get moderator " +
                    "account by access token.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        return moderators;
    }

    /**
     * Performs an update on the data of the moderator account which is identified by the given id. The moderator
     * object contains the fields which should be updated and the new values. As far as no data and access conditions
     * are harmed, the fields will be updated in the database.
     *
     * @param accessToken The access token of the requestor.
     * @param moderatorId The id of the moderator account which should be updated.
     * @param moderator The moderator object with the new data values which have been transmitted with the request.
     * @return The moderator object with the updated data values.
     * @throws ServerException If the new data values have harmed certain conditions, the moderator is not authorized,
     * doesn't have the required permissions or if a database failure has occurred.
     */
    public Moderator changeModerator(String accessToken, int moderatorId, Moderator moderator) throws ServerException {
        if (moderator.getFirstName() == null && moderator.getLastName() == null && moderator.getLanguage() == null &&
                moderator.getEmail() == null && moderator.getPassword() == null && moderator.isLocked() == null && moderator
                .isAdmin() == null) {
            logger.error(LOG_SERVER_EXCEPTION, 400, MODERATOR_DATA_INCOMPLETE, "Moderator PATCH data is incomplete.");
            throw new ServerException(400, MODERATOR_DATA_INCOMPLETE);
        }

        // Check the given access token.
        TokenType tokenType = verifyAccessToken(accessToken);
        if (tokenType == TokenType.USER) {
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, "User is not allowed to perform the requested " +
                    "operation.");
            throw new ServerException(403, USER_FORBIDDEN);
        } else if (tokenType == TokenType.INVALID) {
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }

        boolean isOwnAccountChanged;
        Moderator moderatorDB;
        try {
            // Get moderator (requestor) identified by access token from database.
            moderatorDB = moderatorDBM.getModeratorByToken(accessToken);
            isOwnAccountChanged = moderatorId == moderatorDB.getId();
            // Only an administrator can alter another than their own moderator account.
            if (!moderatorDB.isAdmin() && !isOwnAccountChanged) {
                logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Only an administrator is allowed to " +
                        "perform the requested operation.");
                throw new ServerException(403, MODERATOR_FORBIDDEN);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't get moderator " +
                    "account by access token.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        if (!isOwnAccountChanged) {
            try {
                // Get requested moderator identified by id from database.
                moderatorDB = moderatorDBM.getModeratorById(moderatorId);
            } catch (DatabaseException e) {
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't get moderator " +
                        "account by id.");
                throw new ServerException(500, DATABASE_FAILURE);
            }
            // Verify moderator account exists.
            if (moderatorDB == null) {
                logger.error(LOG_SERVER_EXCEPTION, 404, MODERATOR_NOT_FOUND, "Moderator account not found.");
                throw new ServerException(404, MODERATOR_NOT_FOUND);
            }
            moderatorDB = updateModeratorAsAdmin(moderator, moderatorDB);
        } else {
            moderatorDB = updateModerator(moderator, moderatorDB);
        }

        // Update changed moderator in the database.
        try {
            moderatorDBM.updateModerator(moderatorDB);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't update moderator.");
            logger.debug("SQL error:{}", e.getMessage());
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // TODO Send account (un)locked / (remove/add) admin rights email to moderator.

        // Clear fields which should not be delivered to the requestor.
        moderatorDB.setPassword(null);
        moderatorDB.setServerAccessToken(null);

        return moderatorDB;
    }

    /**
     * Updates several fields of the moderator object retrieved from the database. Only the following fields can be
     * changed: locked and admin.
     *
     * @param moderator The moderator object with the updated data values.
     * @param moderatorDB The moderator object from the database.
     * @return The complete updated moderator object.
     * @throws ServerException If the given data harms certain conditions.
     */
    private Moderator updateModeratorAsAdmin(Moderator moderator, Moderator moderatorDB) throws ServerException {
        if (moderator.isLocked() == null && moderator.isAdmin() == null) {
            logger.error(LOG_SERVER_EXCEPTION, 400, MODERATOR_DATA_INCOMPLETE, "Moderator PATCH data is incomplete.");
            throw new ServerException(400, MODERATOR_DATA_INCOMPLETE);
        }
        // Only update fields which are set.
        if (moderator.isLocked() != null) {
            moderatorDB.setLocked(moderator.isLocked());
        }
        if (moderator.isAdmin() != null) {
            moderatorDB.setAdmin(moderator.isAdmin());
        }
        // TODO Send account (un)locked / deleted email to moderator.
        return moderatorDB;
    }

    /**
     * Updates several fields of the moderator object retrieved from the database. Only the following fields can be
     * changed: firstName, lastName, language, email and password.
     *
     * @param moderator The moderator object with the updated data values.
     * @param moderatorDB The moderator object from the database.
     * @return The complete updated moderator object.
     * @throws ServerException If the given data harms certain conditions.
     */
    private Moderator updateModerator(Moderator moderator, Moderator moderatorDB) throws ServerException {
        if (moderator.getFirstName() == null && moderator.getLastName() == null && moderator.getLanguage() == null &&
                moderator.getEmail() == null && moderator.getPassword() == null) {
            logger.error(LOG_SERVER_EXCEPTION, 400, MODERATOR_DATA_INCOMPLETE, "Moderator PATCH data is incomplete.");
            throw new ServerException(400, MODERATOR_DATA_INCOMPLETE);
        }
        // Only update fields which are set.
        if (moderator.getFirstName() != null) {
            moderatorDB.setFirstName(moderator.getFirstName());
        }
        if (moderator.getLastName() != null) {
            moderatorDB.setLastName(moderator.getLastName());
        }
        if (moderator.getLanguage() != null) {
            moderatorDB.setLanguage(moderator.getLanguage());
        }
        // Verify proper structure of the given data.
        if (moderator.getEmail() != null) {
            if (!EmailValidator.getInstance().isValid(moderator.getEmail())) {
                logger.error(LOG_SERVER_EXCEPTION, 400, MODERATOR_INVALID_EMAIL, "Email address is invalid.");
                throw new ServerException(400, MODERATOR_INVALID_EMAIL);
            } else {
                moderatorDB.setEmail(moderator.getEmail());
            }
        }
        if (moderator.getPassword() != null){
            if (!Pattern.compile(PASSWORD_PATTERN).matcher(moderator.getPassword()).matches()) {
                logger.error(LOG_SERVER_EXCEPTION, 400, MODERATOR_INVALID_PASSWORD, "Password is invalid.");
                throw new ServerException(400, MODERATOR_INVALID_PASSWORD);
            } else {
                moderatorDB.setPassword(moderator.getPassword());
                moderatorDB.encryptPassword();
            }
        }
        return moderatorDB;
    }

    /**
     * Attempts to delete the moderator account identified by the moderators id. If there are still links between
     * this moderator account and other data, the account will be marked as deleted. The actual deletion will be
     * performed when no more links are attached to the moderator account.
     *
     * @param accessToken The access token of the requestor.
     * @param moderatorId The id of the moderator account which should be deleted.
     * @throws ServerException If the referred resource isn't found, the authorization of the requestor fails or the
     * requestor isn't allowed to perform the operation. Furthermore, a failure of the database also causes a
     * ServerException.
     */
    public void deleteModerator(String accessToken, int moderatorId) throws ServerException {
        // Check the given access token.
        TokenType tokenType = verifyAccessToken(accessToken);
        if (tokenType == TokenType.USER) {
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, "User is not allowed to perform the requested " +
                    "operation.");
            throw new ServerException(403, USER_FORBIDDEN);
        } else if (tokenType == TokenType.INVALID) {
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }

        boolean isOwnAccountDeleted;
        Moderator moderatorDB;
        try {
            // Get moderator (requestor) identified by access token from database.
            moderatorDB = moderatorDBM.getModeratorByToken(accessToken);
            isOwnAccountDeleted = moderatorId == moderatorDB.getId();
            // Only an administrator can delete another than their own moderator account.
            if (!moderatorDB.isAdmin() && !isOwnAccountDeleted) {
                logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Only an administrator is allowed to " +
                        "perform the requested operation.");
                throw new ServerException(403, MODERATOR_FORBIDDEN);
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't get moderator " +
                    "account by access token.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        if (!isOwnAccountDeleted) {
            try {
                // Get requested moderator identified by id from database.
                moderatorDB = moderatorDBM.getModeratorById(moderatorId);
            } catch (DatabaseException e) {
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't get moderator " +
                        "account by id.");
                throw new ServerException(500, DATABASE_FAILURE);
            }
            // Verify moderator account exists.
            if (moderatorDB == null) {
                logger.error(LOG_SERVER_EXCEPTION, 404, MODERATOR_NOT_FOUND, "Moderator account not found.");
                throw new ServerException(404, MODERATOR_NOT_FOUND);
            }
        }

        if(moderatorDB.isAdmin()) {
            logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, "Administrator accounts can't be deleted.");
            throw new ServerException(403, MODERATOR_FORBIDDEN);
        }

        // TODO Get channels ...
    }

    /**
     * Resets the password of the moderator account identified by name and sends the new password to the moderators
     * email address.
     *
     * @param moderatorName The name of the moderator account.
     * @throws ServerException If moderator name has harmed certain conditions, moderator account was not found or if
     * a failure with the database or the email server has occurred.
     */
    public void resetPassword(String moderatorName) throws ServerException {
        // Verify proper moderator name.
        if (moderatorName == null) {
            logger.error(LOG_SERVER_EXCEPTION, 400, DATA_INCOMPLETE, "Moderator name is not set.");
            throw new ServerException(400, DATA_INCOMPLETE);
        } else if (!Pattern.compile(NAME_PATTERN).matcher(moderatorName).matches()) {
            logger.error(LOG_SERVER_EXCEPTION, 400, MODERATOR_INVALID_NAME, "Name is invalid.");
            throw new ServerException(400, MODERATOR_INVALID_NAME);
        }

        // Get moderator from database.
        Moderator moderatorDB;
        try {
            moderatorDB = moderatorDBM.getModeratorByName(moderatorName);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't get moderator " +
                    "account by name.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Verify moderator account exists.
        if (moderatorDB == null) {
            logger.error(LOG_SERVER_EXCEPTION, 404, MODERATOR_NOT_FOUND, "Moderator account not found.");
            throw new ServerException(404, MODERATOR_NOT_FOUND);
        }

        // Generate a new password.
        String newPassword = generatePassword();

        // TODO Internationalization. Get mail text from properties file.
        String subject = "Ulm University News - Password Reset";
        String message;
        message = "Hello " + moderatorDB.getFirstName() + " " + moderatorDB.getLastName() + ",\n\n";
        message += "your password has been reset.\nYour new password is: ";
        message += newPassword + "\n\n";
        message += "Regards, the team of Ulm University News";

        // Send email with new plain text password to the moderator.
        if (!EmailManager.getInstance().sendMail(moderatorDB.getEmail(), subject, message)) {
            logger.error(LOG_SERVER_EXCEPTION, 500, EMAIL_FAILURE, "Couldn't sent email to moderator. Password " +
                    "was not reset.");
            throw new ServerException(500, EMAIL_FAILURE);
        }

        // Hash, set and encrypt the new moderator password.
        moderatorDB.setPassword(hashPassword(newPassword));
        moderatorDB.encryptPassword();

        // Update moderator password in database.
        try {
            moderatorDBM.updatePassword(moderatorDB.getId(), moderatorDB.getPassword());
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't update password.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * Checks if the given combination of moderator name and password is valid. If moderator is authenticated
     * properly, the moderator data including the server access token is returned to the requestor.
     *
     * @param moderatorName The name of the moderator account.
     * @param password The password entered by the moderator.
     * @return The moderator data from the database including the access token.
     * @throws ServerException If moderator name or password have harmed certain conditions, moderator account was not
     * found or if a database failure has occurred.
     */
    public Moderator authenticateModerator(String moderatorName, String password) throws ServerException {
        // Verify proper moderator data.
        if (moderatorName == null || password == null) {
            logger.error(LOG_SERVER_EXCEPTION, 400, DATA_INCOMPLETE, "Authentication data is incomplete.");
            throw new ServerException(400, DATA_INCOMPLETE);
        } else if (!Pattern.compile(NAME_PATTERN).matcher(moderatorName).matches()) {
            // TODO Send 401 Unauthorized instead of 400 Bad Request?
            logger.error(LOG_SERVER_EXCEPTION, 401, MODERATOR_INVALID_NAME, "Name is invalid.");
            throw new ServerException(401, MODERATOR_INVALID_NAME);
        } else if (!Pattern.compile(PASSWORD_PATTERN).matcher(password).matches()) {
            // TODO Send 401 Unauthorized instead of 400 Bad Request?
            logger.error(LOG_SERVER_EXCEPTION, 401, MODERATOR_INVALID_PASSWORD, "Password is invalid.");
            throw new ServerException(401, MODERATOR_INVALID_PASSWORD);
        }

        // Get moderator from database.
        Moderator moderatorDB;
        try {
            moderatorDB = moderatorDBM.getModeratorByName(moderatorName);
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't get moderator " +
                    "account by name.");
            throw new ServerException(500, DATABASE_FAILURE);
        }

        // Verify moderator account exists.
        if (moderatorDB == null) {
            // TODO Send 401 Unauthorized instead of 404 Not Found?
            logger.error(LOG_SERVER_EXCEPTION, 401, MODERATOR_NOT_FOUND, "Moderator account not found.");
            throw new ServerException(401, MODERATOR_NOT_FOUND);
        }

        // Check if moderator account is deleted.
        if (moderatorDB.isDeleted()) {
            logger.error(LOG_SERVER_EXCEPTION, 410, MODERATOR_DELETED, "Moderator account is deleted.");
            throw new ServerException(410, MODERATOR_DELETED);
        }

        // Check if moderator account is locked.
        if (moderatorDB.isLocked()) {
            logger.error(LOG_SERVER_EXCEPTION, 423, MODERATOR_LOCKED, "Moderator account is locked.");
            throw new ServerException(423, MODERATOR_LOCKED);
        }

        // Check if password is correct.
        if (!moderatorDB.verifyPassword(password)) {
            logger.error(LOG_SERVER_EXCEPTION, 401, MODERATOR_INCORRECT_PASSWORD, "Moderator password incorrect.");
            throw new ServerException(401, MODERATOR_INCORRECT_PASSWORD);
        }

        // Do not return the encrypted password to the requestor.
        moderatorDB.setPassword(null);

        // TODO Send back MODERATOR_INCORRECT_PASSWORD, MODERATOR_INVALID_NAME, MODERATOR_INVALID_PASSWORD,
        // TODO MODERATOR_NOT_FOUND, MODERATOR_DELETED, MODERATOR_LOCKED or just the same for all?
        // TODO For example: MODERATOR_UNAUTHORIZED

        return moderatorDB;
    }

    /**
     * Generates a new random password for the Moderator.
     *
     * @return The generated password.
     */
    private String generatePassword() {
        // Define possible characters of the generated password.
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        // Define length of the generated password.
        int len = 12;

        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(len);
        // Choose len random characters of the alphabet as password.
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * Hashes the given password for the Moderator.
     *
     * @return The hashed password.
     */
    private String hashPassword(String password) {
        String passwordHash = null;
        try {
            // Calculate hash on the given password.
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(password.getBytes());

            // Transform the bytes (8 bit signed) into a hexadecimal format.
            StringBuilder hashString = new StringBuilder();
            for (int i = 0; i < hash.length; i++) {
                /*
                Format parameters: %[flags][width][conversion]
                Flag '0' - The result will be zero padded.
                Width '2' - The width is 2 as 1 byte is represented by two hex characters.
                Conversion 'x' - Result is formatted as hexadecimal integer, uppercase.
                 */
                hashString.append(String.format("%02x", hash[i]));
            }
            passwordHash = hashString.toString();
            logger.debug("Moderator password hashed to {}.", passwordHash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Could not hash the moderator password. The expected digest algorithm is not available.", e);
        }
        return passwordHash;
    }
}
