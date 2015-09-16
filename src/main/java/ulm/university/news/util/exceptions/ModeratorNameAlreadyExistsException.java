package ulm.university.news.util.exceptions;

/**
 * The ModeratorNameAlreadyExistsException is thrown when the uniqueness of the moderators name is harmed. A name of
 * a moderator needs to be unique to unambiguously identify a moderator by name in the whole system.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class ModeratorNameAlreadyExistsException extends Exception {

    /**
     * Generates a ModeratorNameAlreadyExistsException with a given error message.
     *
     * @param message The description of the error which caused this Exception.
     */
    public ModeratorNameAlreadyExistsException(String message){
        super(message);
    }

    /**
     * Generates a ModeratorNameAlreadyExistsException with a given error message and the Throwable which caused this
     * Exception.
     *
     * @param message The description of the error which caused this Exception.
     * @param cause An instance of a Throwable object which caused this Exception.
     */
    public ModeratorNameAlreadyExistsException(String message, Throwable cause){
        super(message,cause);
    }

}
