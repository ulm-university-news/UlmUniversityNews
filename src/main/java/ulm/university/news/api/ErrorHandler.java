package ulm.university.news.api;

import ulm.university.news.util.exceptions.ServerException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * The ErrorHandler class is responsible for handling ServerExceptions which have occurred during the execution of
 * the request. If an ServerException has occurred, the execution is aborted and an error message needs to be sent
 * to the client. The ErrorHandler translates the information from the exception into an error message which is then
 * sent to the client.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
@Provider
public class ErrorHandler implements ExceptionMapper<ServerException> {

    /**
     * Constructs server error messages which are sent via HTTP Response to the client if an ServerException
     * has occurred.
     *
     * @param e The ServerException that has occurred.
     * @return Response object which contains the error message.
     */
    @Override
    public Response toResponse(ServerException e) {

        ServerError se = new ServerError(e.getHttpStatusCode(), e.getErrorCode(), e.getMessage());
        return Response.status(e.getHttpStatusCode()).entity(se).build();
    }
}
