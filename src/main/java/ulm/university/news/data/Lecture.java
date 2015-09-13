package ulm.university.news.data;

import ulm.university.news.util.Faculty;

/**
 * The Lecture class is a sub class of Channel. This class adds fields to describe a Lecture.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Lecture extends Channel {
    /** The faculty to which the Lecture belongs. */
    Faculty faculty;
    /** The date on which the Lecture starts represented as text. */
    String startDate;
    /** The date on which the Lecture ends represented as text. */
    String endDate;
    /** The professor who gives the lecture. */
    String professor;
    /** The person who assists the lecture. */
    String assistant;

    public Lecture(Faculty faculty, String startDate, String endDate, String professor, String assistant) {
        // TODO Call super() with params!?
        super();
        this.faculty = faculty;
        this.startDate = startDate;
        this.endDate = endDate;
        this.professor = professor;
        this.assistant = assistant;
    }
}
