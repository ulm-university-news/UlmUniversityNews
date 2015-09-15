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

    // User:
    public static final int USER_NOT_FOUND = 1000;
    public static final int USER_FORBIDDEN = 1002;
    public static final int USER_INCOMPLETE_DATA_RECORD = 1003;

    // Moderator:
    public static final int MODERATOR_NOT_FOUND = 2000;
    public static final int MODERATOR_FORBIDDEN = 2002;

    // Group:
    public static final int GROUP_NOT_FOUND = 4000;
    public static final int CONVERSATION_NOT_FOUND = 4100;

    // General:
    public static final int DATABASE_FAILURE = 5000;
    public static final int TOKEN_INVALID = 5001;

}
