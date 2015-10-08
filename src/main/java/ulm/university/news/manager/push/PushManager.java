package ulm.university.news.manager.push;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.User;
import ulm.university.news.data.enums.PushType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class PushManager {

    // TODO Singleton class for PushManager or one PushManager instance for each Controller? What's faster?

    /** The logger instance for PushManager. */
    private static final Logger logger = LoggerFactory.getLogger(PushManager.class);

    public static final String API_KEY = "AIzaSyCiSqMqBeyOXgiro8uWleNMzEaw2AsZ2SQ";

    public static void notifySubscribers(int channelId, List<User> subscribers, PushType pushType) {
        // TODO Do this check here or in other classes before invoking this method?
        // If there are no subscribers, do nothing.
        if (subscribers == null) {
            return;
        }

        // Split subscribers by platform.
        List<String> userPushTokensAndroid = new ArrayList<String>();
        List<String> userPushTokensWindows = new ArrayList<String>();
        List<String> userPushTokensIOS = new ArrayList<String>();

        for (User subscriber : subscribers) {
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

        // Give push tokens to corresponding notification method.
        notifyAndroid(channelId, userPushTokensAndroid, pushType);
    }

    public static void notifySubscribers(int channelId, int moderatorId, List<User> subscribers, PushType pushType) {

    }

    private static void notifyAndroid(int channelId, List<String> pushTokens, PushType pushType) {
        String[] args = new String[2];
        // TODO Structure of JSON message? PushType, Ids
        args[0] = "Announcement Created";
        // args[1] = pushTokens.get(0);
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
            conn.setRequestProperty("Authorization", "key=" + API_KEY);
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
            System.out.println("Please ensure that API_KEY has been replaced by the server " +
                    "API key, and that the device's registration token is correct (if specified).");
            e.printStackTrace();
        }
    }
}
