package ulm.university.news.util.exceptions;

/**
 * The TokenAlreadyExistsException is thrown when the uniqueness of the access token is harmed. An access token needs
 * to be unique to unambiguously identify an user or moderator in the whole system.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class TokenAlreadyExistsException extends Exception {

    /**
     * Generates a TokenAlreadyExistsException with a given error message.
     *
     * @param message The description of the error which caused this Exception.
     */
    public TokenAlreadyExistsException(String message){
        super(message);
    }

    /**
     * Generates a TokenAlreadyExistsException with a given error message and the Throwable which caused this
     * Exception.
     *
     * @param message The description of the error which caused this Exception.
     * @param cause An instance of a Throwable object which caused this Exception.
     */
    public TokenAlreadyExistsException(String message, Throwable cause){
        super(message,cause);
    }

}
