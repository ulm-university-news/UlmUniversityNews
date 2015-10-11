package ulm.university.news.data;

import ulm.university.news.data.enums.PushType;

import java.util.List;

/**
 * The PushMessage class stores all values which are relevant for user notification. The push message contains one,
 * two or three different ids. The semantics of the given ids is identified by the PushType. The list contains users
 * with platform and push access token.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class PushMessage {
    /** The type of the push message. */
    private PushType pushType;
    /** The users who should receive the push message. */
    private List<User> users;
    /** The first id of the push message. */
    private Integer id1;
    /** The second id of the push message. */
    private Integer id2;
    /** The third id of the push message. */
    private Integer id3;

    /**
     * Creates an instance of the PushMessage class.
     *
     * @param pushType The type of the push message.
     * @param users The users who should be notified.
     * @param id1 The first id of the push message.
     * @param id2 The second id of the push message.
     * @param id3 The third id of the push message.
     */
    public PushMessage(PushType pushType, List<User> users, Integer id1, Integer id2, Integer id3) {
        this.pushType = pushType;
        this.users = users;
        this.id1 = id1;
        this.id2 = id2;
        this.id3 = id3;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PushMessage that = (PushMessage) o;

        if (pushType != that.pushType) return false;
        if (!users.equals(that.users)) return false;
        if (id1 != null ? !id1.equals(that.id1) : that.id1 != null) return false;
        if (id2 != null ? !id2.equals(that.id2) : that.id2 != null) return false;
        return !(id3 != null ? !id3.equals(that.id3) : that.id3 != null);

    }

    @Override
    public int hashCode() {
        int result = pushType.hashCode();
        result = 31 * result + users.hashCode();
        result = 31 * result + (id1 != null ? id1.hashCode() : 0);
        result = 31 * result + (id2 != null ? id2.hashCode() : 0);
        result = 31 * result + (id3 != null ? id3.hashCode() : 0);
        return result;
    }

    public PushType getPushType() {
        return pushType;
    }

    public void setPushType(PushType pushType) {
        this.pushType = pushType;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public Integer getId1() {
        return id1;
    }

    public void setId1(Integer id1) {
        this.id1 = id1;
    }

    public Integer getId2() {
        return id2;
    }

    public void setId2(Integer id2) {
        this.id2 = id2;
    }

    public Integer getId3() {
        return id3;
    }

    public void setId3(Integer id3) {
        this.id3 = id3;
    }
}