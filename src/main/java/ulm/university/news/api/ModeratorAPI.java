package ulm.university.news.api;

import ulm.university.news.controller.ModeratorController;
import ulm.university.news.data.Moderator;
import ulm.university.news.util.exceptions.ServerException;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
@Path("/moderator")
public class ModeratorAPI {

    /** Instance of the ModeratorController class. */
    private ModeratorController moderatorCtrl = new ModeratorController();

    @GET
    @Path("/{id}")
    public Response getModerator(@PathParam("id") int id) {

        String output = "Moderator id: " + id;

        return Response.status(200).entity(output).build();
    }

    /**
     * Create a new moderator account. The data of the new moderator is provided within the moderator object. The
     * generated moderator resource will be returned including the URI which can be used to access the resource.
     *
     * @param moderator A moderator object including the data of the new moderator.
     * @param uriInfo Information about the URI of this request.
     * @return Response object including the generated moderator object and a set Location Header.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createModerator(Moderator moderator,@Context UriInfo uriInfo){
        try {
            moderator = moderatorCtrl.createModerator(moderator);
        } catch (ServerException e) {
            e.printStackTrace();
            return Response.status(e.getHttpStatusCode()).entity(e).build();    // TODO
        }
        // Create the URI for the generated resource.
        URI createdURI = URI.create(uriInfo.getBaseUri().toString() + "moderator" + "/" + moderator.getId());
        // Return the generated user resource and set the Location Header.
        return Response.status(Response.Status.CREATED).contentLocation(createdURI).entity(moderator).build();
    }

    /**
     * Changes an existing moderator account. The data which should be changed is provided within the moderator
     * object. The changed moderator resource will be returned.
     *
     * @param accessToken The access token of the requestor.
     * @param id The id of the moderator account which should be changed.
     * @param moderator The changed moderator data.
     * @return Response object including the changed moderator object.
     */
    @PATCH
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public Response changeModerator(@HeaderParam("Authorization") String accessToken, @PathParam("id") int id,
                                    Moderator moderator){
        try {
            moderator = moderatorCtrl.changeModerator(accessToken, id, moderator);
        } catch (ServerException e) {
            ServerError se = new ServerError(e.getHttpStatusCode(),e.getErrorCode(), e.getMessage());
            return Response.status(e.getHttpStatusCode()).entity(se).build();    // TODO
        }
        return Response.status(Response.Status.OK).entity(moderator).build();
    }

}
