package ulm.university.news.util.exceptions;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class DatabaseException extends Exception{

    /**
     * Constructs a DatabaseException with a given error message.
     *
     * @param message The description of the error which has caused the exception.
     */
    public DatabaseException(String message){
        super(message);
    }

    /**
     * Constructs a DatabaseException with a given error message and the Throwable which has caused the exception.
     *
     * @param message The description of the error which has caused the exception.
     * @param cause An instance of a Throwable object which has caused this Exception.
     */
    public DatabaseException(String message, Throwable cause){
        super(message, cause);
    }

}
