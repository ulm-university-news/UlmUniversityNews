package ulm.university.news.data;

import ulm.university.news.data.enums.ChannelType;
import ulm.university.news.data.enums.Faculty;

import java.time.ZonedDateTime;

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
    /** The lecturer who gives the lecture. */
    String lecturer;
    /** The person who assists the lecture. */
    String assistant;

    public Lecture() {
    }

    public Lecture(int id, String name, String description, ChannelType type, ZonedDateTime creationDate,
                   ZonedDateTime modificationDate, String term, String locations, String dates, String contacts,
                   String website, Faculty faculty, String startDate, String endDate, String lecturer, String
                           assistant) {
        super(id, name, description, type, creationDate, modificationDate, term, locations, dates, contacts, website);
        this.faculty = faculty;
        this.startDate = startDate;
        this.endDate = endDate;
        this.lecturer = lecturer;
        this.assistant = assistant;
    }

    @Override
    public String toString() {
        return "Lecture{" +
                "faculty=" + faculty +
                ", startDate='" + startDate + '\'' +
                ", endDate='" + endDate + '\'' +
                ", lecturer='" + lecturer + '\'' +
                ", assistant='" + assistant + '\'' +
                "} " + super.toString();
    }

    public Faculty getFaculty() {
        return faculty;
    }

    public void setFaculty(Faculty faculty) {
        this.faculty = faculty;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getLecturer() {
        return lecturer;
    }

    public void setLecturer(String lecturer) {
        this.lecturer = lecturer;
    }

    public String getAssistant() {
        return assistant;
    }

    public void setAssistant(String assistant) {
        this.assistant = assistant;
    }
}
