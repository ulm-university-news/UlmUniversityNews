package ulm.university.news.api;

import ulm.university.news.controller.ModeratorController;
import ulm.university.news.data.Moderator;
import ulm.university.news.util.PATCH;
import ulm.university.news.util.exceptions.ServerException;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

/**
 * The ModeratorAPI is responsible for accepting incoming moderator requests, reading the required data and handing
 * it over to the appropriate controller methods. After the request has been executed successfully, the ModeratorAPI
 * generates the response message. If the execution of the request has failed for whatever reasons, the
 * ServerException is handed over to the ErrorHandler class.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
@Path("/moderator")
public class ModeratorAPI {

    /** Instance of the ModeratorController class. */
    private ModeratorController moderatorCtrl = new ModeratorController();

    /**
     * Delivers the moderator data identified by a given moderator id.
     *
     * @param accessToken The access token of the requestor.
     * @param id The id of the moderator account which should be delivered.
     * @return Response object including the moderator data.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getModerator(@HeaderParam("Authorization") String accessToken, @PathParam("id") int id)
            throws ServerException {
        Moderator moderator = moderatorCtrl.getModerator(accessToken, id);
        // Return the moderator resource.
        return Response.status(Response.Status.OK).entity(moderator).build();
    }

    /**
     * Delivers the moderator data of all existing moderator accounts. The requested accounts can be
     * restricted to a specific selection by the given query params.
     *
     * @param accessToken The access token of the requestor.
     * @param isLocked Defines weather just locked or unlocked accounts are requested.
     * @param isAdmin Defines weather just admin accounts are requested or not.
     * @return Response object including a list with moderator data.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Moderator> getModerators(@HeaderParam("Authorization") String accessToken, @QueryParam("isLocked")
    Boolean isLocked, @QueryParam("isAdmin") Boolean isAdmin) throws ServerException {
        // Return all the requested moderator resources.
        return moderatorCtrl.getModerators(accessToken, isLocked, isAdmin);
    }

    /**
     * Create a new moderator account. The data of the new moderator is provided within the moderator object. The
     * generated moderator resource will be returned including the URI which can be used to access the resource.
     *
     * @param moderator The moderator object which contains the data from the request.
     * @param uriInfo Information about the URI of this request.
     * @return Response object including the created moderator object and a set Location Header.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createModerator(Moderator moderator, @Context UriInfo uriInfo) throws ServerException {
        moderator = moderatorCtrl.createModerator(moderator);
        // Create the URI for the created moderator resource.
        URI createdURI = URI.create(uriInfo.getBaseUri().toString() + "moderator" + "/" + moderator.getId());
        // Return the created moderator resource and set the Location Header.
        return Response.status(Response.Status.CREATED).contentLocation(createdURI).entity(moderator).build();
    }

    /**
     * Changes an existing moderator account. The data which should be changed is provided within the moderator
     * object. The changed moderator resource will be returned.
     *
     * @param accessToken The access token of the requestor.
     * @param id The id of the moderator account which should be changed.
     * @param moderator The changed moderator data.
     * @return Response object including the changed moderator data.
     * @throws ServerException If the execution of the PATCH request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public Response changeModerator(@HeaderParam("Authorization") String accessToken, @PathParam("id") int id,
                                    Moderator moderator) throws ServerException {
        moderator = moderatorCtrl.changeModerator(accessToken, id, moderator);
        // Return the changed moderator resource.
        return Response.status(Response.Status.OK).entity(moderator).build();
    }

    /**
     * Deletes the moderator account identified by the moderators id.
     *
     * @param accessToken The access token of the requestor.
     * @param id The id of the moderator account which should be deleted.
     * @return Response object with no additionally data.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{id}")
    public Response deleteModerator(@HeaderParam("Authorization") String accessToken, @PathParam("id") int id) throws
            ServerException {
        moderatorCtrl.deleteModerator(accessToken, id);
        // No resource is returned.
        return Response.status(Response.Status.OK).build();
    }

    /**
     * Resets the password of an existing moderator account. The data which is used to identify the moderator account
     * is provided within the moderator object.
     *
     * @param moderator Includes the moderators name.
     * @return Response object with no additionally data.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Path("/password")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response resetPassword(Moderator moderator) throws ServerException {
        moderatorCtrl.resetPassword(moderator.getName());
        // No resource is returned.
        return Response.status(Response.Status.OK).build();
    }

    /**
     * Authenticates a moderator via name and password. The required data is provided within the moderator object.
     * The authenticated moderator resource will be returned.
     *
     * @param moderator Includes the moderators name and password.
     * @return Response object including the moderator data.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Path("/authentication")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticateModerator(Moderator moderator) throws ServerException {
        moderator = moderatorCtrl.authenticateModerator(moderator.getName(), moderator.getPassword());
        // Return the changed moderator resource.
        return Response.status(Response.Status.OK).entity(moderator).build();
    }

}
