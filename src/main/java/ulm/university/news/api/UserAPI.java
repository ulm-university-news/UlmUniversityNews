package ulm.university.news.api;


import ulm.university.news.controller.UserController;
import ulm.university.news.data.User;
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
 * The UserAPI is responsible for accepting incoming requests, reading the required data and handing it over to the
 * appropriate controller methods. After the request has been executed successfully, the UserAPI generates the
 * response message. If the execution of the request has failed for whatever reasons, the ServerException is handed
 * over to the ErrorHandler class.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
@Path("/user")
public class UserAPI {

    /**
     * Instance of the UserController class.
     */
    private UserController userCtrl = new UserController();

//    @GET
//    @Path("/{id}")
//    public Response getMsg(@PathParam("id") String msg) {
//
//        String output = "Hello Jersey: " + msg;
//
//        return Response.status(Response.Status.OK).entity(output).build();
//
//    }

    /**
     * Creates a new user account. The data of the new user is provided within the user object. The generated
     * user resource will be returned including the URI which can be used to access the resource.
     *
     * @param user    An user object including the data of the new user.
     * @param uriInfo Information about the URI of this request.
     * @return Response object including the generated user object and a set Location Header.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createUser(User user, @Context UriInfo uriInfo) throws ServerException {
        user = userCtrl.createUser(user);
        // Create the URI for the generated resource.
        URI createdURI = URI.create(uriInfo.getBaseUri().toString() + "user" + "/" + user.getId());
        // Return the generated user resource and set the Location Header.
        return Response.status(Response.Status.CREATED).contentLocation(createdURI).entity(user).build();
    }

    /**
     * Returns a list of all user accounts which are stored within the system.
     *
     * @param accessToken The access token of the requestor.
     * @return Returns a list of users.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> getAllUsers(@HeaderParam("Authorization") String accessToken) throws ServerException {
        List<User> users;
        users = userCtrl.getUsers(accessToken);
        return users;
    }

    /**
     * Returns the data of the user account which is identified with the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param id The id of the user account.
     * @return Returns the data of the user account.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public Response getUser(@HeaderParam("Authorization") String accessToken, @PathParam("id") int id)
            throws ServerException {
        User user = null;
        user = userCtrl.getUser(accessToken, id);
        return Response.status(Response.Status.OK).entity(user).build();
    }

    /**
     * Updates the account information of the user which is identified by the given id based on a description of
     * changes. The description of changes is provided in the JSON Merge Patch format which is automatically
     * translated into a user object.
     *
     * @param accessToken The access token of the requestor.
     * @param id The id of the user account which should be updated.
     * @param user The new data values taken from the JSON Merge Patch document and stored in an user object.
     * @return The updated version of the user resource.
     * @throws ServerException If the execution of the PATCH reuquest has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @PATCH
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public Response changeUser(@HeaderParam("Authorization") String accessToken, @PathParam("id") int id, User user)
            throws ServerException {
        user = userCtrl.changeUser(accessToken, id, user);
        return Response.status(Response.Status.OK).entity(user).build();
    }

}
