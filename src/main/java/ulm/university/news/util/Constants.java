package ulm.university.news.util;

import java.time.ZoneId;

/**
 * This class provides some constant values which can be used in the whole application.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Constants {
    /** The time zone where the server is located. */
    public static final ZoneId TIME_ZONE = ZoneId.of("Europe/Berlin");

    /** A pattern which describes the valid form of a user or moderator name. */
    public static final String NAME_PATTERN = "^[a-zA-Z0-9]{3,35}$";

    /** A pattern which describes the valid form of a moderator password. */
    public static final String PASSWORD_PATTERN = "^[a-zA-Z0-9]{10,35}$";

    /** A pattern which describes the valid form of an user access token. */
    public static final String USER_TOKEN_PATTERN = "^[a-fA-F0-9]{56}$";

    /** A pattern which describes the valid form of a moderator access token. */
    public static final String MODERATOR_TOKEN_PATTERN = "^[a-fA-F0-9]{64}$";

    /** A pattern which describes the valid form of a group name. */
    public static final String GROUP_NAME_PATTERN = "^[a-zA-Z0-9\\p{Blank}]{3,45}$";

    // TODO Don't we except a hash here? That's a topic for the next meeting.
    /** A pattern which describes the valid form of a group password. */
    public static final String GROUP_PASSWORD_PATTERN = "^[a-zA-Z0-9\\p{Punct}]{3,64}$";

    /** A pattern which describes the valid form of a term string. The term is always noted in the form WS or SS plus
     *  the year yyyy. In WS, the year can also be given as yyyyy/yy, e.g. 2015/16.*/
    public static final String TERM_PATTERN = "^[W,S][S][0-9]{4}[/]?[0-9]{0,2}$";

    /** A pattern which describes a valid resource description. A resource description can contain any ASCII characters,
     *  but is limited to 500 characters. */
    public static final String DESCRIPTION_PATTERN = "^[.\\p{Print}\\p{Blank}]{1,500}$";



    // Logging:
    public static final String LOG_SERVER_EXCEPTION = "httpStatusCode:{}, errorCode:{}, message:{}";
    public static final String LOG_SQL_EXCEPTION = "SQLException occurred with SQLState:{}, errorCode:{} and " +
            "message:{}.";

    // Error Codes:
    // User:
    public static final int USER_NOT_FOUND = 1000;
    public static final int USER_FORBIDDEN = 1001;
    public static final int USER_DATA_INCOMPLETE = 1002;
    public static final int USER_NAME_INVALID = 1003;

    // Moderator:
    public static final int MODERATOR_NOT_FOUND = 2000;
    public static final int MODERATOR_FORBIDDEN = 2001;
    public static final int MODERATOR_DATA_INCOMPLETE = 2002;
    public static final int MODERATOR_INVALID_NAME = 2003;
    public static final int MODERATOR_INVALID_EMAIL = 2004;
    public static final int MODERATOR_INVALID_PASSWORD = 2005;
    public static final int MODERATOR_NAME_ALREADY_EXISTS = 2006;
    public static final int MODERATOR_DELETED = 2007;
    public static final int MODERATOR_LOCKED = 2008;
    public static final int MODERATOR_INCORRECT_PASSWORD = 2009;

    // Group:
    public static final int GROUP_NOT_FOUND = 4000;
    public static final int GROUP_DATA_INCOMPLETE = 4002;
    public static final int GROUP_INVALID_NAME = 4002;
    public static final int GROUP_INVALID_PASSWORD = 4003;
    public static final int GROUP_INVALID_DESCRIPTION = 4004;
    public static final int GROUP_INVALID_TERM = 4005;
    public static final int GROUP_INVALID_GROUP_ADMIN = 4006;

    public static final int CONVERSATION_NOT_FOUND = 4100;

    // General:
    public static final int DATABASE_FAILURE = 5000;
    public static final int TOKEN_INVALID = 5001;
    public static final int DATA_INCOMPLETE = 5002;
    public static final int EMAIL_FAILURE = 5003;

}
