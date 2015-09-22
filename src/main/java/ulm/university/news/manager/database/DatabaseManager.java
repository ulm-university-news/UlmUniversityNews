package ulm.university.news.manager.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.util.Constants;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * The DatabaseManager class provides basic functionality to retrieve an open connection to the database from
 * the JDBC Connection Pool and to return it to the pool after usage.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class DatabaseManager {

    /**
     * An instance of the Logger class which performs logging for the DatabaseManager.
     */
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    /**
     * Retrieves a Connection from the JDBC Connection Pool and returns it.
     *
     * @return A connection to the database.
     */
    protected Connection getDatabaseConnection() throws SQLException {
        Connection con = null;
        try {
            String resourceName = "jdbc/UniversityNewsDB";  //Name of the resource which should be accessed.

            Context initialContext = new InitialContext();
            Context environmentContext = (Context) initialContext.lookup("java:comp/env");
            DataSource dataSource = (DataSource) environmentContext.lookup(resourceName);

            //Request a connection from the pool.
            con = dataSource.getConnection();
        } catch (NamingException e) {
            // If exception occurs in this critical area, StackTrace will be logged.
            logger.error("NamingException occurred during JNDI lookup. No DB connection could be enabled.", e);
            throw new SQLException("Naming Exception occurred during the JNDI lookup.", e);
        } catch (SQLException e) {
            // If exception occurs in this critical area, StackTrace will be logged.
            logger.error("SQLException occurred", e);
            throw e;
        }
        if (con == null) {
            logger.error("No database connection could be established. Connection is null.");
            throw new SQLException("Failed to establish a connection to the database.");
        }
        return con;
    }

    /**
     * Returns an open connection to the database back to the JDBC Connection Pool.
     *
     * @param con The connection which should be returned.
     */
    protected void returnConnection(Connection con) {
        //Release connection back to the pool.
        if (con != null) {
            try {
                con.close();
            } catch (SQLException e) {
                logger.error(Constants.LOG_SQL_EXCEPTION, e.getSQLState(), e.getErrorCode(), e.getMessage());
                e.printStackTrace();
            }
        }
        con = null; //Prevent any future access.
    }

}
