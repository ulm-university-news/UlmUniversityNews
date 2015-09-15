package ulm.university.news.api;


import ulm.university.news.controller.UserController;
import ulm.university.news.data.User;
import ulm.university.news.util.exceptions.ServerException;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
@Path("/user")
public class UserAPI {

    /** Instance of the UserController class. */
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

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createUser(User user,@Context UriInfo uriInfo){
        return createUserAccount(user, uriInfo);
    }

    /**
     * Create a new user account. The data of the new user is provided within the user object. The generated
     * user resource will be returned including the URI which can be used to access the resource.
     *
     * @param user An user object including the data of the new user.
     * @param uriInfo Information about the URI of this request.
     * @return Response object including the generated user object and a set Location Header.
     */
    private Response createUserAccount(User user, @Context UriInfo uriInfo){
        try {
            user = userCtrl.createUser(user);
        } catch (ServerException e) {
            ServerError se = new ServerError(e.getHttpStatusCode(),e.getErrorCode(), e.getMessage());
            return Response.status(e.getHttpStatusCode()).entity(se).build();    // TODO
        }
        // Create the URI for the generated resource.
        URI createdURI = URI.create(uriInfo.getBaseUri().toString() + "user" + "/" + user.getId());
        // Return the generated user resource and set the Location Header.
        return Response.status(Response.Status.CREATED).contentLocation(createdURI).entity(user).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> getAllUsers(@HeaderParam("Authorization") String accessToken){
        List<User> users;
        try {
            users = userCtrl.getUsers(accessToken);
        } catch (ServerException e) {
            return null;    // TODO
        }
        return users;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public Response getUser(@HeaderParam("Authorization") String accessToken, @PathParam("id") int id){
        User user = null;
        try {
            user = userCtrl.getUser(accessToken, id);
        } catch (ServerException e) {
            ServerError se = new ServerError(e.getHttpStatusCode(),e.getErrorCode(), e.getMessage());
            return Response.status(e.getHttpStatusCode()).entity(se).build();    // TODO
        }
        return Response.status(Response.Status.OK).entity(user).build();
    }

    @PATCH
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public Response changeUser(@HeaderParam("Authorization") String accessToken, @PathParam("id") int id, User user){
        try {
            user = userCtrl.changeUser(accessToken, id, user);
        } catch (ServerException e) {
            ServerError se = new ServerError(e.getHttpStatusCode(),e.getErrorCode(), e.getMessage());
            return Response.status(e.getHttpStatusCode()).entity(se).build();    // TODO
        }
        return Response.status(Response.Status.OK).entity(user).build();
    }

}
