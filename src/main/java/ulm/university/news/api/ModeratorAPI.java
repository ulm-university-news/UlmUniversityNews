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
@Path("/moderator")
public class ModeratorAPI {

    @GET
    @Path("/{id}")
    public Response getModerator(@PathParam("id") int id) {

        String output = "Moderator id: " + id;

        return Response.status(200).entity(output).build();

    }

}
