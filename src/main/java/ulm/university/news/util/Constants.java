package ulm.university.news.util;

import java.time.ZoneId;

/**
 * This class provides some constant values which can be used in the whole application.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Constants {
    /** The name of the application. */
    public static final String APPLICATION_NAME = "Ulm University News";

    /** The time zone where the server is located. */
    public static final ZoneId TIME_ZONE = ZoneId.of("Europe/Berlin");

    /** The location of the Email resource bundle. */
    public static final String RESOURCE_BUNDLE_EMAIL = "i18n/Email";

    /** A pattern which describes the valid form of a user or moderator name. */
    public static final String ACCOUNT_NAME_PATTERN = "^[-_a-zA-Z0-9]{3,35}$";

    /** A pattern which describes the valid form of a password hash. */
    public static final String PASSWORD_HASH_PATTERN = "^[a-fA-F0-9]{64}$";

    /** A pattern which describes the valid form of an user access token. */
    public static final String USER_TOKEN_PATTERN = "^[a-fA-F0-9]{56}$";

    /** A pattern which describes the valid form of a moderator access token. */
    public static final String MODERATOR_TOKEN_PATTERN = "^[a-fA-F0-9]{64}$";

    /** A pattern which describes the valid form of a resource name or title. */
    public static final String NAME_PATTERN = "^[!?_-:öÖäÄüÜßa-zA-Z0-9\\p{Blank}]{3,45}$";

    /** A pattern which describes the valid form of a term string. The term is always noted in the form WS or SS plus
     *  the year yyyy. In WS, the year can also be given as yyyy/yy, e.g. 2015/16.*/
    public static final String TERM_PATTERN = "^[W,S][S][0-9]{4}[/]?[0-9]{0,2}$";

    /** The maximum length of a description field. */
    public static final int DESCRIPTION_MAX_LENGTH = 500;

    /** The maximum length of a push access token which is used to send push notifications to the client. */
    public static final int PUSH_TOKEN_MAX_LENGTH = 1024;

    /** The maximum length of a text assigned to a option. */
    public static final int OPTION_TEXT_MAX_LENGTH = 300;

    /** The maximum length of a moderators first or last name. */
    public static final int MODERATOR_NAME_MAX_LENGTH = 45;

    /** The maximum length of the motivation text. */
    public static final int MOTIVATION_TEXT_MAX_LENGTH = 300;

    /** The maximum length of the contacts text of a channel. */
    public static final int CHANNEL_CONTACTS_MAX_LENGTH = 120;

    /** The maximum length of the locations text of a channel. */
    public static final int CHANNEL_LOCATIONS_MAX_LENGTH = 120;

    /** The maximum length of the dates text of a channel. */
    public static final int CHANNEL_DATES_MAX_LENGTH = 150;

    /** The maximum length of the cost text of a channel. */
    public static final int CHANNEL_COST_MAX_LENGTH = 150;

    /** The maximum length of the website text of a channel. */
    public static final int CHANNEL_WEBSITE_MAX_LENGTH = 500;

    /** The maximum length of the participants text of a channel. */
    public static final int CHANNEL_PARTICIPANTS_MAX_LENGTH = 150;

    /** The maximum length of the channels start or end date. */
    public static final int CHANNEL_DATE_MAX_LENGTH = 45;

    /** The maximum length of a message. */
    public static final int MESSAGE_MAX_LENGTH = 500;

    /** The maximum length of an announcement title. */
    public static final int ANNOUNCEMENT_TITLE_MAX_LENGTH = 45;


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
    public static final int USER_PUSH_TOKEN_INVALID = 1004;

    // Moderator:
    public static final int MODERATOR_NOT_FOUND = 2000;
    public static final int MODERATOR_FORBIDDEN = 2001;
    public static final int MODERATOR_DATA_INCOMPLETE = 2002;
    public static final int MODERATOR_INVALID_NAME = 2003;
    public static final int MODERATOR_INVALID_FIRST_NAME = 2004;
    public static final int MODERATOR_INVALID_LAST_NAME = 2005;
    public static final int MODERATOR_INVALID_EMAIL = 2006;
    public static final int MODERATOR_INVALID_PASSWORD = 2007;
    public static final int MODERATOR_INVALID_MOTIVATION = 2008;
    public static final int MODERATOR_NAME_ALREADY_EXISTS = 2009;
    public static final int MODERATOR_DELETED = 2010;
    public static final int MODERATOR_LOCKED = 2011;
    public static final int MODERATOR_UNAUTHORIZED = 2012;

    // Channel:
    public static final int CHANNEL_NOT_FOUND = 3000;
    public static final int CHANNEL_DATA_INCOMPLETE = 3002;
    public static final int CHANNEL_INVALID_NAME = 3003;
    public static final int CHANNEL_INVALID_TERM = 3004;
    public static final int CHANNEL_INVALID_CONTACTS = 3005;
    public static final int CHANNEL_INVALID_LOCATIONS = 3006;
    public static final int CHANNEL_INVALID_DESCRIPTION = 3007;
    public static final int CHANNEL_INVALID_DATES = 3008;
    public static final int CHANNEL_INVALID_TYPE = 3009;
    public static final int CHANNEL_INVALID_LECTURER = 3010;
    public static final int CHANNEL_INVALID_ASSISTANT = 3011;
    public static final int CHANNEL_INVALID_COST = 3012;
    public static final int CHANNEL_INVALID_PARTICIPANTS = 3013;
    public static final int CHANNEL_INVALID_ORGANIZER = 3014;
    public static final int CHANNEL_INVALID_WEBSITE = 3015;
    public static final int CHANNEL_INVALID_START_DATE = 3016;
    public static final int CHANNEL_INVALID_END_DATE = 3017;
    public static final int CHANNEL_NAME_ALREADY_EXISTS = 3018;

    public static final int ANNOUNCEMENT_NOT_FOUND = 3100;
    public static final int ANNOUNCEMENT_DATA_INCOMPLETE = 3102;
    public static final int ANNOUNCEMENT_INVALID_TEXT = 3103;
    public static final int ANNOUNCEMENT_INVALID_TITLE = 3104;

    public static final int REMINDER_NOT_FOUND = 3200;
    public static final int REMINDER_DATA_INCOMPLETE = 3202;
    public static final int REMINDER_INVALID_TEXT = 3203;
    public static final int REMINDER_INVALID_TITLE = 3204;
    public static final int REMINDER_INVALID_DATES = 3205;
    public static final int REMINDER_INVALID_INTERVAL = 3206;

    // Group:
    public static final int GROUP_NOT_FOUND = 4000;
    public static final int GROUP_DATA_INCOMPLETE = 4002;
    public static final int GROUP_INVALID_NAME = 4003;
    public static final int GROUP_INVALID_PASSWORD = 4004;
    public static final int GROUP_INVALID_DESCRIPTION = 4005;
    public static final int GROUP_INVALID_TERM = 4006;
    public static final int GROUP_INVALID_GROUP_ADMIN = 4007;
    public static final int GROUP_INCORRECT_PASSWORD = 4008;
    public static final int GROUP_MISSING_PASSWORD = 4009;
    public static final int GROUP_PARTICIPANT_NOT_FOUND = 4010;
    public static final int GROUP_ADMIN_NOT_ALLOWED_TO_EXIT = 4011;

    public static final int CONVERSATION_NOT_FOUND = 4100;
    public static final int CONVERSATION_DATA_INCOMPLETE = 4102;
    public static final int CONVERSATION_INVALID_TITLE = 4103;

    public static final int CONVERSATIONMESSAGE_DATA_INCOMPLETE = 4202;
    public static final int CONVERSATIONMESSAGE_INVALID_TEXT = 4203;

    public static final int BALLOT_NOT_FOUND = 4300;
    public static final int BALLOT_DATA_INCOMPLETE = 4302;
    public static final int BALLOT_INVALID_TITLE = 4303;
    public static final int BALLOT_INVALID_DESCRIPTION = 4304;
    public static final int BALLOT_CLOSED = 4305;
    public static final int BALLOT_USER_HAS_ALREADY_VOTED = 4306;

    public static final int OPTION_NOT_FOUND = 4400;
    public static final int OPTION_DATA_INCOMPLETE = 4402;
    public static final int OPTION_INVALID_TEXT = 4403;
    public static final int OPTION_USER_HAS_ALREADY_VOTED = 4406;


    // General:
    public static final int DATABASE_FAILURE = 5000;
    public static final int TOKEN_INVALID = 5001;
    public static final int DATA_INCOMPLETE = 5002;
    public static final int EMAIL_FAILURE = 5003;
    public static final int RESOURCE_BUNDLE_NOT_FOUND = 5004;
    public static final int PARSING_FAILURE = 5005;

}
