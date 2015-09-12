package ulm.university.news.manager.database;

import ulm.university.news.data.User;
import ulm.university.news.util.DatabaseException;
import ulm.university.news.util.Platform;
import ulm.university.news.util.TokenAlreadyExistsException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class UserDatabaseManager extends DatabaseManager {

    /**
     * Creates an instance of the UserDatabaseManager class.
     */
    public UserDatabaseManager(){

    }

    /**
     * Stores the user data into the database.
     *
     * @param user The user object which contains the user data.
     * @throws TokenAlreadyExistsException If the access token of this user is already stored in the database for
     * another user. The access token needs to be unique in the whole system so the storing of the data is aborted.
     * @throws DatabaseException If the data could not be stored in the database due to database failure.
     */
    public void storeUser(User user) throws TokenAlreadyExistsException, DatabaseException {
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "INSERT INTO User (Name, ServerAccessToken, PushAccessToken, Platform) " +
                    "VALUES (?,?,?,?); ";

            PreparedStatement storeUserStmt = con.prepareStatement(query);
            storeUserStmt.setString(1, user.getName());
            storeUserStmt.setString(2, user.getServerAccessToken());
            storeUserStmt.setString(3, user.getPushAccessToken());
            storeUserStmt.setInt(4, user.getPlatform().ordinal());

            storeUserStmt.execute();

            // Retrieve auto incremented id of the database record.
            String getIdQuery = "SELECT LAST_INSERT_ID();";

            Statement getIdStmt = con.createStatement();
            ResultSet getIdRs = getIdStmt.executeQuery(getIdQuery);
            if(getIdRs.next()){
                user.setId(getIdRs.getInt(1));
            }

            storeUserStmt.close();
            getIdStmt.close();
        } catch (SQLException e) {
            // Check if the uniqueness of the access token was harmed.
            if(e.getErrorCode() == 1062){
                // TODO Logging
                throw new TokenAlreadyExistsException("Token already in database, a new token needs to be created.");
            }

            // TODO Logging
            System.err.println("SQL error occurred, SQLState " + e.getSQLState() + ", ErrorCode " + e.getErrorCode());
            e.printStackTrace();

            // Throw back DatabaseException to the Controller
            throw new DatabaseException(e.getMessage(), e);
        }
        finally {
            returnConnection(con);
        }
    }

    /**
     * Checks if an user exists which is identified with the given access token. If there is an user which is
     * identified by this access token, the access token is valid.
     *
     * @param accessToken The access token which should be checked.
     * @return Returns true if access token is valid, false otherwise.
     * @throws DatabaseException If connection to the database has failed.
     */
    public boolean isValidUserToken(String accessToken) throws DatabaseException {
        Connection con = null;
        boolean valid = false;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT * " +
                    "FROM User " +
                    "WHERE ServerAccessToken=?;";

            PreparedStatement getUserStmt = con.prepareStatement(query);
            getUserStmt.setString(1, accessToken);

            ResultSet getUserRs = getUserStmt.executeQuery();
            if(getUserRs.next()){
                valid = true;
            }
            getUserStmt.close();
        } catch (SQLException e) {
            //TODO Logging
            e.printStackTrace();
            throw new DatabaseException("Database failure with SqlState " + e.getSQLState() +
                    " and error code " + e.getErrorCode() + "; message " + e.getMessage());
        }
        finally {
            returnConnection(con);
        }
        return valid;
    }

    /**
     * Returns all user accounts which are stored in the database.
     *
     * @return List of users.
     * @throws DatabaseException If connection to the database has failed.
     */
    public List<User> getUsers() throws DatabaseException {
        List<User> users = new ArrayList<User>();
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT * " +
                    "FROM User;";

            Statement getUsersStmt = con.createStatement();

            ResultSet getUsersRs = getUsersStmt.executeQuery(query);
            while(getUsersRs.next()){
                int id = getUsersRs.getInt("Id");
                String name = getUsersRs.getString("Name");
                String serverAccessToken = getUsersRs.getString("ServerAccessToken");
                String pushAccessToken = getUsersRs.getString("PushAccessToken");
                Platform platform = Platform.values[getUsersRs.getInt("Platform")];

                User tmp = new User(id, name, serverAccessToken, pushAccessToken, platform);
                users.add(tmp);
            }
            getUsersStmt.close();
        } catch (SQLException e) {
            // TODO Logging
            e.printStackTrace();
            throw new DatabaseException("Database failure with SqlState " + e.getSQLState() +
                    " and error code " + e.getErrorCode() + "; message " + e.getMessage());
        }
        finally {
            returnConnection(con);
        }
        return users;
    }

    /**
     * Get the data of the user with the given Id from the database.
     *
     * @param userId The id of the user.
     * @return Returns the user object or null if no entry for the given id in the database.
     * @throws DatabaseException If connection to the database has failed.
     */
    public User getUser(int userId) throws DatabaseException {
        User user = null;
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT * " +
                    "FROM User " +
                    "WHERE Id=?;";

            PreparedStatement getUserStmt = con.prepareStatement(query);
            getUserStmt.setInt(1, userId);

            ResultSet getUserRs = getUserStmt.executeQuery();
            if(getUserRs.next()){
                String name = getUserRs.getString("Name");
                String serverAccessToken = getUserRs.getString("ServerAccessToken");
                String pushAccessToken = getUserRs.getString("PushAccessToken");
                Platform platform = Platform.values[getUserRs.getInt("Platform")];

                user = new User(userId, name, serverAccessToken, pushAccessToken, platform);
            }
            getUserStmt.close();
        } catch (SQLException e) {
            // TODO Logging
            e.printStackTrace();
            throw new DatabaseException("Database failure with SqlState " + e.getSQLState() +
                    " and error code " + e.getErrorCode() + "; message " + e.getMessage());
        }
        finally {
            returnConnection(con);
        }
        return user;
    }

    /**
     * Updates the data of the user in the database.
     *
     * @param user The user object which contains the new user data.
     * @throws DatabaseException If connection to the database has failed or the update caused an Exception.
     */
    public void updateUser(User user) throws DatabaseException {
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query = "UPDATE User " +
                    "SET Name=?, PushAccessToken=?, Platform=? " +
                    "WHERE Id=?;";

            PreparedStatement updateUserStmt = con.prepareStatement(query);
            updateUserStmt.setString(1, user.getName());
            updateUserStmt.setString(2, user.getPushAccessToken());
            updateUserStmt.setInt(3, user.getPlatform().ordinal());
            updateUserStmt.setInt(4, user.getId());

            updateUserStmt.executeUpdate();
            updateUserStmt.close();
        } catch (SQLException e) {
            // TODO Logging
            e.printStackTrace();
            throw new DatabaseException("Database failure with SqlState " + e.getSQLState() +
                    " and error code " + e.getErrorCode() + "; message " + e.getMessage());
        }
        finally {
            returnConnection(con);
        }
    }



}
