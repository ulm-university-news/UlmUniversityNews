package ulm.university.news.api;


import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
@Path("/user")
public class UserAPI {

    @GET
    @Path("/{id}")
    public Response getMsg(@PathParam("id") String msg) {

        String output = "Hello Jersey: " + msg;

        return Response.status(200).entity(output).build();

    }

}
