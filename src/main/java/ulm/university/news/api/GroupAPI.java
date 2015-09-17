package ulm.university.news.api;

import ulm.university.news.controller.GroupController;
import ulm.university.news.data.Group;
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
@Path("/group")
public class GroupAPI {

    /** Instance of the GroupController class. */
    private GroupController groupController = new GroupController();

    /**
     * Creates an instance of the GroupAPI class.
     */
    public GroupAPI(){

    }

    /**
     * Creates a new group. The data of the new group is provided within the group object. The created group resource
     * will be returned including the URI which can be used to access the group resource.
     *
     * @param accessToken The access token of the requestor.
     * @param group The group object which contains the data of the new group.
     * @param uriInfo Information about the URI of the request.
     * @return Response object including the created resource and the URI to access this resource.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createGroup(@HeaderParam("Authorization") String accessToken, Group group, @Context UriInfo
            uriInfo) throws ServerException {
        group = groupController.createGroup(accessToken,group);
        // Create the URI which can be used to access the created resource.
        URI createdURI = URI.create(uriInfo.getBaseUri().toString() + "group" + "/" + group.getId());
        // Return the created resource and set the location header.
        return Response.status(Response.Status.CREATED).location(createdURI).entity(group).build();
    }



}
