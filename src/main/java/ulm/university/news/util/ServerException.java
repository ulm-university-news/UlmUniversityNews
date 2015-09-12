package ulm.university.news.util;

/**
 * Created by Philipp on 12.09.2015.
 */
public class ServerException extends Exception{

    /** Determines the HTTP status code which should be returned in the HTTP Response if this exception occurs. */
    private int httpStatusCode;
    /** An application specific error code which identifies the error that has occurred. */
    private int errorCode;

    /**
     * Constructs a new ServerException with the specified HTTP status code, error code and error message.
     *
     * @param httpStatusCode The HTTP status code which should be returned in the response after this exception
     *                       has occurred.
     * @param errorCode The application specific error code for the occurred error.
     * @param message A message describing the occurred error.
     */
    public ServerException(int httpStatusCode, int errorCode, String message){
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.errorCode = errorCode;
    }

    /**
     * Returns the HTTP status code which should be returned in the HTTP Response.
     *
     * @return The HTTP status code.
     */
    public int getHttpStatusCode(){
        return httpStatusCode;
    }

    /**
     * Returns the application specific error code.
     *
     * @return The error code of the error which has caused this exception.
     */
    public int getErrorCode(){
        return errorCode;
    }


}
