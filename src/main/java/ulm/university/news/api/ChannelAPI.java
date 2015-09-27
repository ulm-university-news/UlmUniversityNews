package ulm.university.news.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.controller.ChannelController;
import ulm.university.news.data.*;
import ulm.university.news.data.enums.ChannelType;
import ulm.university.news.util.exceptions.ServerException;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;

import static ulm.university.news.util.Constants.CHANNEL_INVALID_TYPE;
import static ulm.university.news.util.Constants.LOG_SERVER_EXCEPTION;

/**
 * The ChannelAPI is responsible for accepting incoming channel requests, reading the required data and handing
 * it over to the appropriate controller methods. After the request has been executed successfully, the ChannelAPI
 * generates the response message. If the execution of the request has failed for whatever reasons, the
 * ServerException is handed over to the ErrorHandler class.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
@Path("/channel")
public class ChannelAPI {

    /** Instance of the ChannelController class. */
    private ChannelController channelCtrl = new ChannelController();

    /** The logger instance for ChannelAPI. */
    private static final Logger logger = LoggerFactory.getLogger(ChannelAPI.class);

    /**
     * Create a new channel and adds the creator to its responsible moderators. The data of the new channel is
     * provided within the JSON String. The appropriate channel subclass will be created from the JSON representation.
     * The created resource will be returned including the URI which can be used to access the resource.
     *
     * @param accessToken The access token of the requestor.
     * @param jsonString The JSON String of a channel which is contained in the body of the HTTP request.
     * @param uriInfo Information about the URI of this request.
     * @return Response object including the created channel object and a set Location Header.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createChannel(@HeaderParam("Authorization") String accessToken, @Context UriInfo uriInfo, String
            jsonString) throws ServerException {
        logger.debug("Start with JSON String:{}", jsonString);
        Channel channel;
        try {
            // Read channel type form JSON to determine channel subclass.
            ObjectMapper mapper = new ObjectMapper();
            // Set fields and Enum values which are unknown to null and continue parsing.
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
            String type = mapper.readTree(jsonString).get("type").asText();
            ChannelType channelType;
            try {
                channelType = ChannelType.valueOf(type);
            } catch (IllegalArgumentException e) {
                // Invalid channel type. Abort method.
                logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_TYPE, "Channel type is invalid.");
                throw new ServerException(400, CHANNEL_INVALID_TYPE);
            }

            // Generate appropriate channel subclass from JSON representation.
            logger.debug("channelType:{}", channelType);
            switch (channelType) {
                case LECTURE:
                    channel = mapper.readValue(jsonString, Lecture.class);
                    logger.debug("Created channel subclass lecture:{}.", channel);
                    break;
                case EVENT:
                    channel = mapper.readValue(jsonString, Event.class);
                    logger.debug("Created channel subclass lecture:{}.", channel);
                    break;
                case SPORTS:
                    channel = mapper.readValue(jsonString, Sports.class);
                    logger.debug("Created channel subclass lecture:{}.", channel);
                    break;
                default:
                    // There is no subclass for channel type OTHER and STUDENT_GROUP, so create normal channel object.
                    channel = mapper.readValue(jsonString, Channel.class);
                    logger.debug("Created channel subclass lecture:{}.", channel);
                    break;
            }
        } catch (IOException e) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_TYPE, "Channel type is invalid.");
            throw new ServerException(400, CHANNEL_INVALID_TYPE);
        }

        channel = channelCtrl.createChannel(accessToken, channel);
        // Create the URI for the created channel resource.
        URI createdURI = URI.create(uriInfo.getBaseUri().toString() + "channel" + "/" + channel.getId());
        // Return the created channel resource and set the Location Header.
        return Response.status(Response.Status.CREATED).contentLocation(createdURI).entity(channel).build();
    }

    /**
     * Adds a moderator to a channel. Afterwards the moderator is registered as responsible moderator for the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the moderator should be added.
     * @param moderator The moderator (including the name) who should be added to the channel.
     * @return Response object.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Path("/{id}/moderator")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addModeratorToChannel(@HeaderParam("Authorization") String accessToken, @PathParam("id") int
            channelId, Moderator moderator) throws ServerException {
        channelCtrl.addModeratorToChannel(accessToken, channelId, moderator.getName());
        // Return 201 Created TODO Set Location Header?
        return Response.status(Response.Status.CREATED).build();
    }

    /**
     * Adds a user to a channel. Afterwards the user is registered as subscriber of the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the user should be added.
     * @return Response object.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Path("/{id}/user")
    public Response subscribeChannel(@HeaderParam("Authorization") String accessToken, @PathParam("id") int
            channelId) throws ServerException {
        channelCtrl.subscribeChannel(accessToken, channelId);
        // Return 201 Created TODO Set Location Header?
        return Response.status(Response.Status.CREATED).build();
    }

}
