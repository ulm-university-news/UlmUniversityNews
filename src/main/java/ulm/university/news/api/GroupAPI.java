package ulm.university.news.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.controller.GroupController;
import ulm.university.news.data.*;
import ulm.university.news.data.enums.GroupType;
import ulm.university.news.util.Constants;
import ulm.university.news.util.PATCH;
import ulm.university.news.util.exceptions.ServerException;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import static ulm.university.news.util.Constants.LOG_SERVER_EXCEPTION;
import static ulm.university.news.util.Constants.PARSING_FAILURE;

/**
 * The GroupAPI is responsible for accepting incoming group requests and requests for the corresponding sub-resources
 * . Sub-resources of groups are ballots and conversations. The class takes incoming requests, reads the content and
 * parameters and hands it over to the appropriate controller methods. After the request has been executed
 * successfully, the GroupAPI generates the response message. If the execution of the request has failed for whatever
 * reasons, the ServerException is handed over to the ErrorHandler class.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
@Path("/group")
public class GroupAPI {

    /** Instance of the GroupController class. */
    private GroupController groupController = new GroupController();

    /** The logger instance for GroupAPI. */
    private static final Logger logger = LoggerFactory.getLogger(GroupAPI.class);

    /** An instance of the Jackson mapper to parse dates properly to JSON. */
    ObjectMapper mapper;

    /**
     * Instantiates the ChannelAPI and configures the Jackson mapper.
     */
    public GroupAPI() {
        mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        // Make sure that dates are formatted correctly.
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
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
        group = groupController.createGroup(accessToken, group);
        // Create the URI which can be used to access the created resource.
        URI createdURI = URI.create(uriInfo.getBaseUri().toString() + "group" + "/" + group.getId());
        String groupAsJson = parseToJson(group);
        // Return the created resource and set the location header.
        return Response.status(Response.Status.CREATED).location(createdURI).entity(groupAsJson).build();
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
    public Response getGroups(@HeaderParam("Authorization") String accessToken, @QueryParam("groupName") String
            groupName, @QueryParam("groupType") GroupType groupType) throws ServerException {
        List<Group> groups = groupController.getGroups(accessToken, groupName, groupType);
        String groupsAsJson = parseToJson(groups);
        return Response.status(Response.Status.OK).entity(groupsAsJson).build();
    }

    /**
     * Returns the group which is identified by the specified id. It can be defined whether the group object should
     * contain a list of all participants of the group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group which should be returned.
     * @param withParticipants Indicates whether the group object should contain a list of the participants of the
     * group.
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
        Group group = groupController.getGroup(accessToken, groupId, withParticipants);
        String groupAsJson = parseToJson(group);
        return Response.status(Response.Status.OK).entity(groupAsJson).build();
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
        String groupAsJson = parseToJson(group);
        return Response.status(Response.Status.OK).entity(groupAsJson).build();
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

    /**
     * Adds a user as a participant to the group which is identified by the given id. The user who is intended to join
     * the group is identified via the access token. This implies that only the requestor can join a group, it is
     * impossible to specify another user id than its own. The requestor also needs to provide the password for the
     * group in order to join it.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group.
     * @param jsonString The JSON String which is contained in the body of the HTTP request.
     * @return Response without content.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/user")
    public Response addParticipantToGroup(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, String jsonString, @Context UriInfo uriInfo) throws ServerException {
        ObjectMapper mapper = new ObjectMapper();
        String password = "";
        try {
            // Reads the password from the received JSON String with Jackson.
            JsonNode jsonObj = mapper.readTree(jsonString);
            if (jsonObj.get("password") != null) {
                password = jsonObj.get("password").asText();
            }
        } catch (IOException e) {
            throw new ServerException(400, Constants.GROUP_MISSING_PASSWORD);
        }
        groupController.addParticipant(accessToken, groupId, password);
        return Response.status(Response.Status.CREATED).build();
    }

    /**
     * Returns a list of users who are participants of the group which is identified by the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group-
     * @return A list of users.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/user")
    public List<User> getParticipants(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId) throws ServerException {
        return groupController.getParticipants(accessToken, groupId);
    }

    /**
     * Removes a participant from the group. The user which is identified by the specified id is removed as an active
     * participant of the group. However, the user remains in the list of the participants as an inactive participant
     * . The user needs to remain in the list to be able to resolve possible dependencies.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group.
     * @param userId The id of the user who should be removed as a participant from the group.
     * @return Response object with no content.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{groupId}/user/{userId}")
    public Response deleteParticipant(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("userId") int userId) throws ServerException {
        groupController.deleteParticipant(accessToken, groupId, userId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Creates a new ballot in the group with the specified id. The data of the new ballot is provided within the
     * ballot object. The created ballot resource will be returned including the URI which can be used to access the
     * ballot resource.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group in which the ballot should be created.
     * @param ballot The ballot object containing the ballot data.
     * @param uriInfo The uriInfo contains information about the request URI.
     * @return The created ballot resource with all corresponding data and the URI of the new resource.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/ballot")
    public Response createBallot(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, Ballot ballot, @Context UriInfo uriInfo) throws ServerException {
        ballot = groupController.createBallot(accessToken, groupId, ballot);
        URI createdURI = URI.create(uriInfo.getBaseUri() + "group" + "/" + groupId + "/" + "ballot" + "/" + ballot
                .getId());
        return Response.status(Response.Status.CREATED).location(createdURI).entity(ballot).build();
    }

    /**
     * Returns a list of ballots. It can be defined if the ballot resources should contain their corresponding
     * sub-resources like the options and a list of voters for each option.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group.
     * @param subresources Indicates whether the sub-resources should be contained in the response.
     * @return A list of ballot resources.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/ballot")
    public List<Ballot> getBallots(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @DefaultValue("false") @QueryParam("subresources") boolean subresources) throws ServerException {
        return groupController.getBallots(accessToken, groupId, subresources);
    }

    /**
     * Returns the ballot resource with the specified id which belongs to the defined group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group.
     * @param ballotId The id of the ballot.
     * @return Returns the ballot resource.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/ballot/{ballotId}")
    public Response getBallot(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("ballotId") int ballotId, @DefaultValue("false") @QueryParam("subresources")
                              boolean subresources) throws ServerException {
        Ballot ballot = groupController.getBallot(accessToken, groupId, ballotId, subresources);
        return Response.status(Response.Status.OK).entity(ballot).build();
    }

    /**
     * Updates the ballot data of the ballot which is identified by the id specified in the URL. The data is updated
     * based on a description of changes. The description of changes is provided in the JSON Merge Patch format
     * which is automatically translated into a ballot object.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group extracted from the URL.
     * @param ballotId The id of the ballot extracted from the URL.
     * @param ballot The ballot object which has been generated from the JSON Merge Patch document. It contains the
     * new data for the ballot.
     * @return Returns an updated version of the ballot resource.
     * @throws ServerException If the execution of the PATCH request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/ballot/{ballotId}")
    public Response changeBallot(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("ballotId") int ballotId, Ballot ballot) throws ServerException {
        ballot = groupController.changeBallot(accessToken, groupId, ballotId, ballot);
        return Response.status(Response.Status.OK).entity(ballot).build();
    }

    /**
     * Deletes the ballot which is identified by the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot.
     * @return An empty response.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/ballot/{ballotId}")
    public Response deleteBallot(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("ballotId") int ballotId) throws ServerException {
        groupController.deleteBallot(accessToken, groupId, ballotId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Creates a new option for the ballot which is identified by the specified id. The data of the new option is
     * provided within the option object. The created option resource will be returned including the URI which can be
     * used to access the option resource.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the option belongs.
     * @param option The option object containing the data from the request.
     * @param uriInfo Information about the URI of the request.
     * @return The created resource including the URI which can be used to access the resource.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/ballot/{ballotId}/option")
    public Response createOption(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("ballotId") int ballotId, Option option, @Context UriInfo uriInfo) throws
            ServerException {
        groupController.createOption(accessToken, groupId, ballotId, option);
        URI createdURI = URI.create(uriInfo.getBaseUri() + "group" + "/" + groupId + "/" + "ballot" + "/" + ballotId
                + "/" + "option" + "/" + option.getId());
        return Response.status(Response.Status.CREATED).location(createdURI).entity(option).build();
    }

    /**
     * Returns a list of options for the ballot which is identified by the defined id taken from the path.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot for which the options are requested.
     * @return A list of options. The list can also be empty.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/ballot/{ballotId}/option")
    public List<Option> getOptions(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("ballotId") int ballotId, @DefaultValue("false") @QueryParam("subresources")
    boolean subresources) throws ServerException {
        List<Option> options;
        options = groupController.getOptions(accessToken, groupId, ballotId, subresources);
        return options;
    }

    /**
     * Returns the option which is identified by the given id taken from the path. The option belongs to the ballot
     * with the specified id which in turn belongs to the group with the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The id of the option.
     * @return Returns the option resource.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/ballot/{ballotId}/option/{optionId}")
    public Response getOption(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("ballotId") int ballotId, @PathParam("optionId") int optionId) throws ServerException {
        Option option = groupController.getOption(accessToken, groupId, ballotId, optionId);
        return Response.status(Response.Status.OK).entity(option).build();
    }

    /**
     * Deletes the option with the specified id. The option belongs to the ballot with the given id taken from the
     * path. The ballot belongs to the specified group.
     *
     * @param accessToken The access token of the reqeustor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The option which should be deleted.
     * @return An response with no content.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{groupId}/ballot/{ballotId}/option/{optionId}")
    public Response deleteOption(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("ballotId") int ballotId, @PathParam("optionId") int optionId) throws ServerException {
        groupController.deleteOption(accessToken, groupId, ballotId, optionId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Creates a vote. The requestor votes for the specified option with the id taken from the path. The option
     * belongs to the given ballot which in turn belongs to the defined group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the group belongs.
     * @param optionId The id of the option for which the requestor votes.
     * @return Returns a reponse with status created, but no content.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Path("/{groupId}/ballot/{ballotId}/option/{optionId}/user")
    public Response createVote(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("ballotId") int ballotId, @PathParam("optionId") int optionId) throws ServerException {
        groupController.createVote(accessToken, groupId, ballotId, optionId);
        return Response.status(Response.Status.CREATED).build();
    }

    /**
     * Returns a list of user resources. The list contains the users who have voted for the specified option. The
     * option belongs to the given ballot which in turn belongs to the defined group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The id of the option for which the users have voted.
     * @return A list of users. The list can also be empty.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/ballot/{ballotId}/option/{optionId}/user")
    public List<User> getVoters(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("ballotId") int ballotId, @PathParam("optionId") int optionId) throws ServerException {
        return groupController.getVoters(accessToken, groupId, ballotId, optionId);
    }

    /**
     * Deletes the vote from the user with the specified id for the defined option. The option
     * belongs to the given ballot which in turn belongs to the defined group.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The id of the option for which the users have voted.
     * @param userId The id of the user for whom the vote should be deleted.
     * @return A response with no content.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{groupId}/ballot/{ballotId}/option/{optionId}/user/{userId}")
    public Response deleteVote(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("ballotId") int ballotId, @PathParam("optionId") int optionId, @PathParam("userId")
                               int userId) throws ServerException {
        groupController.deleteVote(accessToken, groupId, ballotId, optionId, userId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Creates a new conversation for the group which is identified by the specified id. The data of the new
     * conversation is provided within the conversation object which is generated from the request content. The
     * created conversation resource will be returned including the URI which can be used to access the conversation
     * resource.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group for which the conversation is created.
     * @param conversation The conversation object containing the data from the request.
     * @param uriInfo Information about the request URI.
     * @return The created conversation resource and the URI for that resource.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/conversation")
    public Response createConversation(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, Conversation conversation, @Context UriInfo uriInfo) throws ServerException {
        conversation = groupController.createConversation(accessToken, groupId, conversation);
        URI createdURI = URI.create(uriInfo.getBaseUri().toString() + "group" + "/" + groupId + "/" + "conversation" +
                "/" + conversation.getId());
        return Response.status(Response.Status.CREATED).location(createdURI).entity(conversation).build();
    }

    /**
     * Returns a list of conversation resources which belong to the group with the specified id. The sub-resources
     * parameter taken from the request URL determines whether the single conversations should contain a list of
     * their sub-resources, i.e. a list of the corresponding conversation messages.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group for which the conversations are requested.
     * @param subresources Indicates whether the conversations should contain a list of their sub-resources.
     * @return A list of conversation resources. The list can also be empty.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/conversation")
    public List<Conversation> getConversations(@HeaderParam("Authorization") String accessToken, @PathParam
            ("groupId") int groupId, @DefaultValue("false") @QueryParam("subresources") boolean subresources) throws
            ServerException {
        return groupController.getConversations(accessToken, groupId, subresources);
    }

    /**
     * Returns the conversation which is identified by the specified id. The conversation belongs to the group with
     * the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation which should be retrieved.
     * @return The conversation object.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/conversation/{conversationId}")
    public Response getConversation(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("conversationId") int conversationId) throws ServerException {
        Conversation conversation = groupController.getConversation(accessToken, groupId, conversationId);
        return Response.status(Response.Status.OK).entity(conversation).build();
    }

    /**
     * Updates the conversation data of the conversation which is identified by the id specified in the URL. The data
     * is updated based on a description of changes. The description of changes is provided in the JSON Merge Patch
     * format which is automatically translated into a conversation object.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation which should be updated.
     * @param conversation The conversation object which has been generated from the JSON Merge Patch document. It
     * contains the new data for the conversation.
     * @return Returns an updated version of the conversation resource.
     * @throws ServerException If the execution of the PATCH request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/conversation/{conversationId}")
    public Response changeConversation(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("conversationId") int conversationId, Conversation conversation) throws
            ServerException {
        conversation = groupController.changeConversation(accessToken, groupId, conversationId, conversation);
        return Response.status(Response.Status.OK).entity(conversation).build();
    }

    /**
     * Deletes the conversation resource which is identified by the specified id taken from the request URL.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation that should be deleted.
     * @return A response with no content.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{groupId}/conversation/{conversationId}")
    public Response deleteConversation(@HeaderParam("Authorization") String accessToken, @PathParam("groupId") int
            groupId, @PathParam("conversationId") int conversationId) throws ServerException {
        groupController.deleteConversation(accessToken, groupId, conversationId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Creates a new message for the conversation which is identified by the specified id. The data of the message is
     * taken from the request.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation.
     * @param conversationMessage The object containing the data of the message taken from the request.
     * @return Returns the created message resource.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/conversation/{conversationId}/message")
    public Response createConversationMessage(@HeaderParam("Authorization") String accessToken, @PathParam("groupId")
    int groupId, @PathParam("conversationId") int conversationId, ConversationMessage conversationMessage) throws
            ServerException {
        conversationMessage = groupController.createConversationMessage(accessToken, groupId, conversationId,
                conversationMessage);
        String conversationMessageAsJson = parseToJson(conversationMessage);
        return Response.status(Response.Status.CREATED).entity(conversationMessageAsJson).build();
    }

    /**
     * Returns the messages for the conversation starting from a defined message number which is taken form the
     * request URL. The method returns a list of all messages of the conversation which have a higher message number
     * than the one defined in the request.
     *
     * @param accessToken The access token of the requestor.
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation.
     * @param messageNr The starting message number. All messages of the conversation are returned which have a
     * higher message number than the one defined in this parameter.
     * @return A list of conversation messages. The list can also be empty.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{groupId}/conversation/{conversationId}/message")
    public Response getConversationMessages(@HeaderParam("Authorization") String accessToken,
                                            @PathParam("groupId") int groupId, @PathParam("conversationId") int conversationId, @DefaultValue("0")
                                            @QueryParam("messageNr") int messageNr) throws ServerException {
        List<ConversationMessage> conversationMessages = groupController.getConversationMessages(accessToken, groupId,
                conversationId, messageNr);
        String conversationMessagesAsJson = parseToJson(conversationMessages);
        return Response.status(Response.Status.OK).entity(conversationMessagesAsJson).build();
    }

    /**
     * Parses the given object to a JSON String.
     *
     * @param object The object which should be parsed to JSON.
     * @return The JSON String generated form given object.
     * @throws ServerException If a parsing exception occurred.
     */
    private String parseToJson(Object object) throws ServerException {
        try {
            // Return the channel resources.
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, PARSING_FAILURE, "Couldn't parse object to JSON String.");
            throw new ServerException(500, PARSING_FAILURE);
        }
    }
}
