package ulm.university.news.manager.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.Reminder;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

import static ulm.university.news.util.Constants.*;

/**
 * The ReminderManager provides methods to activate and deactivate the production of Announcements of a Reminder.
 * This class schedules the ReminderTasks.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class ReminderManager {

    /** The logger instance for ReminderManager. */
    private static final Logger logger = LoggerFactory.getLogger(ReminderManager.class);

    /** Schedules active ReminderTasks. */
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** Holds references to active ReminderTasks to allow their deactivation. */
    private static ConcurrentHashMap<Integer, Future<?>> activeReminders = new ConcurrentHashMap<Integer, Future<?>>();

    /**
     * Activates a Reminder. An active Reminder produces Announcements at the specified times.
     *
     * @param reminder The Reminder which should be activated.
     */
    public static synchronized void addReminder(Reminder reminder) {
        ZonedDateTime currentDate = ZonedDateTime.now(TIME_ZONE);

        Future<?> timingTask;
        //Starting reminder tasks is exact to the second.
        if(reminder.getInterval() == 0){
            // If interval is 0, it's a one time reminder.
            timingTask = scheduler.schedule(new ReminderTask(reminder), ChronoUnit.SECONDS.between(currentDate,
                    reminder.getNextDate()), TimeUnit.SECONDS);
        } else {
            // If interval isn't 0, it's a periodic reminder.
            timingTask = scheduler.scheduleAtFixedRate(new ReminderTask(reminder), ChronoUnit.SECONDS.between
                    (currentDate, reminder.getNextDate()), reminder.getInterval(), TimeUnit.SECONDS);
        }

        activeReminders.put(reminder.getId(), timingTask);
        logger.info("Reminder with id {} has been activated.", reminder.getId());
    }

    /**
     * Deactivates a Reminder. The Reminder will be removed from the list of active Reminders, which means that the
     * Reminder produces no more Announcements.
     *
     * @param reminderId The id of the Reminder which should be deactivated.
     */
    public static synchronized void removeReminder(int reminderId) {
        // Cancel already scheduled reminder tasks.
        if (activeReminders.containsKey(reminderId)) {
            activeReminders.get(reminderId).cancel(false);
            activeReminders.remove(reminderId);
            logger.info("Reminder with id {} has been deactivated.", reminderId);
        }
    }

    /**
     * Changes an active Reminder. Simply removes the running Reminder and adds the changed Reminder again.
     *
     * @param reminder The changed reminder which should be activated.
     */
    public static synchronized void changeReminder(Reminder reminder) {
        removeReminder(reminder.getId());
        addReminder(reminder);
    }

}
