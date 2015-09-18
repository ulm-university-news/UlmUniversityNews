package ulm.university.news.manager.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.Group;
import ulm.university.news.data.User;
import ulm.university.news.data.enums.GroupType;
import ulm.university.news.data.enums.Platform;
import ulm.university.news.util.Constants;
import ulm.university.news.util.exceptions.DatabaseException;

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
                String term = getGroupRs.getString("Term");
                int groupAdmin = getGroupRs.getInt("GroupAdmin_User_Id");

                // The password is never returned in a GET request, it is set to null.
                group = new Group(groupId, name, description, groupType, creationDate, modificationDate, term, null,
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


}
