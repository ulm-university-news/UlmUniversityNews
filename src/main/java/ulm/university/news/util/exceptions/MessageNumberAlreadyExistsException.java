package ulm.university.news.util.exceptions;

/**
 * The class represents an exception which is caused by a duplicate entry of a message number in the database. If
 * this error occurs, the message needs to be stored with another message number.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class MessageNumberAlreadyExistsException extends Exception{

    /**
     * Creates an instance of the MessageNNumberAlreadyExistsException class.
     *
     * @param message A description of the error that has occurred.
     */
    public MessageNumberAlreadyExistsException(String message){
        super(message);
    }
}
