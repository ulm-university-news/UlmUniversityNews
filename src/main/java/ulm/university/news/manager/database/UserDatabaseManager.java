package ulm.university.news.manager.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.User;
import ulm.university.news.util.Constants;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.data.enums.Platform;
import ulm.university.news.util.exceptions.TokenAlreadyExistsException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The class UserDatabaseManager contains methods to receive data about users from the database as well as methods to
 * insert or update this data.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class UserDatabaseManager extends DatabaseManager {

    /** An instance of the Logger class which performs logging for the UserDatabaseManager. */
    private static final Logger logger = LoggerFactory.getLogger(UserDatabaseManager.class);

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
        logger.debug("Start with user:{}.", user);
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
            logger.info("Stored user with id {}.", user.getId());
        } catch (SQLException e) {
            // Check if the uniqueness of the access token was harmed.
            if(e.getErrorCode() == 1062){
                logger.warn("Uniqueness of the access token harmed. Cannot store user with id {}.", user.getId());
                throw new TokenAlreadyExistsException("Token already in database, a new token needs to be created.");
            }

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
     * Checks if an user exists which is identified with the given access token. If there is an user which is
     * identified by this access token, the access token is valid.
     *
     * @param accessToken The access token which should be checked.
     * @return Returns true if access token is valid, false otherwise.
     * @throws DatabaseException If connection to the database has failed.
     */
    public boolean isValidUserToken(String accessToken) throws DatabaseException {
        logger.debug("Start with accessToken:{}", accessToken);
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
     * Returns all user accounts which are stored in the database.
     *
     * @return List of users.
     * @throws DatabaseException If connection to the database has failed.
     */
    public List<User> getUsers() throws DatabaseException {
        logger.debug("Start.");
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
                String pushAccessToken = getUsersRs.getString("PushAccessToken");
                Platform platform = Platform.values[getUsersRs.getInt("Platform")];

                // The ServerAccessToken is never returned in a response to a GET request, it is set to null.
                User tmp = new User(id, name, null, pushAccessToken, platform);
                users.add(tmp);
            }
            logger.info("Returns list of users with {} entries.", users.size());
            getUsersStmt.close();
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
     * Get the data of the user with the given Id from the database.
     *
     * @param userId The id of the user.
     * @return Returns the user object or null if no entry for the given id in the database.
     * @throws DatabaseException If connection to the database has failed.
     */
    public User getUser(int userId) throws DatabaseException {
        logger.debug("Start with userId:{}.", userId);
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
                String pushAccessToken = getUserRs.getString("PushAccessToken");
                Platform platform = Platform.values[getUserRs.getInt("Platform")];

                // The ServerAccessToken is never returned in a response to a GET request, it is set to null.
                user = new User(userId, name, null, pushAccessToken, platform);
            }
            getUserStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End with user:{}.", user);
        return user;
    }

    /**
     * Returns the user which is identified by the given access token.
     *
     * @param accessToken The access token which has been received with the request.
     * @return The user which is identified by the access token.
     * @throws DatabaseException If connection to the database has failed.
     */
    public User getUserByToken(String accessToken) throws DatabaseException {
        logger.debug("Start with accessToken:{}.", accessToken);
        Connection con = null;
        User user = null;
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
                int userId = getUserRs.getInt("Id");
                String name = getUserRs.getString("Name");
                String pushAccessToken = getUserRs.getString("PushAccessToken");
                Platform platform = Platform.values[getUserRs.getInt("Platform")];

                // The ServerAccessToken is never returned in a response to a GET request, it is set to null.
                user = new User(userId, name, null, pushAccessToken, platform);
            }
            getUserStmt.close();
        } catch (SQLException e) {
            logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Throw back DatabaseException to the Controller.
            throw new DatabaseException("Database failure.");
        }
        finally {
            returnConnection(con);
        }
        logger.debug("End with user:{}.", user);
        return user;
    }

    /**
     * Updates the data of the user in the database.
     *
     * @param user The user object which contains the new user data.
     * @throws DatabaseException If connection to the database has failed or the update caused an Exception.
     */
    public void updateUser(User user) throws DatabaseException {
        logger.debug("Start with user:{}.", user);
        Connection con = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "UPDATE User " +
                    "SET Name=?, PushAccessToken=?, Platform=? " +
                    "WHERE Id=?;";

            PreparedStatement updateUserStmt = con.prepareStatement(query);
            updateUserStmt.setString(1, user.getName());
            updateUserStmt.setString(2, user.getPushAccessToken());
            updateUserStmt.setInt(3, user.getPlatform().ordinal());
            updateUserStmt.setInt(4, user.getId());

            updateUserStmt.executeUpdate();
            updateUserStmt.close();
            logger.info("Updated user with id {}.", user.getId());
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
