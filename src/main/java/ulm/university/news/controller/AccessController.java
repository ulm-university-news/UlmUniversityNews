package ulm.university.news.controller;

import ulm.university.news.manager.database.ModeratorDatabaseManager;
import ulm.university.news.manager.database.UserDatabaseManager;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.ServerException;
import ulm.university.news.data.enums.TokenType;


/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class AccessController {

    /** Instance of the UserDatabaseManager. */
    private UserDatabaseManager userDB = new UserDatabaseManager();
    /** Instance of the ModeratorDatabaseManager. */
    private ModeratorDatabaseManager moderatorDB = new ModeratorDatabaseManager();


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
            if (accessToken.matches("[a-fA-F0-9]{56}") && userDB.isValidUserToken(accessToken)) {
                tokenType = TokenType.USER;
            } else if (accessToken.matches("[a-fA-F0-9]{64}") && moderatorDB.isValidModeratorToken(accessToken)) {
                tokenType = TokenType.MODERATOR;
            } else {
                tokenType = TokenType.INVALID;
            }
        }catch(DatabaseException e){
            //TODO Logging
            throw new ServerException(500, 0, e.getMessage());
        }
        return tokenType;
    }

}
