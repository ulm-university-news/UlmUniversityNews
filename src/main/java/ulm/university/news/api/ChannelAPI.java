package ulm.university.news.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import static ulm.university.news.util.Constants.*;

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
     * Delivers all the existing channel data. The requested channels can be restricted to a specific selection by the
     * given query params.
     *
     * @param accessToken The access token of the requestor.
     * @param moderatorId Get only channels for which the moderator with the given id is responsible.
     * @param lastUpdated Get only channels with a newer modification data as the last updated date.
     * @return Response object including a list with channel data.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getChannels(@HeaderParam("Authorization") String accessToken, @QueryParam("moderatorId")
    Integer moderatorId, @QueryParam("lastUpdated") String lastUpdated) throws ServerException {
        // TODO Note: On client site replace + in date String with %2B
        // TODO Replace other reserved characters too? https://de.wikipedia.org/wiki/URL-Encoding
        ZonedDateTime lastUpdatedDate = null;
        try {
            // Verify correct date format.
            if (lastUpdated != null) {
                lastUpdatedDate = ZonedDateTime.parse(lastUpdated);
            }
        } catch (DateTimeParseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 400, PARSING_FAILURE, "Couldn't parse date String.");
            throw new ServerException(400, PARSING_FAILURE);
        }
        // Get all the requested channel resources.
        List<Channel> channels = channelCtrl.getChannels(accessToken, moderatorId, lastUpdatedDate);

        // Write channels with subclass attributes as JSON String.
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        // Make sure that dates are formatted correctly.
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, true);
        try {
            return mapper.writeValueAsString(channels);
        } catch (JsonProcessingException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, PARSING_FAILURE, "Couldn't parse channels to JSON String.");
            throw new ServerException(500, PARSING_FAILURE);
        }
    }

    /**
     * Delivers the channel data of a specific channel identified by id.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel.
     * @return Response object including a the channel data.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public String getChannel(@HeaderParam("Authorization") String accessToken, @PathParam("id") int channelId) throws
            ServerException {
        // Get the requested channel resource.
        Channel channel = channelCtrl.getChannel(accessToken, channelId);

        // Write channel with subclass attributes as JSON String.
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        // Make sure that dates are formatted correctly.
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, true);
        try {
            return mapper.writeValueAsString(channel);
        } catch (JsonProcessingException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, PARSING_FAILURE, "Couldn't parse channels to JSON String.");
            throw new ServerException(500, PARSING_FAILURE);
        }
    }

    /**
     * Deletes a channel from the database. Afterwards, it's no longer available on the server.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel which should be deleted.
     * @return Response object.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{id}")
    public Response deleteChannel(@HeaderParam("Authorization") String accessToken, @PathParam("id") int channelId)
            throws ServerException {
        channelCtrl.deleteChannel(accessToken, channelId);
        // Return 204 No Content
        return Response.status(Response.Status.NO_CONTENT).build();
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
     * Removes a moderator from a channel. Afterwards the moderator is no longer responsible for the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel for which the moderator is responsible.
     * @param moderatorId The id of the moderator who should be removed as responsible moderator from the channel.
     * @return Response object.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{channelId}/moderator/{moderatorId}")
    public Response removeModeratorFromChannel(@HeaderParam("Authorization") String accessToken, @PathParam("channelId")
    int channelId, @PathParam("moderatorId") int moderatorId) throws ServerException {
        channelCtrl.removeModeratorFromChannel(accessToken, channelId, moderatorId);
        // Return 204 No Content
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Delivers the moderator data of all moderators who are responsible for the channel with the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel.
     * @return Response object including a list with moderator data.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Path("/{id}/moderator")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Moderator> getResponsibleModerators(@HeaderParam("Authorization") String accessToken, @PathParam
            ("id") int channelId) throws ServerException {
        // Return all the requested moderator resources.
        return channelCtrl.getResponsibleModerators(accessToken, channelId);
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

    /**
     * Removes a user from a channel. Afterwards the user is no longer a subscriber of the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the user is subscribed.
     * @param userId The id of the user who should be removed as subscriber from the channel.
     * @return Response object.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{channelId}/user/{userId}")
    public Response unsubscribeChannel(@HeaderParam("Authorization") String accessToken, @PathParam("channelId") int
            channelId, @PathParam("userId") int userId) throws ServerException {
        channelCtrl.unsubscribeChannel(accessToken, channelId, userId);
        // Return 204 No Content
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Delivers the user data of all users who are subscribed to the channel with the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel.
     * @return Response object including a list with user data.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Path("/{id}/user")
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> getSubscribers(@HeaderParam("Authorization") String accessToken, @PathParam
            ("id") int channelId) throws ServerException {
        // Return all the requested user resources.
        return channelCtrl.getSubscribers(accessToken, channelId);
    }
}
