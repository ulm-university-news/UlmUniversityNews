package ulm.university.news.api;

import ulm.university.news.controller.GroupController;
import ulm.university.news.data.Group;
import ulm.university.news.data.enums.GroupType;
import ulm.university.news.util.PATCH;
import ulm.university.news.util.exceptions.ServerException;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.URI;
import java.util.List;

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

    /**
     * Returns a list of groups. The result of the method depends on the search parameters. Setting the search
     * parameters for the group name or type influences the result as only groups will be returned which fit the
     * search parameters.
     *
     * @param accessToken The access token of the requestor.
     * @param groupName The search parameter for the group name.
     * @param groupType The search parameter for the group type.
     * @return A list of groups. The list can also be empty.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Group> getGroups(@HeaderParam("Authorization") String accessToken,@QueryParam("groupName") String
            groupName, @QueryParam("groupType") GroupType groupType) throws ServerException {
        List<Group> groups = groupController.getGroups(accessToken, groupName, groupType);
        return groups;
    }

    /**
     * Returns the group which is identified by the specified id. It can be defined whether the group object should
     * contain a list of all participants of the group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group which should be returned.
     * @param withParticipants Indicates whether the group object should contain a list of the participants of the
     *                         group.
     * @return The group object.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public Response getGroup(@HeaderParam("Authorization") String accessToken, @PathParam("id") int groupId,
                          @DefaultValue("false") @QueryParam("withParticipants") boolean withParticipants, @Context
                             UriInfo ui)
            throws ServerException {
//        MultivaluedMap<String, String> queryParams = ui.getQueryParameters();
//        System.out.println(queryParams.get("withParticipants"));
        Group group = groupController.getGroup(accessToken, groupId, withParticipants);
        return Response.status(Response.Status.OK).entity(group).build();
    }

    /**
     * Updates the group data of the group which is identified by the id specified in the URL. The data is updated
     * based on a description of changes. The description of changes is provided in the JSON Merge Patch format
     * which is automatically translated into a group object.
     *
     * @param accessToken The access token of the requestor.
     * @param id The id of the group which should be updated.
     * @param group The group object which contains the new data values for the group.
     * @return An updated version of the group resource.
     * @throws ServerException If the execution of the PATCH request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @PATCH
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public Response changeGroup(@HeaderParam("Authorization") String accessToken, @PathParam("id") int id, Group
            group) throws ServerException {
        group = groupController.changeGroup(accessToken, id, group);
        return Response.status(Response.Status.OK).entity(group).build();
    }

    /**
     * Deletes the group with the specified id which is extracted from the request URL.
     *
     * @param accessToken The access token of the requestor.
     * @param id The id of the group which should be deleted.
     * @return Returns an HTTP message with no content.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{id}")
    public Response deleteGroup(@HeaderParam("Authorization") String accessToken, @PathParam("id") int id) throws
            ServerException {
        groupController.deleteGroup(accessToken, id);
        return Response.status(Response.Status.NO_CONTENT).build();
    }


}
