package ulm.university.news.manager.push;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.PushMessage;
import ulm.university.news.data.User;
import ulm.university.news.data.enums.PushType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class PushManager {

    /** The logger instance for PushManager. */
    private static final Logger logger = LoggerFactory.getLogger(PushManager.class);

    /** The access key for the GCM server retrieved from properties file. */
    private String GCM_API_KEY = null;

    /** A hash map which stores all cached push messages. */
    private ConcurrentHashMap<PushMessage, Integer> cachedPushMessages;

    /** A reference for the PushManager Singleton class. */
    private static PushManager _instance = new PushManager();

    /** Schedules active PushMessageTasks. */
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** The time in seconds which defines how long the PushManager waits for other push messages. */
    private static final int CACHING_DELAY = 5;

    /**
     * Creates an Instance of the PushManager class.
     */
    public PushManager() {
        cachedPushMessages = new ConcurrentHashMap<>();
        Properties pushCredentials = retrievePushCredentials();
        if (pushCredentials != null) {
            GCM_API_KEY = pushCredentials.getProperty("gcmApiKey");
            // TODO Read Windows stuff.
        } else {
            logger.error("PushManager was unable to read the push credentials of the properties object.");
        }
        if (GCM_API_KEY == null) {
            logger.error("PushManager was unable to read the GCM API key of the properties object.");
        }
    }

    /**
     * Get an instance of the PushManager class.
     *
     * @return Instance of PushManager.
     */
    public static PushManager getInstance() {
        if (_instance == null) {
            _instance = new PushManager();
        }
        return _instance;
    }

    /**
     * Gets the number of cached push messages which are equal to the given one.
     *
     * @param pushMessageMap The push message which is stored in the hash map.
     * @return The number of cached push messages.
     */
    public int getNumberOfCachedPushMessages(PushMessage pushMessageMap) {
        return cachedPushMessages.get(pushMessageMap);
    }

    /**
     * Reads the properties file which contains credentials for communication with different push servers. Returns
     * the properties in a Properties object.
     *
     * @return Returns Properties object, or null if reading of the properties file has failed.
     */
    private Properties retrievePushCredentials() {
        Properties pushCredentials = new Properties();
        InputStream input = getClass().getClassLoader().getResourceAsStream("PushManager.properties");
        if (input == null) {
            logger.error("PushManager could not localize the file PushManager.properties.");
            return null;
        }
        try {
            pushCredentials.load(input);
        } catch (IOException e) {
            logger.error("Failed to load the properties of the PushManager credentials.");
            return null;
        }
        return pushCredentials;
    }

    /**
     * Sends push messages to given users. The push message contains one, two or three different ids. The semantics
     * of the given ids is identified by the PushType.
     *
     * @param pushType The type of the push message.
     * @param users The users who should be notified.
     * @param id1 The first id of the push message. Not null.
     * @param id2 The second id of the push message. Nullable.
     * @param id3 The third id of the push message. Nullable.
     */
    public void notifyUsers(PushType pushType, List<User> users, Integer id1, Integer id2, Integer id3) {
        // Check parameters and caching.
        if (!checkPushMessage(pushType, users, id1, id2, id3)) {
            return;
        }

        // Split users by platform.
        List<String> userPushTokensAndroid = new ArrayList<String>();
        List<String> userPushTokensWindows = new ArrayList<String>();
        List<String> userPushTokensIOS = new ArrayList<String>();
        assignPushTokens(users, userPushTokensAndroid, userPushTokensWindows, userPushTokensIOS);

        // Give push tokens to corresponding notification method.
        String jsonPushMessage = createPushMessage(pushType, id1, id2, id3);
        notifyAndroid(userPushTokensAndroid, jsonPushMessage);
        notifyWindows(userPushTokensAndroid, jsonPushMessage);
        notifyIOS(userPushTokensAndroid, jsonPushMessage);

    }

    /**
     * Sends cached push messages to given users. The push message contains one, two or three different ids. The
     * semantics of the given ids is identified by the PushType.
     *
     * @param pushType The type of the push message.
     * @param users The users who should be notified.
     * @param id1 The first id of the push message. Not null.
     * @param id2 The second id of the push message. Nullable.
     * @param id3 The third id of the push message. Nullable.
     * @param pushMessageMap The push message which is stored in the hash map.
     */
    public void notifyUsersAsTask(PushType pushType, List<User> users, Integer id1, Integer id2, Integer id3,
                                  PushMessage pushMessageMap) {
        // Remove cached push message from hash map.
        cachedPushMessages.remove(pushMessageMap);

        // Split users by platform.
        List<String> userPushTokensAndroid = new ArrayList<String>();
        List<String> userPushTokensWindows = new ArrayList<String>();
        List<String> userPushTokensIOS = new ArrayList<String>();
        assignPushTokens(users, userPushTokensAndroid, userPushTokensWindows, userPushTokensIOS);

        // Give push tokens to corresponding notification method.
        String jsonPushMessage = createPushMessage(pushType, id1, id2, id3);
        notifyAndroid(userPushTokensAndroid, jsonPushMessage);
        notifyWindows(userPushTokensAndroid, jsonPushMessage);
        notifyIOS(userPushTokensAndroid, jsonPushMessage);
    }

    /**
     * Checks if given user list is empty. Starts caching of push message if needed.
     *
     * @param pushType The type of the push message.
     * @param users The users who should be notified.
     * @param id1 The first id of the push message. Not null.
     * @param id2 The second id of the push message. Nullable.
     * @param id3 The third id of the push message. Nullable.
     * @return false if there are no users to notify or caching is used.
     */
    private boolean checkPushMessage(PushType pushType, List<User> users, Integer id1, Integer id2, Integer id3) {
        // If there are no subscribers or participants, do nothing.
        if (users == null || users.size() == 0) {
            logger.info("List of users is empty. No user was notified.");
            return false;
        }

        if (pushType == PushType.BALLOT_CHANGED || pushType == PushType.BALLOT_OPTION_NEW || pushType == PushType
                .BALLOT_OPTION_VOTE || pushType == PushType.CONVERSATION_CHANGED) {
            // Create push message for caching.
            PushMessage pushMessageMap = createPushMessageMap(pushType, users, id1, id2);
            if (cachedPushMessages.containsKey(pushMessageMap)) {
                // Same push message already cached. Increment counter.
                cachedPushMessages.put(pushMessageMap, cachedPushMessages.get(pushMessageMap) + 1);
            } else {
                // No such push message cached so far. Cache push message.
                PushMessage pushMessage = new PushMessage(pushType, users, id1, id2, id3);
                cachedPushMessages.put(pushMessageMap, 1);
                // Wait for same push messages.
                scheduler.schedule(new PushMessageTask(pushMessage, pushMessageMap), CACHING_DELAY, TimeUnit.SECONDS);
            }
            logger.debug("Push message has been cached. Waiting for same push messages.");
            return false;
        }
        return true;
    }

    /**
     * Creates a push message object which doesn't contain the highest set id. This object is used to find equal push
     * messages in the hash map.
     *
     * @param pushType The type of the push message.
     * @param users The users who should be notified.
     * @param id1 The first id of the push message.
     * @param id2 The second id of the push message.
     * @return The message object for the hash map.
     */
    private PushMessage createPushMessageMap(PushType pushType, List<User> users, Integer id1, Integer id2) {
        PushMessage pushMessageMap = new PushMessage(pushType, users, id1, null, null);
        if (pushType == PushType.BALLOT_OPTION_NEW || pushType == PushType.BALLOT_OPTION_VOTE) {
            pushMessageMap.setId2(id2);
        }
        return pushMessageMap;
    }

    /**
     * Split users by platform and assign their push tokens to corresponding token list.
     *
     * @param users The users who should be notified.
     * @param userPushTokensAndroid The list of Android push tokens.
     * @param userPushTokensWindows The list of Windows push tokens.
     * @param userPushTokensIOS The list of iOS push tokens.
     */
    private void assignPushTokens(List<User> users, List<String> userPushTokensAndroid, List<String>
            userPushTokensWindows, List<String> userPushTokensIOS) {
        // Split users by platform.
        for (User subscriber : users) {
            switch (subscriber.getPlatform()) {
                case ANDROID:
                    userPushTokensAndroid.add(subscriber.getPushAccessToken());
                    break;
                case WINDOWS:
                    userPushTokensWindows.add(subscriber.getPushAccessToken());
                    break;
                case IOS:
                    userPushTokensIOS.add(subscriber.getPushAccessToken());
                    break;
            }
        }
    }

    /**
     * Creates a JSON String from given PushType and given id(s).
     *
     * @param pushType The type of the push message.
     * @param id1 The first id of the push message. Not null.
     * @param id2 The second id of the push message. Nullable.
     * @param id3 The third id of the push message. Nullable.
     * @return The created JSON String.
     */
    private String createPushMessage(PushType pushType, Integer id1, Integer id2, Integer id3) {
        JSONObject jData = new JSONObject();
        jData.put("pushType", pushType);
        jData.put("id1", id1);
        jData.put("id2", id2);
        jData.put("id3", id3);
        logger.debug("JSON push message created: {}", jData.toString());
        return jData.toString();
    }

    /**
     * Sends the given JSON push message to the Android clients. Creates a GCM JSON message for each user and sends
     * them to the Google Cloud Messaging (GCM) server. The GCM server will forward the messages to the Android app.
     *
     * @param pushTokens A list of Android push tokens.
     * @param jsonPushMessage The push message as a JSON String.
     */
    private void notifyAndroid(List<String> pushTokens, String jsonPushMessage) {
        // TODO
        String[] args = new String[2];
        args[0] = jsonPushMessage;
        args[1] = pushTokens.get(0);
        System.out.println("length: " + args[1].length());
        // Prepare JSON containing the GCM message content. What to send and where to send.
        JSONObject jGcmData = new JSONObject();
        JSONObject jData = new JSONObject();
        jData.put("message", args[0].trim());
        // Where to send GCM message.
        if (args.length > 1 && args[1] != null) {
            jGcmData.put("to", args[1].trim());
        } else {
            jGcmData.put("to", "/topics/global");
        }
        // What to send in GCM message.
        jGcmData.put("data", jData);

        try {
            // Create connection to send GCM Message request.
            URL url = new URL("https://android.googleapis.com/gcm/send");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "key=" + GCM_API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            // Send GCM message content.
            OutputStream outputStream = conn.getOutputStream();
            outputStream.write(jGcmData.toString().getBytes());

            // Read GCM response.
            InputStream inputStream = conn.getInputStream();

            String resp = IOUtils.toString(inputStream);
            System.out.println(resp);
            System.out.println("Check your device/emulator for notification or logcat for " +
                    "confirmation of the receipt of the GCM message.");
        } catch (IOException e) {
            System.out.println("Unable to send GCM message.");
            System.out.println("Please ensure that GCM_API_KEY has been replaced by the server " +
                    "API key, and that the device's registration token is correct (if specified).");
            e.printStackTrace();
        }
    }

    /**
     * Sends the given JSON push message to the Android clients. TODO
     *
     * @param pushTokens A list of Windows push tokens.
     * @param jsonPushMessage The push message as a JSON String.
     */
    private void notifyWindows(List<String> pushTokens, String jsonPushMessage) {
        // TODO
    }

    /**
     * Sends the given JSON push message to the iOS clients.
     *
     * @param pushTokens A list of iOS push tokens.
     * @param jsonPushMessage The push message as a JSON String.
     */
    private void notifyIOS(List<String> pushTokens, String jsonPushMessage) {
        /*
        NOTE: The iOS methods won't be implemented in this project.
         */
    }
}
