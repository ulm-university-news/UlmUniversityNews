package ulm.university.news.manager.database;

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
     * Retrieves a Connection from the JDBC Connection Pool and returns it.
     * @return A connection to the database.
     */
    protected Connection getDatabaseConnection(){
        Connection con = null;
        try {
            String resourceName = "jdbc/UniversityNewsDB";  //Name of the resource which should be accessed.

            Context initialContext = new InitialContext();
            Context environmentContext = (Context) initialContext.lookup("java:comp/env");
            DataSource dataSource = (DataSource) environmentContext.lookup(resourceName);

            //Request a connetion from the pool.
            con = dataSource.getConnection();
        } catch (NamingException e) {
            //TODO
            e.printStackTrace();
        } catch (SQLException e) {
            //TODO
            e.printStackTrace();
        }
        return con;
    }

    /**
     * Returns an open connection to the database back to the JDBC Connection Pool.
     * @param con The connection which should be returned.
     */
    protected void returnConnection(Connection con){
        //Release connection back to the pool.
        if(con != null){
            try {
                con.close();
            } catch (SQLException e) {
                //TODO
                e.printStackTrace();
            }
        }
        con = null; //Prevent any future access.
    }

}
