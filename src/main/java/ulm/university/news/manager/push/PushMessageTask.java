package ulm.university.news.manager.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.PushMessage;
import ulm.university.news.data.enums.PushType;

/**
 * TODO
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
     * TODO
     *
     * @param pushMessage
     * @param pushMessageMap
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
     * TODO
     */
    private void processPushMessage() {
        int numberOfCachedPushMessages = PushManager.getInstance().getNumberOfCachedPushMessages(pushMessageMap);

        if (numberOfCachedPushMessages > 1) {
            switch (pushMessage.getPushType()) {
                case BALLOT_CHANGED:
                    pushMessage.setPushType(PushType.BALLOT_CHANGED_ALL);
                    break;
                case BALLOT_OPTION_NEW:
                    pushMessage.setPushType(PushType.BALLOT_OPTION_ALL);
                    break;
                case BALLOT_OPTION_VOTE:
                    pushMessage.setPushType(PushType.BALLOT_OPTION_VOTE_ALL);
                    break;
                case CONVERSATION_CHANGED:
                    pushMessage.setPushType(PushType.CONVERSATION_CHANGED_ALL);
                    break;
            }
        }
        PushManager.getInstance().notifyUsersAsTask(pushMessage.getPushType(), pushMessage.getUsers(), pushMessage.getId1(),
                pushMessage.getId2(), pushMessage.getId3(), pushMessageMap);
    }
}
