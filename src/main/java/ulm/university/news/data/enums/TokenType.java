package ulm.university.news.data.enums;

/**
 * The TokenType defines to which resources a received token belongs. If it is of type USER, it identifies an user
 * account. If it is of type MODERATOR, it identifies a moderator account. If the received token doesn't match one
 * of these cases, it is considered invalid and the request needs to be rejected.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public enum TokenType {
    USER, MODERATOR, INVALID;

    public static final TokenType values[] = values();
}
