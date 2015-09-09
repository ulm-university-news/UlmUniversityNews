package ulm.university.news.manager.database;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class DatabaseManager {

    /**
     * Retrieves a Connection from the JDBC Connection Pool and returns it.
     * @return a connection to the database
     */
    protected Connection getDatabaseConnection(){
        Connection con = null;
        try {
            String resourceName = "jdbc/UniversityNewsDB";  //name of the resource which should be accessed

            Context initialContext = new InitialContext();
            Context environmentContext = (Context) initialContext.lookup("java:comp/env");
            DataSource dataSource = (DataSource) environmentContext.lookup(resourceName);

            //request a connetion from the pool
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
     * @param con the connection which should be returned
     */
    protected void returnConnection(Connection con){
        //release connection back to the pool
        if(con != null){
            try {
                con.close();
            } catch (SQLException e) {
                //TODO
                e.printStackTrace();
            }
        }
        con = null; //prevent any future access
    }

}
