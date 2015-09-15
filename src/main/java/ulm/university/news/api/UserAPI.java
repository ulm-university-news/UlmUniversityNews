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
 * TODO
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
     * Create a new user account. The data of the new user is provided within the user object. The generated
     * user resource will be returned including the URI which can be used to access the resource.
     *
     * @param user    An user object including the data of the new user.
     * @param uriInfo Information about the URI of this request.
     * @return Response object including the generated user object and a set Location Header.
     * @throws ServerException // TODO
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


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> getAllUsers(@HeaderParam("Authorization") String accessToken) throws ServerException {
        List<User> users;
        users = userCtrl.getUsers(accessToken);
        return users;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public Response getUser(@HeaderParam("Authorization") String accessToken, @PathParam("id") int id)
            throws ServerException {
        User user = null;
        user = userCtrl.getUser(accessToken, id);
        return Response.status(Response.Status.OK).entity(user).build();
    }

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
