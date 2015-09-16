package ulm.university.news.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.manager.database.ModeratorDatabaseManager;
import ulm.university.news.manager.database.UserDatabaseManager;
import ulm.university.news.util.Constants;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ServerException;
import ulm.university.news.data.enums.TokenType;

import static ulm.university.news.util.Constants.DATABASE_FAILURE;
import static ulm.university.news.util.Constants.LOG_SERVER_EXCEPTION;


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
    protected UserDatabaseManager userDB = new UserDatabaseManager();
    /** Instance of the ModeratorDatabaseManager class. */
    protected ModeratorDatabaseManager moderatorDB = new ModeratorDatabaseManager();

    /**
     * Creates an instance of the AccessController class.
     */
    public AccessController(){

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
        if(accessToken == null){
            return TokenType.INVALID;
        }
        try {
            if (accessToken.matches(Constants.USER_TOKEN_PATTERN) && userDB.isValidUserToken(accessToken)) {
                tokenType = TokenType.USER;
            } else if (accessToken.matches(Constants.MODERATOR_TOKEN_PATTERN) &&
                    moderatorDB.isValidModeratorToken(accessToken)) {
                tokenType = TokenType.MODERATOR;
            } else {
                tokenType = TokenType.INVALID;
            }
        }catch(DatabaseException e){
            logger.error(LOG_SERVER_EXCEPTION, 500, DATABASE_FAILURE, "Database Failure. Verification of access token" +
                    " failed.");
            throw new ServerException(500, DATABASE_FAILURE);
        }
        return tokenType;
    }

}
