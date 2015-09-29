package ulm.university.news.manager.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.*;
import ulm.university.news.data.enums.GroupType;
import ulm.university.news.data.enums.Platform;
import ulm.university.news.data.enums.Priority;
import ulm.university.news.util.Constants;
import ulm.university.news.util.exceptions.DatabaseException;

import java.sql.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The GroupDatabaseManager provides methods to access, insert or update data in the database related to Group
 * resources and its corresponding sub-resources. The sub-resources of a group are ballots, options and conversations.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class GroupDatabaseManager extends DatabaseManager {

    /** An instance of the Logger class which performs logging for the UserController class. */
    private static final Logger logger = LoggerFactory.getLogger(GroupDatabaseManager.class);

    /**
     * Creates an instance of the GroupDatabaseManager class.
     */
    public GroupDatabaseManager(){

    }

    /**
     * Stores the group data into the database.
     *
     * @param group The group object which contains the data of the group.
     * @throws DatabaseException If the data could not be stored in the database due to database failure.
     */
    public void storeGroup(Group group) throws DatabaseException {
        logger.debug("Start with group:{}.", group);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            con.setAutoCommit(false);
            String query =
                    "INSERT INTO `Group` (Name, Description, Type, CreationDate, ModificationDate, Term, " +
                    "Password, GroupAdmin_User_Id) " +
                    "VALUES (?,?,?,?,?,?,?,?);";

            PreparedStatement insertGroupStmt = con.prepareStatement(query);
            insertGroupStmt.setString(1, group.getName());
            insertGroupStmt.setString(2, group.getDescription());
            insertGroupStmt.setInt(3, group.getGroupType().ordinal());
            insertGroupStmt.setTimestamp(4, Timestamp.from(group.getCreationDate().toInstant()));
            insertGroupStmt.setTimestamp(5, Timestamp.from(group.getModificationDate().toInstant()));
            insertGroupStmt.setString(6, group.getTerm());
            insertGroupStmt.setString(7, group.getPassword());
            insertGroupStmt.setInt(8,group.getGroupAdmin());

            insertGroupStmt.execute();

            // Retrieve auto incremented id of the database record.
            String getIdQuery = "SELECT LAST_INSERT_ID();";

            Statement getIdStmt = con.createStatement();
            ResultSet getIdRs = getIdStmt.executeQuery(getIdQuery);
            if(getIdRs.next()){
                group.setId(getIdRs.getInt(1));
            }
            logger.info("Stored group with id:{}.", group.getId());

            // Add the creator of the group, i.e. the group admin in this case, to the participants of the group.
            String addParticipantQuery =
                    "INSERT INTO UserGroup (User_Id, Group_Id, Active) " +
                    "VALUES (?,?,?);";

            PreparedStatement addParticipantStmt = con.prepareStatement(addParticipantQuery);
            addParticipantStmt.setInt(1, group.getGroupAdmin());
            addParticipantStmt.setInt(2, group.getId());
            addParticipantStmt.setBoolean(3, true);  // It is an active participant.

            addParticipantStmt.executeUpdate();
            logger.info("Added the user with the id {} as an participant for the group with the id {}.", group
                    .getGroupAdmin(), group.getId());

            // Commit changes.
            con.commit();

            insertGroupStmt.close();
            getIdStmt.close();
            addParticipantStmt.close();
        } catch (SQLException e) {
            try {
                logger.warn("Need to rollback the transaction.");
                con.rollback();
            } catch (SQLException e1) {
                logger.warn("Rollback failed.");
                logger.error(Constants.LOG_SQL_EXCEPTION, e1.getSQLState(), e1.getErrorCode(), e1.getMessage());
            }
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            try {
                con.setAutoCommit(true);
            } catch (SQLException e) {
                logger.warn("Failed to set auto commit back to true.");
                logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            }
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Returns a list of groups. The result of the method can be influenced by search parameters. Groups can be
     * searched by name and type. If the search parameters are set, the method will only return the groups which
     * contain the given name value in their name or groups which are of a given type.
     *
     * @param groupName The search parameter for the group name. If it is set, the method will only return groups
     *                  which contain this value in their name.
     * @param groupType The search parameter for the group type. If it is set, the method will only return groups
     *                  which are of the specified type.
     * @return A list of groups. The list can also be empty.
     * @throws DatabaseException If the search query could not be executed due to database failure.
     */
    public List<Group> getGroups(String groupName, GroupType groupType) throws DatabaseException {
        logger.debug("Start with groupName:{} and groupType:{}.", groupName, groupType);
        List<Group> groups = new ArrayList<Group>();
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT * " +
                    "FROM `Group` " +
                    "WHERE Name LIKE ? AND Type BETWEEN ? AND ?;";

            // Check which search parameters are set and adjust them to fit into the GET query.
            if(groupName == null){
                // Use wildcard value to fit any value for the name.
                groupName = "%";
            }else{
                /* Add wildcards in front and behind the search parameter to get any group which contains the search
                parameter in its name. */
                groupName = "%" + groupName + "%";
            }
            int typeIntervalStart;
            int typeIntervalEnd;
            if(groupType == null){
                /* If no groupType search parameter is specified, accept all possible group types. To achieve this,
                 the interval is set to the whole range of possible values, starting with 0. */
                typeIntervalStart = 0;
                typeIntervalEnd = GroupType.values.length-1;
            }else{
                // GroupType is set, thus restrict the interval to the search parameter.
                typeIntervalStart = groupType.ordinal();
                typeIntervalEnd = groupType.ordinal();
            }

            PreparedStatement getGroupsStmt = con.prepareStatement(query);
            getGroupsStmt.setString(1, groupName);
            getGroupsStmt.setInt(2, typeIntervalStart);
            getGroupsStmt.setInt(3, typeIntervalEnd);

            ResultSet getGroupsRs = getGroupsStmt.executeQuery();
            while (getGroupsRs.next()){
                int id = getGroupsRs.getInt("Id");
                String name = getGroupsRs.getString("Name");
                String description = getGroupsRs.getString("Description");
                groupType = GroupType.values[getGroupsRs.getInt("Type")];
                ZonedDateTime creationDate = getGroupsRs.getTimestamp("CreationDate").toLocalDateTime().atZone
                        (Constants.TIME_ZONE);
                ZonedDateTime modificationDate = getGroupsRs.getTimestamp("ModificationDate").toLocalDateTime()
                        .atZone(Constants.TIME_ZONE);
                String term = getGroupsRs.getString("Term");
                int groupAdmin = getGroupsRs.getInt("GroupAdmin_User_Id");

                // The password is never returned in the GET all groups request, it is set to null.
                Group tmp = new Group(id, name, description, groupType, creationDate, modificationDate, term, null,
                        groupAdmin);
                groups.add(tmp);
            }
            getGroupsStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End with groups:{}.", groups);
        return groups;
    }

    /**
     * Returns all groups for which the user with the given id is listed as an active participant.
     *
     * @param participantId The id of the user.
     * @return Returns a list of all groups in which the specified user is an active participant.
     * @throws DatabaseException If the retrieval of the groups fails due to a database failure.
     */
    public List<Group> getGroupsByParticipant(int participantId) throws DatabaseException {
        List<Group> groups = new ArrayList<Group>();
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getGroupsQuery =
                    "SELECT * " +
                    "FROM Group AS g JOIN UserGroup AS ug ON g.Id=ug.Group_Id " +
                    "WHERE ug.User_Id=? AND ug.Active=?;";

            PreparedStatement getGroupsStmt = con.prepareStatement(getGroupsQuery);
            getGroupsStmt.setInt(1, participantId);
            getGroupsStmt.setBoolean(2, true);

            ResultSet getGroupsRs = getGroupsStmt.executeQuery();
            while(getGroupsRs.next()){
                int id = getGroupsRs.getInt("Id");
                String name = getGroupsRs.getString("Name");
                String description = getGroupsRs.getString("Description");
                GroupType groupType = GroupType.values[getGroupsRs.getInt("Type")];
                ZonedDateTime creationDate = getGroupsRs.getTimestamp("CreationDate").toLocalDateTime().atZone
                        (Constants.TIME_ZONE);
                ZonedDateTime modificationDate = getGroupsRs.getTimestamp("ModificationDate").toLocalDateTime()
                        .atZone(Constants.TIME_ZONE);
                String term = getGroupsRs.getString("Term");
                int groupAdmin = getGroupsRs.getInt("GroupAdmin_User_Id");

                // The password is never returned in the GET all groups request, it is set to null.
                Group tmp = new Group(id, name, description, groupType, creationDate, modificationDate, term, null,
                        groupAdmin);
                groups.add(tmp);
            }

            getGroupsStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        return groups;
    }

    /**
     * Returns the group which is identified by the given id.
     *
     * @param groupId The id of the group which should be returned.
     * @param withParticipants Indicates whether the list of participants of this group should be set in the group
     *                         object.
     * @return The group object which contains the group data.
     * @throws DatabaseException If the execution has failed due to database failure.
     */
    public Group getGroup(int groupId, boolean withParticipants) throws DatabaseException {
        logger.debug("Start with groupId:{} and withParticipants:{}.", groupId, withParticipants);
        Group group = null;
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getGroupQuery =
                    "SELECT * " +
                    "FROM `Group` " +
                    "WHERE Id=?;";

            PreparedStatement getGroupStmt = con.prepareStatement(getGroupQuery);
            getGroupStmt.setInt(1, groupId);

            ResultSet getGroupRs = getGroupStmt.executeQuery();
            if(getGroupRs.next()){
                String name = getGroupRs.getString("Name");
                String description = getGroupRs.getString("Description");
                GroupType groupType = GroupType.values[getGroupRs.getInt("Type")];
                ZonedDateTime creationDate = getGroupRs.getTimestamp("CreationDate").toLocalDateTime().atZone
                        (Constants.TIME_ZONE);
                ZonedDateTime modificationDate = getGroupRs.getTimestamp("ModificationDate").toLocalDateTime().atZone
                        (Constants.TIME_ZONE);
                String password = getGroupRs.getString("Password");
                String term = getGroupRs.getString("Term");
                int groupAdmin = getGroupRs.getInt("GroupAdmin_User_Id");

                /* The password is returned here as it is required for the password verification in the
                   addParticipant method. If it should not be returned to the requestor, make sure to set it to null
                   in the Controller. */
                group = new Group(groupId, name, description, groupType, creationDate, modificationDate, term, password,
                        groupAdmin);
            }

            // Check if list of participants should be set for this group.
            if(group != null && withParticipants){
                List<User> participants = new ArrayList<User>();
                String getUserIdsQuery =
                        "SELECT * " +
                        "FROM UserGroup " +
                        "WHERE Group_Id=?;";
                String getUserQuery =
                        "SELECT * " +
                        "FROM User " +
                        "WHERE Id=?;";

                PreparedStatement getUserIdsStmt = con.prepareStatement(getUserIdsQuery);
                getUserIdsStmt.setInt(1, group.getId());

                PreparedStatement getUserStmt = con.prepareStatement(getUserQuery);

                // First, request the ids of the users which are participants of the group.
                ResultSet getUserIdsRs = getUserIdsStmt.executeQuery();
                while(getUserIdsRs.next()){
                    int userId = getUserIdsRs.getInt("User_Id");
                    boolean active = getUserIdsRs.getBoolean("Active");

                    // Now request the user identified by this id.
                    getUserStmt.setInt(1, userId);
                    ResultSet getUserRs = getUserStmt.executeQuery();
                    if(getUserRs.next()){
                        String username = getUserRs.getString("Name");
                        String pushAccessToken = getUserRs.getString("PushAccessToken");
                        Platform platform = Platform.values[getUserRs.getInt("Platform")];

                        // The ServerAccessToken is never returned, it is set to null.
                        User tmp = new User(userId, username, null, pushAccessToken, platform, active);
                        participants.add(tmp);
                    }
                }
                getUserStmt.close();
                getUserIdsStmt.close();

                // Finally, set the participants list in the group object.
                group.setParticipants(participants);
                logger.info("Added list of participants to the group object of the group with id: {}.", group.getId());
            }
            getGroupStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End with group:{}.", group);
        return group;
    }

    /**
     * Updates the data of the group in the database.
     *
     * @param updatedGroup The group object which contains the new data values for this group.
     * @throws DatabaseException If the update has failed due to database failure.
     */
    public void updateGroup(Group updatedGroup) throws DatabaseException {
        logger.debug("Start with updatedGroup:{}.", updatedGroup);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "UPDATE `Group` " +
                    "SET Name=?, Description=?, Password=?, Term=?, GroupAdmin_User_Id=?, ModificationDate=? " +
                    "WHERE Id=?;";

            PreparedStatement updateGroupStmt = con.prepareStatement(query);
            updateGroupStmt.setString(1, updatedGroup.getName());
            updateGroupStmt.setString(2, updatedGroup.getDescription());
            updateGroupStmt.setString(3, updatedGroup.getPassword());
            updateGroupStmt.setString(4, updatedGroup.getTerm());
            updateGroupStmt.setInt(5, updatedGroup.getGroupAdmin());
            updateGroupStmt.setTimestamp(6, Timestamp.from(updatedGroup.getModificationDate().toInstant()));
            updateGroupStmt.setInt(7, updatedGroup.getId());

            updateGroupStmt.executeUpdate();
            updateGroupStmt.close();
            logger.info("Updated group with id:{}.", updatedGroup.getId());
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Deletes the group with the specified id from the database. All sub-resources of the group are deleted as well.
     * This happens automatically as the sub-resources depend on the group and are thus removed if the group is deleted.
     *
     * @param groupId The id of the group which should be deleted.
     * @throws DatabaseException If the group could not be deleted due to a database failure.
     */
    public void deleteGroup(int groupId) throws DatabaseException {
        logger.debug("Start with groupId:{}.", groupId);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "DELETE FROM `Group` " +
                    "WHERE Id=?;";

            PreparedStatement deleteGroupStmt = con.prepareStatement(query);
            deleteGroupStmt.setInt(1, groupId);

            deleteGroupStmt.execute();
            deleteGroupStmt.close();
            logger.info("Deleted group with id:{}.", groupId);
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Checks if the group with the specified id exists in the database.
     *
     * @param groupId The id of a group which should be checked.
     * @return Returns true if the group exists in the database, false otherwise.
     * @throws DatabaseException If the execution of the database lookup fails.
     */
    public boolean isValidGroup(int groupId) throws DatabaseException {
        logger.debug("Start with groupId:{}.", groupId);
        boolean valid = false;
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT Id " +
                    "FROM `Group` " +
                    "WHERE Id=?;";

            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, groupId);

            ResultSet rs = stmt.executeQuery();
            if(rs.next()){
                valid = true;
            }
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.debug("End with valid:{}.", valid);
        return valid;
    }

    /**
     * Checks whether the user with the specified id is an active participant of the group with the given id.
     *
     * @param groupId The id of the group.
     * @param userId The id of the user.
     * @return Returns true, if the user is an active participant of the group, false otherwise.
     * @throws DatabaseException If the execution of the query fails due to a database failure.
     */
    public boolean isActiveParticipant(int groupId, int userId) throws DatabaseException {
        logger.debug("Start with groupId:{} and userId:{}.", groupId, userId);
        Connection con = null;
        boolean valid = false;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT * " +
                    "FROM UserGroup " +
                    "WHERE User_Id=? AND Group_Id=?;";

            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, userId);
            stmt.setInt(2, groupId);

            ResultSet rs = stmt.executeQuery();
            if(rs.next()){
                boolean active = rs.getBoolean("Active");
                if(active){
                    valid = true;
                }
            }
            stmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End with valid:{}", valid);
        return valid;
    }

    /**
     * Adds an user as a participant to a group. The user and the group are both identified by the given ids.
     *
     * @param groupId The id of the group that the user intends to join.
     * @param userId The id of the user who joins the group.
     * @throws DatabaseException If the participant could not be added to the group due to a database failure.
     */
    public void addParticipantToGroup(int groupId, int userId) throws DatabaseException {
        logger.debug("Start with groupId:{} and userId:{}.", groupId, userId);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getParticipantQuery =
                    "SELECT * " +
                    "FROM UserGroup " +
                    "WHERE User_Id=? AND Group_Id=?;";

            // Check if the user is already a participant of the group.
            PreparedStatement getParticipantStmt = con.prepareStatement(getParticipantQuery);
            getParticipantStmt.setInt(1, userId);
            getParticipantStmt.setInt(2, groupId);

            ResultSet getParticipantRs = getParticipantStmt.executeQuery();
            if(getParticipantRs.next()){
                logger.warn("User with id: {} is already in the participant table of the group with id {}. If the " +
                        "status of the user is inactive, it will be set to active again. Otherwise, no action will " +
                        "be taken.", userId, groupId);
                boolean active = getParticipantRs.getBoolean("Active");

                if(!active){
                    // Set the active field to true again.
                    String updateParticipantQuery =
                            "UPDATE UserGroup " +
                            "SET Active=? " +
                            "WHERE User_Id=? AND Group_Id=?;";

                    PreparedStatement updateParticipantStmt = con.prepareStatement(updateParticipantQuery);
                    updateParticipantStmt.setBoolean(1, true);
                    updateParticipantStmt.setInt(2, userId);
                    updateParticipantStmt.setInt(3, groupId);

                    updateParticipantStmt.executeUpdate();
                    logger.info("Set the active field for the user with id {} to true again. The user is an active " +
                            "participant of the group with id {} again.", userId, groupId);
                    updateParticipantStmt.close();
                }
                else{
                    logger.info("User with id {} is already an active participant of the group with id {}. No action " +
                                    "performed.", userId, groupId);
                }
            }
            else {
                // Add the user as a participant to the group.
                String insertParticipantQuery =
                        "INSERT INTO UserGroup (User_Id, Group_Id, Active) " +
                                "VALUES (?,?,?);";


                PreparedStatement addParticipantStmt = con.prepareStatement(insertParticipantQuery);
                addParticipantStmt.setInt(1, userId);
                addParticipantStmt.setInt(2, groupId);
                addParticipantStmt.setBoolean(3, true);

                addParticipantStmt.execute();

                logger.info("Added the user with id {} as a participant for the group with id {}.", userId, groupId);
                addParticipantStmt.close();
            }
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Returns a list of users who are participants of the group with the specified id. Returns all participants,
     * also those who have left the group and are thus not active participants anymore. It is required to provide all
     * users who have been a participant of the group at any time to resolve possible dependencies.
     *
     * @param groupId The id of the group.
     * @return Returns a list of users. The list can also be empty.
     * @throws DatabaseException If the execution fails due to database failure.
     */
    public List<User> getParticipants(int groupId) throws DatabaseException {
        logger.debug("Start with groupId:{}.", groupId);
        List<User> users = new ArrayList<User>();
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getUserIdsQuery =
                    "SELECT User_Id, Active " +
                    "FROM UserGroup " +
                    "WHERE Group_Id=?;";
            String getUserQuery =
                    "SELECT * " +
                    "FROM User " +
                    "WHERE Id=?;";

            PreparedStatement getUserIdsStmt = con.prepareStatement(getUserIdsQuery);
            PreparedStatement getUserStmt = con.prepareStatement(getUserQuery);

            // First request the ids of the users which are a participant of the group.
            getUserIdsStmt.setInt(1, groupId);
            ResultSet getUserIdsRs = getUserIdsStmt.executeQuery();
            while(getUserIdsRs.next()){
                int userId = getUserIdsRs.getInt("User_Id");
                boolean active = getUserIdsRs.getBoolean("Active");

                // Request the user data for the user with this id.
                getUserStmt.setInt(1, userId);
                ResultSet getUserRs = getUserStmt.executeQuery();
                if(getUserRs.next()){
                    String name = getUserRs.getString("Name");
                    String pushAccessToken = getUserRs.getString("PushAccessToken");
                    Platform platform = Platform.values[getUserRs.getInt("Platform")];

                    // The server access token is not returned, it is set to null.
                    User tmp = new User(userId, name, null, pushAccessToken, platform, active);
                    users.add(tmp);
                }
            }
            getUserIdsStmt.close();
            getUserStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End with users:{}.", users);
        return users;
    }

    /**
     * Removes the user who is identified by the specified id from the group. This is done by setting the participant
     * to an inactive status. The user remains a part of the participant list of the group, but is not considered an
     * active participant anymore. The user needs to remain in the participant list to be able to resolve possible
     * dependencies.
     *
     * @param groupId The id of the group.
     * @param userId The id of the user.
     * @throws DatabaseException If the execution fails due to a database failure.
     */
    public void removeParticipantFromGroup(int groupId, int userId) throws DatabaseException {
        logger.debug("Start with groupId:{} and userId:{}.", groupId, userId);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "UPDATE UserGroup " +
                    "SET Active=? " +
                    "WHERE User_Id=? AND Group_Id=?;";

            PreparedStatement removeParticipantStmt = con.prepareStatement(query);
            removeParticipantStmt.setBoolean(1, false);
            removeParticipantStmt.setInt(2, userId);
            removeParticipantStmt.setInt(3, groupId);

            removeParticipantStmt.executeUpdate();
            logger.info("Remove user with id {} from the group with id {}. The user is not an active participant of " +
                    "the group anymore.", userId, groupId);
            removeParticipantStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Stores the data of a new ballot into the database. The ballot belongs to a group which is identified by the
     * specified id.
     *
     * @param ballot The ballot object containing the data for the new ballot.
     * @param groupId The id of the group to which the ballot belongs.
     * @throws DatabaseException If the ballot could not be stored due to a database failure.
     */
    public void storeBallot(Ballot ballot, int groupId) throws DatabaseException {
        logger.debug("Start with groupId:{} and ballot:{}.", groupId, ballot);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "INSERT INTO Ballot (Title, Description, MultipleChoice, Public, Closed, Group_Id, " +
                    "BallotAdmin_User_Id) " +
                    "VALUES (?,?,?,?,?,?,?);";

            // When the ballot is stored into the database, it is not closed.
            ballot.setClosed(false);

            PreparedStatement insertBallotStmt = con.prepareStatement(query);
            insertBallotStmt.setString(1, ballot.getTitle());
            insertBallotStmt.setString(2, ballot.getDescription());
            insertBallotStmt.setBoolean(3, ballot.getMultipleChoice());
            insertBallotStmt.setBoolean(4, ballot.getPublicVotes());
            insertBallotStmt.setBoolean(5, ballot.getClosed());
            insertBallotStmt.setInt(6, groupId);
            insertBallotStmt.setInt(7, ballot.getAdmin());

            insertBallotStmt.execute();

            // Retrieve auto incremented id of the database record.
            String getIdQuery = "SELECT LAST_INSERT_ID();";

            Statement getIdStmt = con.createStatement();
            ResultSet getIdRs = getIdStmt.executeQuery(getIdQuery);
            if(getIdRs.next()){
                ballot.setId(getIdRs.getInt(1));
            }

            logger.info("Stored the ballot with id {} for the group with id {} in the database.", ballot.getId(),
                    groupId);
            insertBallotStmt.close();
            getIdStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Returns a list of ballots which belong to the group with the specified id. It can be defined whether the
     * ballot objects should already contain corresponding sub-resources like their options and the list of voters for
     * each option.
     *
     * @param groupId The id of the group for which the ballots should be returned.
     * @param withSubresources Indicates whether the ballot objects, which are returned, should contain a list of
     *                         their options and a list of voters per option.
     * @return A list of ballot objects. The list might also be empty.
     * @throws DatabaseException If the ballots could not be retrieved from the database.
     */
    public List<Ballot> getBallots(int groupId, boolean withSubresources) throws DatabaseException {
        logger.debug("Start with groupId:{} and withSubresources:{}.", groupId, withSubresources);
        List<Ballot> ballots = new ArrayList<Ballot>();
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getBallotsQuery =
                    "SELECT *" +
                    "FROM Ballot " +
                    "WHERE Group_Id=?;";
            String getBallotOptionsQuery =
                    "SELECT * " +
                    "FROM `Option` " +
                    "WHERE Ballot_Id=?;";
            String getUserIdsQuery =
                    "SELECT User_Id " +
                    "FROM UserOption " +
                    "WHERE Option_Id=?;";

            PreparedStatement getBallotsStmt = con.prepareStatement(getBallotsQuery);
            getBallotsStmt.setInt(1, groupId);

            // If the sub-resources should be contained, prepare the statements here.
            PreparedStatement getBallotOptionsStmt = null;
            PreparedStatement getBallotOptionVotersStmt = null;
            if(withSubresources){
                logger.info("Including the sub-resources into the ballots of group with id {}.", groupId);
                getBallotOptionsStmt = con.prepareStatement(getBallotOptionsQuery);
                getBallotOptionVotersStmt = con.prepareStatement(getUserIdsQuery);
            }

            // Request the ballots for the given group.
            ResultSet getBallotsRs = getBallotsStmt.executeQuery();
            while(getBallotsRs.next()){
                int id = getBallotsRs.getInt("Id");
                String title = getBallotsRs.getString("Title");
                String description = getBallotsRs.getString("Description");
                Boolean multipleChoice = getBallotsRs.getBoolean("MultipleChoice");
                Boolean publicVotes = getBallotsRs.getBoolean("Public");
                Boolean closed = getBallotsRs.getBoolean("Closed");
                int ballotAdmin = getBallotsRs.getInt("BallotAdmin_User_Id");

                Ballot ballotTmp = new Ballot(id, title, description, ballotAdmin, closed, multipleChoice, publicVotes);

                // Request the sub-resources options and voters if required.
                if(withSubresources){
                    List<Option> ballotOptions = new ArrayList<Option>();
                    // Request all options for the ballot.
                    getBallotOptionsStmt.setInt(1, ballotTmp.getId());
                    ResultSet getBallotOptionsRs = getBallotOptionsStmt.executeQuery();
                    while(getBallotOptionsRs.next()){
                        int optionId = getBallotOptionsRs.getInt("Id");
                        String text = getBallotOptionsRs.getString("Text");

                        Option optionTmp = new Option(optionId, text);

                        // Request the ids of the users who have voted for this particular option.
                        List<Integer> voters = new ArrayList<Integer>();
                        getBallotOptionVotersStmt.setInt(1, optionId);
                        ResultSet getBallotOptionVotersRs = getBallotOptionVotersStmt.executeQuery();
                        while(getBallotOptionVotersRs.next()){
                            int userId = getBallotOptionVotersRs.getInt("User_Id");

                            // Add the id of the user to the voters list.
                            voters.add(userId);
                        }

                        // Add the list of voters to the Option object.
                        optionTmp.setVoters(voters);

                        // Add the option object to the list of ballot options.
                        ballotOptions.add(optionTmp);
                    }

                    // Add the list of options to the ballot object.
                    ballotTmp.setOptions(ballotOptions);
                }

                // Add the ballot object to the list of ballots.
                ballots.add(ballotTmp);
            }

            getBallotsStmt.close();
            if(getBallotOptionsStmt != null && getBallotOptionVotersStmt != null){
                getBallotOptionsStmt.close();
                getBallotOptionVotersStmt.close();
            }
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End with ballots:{}.", ballots);
        return ballots;
    }

    /**
     * Returns the ballots which belong to the group with the specified id and have the user with the defined id as
     * the ballot administrator.
     *
     * @param groupId The id of the group.
     * @param adminId The id of the user who is the ballot administrator of the ballots.
     * @return List of ballot objects. The list can also be empty.
     * @throws DatabaseException If the could not be retrieved from the database.
     */
    public List<Ballot> getBallots(int groupId, int adminId) throws DatabaseException {
        logger.debug("Start with groupId:{} and adminId:{}.", groupId, adminId);
        List<Ballot> ballots = new ArrayList<Ballot>();
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getBallotsQuery =
                    "SELECT * " +
                    "FROM Ballot " +
                    "WHERE Group_Id=? AND BallotAdmin_User_Id=?;";

            PreparedStatement getBallotsStmt = con.prepareStatement(getBallotsQuery);
            getBallotsStmt.setInt(1, groupId);
            getBallotsStmt.setInt(2, adminId);

            ResultSet getBallotsRs = getBallotsStmt.executeQuery();
            while(getBallotsRs.next()){
                int id = getBallotsRs.getInt("Id");
                String title = getBallotsRs.getString("Title");
                String description = getBallotsRs.getString("Description");
                Boolean multipleChoice = getBallotsRs.getBoolean("MultipleChoice");
                Boolean publicVotes = getBallotsRs.getBoolean("Public");
                Boolean closed = getBallotsRs.getBoolean("Closed");

                Ballot ballotTmp = new Ballot(id, title, description, adminId, closed, multipleChoice, publicVotes);
                ballots.add(ballotTmp);
            }

            getBallotsStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End with ballots:{}.", ballots);
        return ballots;
    }

    /**
     * Returns the ballot object of the ballot with the specified id. The ballot needs to belong to the group which
     * is identified by the defined id.
     *
     * @param groupId The id of the group.
     * @param ballotId The id of the ballot.
     * @return The ballot object containing the data of the ballot. Returns null if no ballot with the given id.
     * @throws DatabaseException If the ballot could not be retrieved from the database.
     */
    public Ballot getBallot(int groupId, int ballotId) throws DatabaseException {
        logger.debug("Start with ballotId:{}.", ballotId);
        Ballot ballot = null;
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getBallotQuery =
                    "SELECT * " +
                    "FROM Ballot " +
                    "WHERE Id=? AND Group_Id=?;";

            PreparedStatement getBallotStmt = con.prepareStatement(getBallotQuery);
            getBallotStmt.setInt(1, ballotId);
            getBallotStmt.setInt(2, groupId);

            ResultSet getBallotRs = getBallotStmt.executeQuery();
            if(getBallotRs.next()){
                String title = getBallotRs.getString("Title");
                String description = getBallotRs.getString("Description");
                Boolean multipleChoice = getBallotRs.getBoolean("MultipleChoice");
                Boolean publicVotes = getBallotRs.getBoolean("Public");
                Boolean closed = getBallotRs.getBoolean("Closed");
                int ballotAdmin = getBallotRs.getInt("BallotAdmin_User_Id");

                ballot = new Ballot(ballotId, title, description, ballotAdmin, closed, multipleChoice, publicVotes);
            }

            getBallotStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
        return ballot;
    }

    /**
     * Updates the data of the ballot in the database.
     *
     * @param ballot The ballot object which contains the new data for the ballot.
     * @throws DatabaseException If the update of the ballot fails.
     */
    public void updateBallot(Ballot ballot) throws DatabaseException {
        logger.debug("Start with ballot:{}.", ballot);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "UPDATE Ballot " +
                    "SET Title=?, Description=?, Closed=?, BallotAdmin_User_Id=? " +
                    "WHERE Id=?;";

            PreparedStatement updateBallotStmt = con.prepareStatement(query);
            updateBallotStmt.setString(1, ballot.getTitle());
            updateBallotStmt.setString(2, ballot.getDescription());
            updateBallotStmt.setBoolean(3, ballot.getClosed());
            updateBallotStmt.setInt(4, ballot.getAdmin());
            updateBallotStmt.setInt(5, ballot.getId());

            updateBallotStmt.executeUpdate();

            logger.info("Updated the ballot with id {}.", ballot.getId());
            updateBallotStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Deletes the ballot which is identified by the specified id.
     *
     * @param groupId The id of the group to which the ballot belongs.
     * @param ballotId The id of the ballot.
     * @throws DatabaseException If the deletion fails due to a database failure.
     */
    public void deleteBallot(int groupId, int ballotId) throws DatabaseException {
        logger.debug("Start with groupId:{} and ballotId:{}." , groupId, ballotId);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String deleteBallotQuery =
                    "DELETE FROM Ballot " +
                    "WHERE Id=? AND Group_Id=?;";

            PreparedStatement deleteBallotStmt = con.prepareStatement(deleteBallotQuery);
            deleteBallotStmt.setInt(1, ballotId);
            deleteBallotStmt.setInt(2, groupId);

            deleteBallotStmt.execute();

            logger.info("Deleted the ballot with id {} from the group with id {}.", ballotId, groupId);
            deleteBallotStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Checks whether the ballot with the specified id exists within the group which is identified by the given id.
     *
     * @param groupId The id of the group.
     * @param ballotId The id of the ballot whose existence should be checked.
     * @return Returns true if the ballot is a valid ballot of the specified group, false otherwise.
     * @throws DatabaseException If the verification of the ballot existence fails due to a database failure.
     */
    public boolean isValidBallot(int groupId, int ballotId) throws DatabaseException {
        boolean isValid = false;
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT Id " +
                    "FROM Ballot " +
                    "WHERE Id=? AND Group_Id=?;";

            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, ballotId);
            stmt.setInt(2, groupId);

            ResultSet rs = stmt.executeQuery();
            if(rs.next()){
                isValid = true;
            }
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        return isValid;
    }

    /**
     * Stores an option in the database. The option belongs to the ballot which is identified by the specified id.
     *
     * @param ballotId The id of the ballot to which the option should belong.
     * @param option The option object containing the data of the option.
     * @throws DatabaseException If the option could not be stored in the database.
     */
    public void storeOption(int ballotId, Option option) throws DatabaseException {
        logger.debug("Start with ballotId:{} and option:{}.", ballotId, option);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String insertOptionQuery =
                    "INSERT INTO `Option` (Text, Ballot_Id) " +
                    "VALUES (?,?);";

            PreparedStatement insertOptionStmt = con.prepareStatement(insertOptionQuery);
            insertOptionStmt.setString(1, option.getText());
            insertOptionStmt.setInt(2, ballotId);

            insertOptionStmt.execute();

            // Retrieve auto incremented id of the database record.
            String getIdQuery = "SELECT LAST_INSERT_ID();";

            Statement getIdStmt = con.createStatement();
            ResultSet getIdRs = getIdStmt.executeQuery(getIdQuery);
            if(getIdRs.next()){
                option.setId(getIdRs.getInt(1));
            }

            logger.info("Stored the option with id {}. It belongs to the ballot with id {}.", option.getId(), ballotId);
            getIdStmt.close();
            insertOptionStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Returns a list of options which belong to the ballot which is identified by the specified id.
     *
     * @param ballotId The id of the ballot.
     * @return A list of options which belong to the ballot. The list can also be empty.
     * @throws DatabaseException If the retrieval of the options from the database fails.
     */
    public List<Option> getOptions(int ballotId) throws DatabaseException {
        logger.debug("Start with ballotId:{}.", ballotId);
        List<Option> options = new ArrayList<Option>();
        Connection con = null;

        try {
            con = getDatabaseConnection();
            String getOptionsQuery =
                    "SELECT * " +
                    "FROM `Option` " +
                    "WHERE Ballot_Id=?;";

            PreparedStatement getOptionsStmt = con.prepareStatement(getOptionsQuery);
            getOptionsStmt.setInt(1, ballotId);

            ResultSet getOptionsRs = getOptionsStmt.executeQuery();
            while(getOptionsRs.next()){
                int id = getOptionsRs.getInt("Id");
                String text = getOptionsRs.getString("Text");

                Option tmp = new Option(id, text);
                options.add(tmp);
            }

            getOptionsStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.debug("End with options:{}.", options);
        return options;
    }

    /**
     * Returns the option which is identified by the specified id and belongs to the ballot with the given id.
     *
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The id of the option.
     * @return The option object containing the data of the option.
     * @throws DatabaseException If the retrieval of the option fails.
     */
    public Option getOption(int ballotId, int optionId) throws DatabaseException {
        logger.debug("Start with ballotId:{} and optionId:{}.", ballotId, optionId);
        Option option = null;
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getOptionQuery =
                    "SELECT * " +
                    "FROM `Option` " +
                    "WHERE Id=? AND Ballot_Id=?;";

            PreparedStatement getOptionStmt = con.prepareStatement(getOptionQuery);
            getOptionStmt.setInt(1, optionId);
            getOptionStmt.setInt(2, ballotId);

            ResultSet getOptionRs = getOptionStmt.executeQuery();
            if(getOptionRs.next()){
                String text = getOptionRs.getString("Text");

                option = new Option(optionId, text);
            }

            getOptionStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.debug("End with option:{}.", option);
        return option;
    }

    /**
     * Deletes the option with the specified id.
     *
     * @param ballotId The id of the ballot to which the option belongs.
     * @param optionId The id of the option which should be deleted.
     * @throws DatabaseException If the deletion fails due to a database failure.
     */
    public void deleteOption(int ballotId, int optionId) throws DatabaseException {
        logger.debug("Start with ballotId:{} and optionId:{}.", ballotId, optionId);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "DELETE FROM `Option` " +
                    "WHERE Id=? AND Ballot_Id=?;";

            PreparedStatement deleteOptionStmt = con.prepareStatement(query);
            deleteOptionStmt.setInt(1, optionId);
            deleteOptionStmt.setInt(2, ballotId);

            deleteOptionStmt.execute();

            logger.info("Deleted the option with id {} from ballot with id {}.", optionId, ballotId);
            deleteOptionStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Checks if the option with the specified id is a valid option of the ballot with the given id.
     *
     * @param ballotId The id of the ballot.
     * @param optionId The id of the option.
     * @return Returns true if the option is a valid option of the ballot, false otherwise.
     * @throws DatabaseException If the validation fails due to a database failure.
     */
    public boolean isValidOption(int ballotId, int optionId) throws DatabaseException {
        logger.debug("Start with ballotId:{} and optionId:{}.", ballotId, optionId);
        boolean isValid = false;
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT Id " +
                    "FROM `Option` " +
                    "WHERE Id=? AND Ballot_Id=?;";

            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, optionId);
            stmt.setInt(2, ballotId);

            ResultSet rs = stmt.executeQuery();
            if(rs.next()){
                isValid = true;
            }

            stmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.debug("End with isValid:{}.", isValid);
        return isValid;
    }

    /**
     * Stores the vote from the user with the specified id for the defined option into the database.
     *
     * @param optionId The id for the option which is affected by the vote.
     * @param userId The id of the user who performs the vote.
     * @throws DatabaseException If the vote could not be stored due to a database failure.
     */
    public void storeVote(int optionId, int userId) throws DatabaseException {
        logger.debug("Start with optionId:{} and userId:{}.", optionId, userId);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String storeVoteQuery =
                    "INSERT INTO UserOption (User_Id, Option_Id) " +
                    "VALUES (?,?);";

            PreparedStatement storeVoteStmt = con.prepareStatement(storeVoteQuery);
            storeVoteStmt.setInt(1, userId);
            storeVoteStmt.setInt(2, optionId);

            storeVoteStmt.execute();

            logger.info("Vote from user with id {} for the option with id {} is stored.", userId, optionId);
            storeVoteStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Checks if the vote from the user with the specified id for the defined option is valid. The vote is valid if
     * the user has not voted for the defined option before.
     *
     * @param optionId The id of the option.
     * @param userId The id of the user.
     * @return Returns true if the vote for the given option is valid, false otherwise.
     * @throws DatabaseException If the validation fails due to a database failure.
     */
    public boolean isValidVote(int optionId, int userId) throws DatabaseException {
        logger.debug("Start with optionId:{} and userId:{}.", optionId, userId);
        boolean isValid = true;
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT * " +
                    "FROM UserOption " +
                    "WHERE Option_Id=? AND User_Id=?;";

            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, optionId);
            stmt.setInt(2, userId);

            ResultSet rs = stmt.executeQuery();
            if(rs.next()){
                isValid = false;
            }
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.info("End with isValid:{}.", isValid);
        return isValid;
    }

    /**
     * Checks if the user with the specified id has already voted for any option in the ballot with the given id.
     *
     * @param ballotId The id of the ballot which is checked.
     * @param userId The if of the user.
     * @return Returns true if the user has already placed at least one vote for the given ballot, false otherwise.
     * @throws DatabaseException If the validation fails due to a database failure.
     */
    public boolean hasAlreadyVoted(int ballotId, int userId) throws DatabaseException {
        logger.debug("Start with ballotId:{} and userId:{}.", ballotId, userId);
        boolean hasVoted = false;
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT COUNT(*) " +
                    "FROM `Option` AS o JOIN UserOption AS uo ON o.Id=uo.Option_Id " +
                    "WHERE o.Ballot_Id=? AND uo.User_Id=?;";

            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, ballotId);
            stmt.setInt(2, userId);

            ResultSet rs = stmt.executeQuery();
            if(rs.next()){
                // The number of returned rows.
                int numberOfRows = rs.getInt(1);
                if (numberOfRows >= 1) {
                    hasVoted = true;
                }
            }
            stmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.debug("End with hasVoted:{}.", hasVoted);
        return hasVoted;
    }

    /**
     * Returns a list of users who have voted for the option with the specified id.
     *
     * @param optionId The id of the option.
     * @return A list of users who have voted for the given option. The list can also be empty.
     * @throws DatabaseException If the retrieval fails due to a database failure.
     */
    public List<User> getVoters(int optionId) throws DatabaseException {
        logger.debug("Start with optionId:{}.", optionId);
        List<User> users = new ArrayList<User>();
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getVotersQuery =
                    "SELECT * " +
                    "FROM User AS u JOIN UserOption AS uo ON u.Id=uo.User_Id " +
                    "WHERE uo.Option_Id=?;";

            PreparedStatement getVotersStmt = con.prepareStatement(getVotersQuery);
            getVotersStmt.setInt(1, optionId);

            ResultSet getVotersRs = getVotersStmt.executeQuery();
            while (getVotersRs.next()){
                int userId = getVotersRs.getInt("Id");
                String name = getVotersRs.getString("Name");
                String pushAccessToken = getVotersRs.getString("PushAccessToken");
                Platform platform = Platform.values[getVotersRs.getInt("Platform")];

                // The server access token is never returned, it is set to null.
                User tmp = new User(userId, name, null, pushAccessToken, platform);
                users.add(tmp);
            }
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.debug("End with users:{}.", users);
        return users;
    }

    /**
     * Deletes the vote from the user with the specified id for the defined option.
     *
     * @param optionId The id of the option.
     * @param userId The id of the user.
     * @throws DatabaseException If the deletion fails due to a database failure.
     */
    public void deleteVote(int optionId, int userId) throws DatabaseException {
        logger.debug("Start with optionId:{} and userId:{}.", optionId, userId);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String deleteVoteQuery =
                    "DELETE FROM UserOption " +
                    "WHERE Option_Id=? AND User_Id=?;";

            PreparedStatement deleteVoteStmt = con.prepareStatement(deleteVoteQuery);
            deleteVoteStmt.setInt(1, optionId);
            deleteVoteStmt.setInt(2, userId);

            deleteVoteStmt.execute();

            logger.info("Deleted the vote from user with id {} for option with id {}.", userId, optionId);
            deleteVoteStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Stores a new conversation in the database for the group with the specified id.
     *
     * @param groupId The id of the group for which the conversation is created.
     * @param conversation The conversation object containing the data of the new conversation.
     * @throws DatabaseException If the conversation could not be stored in the database.
     */
    public void storeConversation(int groupId, Conversation conversation) throws DatabaseException {
        logger.debug("Start with groupId:{} and conversation:{}.", groupId, conversation);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String insertConversationQuery =
                    "INSERT INTO Conversation (Title, Closed, Group_Id, ConversationAdmin_User_Id) " +
                    "VALUES (?,?,?,?);";

            // Conversation is not closed when it is stored within the database.
            conversation.setClosed(false);

            PreparedStatement insertConversationStmt = con.prepareStatement(insertConversationQuery);
            insertConversationStmt.setString(1, conversation.getTitle());
            insertConversationStmt.setBoolean(2, conversation.getClosed());
            insertConversationStmt.setInt(3, groupId);
            insertConversationStmt.setInt(4, conversation.getAdmin());

            insertConversationStmt.execute();

            // Retrieve auto incremented id of the database record.
            String getIdQuery = "SELECT LAST_INSERT_ID();";

            Statement getIdStmt = con.createStatement();
            ResultSet getIdRs = getIdStmt.executeQuery(getIdQuery);
            if(getIdRs.next()){
                conversation.setId(getIdRs.getInt(1));
            }

            logger.info("Stored the conversation with id {} in the database. The conversation belongs to the group " +
                    "with id {}.", conversation.getId(), groupId);
            getIdStmt.close();
            insertConversationStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Returns a list of conversation objects which belong to the group with the specified id. It can be defined
     * whether the single conversation objects should contain a list of their sub-resources, i.e. the conversation
     * messages which have been sent into the conversation.
     *
     * @param groupId The if of the group for which the conversations are requested.
     * @param withSubresources Indicates whether the conversations should contain a list of their sub-resources.
     * @return Returns a list of conversation objects. The list can also be empty.
     * @throws DatabaseException If the retrieval of the conversation fails due to a database failure.
     */
    public List<Conversation> getConversations(int groupId, boolean withSubresources) throws DatabaseException {
        logger.debug("Start with groupId:{} and withSubresources:{}.", groupId, withSubresources);
        List<Conversation> conversations = new ArrayList<Conversation>();
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getConversationsQuery =
                    "SELECT * " +
                    "FROM Conversation " +
                    "WHERE Group_Id=?;";
            String getMessagesQuery =
                    "SELECT * " +
                    "FROM ConversationMessage AS cm JOIN Message AS m ON cm.Message_Id=m.Id " +
                    "WHERE cm.Conversation_Id=?;";

            PreparedStatement getConversationsStmt = con.prepareStatement(getConversationsQuery);
            getConversationsStmt.setInt(1, groupId);

            // Prepare statement only when sub-resources are requested.
            PreparedStatement getMessagesStmt = null;
            if(withSubresources){
                logger.info("Conversations for group with id {} are requested including sub-resources.", groupId);
                getMessagesStmt = con.prepareStatement(getMessagesQuery);
            }

            // Get all the conversations from the database.
            ResultSet getConversationsRs = getConversationsStmt.executeQuery();
            while(getConversationsRs.next()){
                int conversationId = getConversationsRs.getInt("Id");
                String title = getConversationsRs.getString("Title");
                boolean closed = getConversationsRs.getBoolean("Closed");
                int admin = getConversationsRs.getInt("ConversationAdmin_User_Id");

                Conversation conversationTmp = new Conversation(conversationId, title, closed, admin);

                // Get messages for this conversation if sub-resources are requested.
                if(withSubresources){
                    List<ConversationMessage> conversationMessages = new ArrayList<ConversationMessage>();

                    // Execute the query to get the messages for the conversation with the given id.
                    getMessagesStmt.setInt(1, conversationTmp.getId());
                    ResultSet getMessagesRs = getMessagesStmt.executeQuery();
                    while(getMessagesRs.next()){
                        int messageId = getMessagesRs.getInt("Id");
                        String text = getMessagesRs.getString("Text");
                        ZonedDateTime creationDate = getMessagesRs.getTimestamp("CreationDate").toLocalDateTime()
                                .atZone(Constants.TIME_ZONE);
                        Priority priority = Priority.values[getMessagesRs.getInt("Priority")];
                        int messageNumber = getMessagesRs.getInt("MessageNumber");
                        int authorUser = getMessagesRs.getInt("Author_User_Id");

                        // Add the message to the list.
                        ConversationMessage conversationMsgTmp = new ConversationMessage(messageId, text, messageNumber,
                                priority, creationDate, authorUser, conversationTmp.getId());
                        conversationMessages.add(conversationMsgTmp);
                    }

                    // Add the list of conversation messages to the conversation object.
                    conversationTmp.setConversationMessages(conversationMessages);
                }

                // Add the conversation object to the list of conversation objects.
                conversations.add(conversationTmp);
            }

            getConversationsStmt.close();
            if(withSubresources){
                getMessagesStmt.close();
            }
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.debug("End with conversations:{}.", conversations);
        return conversations;
    }

    /**
     * Returns a list of conversation objects for a group which have the user with the specified id as the
     * administrator.
     *
     * @param groupId The id of the group to which the conversations belong.
     * @param adminId The id of the user who is the administrator of the conversations.
     * @return A list of conversation objects. The list can also be empty.
     * @throws DatabaseException If the retrieval of the conversation fails due to a database failure.
     */
    public List<Conversation> getConversations(int groupId, int adminId) throws DatabaseException {
        logger.debug("Start with groupId:{} and adminId:{}.", groupId, adminId);
        List<Conversation> conversations = new ArrayList<Conversation>();
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getConversationsQuery =
                    "SELECT * " +
                    "FROM Conversation " +
                    "WHERE Group_Id=? AND ConversationAdmin_User_Id=?;";

            PreparedStatement getConversationsStmt = con.prepareStatement(getConversationsQuery);
            getConversationsStmt.setInt(1, groupId);
            getConversationsStmt.setInt(2, adminId);

            ResultSet getConversationsRs = getConversationsStmt.executeQuery();
            while(getConversationsRs.next()){
                int conversationId = getConversationsRs.getInt("Id");
                String title = getConversationsRs.getString("Title");
                Boolean closed = getConversationsRs.getBoolean("Closed");

                Conversation conversationTmp = new Conversation(conversationId, title, closed, adminId);
                conversations.add(conversationTmp);
            }

            getConversationsStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.debug("End with conversations:{}.", conversations);
        return conversations;
    }

    /**
     * Returns the conversation with the specified id. The conversation belongs to the group with the given id.
     *
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation which should be retrieved.
     * @return The conversation object, null if the conversation is not found.
     * @throws DatabaseException If the retrieval fails due to a database failure.
     */
    public Conversation getConversation(int groupId, int conversationId) throws DatabaseException {
        logger.debug("Start with groupId:{} and conversationId:{}.", groupId, conversationId);
        Conversation conversation = null;
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String getConversationQuery =
                    "SELECT * " +
                    "FROM Conversation " +
                    "WHERE Group_Id=? AND Id=?;";

            PreparedStatement getConversationStmt = con.prepareStatement(getConversationQuery);
            getConversationStmt.setInt(1, groupId);
            getConversationStmt.setInt(2, conversationId);

            ResultSet getConversationRs = getConversationStmt.executeQuery();
            if(getConversationRs.next()){
                String title = getConversationRs.getString("Title");
                Boolean closed = getConversationRs.getBoolean("Closed");
                int admin = getConversationRs.getInt("ConversationAdmin_User_Id");

                conversation = new Conversation(conversationId, title, closed, admin);
            }

            getConversationStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.debug("End with conversation:{}.", conversation);
        return conversation;
    }

    /**
     * Updates the data record of the specified conversation. The conversation object contains the new data values
     * which are then used to update the record in the database.
     *
     * @param conversation The conversation object containing the new data values for the conversation.
     * @throws DatabaseException If the update fails due to a database failure.
     */
    public void updateConversation(Conversation conversation) throws DatabaseException {
        logger.debug("Start with conversation:{}.", conversation);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String updateConversationQuery =
                    "UPDATE Conversation " +
                    "SET Title=?, ConversationAdmin_User_Id=?, Closed=? " +
                    "WHERE Id=?;";

            PreparedStatement updateConversationStmt = con.prepareStatement(updateConversationQuery);
            updateConversationStmt.setString(1, conversation.getTitle());
            updateConversationStmt.setInt(2, conversation.getAdmin());
            updateConversationStmt.setBoolean(3, conversation.getClosed());
            updateConversationStmt.setInt(4, conversation.getId());

            updateConversationStmt.executeUpdate();

            logger.info("Updated the conversation data for the conversation with id {}.", conversation.getId());
            updateConversationStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Removes the conversation with the specified id from the database.
     *
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation.
     * @throws DatabaseException If the deletion of the conversation fails due to a database failure.
     */
    public void deleteConversation(int groupId, int conversationId) throws DatabaseException {
        logger.debug("Start with groupId:{} and conversationId:{}.", groupId, conversationId);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String deleteConversationQuery =
                    "DELETE FROM Conversation " +
                    "WHERE Id=? AND Group_Id=?;";

            PreparedStatement deleteConversationStmt = con.prepareStatement(deleteConversationQuery);
            deleteConversationStmt.setInt(1, conversationId);
            deleteConversationStmt.setInt(2, groupId);

            deleteConversationStmt.execute();

            logger.info("Deleted the conversation with id {} from the group with id {}.", conversationId, groupId);
            deleteConversationStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Stores a new conversation message into the database. The message belongs to the conversation with the given id
     * . The conversationMessage object contains the data of the message.
     *
     * @param conversationId The id of the conversation to which the message belongs.
     * @param conversationMessage Contains the data of the message.
     * @throws DatabaseException If the message could not be stored due to a database failure.
     */
    public void storeConversationMessage(int conversationId, ConversationMessage conversationMessage) throws
            DatabaseException {
        logger.debug("Start with conversationId:{} and conversationMessage:{}.", conversationId, conversationMessage);
        Connection con = null;
        try {
            con = getDatabaseConnection();

            // Set auto commit to false for the following queries.
            con.setAutoCommit(false);

            String insertMsgQuery =
                    "INSERT INTO Message (Text, CreationDate, Priority) " +
                    "VALUES (?,?,?);";
            String insertConversationMsgQuery =
                    "INSERT INTO ConversationMessage (MessageNumber, Conversation_Id, Author_User_Id, Message_Id) " +
                    "VALUES (?,?,?,?);";
            String getMessageNumberQuery =
                    "SELECT MAX(MessageNumber) " +
                    "FROM ConversationMessage " +
                    "WHERE Conversation_Id=?;";

            PreparedStatement insertMsgStmt = con.prepareStatement(insertMsgQuery);
            PreparedStatement insertConversationMsgStmt = con.prepareStatement(insertConversationMsgQuery);
            PreparedStatement getMessageNumberStmt = con.prepareStatement(getMessageNumberQuery);

            // First, insert the message relevant data fields into the Message table.
            insertMsgStmt.setString(1, conversationMessage.getText());
            insertMsgStmt.setTimestamp(2, Timestamp.from(conversationMessage.getCreationDate().toInstant()));
            insertMsgStmt.setInt(3, conversationMessage.getPriority().ordinal());

            insertMsgStmt.execute();

            // Second, retrieve auto incremented id of the database record for the message.
            String getIdQuery = "SELECT LAST_INSERT_ID();";

            Statement getIdStmt = con.createStatement();
            ResultSet getIdRs = getIdStmt.executeQuery(getIdQuery);
            if(getIdRs.next()){
                // Set the id taken from the database to the conversationMessage object.
                conversationMessage.setId(getIdRs.getInt(1));
            }

            // The message number for the new message.
            int messageNumber = 0;
            // Third, retrieve the next message number for the given conversation.
            getMessageNumberStmt.setInt(1, conversationId);
            ResultSet getMessageNumberRs = getMessageNumberStmt.executeQuery();
            if(getMessageNumberRs.next()){
                messageNumber = getMessageNumberRs.getInt(1);
            }
            messageNumber++; // Increment to get the next free message number.
            // Set the message number in the object.
            conversationMessage.setMessageNumber(messageNumber);

            // Fourth, insert the conversation message data fields into the ConversationMessage table.
            insertConversationMsgStmt.setInt(1, messageNumber);
            insertConversationMsgStmt.setInt(2, conversationId);
            insertConversationMsgStmt.setInt(3, conversationMessage.getAuthorUser());
            insertConversationMsgStmt.setInt(4, conversationMessage.getId());

            insertConversationMsgStmt.execute();

            //End transaction.
            con.commit();
            logger.info("Stored the conversation message with the id {} and the message number {}.",
                    conversationMessage.getId(), messageNumber);

            insertMsgStmt.close();
            insertConversationMsgStmt.close();
            getMessageNumberStmt.close();
        } catch (SQLException e) {
            try {
                logger.warn("SQLException occurred during conversationMsg storage, need to rollback the transaction.");
                con.rollback();
            } catch (SQLException e1) {
                logger.warn("Rollback failed.");
                logger.error(Constants.LOG_SQL_EXCEPTION, e1.getSQLState(), e1.getErrorCode(), e1.getMessage());
            }
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            try {
                assert con != null;
                con.setAutoCommit(true);
            } catch (SQLException e) {
                logger.warn("Set auto commit to true has failed.");
                logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            }
            returnConnection(con);
        }
        logger.debug("End.");
    }

    /**
     * Returns the conversation messages of the conversation which is identified by the specified id. The method
     * returns all messages which have a message number higher than the defined one.
     *
     * @param conversationId The id of the conversation for which the messages are retrieved.
     * @param messageNumber Defines the starting message number. The method will return all messsages from the
     *                      conversation which have a higher message number than the one defined with this parameter.
     * @return A list of conversation messages. The list can also be empty.
     * @throws DatabaseException If the retrieval fails due to a database failure.
     */
    public List<ConversationMessage> getConversationMessages(int conversationId, int messageNumber) throws
            DatabaseException {
        logger.debug("Start with conversationId:{} and messageNumber:{}.", conversationId, messageNumber);
        List<ConversationMessage> messages = new ArrayList<ConversationMessage>();
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT * " +
                    "FROM Message AS m JOIN ConversationMessage AS cm ON m.Id=cm.Message_Id " +
                    "WHERE cm.Conversation_Id=? AND cm.MessageNumber > ?;";

            PreparedStatement getMessagesStmt = con.prepareStatement(query);
            getMessagesStmt.setInt(1, conversationId);
            getMessagesStmt.setInt(2, messageNumber);

            ResultSet getMessagesRs = getMessagesStmt.executeQuery();
            while (getMessagesRs.next()){
                int messageId = getMessagesRs.getInt("Id");
                String text = getMessagesRs.getString("Text");
                ZonedDateTime creationDate = getMessagesRs.getTimestamp("CreationDate").toLocalDateTime().atZone
                        (Constants.TIME_ZONE);
                Priority priority = Priority.values[getMessagesRs.getInt("Priority")];
                int messageNr = getMessagesRs.getInt("MessageNumber");
                int authorId = getMessagesRs.getInt("Author_User_Id");

                ConversationMessage tmp = new ConversationMessage(messageId, text, messageNr, priority, creationDate,
                        authorId, conversationId);
                messages.add(tmp);
            }

            getMessagesStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.debug("End with messages:{}.", messages);
        return messages;
    }

    /**
     * Checks if the conversation with the specified id is a valid conversation of the given group.
     *
     * @param groupId The id of the group to which the conversation belongs.
     * @param conversationId The id of the conversation.
     * @return Returns true if the conversation is a valid conversation of the group, false otherwise.
     * @throws DatabaseException If the validation fails due to a database failure.
     */
    public boolean isValidConversation(int groupId, int conversationId) throws DatabaseException {
        logger.debug("Start with groupId:{} and conversationId:{}.", groupId, conversationId);
        boolean isValid = false;
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT Id " +
                    "FROM Conversation " +
                    "WHERE Id=? AND Group_Id=?;";

            PreparedStatement stmt = con.prepareStatement(query);
            stmt.setInt(1, conversationId);
            stmt.setInt(2, groupId);

            ResultSet rs = stmt.executeQuery();
            if(rs.next()){
                isValid = true;
            }
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }

        logger.debug("End with isValid:{}.", isValid);
        return isValid;
    }

}
