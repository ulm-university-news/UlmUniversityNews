package ulm.university.news.manager.database;

import ulm.university.news.data.Moderator;
import ulm.university.news.util.exceptions.DatabaseException;
import ulm.university.news.util.exceptions.TokenAlreadyExistsException;

import java.sql.*;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class ModeratorDatabaseManager extends DatabaseManager {

    /**
     * Creates an instance of the ModeratorDatabaseManager class.
     */
    public ModeratorDatabaseManager() {

    }

    /**
     * Checks if a moderator account exists which is identified with the given access token. If there is a moderator
     * which is identified by this access token, the access token is valid.
     *
     * @param accessToken The access token which should be checked.
     * @return Returns true if access token is valid, false otherwise.
     * @throws DatabaseException If connection to the database has failed.
     */
    public boolean isValidModeratorToken(String accessToken) throws DatabaseException {
        Connection con = null;
        boolean valid = false;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT * " +
                            "FROM Moderator " +
                            "WHERE ServerAccessToken=?;";

            PreparedStatement getModeratorStmt = con.prepareStatement(query);
            getModeratorStmt.setString(1, accessToken);

            ResultSet getModeratorRs = getModeratorStmt.executeQuery();
            if (getModeratorRs.next()) {
                valid = true;
            }
            getModeratorStmt.close();
        } catch (SQLException e) {
            //TODO Logging
            e.printStackTrace();
            throw new DatabaseException("Database failure with SqlState " + e.getSQLState() +
                    " and error code " + e.getErrorCode() + "; message " + e.getMessage());
        } finally {
            returnConnection(con);
        }
        return valid;
    }

    /**
     * Stores the moderator data into the database.
     *
     * @param moderator The moderator object which contains the moderator data.
     * @throws TokenAlreadyExistsException If the access token of this moderator is already stored in the database for
     * another moderator. The access token needs to be unique in the whole system so the storing of the data is aborted.
     * @throws DatabaseException If the data could not be stored in the database due to database failure.
     */
    public void storeModerator(Moderator moderator) throws TokenAlreadyExistsException, DatabaseException {
        Connection con = null;
        try {
            con = getDatabaseConnection();

            String query =
                    "INSERT INTO Moderator (Name, LastName, FirstName, Email, Password, Motivation, " +
                            "ServerAccessToken, Locked, Admin, Deleted) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?); ";

            PreparedStatement storeModeratorStmt = con.prepareStatement(query);
            storeModeratorStmt.setString(1, moderator.getName());
            storeModeratorStmt.setString(2, moderator.getLastName());
            storeModeratorStmt.setString(3, moderator.getFirstName());
            storeModeratorStmt.setString(4, moderator.getEmail());
            storeModeratorStmt.setString(5, moderator.getPassword());
            storeModeratorStmt.setString(6, moderator.getMotivation());
            storeModeratorStmt.setString(7, moderator.getServerAccessToken());
            storeModeratorStmt.setBoolean(8, moderator.isLocked());
            storeModeratorStmt.setBoolean(9, moderator.isAdmin());
            storeModeratorStmt.setBoolean(10, moderator.isDeleted());

            storeModeratorStmt.execute();

            // Retrieve auto incremented id of the database record.
            String getIdQuery = "SELECT LAST_INSERT_ID();";

            Statement getIdStmt = con.createStatement();
            ResultSet getIdRs = getIdStmt.executeQuery(getIdQuery);
            if (getIdRs.next()) {
                moderator.setId(getIdRs.getInt(1));
            }

            storeModeratorStmt.close();
            getIdStmt.close();
        } catch (SQLException e) {
            // Check if the uniqueness of the access token was harmed.
            if (e.getErrorCode() == 1062) {
                // TODO Logging
                throw new TokenAlreadyExistsException("Token already in database, a new token needs to be created.");
            }

            // TODO Logging
            System.err.println("SQL error occurred, SQLState " + e.getSQLState() + ", ErrorCode " + e.getErrorCode());
            e.printStackTrace();

            // Throw back DatabaseException to the Controller
            throw new DatabaseException(e.getMessage(), e);
        } finally {
            returnConnection(con);
        }
    }

    /**
     * Returns the moderator who is identified by the given access token.
     *
     * @param accessToken The access token which has been received with the request.
     * @return The moderator who is identified by the access token.
     * @throws DatabaseException If connection to the database has failed.
     */
    public Moderator getModeratorByToken(String accessToken) throws DatabaseException {
        Connection con = null;
        Moderator moderator = null;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT * " +
                            "FROM Moderator " +
                            "WHERE ServerAccessToken=?;";

            PreparedStatement getModeratorStmt = con.prepareStatement(query);
            getModeratorStmt.setString(1, accessToken);

            ResultSet getModeratorRs = getModeratorStmt.executeQuery();
            if (getModeratorRs.next()) {
                int id = getModeratorRs.getInt("Id");
                String name = getModeratorRs.getString("Name");
                String firstName = getModeratorRs.getString("FirstName");
                String lastName = getModeratorRs.getString("LastName");
                String email = getModeratorRs.getString("Email");
                String password = getModeratorRs.getString("Password");
                String motivation = getModeratorRs.getString("Motivation");
                String serverAccessToken = getModeratorRs.getString("ServerAccessToken");
                boolean locked = getModeratorRs.getBoolean("Locked");
                boolean admin = getModeratorRs.getBoolean("Admin");
                boolean deleted = getModeratorRs.getBoolean("Deleted");

                moderator = new Moderator(id, name, firstName, lastName, email, serverAccessToken, password,
                        motivation, locked, admin, deleted, false);
            }
            getModeratorStmt.close();
        } catch (SQLException e) {
            // TODO Logging
            e.printStackTrace();
            throw new DatabaseException("Database failure with SqlState " + e.getSQLState() +
                    " and error code " + e.getErrorCode() + "; message " + e.getMessage());
        } finally {
            returnConnection(con);
        }
        return moderator;
    }

    /**
     * Checks whether the Moderator identified by the given moderator id is responsible for the Channel identified by
     * the given channel id or not.
     *
     * @param moderatorId The id of the Moderator.
     * @param channelId The id of the Channel.
     * @return true if the Moderator is responsible for the Channel.
     */
    public boolean isResponsibleForChannel(int moderatorId, int channelId) throws DatabaseException {
        Connection con = null;
        boolean responsible = false;
        try {
            con = getDatabaseConnection();
            String query =
                    "SELECT * " +
                            "FROM ModeratorChannel " +
                            "WHERE Moderator_Id=? AND Channel_Id=?;";

            PreparedStatement getResponsibleStmt = con.prepareStatement(query);
            getResponsibleStmt.setInt(1, moderatorId);
            getResponsibleStmt.setInt(2, channelId);

            ResultSet getResponsibleRs = getResponsibleStmt.executeQuery();
            if (getResponsibleRs.next()) {
                responsible = true;
            }
            getResponsibleStmt.close();
        } catch (SQLException e) {
            //TODO Logging
            e.printStackTrace();
            throw new DatabaseException("Database failure with SqlState " + e.getSQLState() +
                    " and error code " + e.getErrorCode() + "; message " + e.getMessage());
        } finally {
            returnConnection(con);
        }
        return responsible;
    }

}
