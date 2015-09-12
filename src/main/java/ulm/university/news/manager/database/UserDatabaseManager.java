package ulm.university.news.manager.database;

import ulm.university.news.data.User;
import ulm.university.news.util.DatabaseException;
import ulm.university.news.util.TokenAlreadyExistsException;

import java.sql.*;

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
                    "INSERT INTO user (Name, ServerAccessToken, PushAccessToken, Platform) " +
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


    public boolean isValidUserToken(String accessToken){
        // TODO
        return false;
    }

}
