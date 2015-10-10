package ulm.university.news.manager.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.PushMessage;
import ulm.university.news.data.enums.PushType;

/**
 * The PushMessageTask class is used to perform actions after a certain period of time. It investigates the cached
 * push messages and may replaces them with one compound push message.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class PushMessageTask implements Runnable {

    /** The logger instance for PushMessageTask. */
    private static final Logger logger = LoggerFactory.getLogger(PushMessageTask.class);

    private final PushMessage pushMessage;

    private final PushMessage pushMessageMap;

    /**
     * Constructs an instance of the PushMessageTask class and sets the given push messages.
     *
     * @param pushMessage The push message with all data.
     * @param pushMessageMap The push message which is stored in the hash map.
     */
    public PushMessageTask(PushMessage pushMessage, PushMessage pushMessageMap) {
        this.pushMessage = pushMessage;
        this.pushMessageMap = pushMessageMap;
    }

    /**
     * This method runs when the PushMessageTask has started.
     */
    @Override
    public void run() {
        processPushMessage();
    }

    /**
     * Investigates the cached push messages and may replaces them with one compound push message. Uses the
     * PushManager to send the cached push messages.
     */
    private void processPushMessage() {
        int numberOfCachedPushMessages = PushManager.getInstance().getNumberOfCachedPushMessages(pushMessageMap);
        // Replace two or more push messages with one of another push type.
        if (numberOfCachedPushMessages > 1) {
            switch (pushMessage.getPushType()) {
                case BALLOT_CHANGED:
                    pushMessage.setPushType(PushType.BALLOT_CHANGED_ALL);
                    logger.debug("Replaced push type BALLOT_CHANGED with {}.", pushMessage.getPushType());
                    break;
                case BALLOT_OPTION_NEW:
                    pushMessage.setPushType(PushType.BALLOT_OPTION_ALL);
                    logger.debug("Replaced push type BALLOT_OPTION_NEW with {}.", pushMessage.getPushType());
                    break;
                case BALLOT_OPTION_VOTE:
                    pushMessage.setPushType(PushType.BALLOT_OPTION_VOTE_ALL);
                    logger.debug("Replaced push type BALLOT_OPTION_VOTE with {}.", pushMessage.getPushType());
                    break;
                case CONVERSATION_CHANGED:
                    pushMessage.setPushType(PushType.CONVERSATION_CHANGED_ALL);
                    logger.debug("Replaced push type CONVERSATION_CHANGED with {}.", pushMessage.getPushType());
                    break;
            }
        } else {
            logger.debug("Push type of cached push message hasn't changed.");
        }
        PushManager.getInstance().notifyUsersAsTask(pushMessage.getPushType(), pushMessage.getUsers(), pushMessage
                .getId1(), pushMessage.getId2(), pushMessage.getId3(), pushMessageMap);
    }
}
