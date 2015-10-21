package ulm.university.news.manager.push;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * The NotifyAndroidTask class is used to send push messages to Android clients.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class NotifyAndroidTask implements Runnable {

    /** The logger instance for NotifyAndroidTask. */
    private static final Logger logger = LoggerFactory.getLogger(NotifyAndroidTask.class);

    /** A list of Android push tokens. */
    List<String> pushTokens = null;
    /** The push message as a JSON String. */
    String jsonPushMessage = null;
    /** The access key for the GCM server. */
    String gcmApiKey = null;

    /**
     * Creates a new NotifyAndroidTask which sends push messages to Android clients.
     *
     * @param pushTokens A list of Android push tokens.
     * @param jsonPushMessage The push message as a JSON String.
     * @param gcmApiKey The access key for the GCM server.
     */
    public NotifyAndroidTask(List<String> pushTokens, String jsonPushMessage, String gcmApiKey){
        this.pushTokens = pushTokens;
        this.jsonPushMessage = jsonPushMessage;
        this.gcmApiKey = gcmApiKey;
    }

    /**
     * This method runs when the NotifyAndroidTask has started.
     */
    @Override
    public void run() {
        logger.debug("Started. Notifying Android clients.");
        notifyAndroid();
    }

    /**
     * Creates a GCM JSON message and ensures that each push access token will be included. Sends the given JSON push
     * message to the Android clients.
     */
    private void notifyAndroid() {
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
            conn.setRequestProperty("Authorization", "key=" +gcmApiKey);
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
}
