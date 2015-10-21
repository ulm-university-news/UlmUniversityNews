package ulm.university.news.manager.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * The NotifyWindowsTask class is used to send push messages to Windows clients.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class NotifyWindowsTask implements Runnable {

    /** The logger instance for NotifyWindowsTask. */
    private static final Logger logger = LoggerFactory.getLogger(NotifyWindowsTask.class);

    /** A list of Windows push tokens. */
    List<String> pushTokens = null;
    /** The push message as a JSON String. */
    String jsonPushMessage = null;

    /**
     * Creates a new NotifyWindowsTask which sends push messages to Windows clients.
     *
     * @param pushTokens A list of Windows push tokens.
     * @param jsonPushMessage The push message as a JSON String.
     */
    public NotifyWindowsTask(List<String> pushTokens, String jsonPushMessage){
        this.pushTokens = pushTokens;
        this.jsonPushMessage = jsonPushMessage;
    }

    /**
     * This method runs when the NotifyWindowsTask has started.
     */
    @Override
    public void run() {
        logger.debug("Started. Notifying Windows clients.");
        notifyWindows();
    }

    /**
     * Sends the given JSON push message to the Windows clients which are identified by the given push tokens.
     *
     */
    private void notifyWindows() {
        // Check if there is at least one recipients. Do nothing if there is non.
        if (pushTokens.isEmpty()) {
            logger.info("No Windows push tokens given. No Windows Phone user will be notified.");
            return;
        }
        logger.info("Got a list of {} windows push tokens.", pushTokens.size());

        // Read the access token.
        String accessToken;
        accessToken = PushManager.getInstance().getWnsAccessToken();

        if (accessToken == null) {
            // Request a new access token and store it in the variable.
            PushManager.getInstance().setWnsAccessToken();
        }

        // If sending fails, retry just once according to the best practices.
        int maxRetries = 2;
        int amountOfSuccessfulPushs = 0;
        for (String pushToken : pushTokens) {
            boolean successful = false;
            int attempts = 0;
            while (!successful && attempts < maxRetries) {
                accessToken = PushManager.getInstance().getWnsAccessToken();
                // Try to send the push notification to the device identified by the given push token.
                successful = sendWindowsRawNotification(pushToken, jsonPushMessage, accessToken);
                attempts++;
            }

            if (successful) {
                amountOfSuccessfulPushs++;
                logger.debug("Successfully sent the push notification to the client identified by the push token: {}" +
                        ".", pushToken);
            } else {
                logger.debug("Sending to token:{} has failed.", pushToken);
            }
        }
        logger.info("Push messages send to {} windows client(s).", amountOfSuccessfulPushs);
    }

    /**
     * Sends a raw push notification to the device which is identified by the determined push token.
     *
     * @param pushToken The push token which identifies the client device.
     * @param content The content of the raw notification.
     * @param accessToken The access token which identifies the server at the WNS.
     * @return Returns true if the notification has been sent successfully, false otherwise.
     */
    private boolean sendWindowsRawNotification(String pushToken, String content, String accessToken) {
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
            switch (responseCode) {
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    logger.warn("The raw notification request to the WNS has failed. The access token is invalid.");
                    // The access token is invalid. Request a new one.
                    PushManager.getInstance().setWnsAccessToken();
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
                case HttpURLConnection.HTTP_OK:
                    logger.debug("Successfully sent push notification.");
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
}
