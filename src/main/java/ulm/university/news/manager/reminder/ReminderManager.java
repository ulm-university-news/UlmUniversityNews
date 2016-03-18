package ulm.university.news.manager.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.Reminder;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

import static ulm.university.news.util.Constants.TIME_ZONE;

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

    /** Reference for the EmailManager Singleton class. */
    private static ReminderManager _instance;

    /** Schedules active ReminderTasks. */
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /** Holds references to active ReminderTasks to allow their deactivation. */
    private static ConcurrentHashMap<Integer, Future<?>> activeReminders = new ConcurrentHashMap<>();

    /**
     * Creates an instance of the ReminderManager class.
     */
    public ReminderManager() {
    }

    /**
     * Get an instance of the ReminderManager class.
     *
     * @return Instance of ReminderManager.
     */
    public static synchronized ReminderManager getInstance() {
        if (_instance == null) {
            _instance = new ReminderManager();
        }
        return _instance;
    }

    /**
     * Activates a reminder. An active reminder produces announcements at the specified times.
     *
     * @param reminder The reminder which should be activated.
     */
    public synchronized void addReminder(Reminder reminder) {
        // Check if reminder is already scheduled.
        if (activeReminders.containsKey(reminder.getId())) {
            return;
        }

        ZonedDateTime currentDate = ZonedDateTime.now(TIME_ZONE);
        reminder.computeFirstNextDate();

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
     * Deactivates a reminder. The reminder will be removed from the list of active reminders, which means that the
     * reminder produces no more announcements.
     *
     * @param reminderId The id of the reminder which should be deactivated.
     */
    public synchronized void removeReminder(int reminderId) {
        // Cancel already scheduled reminder tasks.
        if (activeReminders.containsKey(reminderId)) {
            activeReminders.get(reminderId).cancel(false);
            activeReminders.remove(reminderId);
            logger.info("Reminder with id {} has been deactivated.", reminderId);
        }
    }

    /**
     * Changes an active reminder. Simply removes the running reminder and adds the changed reminder again.
     *
     * @param reminder The changed reminder which should be activated.
     */
    public synchronized void changeReminder(Reminder reminder) {
        removeReminder(reminder.getId());
        addReminder(reminder);
    }

}
