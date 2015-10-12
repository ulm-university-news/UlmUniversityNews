package ulm.university.news.manager.push;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.PushMessage;
import ulm.university.news.data.User;
import ulm.university.news.data.enums.PushType;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The PushManager class is used to send push messages to app clients. So far, Android and Windows clients are
 * supported. Caching methods are used to avoid sending unnecessary push messages.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class PushManager {

    /** The logger instance for PushManager. */
    private static final Logger logger = LoggerFactory.getLogger(PushManager.class);

    /** The access key for the GCM server retrieved from properties file. */
    private String GCM_API_KEY = null;

    /** The secure package identifier which identifies a specific Windows Phone app. The identifier is required to
     * tell the WNS to which application a push notification should be sent. */
    private String SID = null;

    /** The client secret which is specific to a certain Windows Phone app and is required for the authentication at
     * the WNS. */
    private String wpClientSecret = null;

    /** A hash map which stores all cached push messages. */
    private ConcurrentHashMap<PushMessage, Integer> cachedPushMessages;

    /** The access token which authenticates the server at the WNS. */
    private static String wnsAccessToken = null;

    private static ReentrantReadWriteLock wnsAccessTokenLock = new ReentrantReadWriteLock();

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
            SID = pushCredentials.getProperty("sid");
            wpClientSecret = pushCredentials.getProperty("wpSecret");
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
        List<String> userPushTokensAndroid = new ArrayList<>();
        List<String> userPushTokensWindows = new ArrayList<>();
        List<String> userPushTokensIOS = new ArrayList<>();
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
        List<String> userPushTokensAndroid = new ArrayList<>();
        List<String> userPushTokensWindows = new ArrayList<>();
        List<String> userPushTokensIOS = new ArrayList<>();
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
                cachedPushMessages.put(pushMessageMap, 1);
                // Wait for same push messages.
                PushMessage pushMessage = new PushMessage(pushType, users, id1, id2, id3);
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
        logger.info("JSON push message created: {}", jData.toString());
        return jData.toString();
    }

    /**
     * Creates a GCM JSON message and ensures that each push access token will be included. Sends the given JSON push
     * message to the Android clients.
     *
     * @param pushTokens A list of Android push tokens.
     * @param jsonPushMessage The push message as a JSON String.
     */
    private void notifyAndroid(List<String> pushTokens, String jsonPushMessage) {
        // Check if there is at least one recipients. Do nothing if there is non.
        if (pushTokens.isEmpty()) {
            logger.info("No Android push tokens given. No Android user will be notified.");
            return;
        }

        // Prepare JSON containing the GCM message content.
        JSONObject jGcmData = new JSONObject();
        JSONObject jData = new JSONObject();
        // Define what to send.
        jData.put("message", jsonPushMessage.trim());
        // Set GCM message content.
        jGcmData.put("data", jData);

        // Send message to a maximum of 1000 recipients per GCM message.
        while (pushTokens.size() > 1000) {
            // Send push messages while there are still unnotified recipients.
            logger.debug("Recipient number > 1000.");
            jGcmData.put("registration_ids", pushTokens.subList(0, 1000));
            // Remove notified recipients to get remaining push tokens.
            pushTokens = pushTokens.subList(1000, pushTokens.size());
            // Send message to GCM server.
            sendAndroid(jGcmData);
        }
        logger.debug("Recipient number < 1000");
        jGcmData.put("registration_ids", pushTokens);
        // Send message to GCM server.
        sendAndroid(jGcmData);
    }

    /**
     * Sends the given GCM JSON message data to the Android clients defined in the data object. The message will be sent
     * to a Google Cloud Messaging (GCM) server. The GCM server will forward the messages to the Android app.
     *
     * @param jGcmData The GCM JSON message which contains the message data and the recipients.
     */
    private void sendAndroid(JSONObject jGcmData) {
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
            logger.debug("GCM response: {}", resp);

            // Extract number of successfully sent messages.
            resp = resp.split("\"success\":")[1].split(",\"failure\"")[0];
            logger.info("Push message send to {} Android client(s).", resp);
        } catch (IOException e) {
            logger.error("Unable to send GCM message.");
            e.printStackTrace();
        }
    }

    /**
     * Sends the given JSON push message to the Windows clients which are identified by the given push tokens.
     *
     * @param pushTokens A list of Windows push tokens.
     * @param jsonPushMessage The push message as a JSON String.
     */
    private void notifyWindows(List<String> pushTokens, String jsonPushMessage) {
        // Check if there is at least one recipients. Do nothing if there is non.
        if(pushTokens.isEmpty()){
            logger.info("No Windows push tokens given. No Windows Phone user will be notified.");
            return;
        }
        logger.info("Got a list of {} windows push tokens.", pushTokens.size());

        // Read the access token.
        String accessToken = null;
        wnsAccessTokenLock.readLock().lock();
        try{
            accessToken = wnsAccessToken;
        }finally {
            wnsAccessTokenLock.readLock().unlock();
        }

        if(accessToken == null){
            // Request a new access token and store it in the variable.
            setWnsAccessToken();
        }

        // If sending fails, retry just once according to the best practices.
        int maxRetries = 2;
        int amountOfSuccessfulPushs = 0;
        for (String pushToken : pushTokens) {
            boolean successful = false;
            int attempts = 0;
            while(!successful && attempts < maxRetries){
                wnsAccessTokenLock.readLock().lock();
                try{
                    accessToken = wnsAccessToken;
                }finally {
                    wnsAccessTokenLock.readLock().unlock();
                }
                // Try to send the push notification to the device identified by the given push token.
                successful = sendWindowsRawNotification(pushToken, jsonPushMessage, accessToken);
                attempts++;
            }

            if(successful){
                amountOfSuccessfulPushs++;
                logger.debug("Successfully sent the push notification to the client identified by the push token: {}" +
                        ".", pushToken);
            }else{
                logger.debug("Sending to token:{} has failed.", pushToken);
            }
        }
        logger.info("Sent push messages to {} windows clients.", amountOfSuccessfulPushs);
    }

    /**
     * Sends a raw push notification to the device which is identified by the determined push token.
     *
     * @param pushToken The push token which identifies the client device.
     * @param content The content of the raw notification.
     * @param accessToken The access token which identifies the server at the WNS.
     * @return Returns true if the notification has been sent successfully, false otherwise.
     */
    private boolean sendWindowsRawNotification(String pushToken, String content, String accessToken){
        boolean successful = true;
        try {
            URL urlFromToken = new URL(pushToken);
            HttpURLConnection conn = (HttpURLConnection) urlFromToken.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("X-WNS-Type", "wns/raw");
            conn.setRequestProperty("X-WNS-Cache-Policy", "cache");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Authorization", String.format("Bearer %s", accessToken));
            conn.setDoOutput(true);

            // Write the HTTP request content.
            OutputStream out = conn.getOutputStream();
            out.write(content.getBytes());
            out.flush();
            out.close();

            // Read the response.
            int responseCode = conn.getResponseCode();
            switch(responseCode){
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    logger.warn("The raw notification request to the WNS has failed. The access token is invalid.");
                    // The access token is invalid. Request a new one.
                    setWnsAccessToken();
                    successful = false;
                    break;
                case HttpURLConnection.HTTP_GONE:
                    logger.warn("The push token is invalid. Could not send a notification. Request won't be retried.");
                    break;
                case HttpURLConnection.HTTP_NOT_FOUND:
                    logger.warn("The push token is invalid. Could not send a notification. Request won't be retried.");
                    break;
                case HttpURLConnection.HTTP_NOT_ACCEPTABLE:
                    logger.warn("The WNS throttles this channel due to to many push notifications in a short amount " +
                            "of time. Request won't be retried.");
                    break;
                default:
                    logger.error("Could not send push notification: Response code is: {}, debug trace is: {}, error " +
                            "description is {}, msg id: {}, wns status: {}.", responseCode, conn.getHeaderField
                            ("X-WNS-Debug-Trace"), conn.getHeaderField("X-WNS-Error-Description"), conn
                            .getHeaderField("X-WNS-Msg-ID"), conn.getHeaderField("X-WNS-Status"));
                    successful = false;
                    break;
            }

        } catch (MalformedURLException e) {
            logger.error("The push token could not be parsed to a valid url.");
        } catch (IOException e) {
            logger.error("Error appeared during the sending process of a push notification to the WNS.");
            successful = false;
        }

        return successful;
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

    /**
     * A helper method which requests a new WNS access token and stores it to the static variable wnsAccessToken. The
     * method is implemented thread-safe. Only one thread can request a new access token at a time. If multiple
     * threads try to request an access token, only one of them will get the lock and the others will immediately
     * leave the method.
     */
    private void setWnsAccessToken(){
        // Request a new access token. Lock so no other thread can access the request token at the same time.
        if(wnsAccessTokenLock.writeLock().tryLock()){
            try{
                // Request the new token and store it to the variable.
                logger.info("Requested a new access token.");
                wnsAccessToken = getWindowsNotificationServiceAccessToken(wpClientSecret, SID);
            } finally {
                wnsAccessTokenLock.writeLock().unlock();
            }
        }
    }

    /**
     * Performs the authorization of the server with the WNS. Requests a valid access token from the WNS which can be
     * used for push notification requests. Returns the access token if the authorization is successful, otherwise
     * the method returns null.
     *
     * @param secret The client secret which provides the permission to send push notifications for this app.
     * @param sid The package security identifier which identifies the app.
     * @return The access token for the WNS or null if authorization fails.
     */
    private String getWindowsNotificationServiceAccessToken(String secret, String sid){
        String accessToken = null;
        try {
            // Encode the secret and the sid to fit the x-www-form-urlencoded format.
            secret = URLEncoder.encode(secret, "UTF-8");
            sid = URLEncoder.encode(sid, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.error("The Windows App secret or sid could not be encoded into a valid URL format.");
            return null;
        }

        // Create the content of the Http request.
        String content = String.format("grant_type=client_credentials&client_id=%s&client_secret=%s&scope=notify" +
                ".windows.com", sid, secret);

        try {
            URL url = new URL("https://login.live.com/accesstoken.srf");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            // Set the content of the request.
            OutputStream outputStream = conn.getOutputStream();
            outputStream.write(content.getBytes());
            outputStream.flush();
            outputStream.close();

            // Read the response of the HTTP Request.
            int responseStatus = conn.getResponseCode();
            if(responseStatus == HttpURLConnection.HTTP_OK){
                // Read the response content.
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while((inputLine = in.readLine()) != null){
                    response.append(inputLine);
                }
                in.close();
                logger.debug("Response from WNS:{}.", response.toString());

                // Extract the accessToken form the json string.
                accessToken = retrieveWNSAccessTokenFromJSON(response.toString());
            }else{
                logger.warn("The Authorization request to the WNS didn't provide the expected 200 OK status. No " +
                        "access token will be returned.");
            }

        } catch (MalformedURLException e) {
            logger.error("The WNS URL for the Server authentication is not valid.");
        } catch (IOException e) {
            logger.error("An IOException occurred during the authorization request. The authorization to WNS has " +
                    "failed.");
        }

        return accessToken;
    }

    /**
     * Parses the JSON string which has been received from the WNS. Searches for the property of access_token and
     * returns the corresponding value as a string.
     *
     * @param jsonString The JSON string.
     * @return The access token or null if the access token could not be retrieved.
     */
    private String retrieveWNSAccessTokenFromJSON(String jsonString){
        JSONTokener tokener = new JSONTokener(jsonString);
        JSONObject jsonObject = new JSONObject(tokener);
        String accessToken;
        try{
            accessToken = jsonObject.getString("access_token");
        }catch(JSONException e){
            logger.warn("Unable to retrieve the access token from the WNS response JSON string. The method will " +
                    "return null.");
            accessToken = null;
        }
        return accessToken;
    }
}
