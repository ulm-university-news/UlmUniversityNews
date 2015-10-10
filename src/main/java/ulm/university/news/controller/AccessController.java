package ulm.university.news.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.Moderator;
import ulm.university.news.data.User;
import ulm.university.news.manager.database.ModeratorDatabaseManager;
import ulm.university.news.manager.database.UserDatabaseManager;
import ulm.university.news.util.Constants;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ServerException;
import ulm.university.news.data.enums.TokenType;

import static ulm.university.news.util.Constants.*;
import static ulm.university.news.util.Constants.TOKEN_INVALID;


/**
 * The AccessController provides the basic functionality of verifying an access token and the determination of the
 * type of an access token. This functionality is required by any other controller classes in order to identify the
 * requestor and to perform the authorization.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class AccessController {

    /** An instance of the Logger class which performs logging for the AccessController class. */
    private static final Logger logger = LoggerFactory.getLogger(AccessController.class);

    /** Instance of the UserDatabaseManager class. */
    protected UserDatabaseManager userDBM = new UserDatabaseManager();
    /** Instance of the ModeratorDatabaseManager class. */
    protected ModeratorDatabaseManager moderatorDBM = new ModeratorDatabaseManager();

    /**
     * Creates an instance of the AccessController class.
     */
    public AccessController() {

    }

    /**
     * Determines the type of a given access token and checks whether it is of a valid format. Furthermore, if the
     * token is of a valid format and type, it is checked whether it belongs to a user or moderator account. The
     * method returns the type of the token.
     *
     * @param accessToken The access token which should be verified.
     * @return TokenType.USER if it is a valid user access token, TokenType.Moderator if it is a valid moderator
     * access token, or TokenType.Invalid if one of the checks has failed.
     * @throws ServerException If the connection to the database fails.
     */
    public TokenType verifyAccessToken(String accessToken) throws ServerException {
        TokenType tokenType = null;
        if (accessToken == null) {
            return TokenType.INVALID;
        }
        try {
            if (accessToken.matches(Constants.USER_TOKEN_PATTERN) && userDBM.isValidUserToken(accessToken)) {
                tokenType = TokenType.USER;
            } else if (accessToken.matches(Constants.MODERATOR_TOKEN_PATTERN) &&
                    moderatorDBM.isValidModeratorToken(accessToken)) {
                tokenType = TokenType.MODERATOR;
            } else {
                tokenType = TokenType.INVALID;
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure. Verification of access token" +
                    " failed.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
        return tokenType;
    }

    /**
     * Checks if the requestor identified by access token is a valid moderator. Validates the access token and rejects
     * the request if the access token does not identify a valid moderator.
     *
     * @param accessToken The access token of the requestor.
     * @return The valid moderator object of the requestor from the database.
     * @throws ServerException If the requestor doesn't provide a valid access token or if the requestor isn't a
     * valid moderator (it's a user).
     */
    public Moderator verifyModeratorAccess(String accessToken) throws ServerException {
        TokenType tokenType = verifyAccessToken(accessToken);

        // Check the given access token. Only a moderator is allowed to perform the request.
        if (tokenType == TokenType.USER) {
            logger.error(LOG_SERVER_EXCEPTION, 403, USER_FORBIDDEN, "User is not allowed to perform the requested " +
                    "operation.");
            throw new ServerException(403, USER_FORBIDDEN);
        } else if (tokenType == TokenType.INVALID) {
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, "To perform this operation a valid access token " +
                    "needs to be provided.");
            throw new ServerException(401, TOKEN_INVALID);
        }

        try {
            // Get moderator (requestor) identified by access token from database.
            Moderator moderatorDB = moderatorDBM.getModeratorByToken(accessToken);
            if (moderatorDB.isLocked()) {
                logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_LOCKED, "Moderator account is locked.");
                throw new ServerException(403, MODERATOR_LOCKED);
            } else {
                // All checks passed. Return valid moderator.
                return moderatorDB;
            }
        } catch (DatabaseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database failure. Couldn't get moderator " +
                    "account by access token.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }


    /**
     * Checks if the requestor identified by the access token is a valid user. Validates the access token and rejects
     * the request if the access token does not identify a valid user.
     *
     * @param accessToken The access token of the requestor.
     * @return The valid user object of the requestor from the database.
     * @throws ServerException Id the reqeustor doesn't provide a valid access token of if the requestor isn't a
     * valid user, e.g. it is a moderator.
     */
    public User verifyUserAccess(String accessToken) throws ServerException {
        TokenType tokenType = verifyAccessToken(accessToken);

        // Check the given access token. Only an user is allowed to perform the request.
        if (tokenType == TokenType.INVALID) {
            String errMsg = "To perform this operation a valid access token needs to be provided.";
            logger.error(LOG_SERVER_EXCEPTION, 401, TOKEN_INVALID, errMsg);
            throw new ServerException(401, TOKEN_INVALID);
        } else if (tokenType == TokenType.MODERATOR) {
            String errMsg = "Moderator is not allowed to perform the requested operation.";
            logger.error(LOG_SERVER_EXCEPTION, 403, MODERATOR_FORBIDDEN, errMsg);
            throw new ServerException(403, MODERATOR_FORBIDDEN);
        }

        try {
            // Get user (requestor) identified by access token from database.
            return userDBM.getUserByToken(accessToken);
        } catch (DatabaseException e) {
            String errMsg = "Database failure. Couldn't get user account by access token.";
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, errMsg);
            throw new ServerException(500, DATABASE_FAILURE);
        }
    }

    /**
     * Checks whether the requestor which is identified by the given access token is a system administrator. This
     * method only determines whether the requestor has administrator rights or not, it does not return the moderator
     * object with the data of the account. This method can be used if the account data of the requestor is
     * irrelevant in the further execution of the request.
     *
     * @param accessToken The access token of the requestor.
     * @return Returns true if the requestor has system administrator rights, false otherwise.
     * @throws ServerException If the administrator status of the requestor could not be determined due to a database
     * failure.
     */
    public boolean isAdministrator(String accessToken) throws ServerException {
        boolean isAdmin = false;
        if (accessToken != null) {
            try {
                /* Get moderator (requestor) identified by access token from database. If its not a moderator token
                the method will return null.*/
                Moderator moderatorDB = moderatorDBM.getModeratorByToken(accessToken);
                // Check if moderator is a system administrator.
                if (moderatorDB != null && moderatorDB.isAdmin()) {
                    isAdmin = true;
                }
            } catch (DatabaseException e) {
                String errMsg = "Database failure. Couldn't determine administrator status of moderator account by " +
                        "access token.";
                logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, errMsg);
                throw new ServerException(500, DATABASE_FAILURE);
            }
        }
        return isAdmin;
    }

}
