package ulm.university.news.manager.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.Ballot;
import ulm.university.news.data.Group;
import ulm.university.news.data.Option;
import ulm.university.news.data.User;
import ulm.university.news.data.enums.GroupType;
import ulm.university.news.data.enums.Platform;
import ulm.university.news.util.Constants;
import ulm.university.news.util.exceptions.DatabaseException;

import javax.naming.ldap.PagedResultsControl;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO
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
            insertGroupStmt.setTimestamp(4,group.getCreationDate());
            insertGroupStmt.setTimestamp(5, group.getModificationDate());
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
     * @return A list of groups. List can also be empty.
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
                Timestamp creationDate = getGroupsRs.getTimestamp("CreationDate");
                Timestamp modificationDate = getGroupsRs.getTimestamp("ModificationDate");
                String term = getGroupsRs.getString("Term");
                int groupAdmin = getGroupsRs.getInt("GroupAdmin_User_Id");

                // The password is never returned in a GET request, it is set to null.
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
                Timestamp creationDate = getGroupRs.getTimestamp("CreationDate");
                Timestamp modificationDate = getGroupRs.getTimestamp("ModificationDate");
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
            if(withParticipants){
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
            updateGroupStmt.setTimestamp(6, updatedGroup.getModificationDate());
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
     * Deletes the group with the specified id from the database. All subresources of the group are deleted as well.
     * This happens automatically as the subresources depend on the group and are thus removed if the group is deleted.
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
     * @throws DatabaseException If the execution of the query fails.
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
                        "user is set to inactive, it will be set to active again. Otherwise, no action will " +
                        "be taken.", userId, groupId);
                boolean active = getParticipantRs.getBoolean("Active");

                if(active == false){
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
        logger.debug("Start with groupId:{].", groupId);
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

            // When the ballot is stored into the database, it is never closed.
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
                    "FROM Option " +
                    "WHERE Ballot_Id=?;";
            String getUserIdsQuery =
                    "SELECT User_Id " +
                    "FROM UserOption " +
                    "WHERE Option_Id=?;";

            PreparedStatement getBallotsStmt = con.prepareStatement(getBallotsQuery);
            getBallotsStmt.setInt(1, groupId);

            // If the subresources should be contained, prepare the statements here.
            PreparedStatement getBallotOptionsStmt = null;
            PreparedStatement getBallotOptionVotersStmt = null;
            if(withSubresources){
                logger.info("Including the subresources into the ballots of group with id {}.", groupId);
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

                // Request the subresources options and voters if required.
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
     * Returns the ballot object of the ballot with the specified id. The ballot needs to belong to the group with
     * the defined id.
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

}
