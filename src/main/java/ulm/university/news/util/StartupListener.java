package ulm.university.news.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.controller.ChannelController;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * The StartupListener class is used to perform some actions on web app startup (and shutdown).
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class StartupListener implements ServletContextListener {

    /** The logger instance for StartupListener. */
    private static final Logger logger = LoggerFactory.getLogger(StartupListener.class);

    /** Instance of the ChannelController class. */
    private ChannelController channelCtrl = new ChannelController();

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        // This method is called only on web app startup.
        logger.info("Web app startup: Loading and activating reminders.");
        // Load stored reminders from database and activate the valid ones.
        int numberOfActivatedReminders = channelCtrl.activateStoredReminders();
        logger.info("Web app startup: {} reminder(s) activated.", numberOfActivatedReminders);
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        // Do nothing on shutdown.
    }
}