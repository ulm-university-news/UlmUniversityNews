package ulm.university.news.data;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.data.enums.Language;
import ulm.university.news.manager.email.EmailManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Random;

/**
 * This class represents the Moderator of the application. A Moderator manages channels. In addition to his
 * capabilities as a Moderator he can also be an administrator with additional rights.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class Moderator {
    /** The unique id of the Moderator. */
    int id;
    /** The unique user name of the Moderator. */
    String name;
    /** The first name of the Moderator. */
    String firstName;
    /** The last name of the Moderator. */
    String lastName;
    /** The email address of the Moderator. */
    String email;
    /** The access token of the Moderator. The token is unique and unambiguously identifies the moderator. */
    String serverAccessToken;
    /** The password of the Moderator. */
    String password;
    /** The motivation of the account application. */
    String motivation;
    /** The preferred language of the Moderator. */
    Language language;
    /** Defines whether the Moderators account is locked or not. */
    boolean locked;
    /** Defines whether the Moderator has admin rights or not. */
    boolean admin;
    /** Defines whether the Moderator account is marked as deleted or not. */
    boolean deleted;
    /** Defines whether the Moderator actively manages a certain channel or not. */
    boolean active;

    /** The logger instance for Moderator. */
    private static final Logger logger = LoggerFactory.getLogger(Moderator.class);

    /**
     * Empty constructor. Needed values are set with corresponding set methods.
     */
    public Moderator() {
    }

    /**
     * Creates an instance of moderator.
     *
     * @param id The unique id of the moderator.
     * @param name The unique user name of the moderator.
     * @param firstName The first name of the moderator.
     * @param lastName The last name of the Moderator.
     * @param email The email address of the Moderator.
     * @param serverAccessToken The access token of the Moderator.
     * @param password The password of the Moderator.
     * @param motivation The motivation of the account application.
     * @param language The preferred language of the Moderator.
     * @param locked Defines whether the Moderators account is locked or not.
     * @param admin Defines whether the Moderator has admin rights or not.
     * @param deleted Defines whether the Moderator account is marked as deleted or not.
     * @param active Defines whether the Moderator actively manages a certain channel or not.
     */
    public Moderator(int id, String name, String firstName, String lastName, String email, String serverAccessToken,
                     String password, String motivation, Language language, boolean locked, boolean admin,
                     boolean deleted, boolean active) {
        this.id = id;
        this.name = name;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.serverAccessToken = serverAccessToken;
        this.password = password;
        this.motivation = motivation;
        this.language = language;
        this.locked = locked;
        this.admin = admin;
        this.deleted = deleted;
        this.active = active;
    }

    /**
     * Creates an instance of Moderator.
     *
     * @param name The unique user name of the Moderator.
     * @param firstName The first name of the Moderator.
     * @param lastName The last name of the Moderator.
     * @param email The email address of the Moderator.
     * @param serverAccessToken The access token of the Moderator.
     * @param password The password of the Moderator.
     * @param motivation The motivation of the account application.
     * @param language The preferred language of the Moderator.
     * @param locked Defines whether the Moderators account is locked or not.
     * @param admin Defines whether the Moderator has admin rights or not.
     * @param deleted Defines whether the Moderator account is marked as deleted or not.
     * @param active Defines whether the Moderator actively manages a certain channel or not.
     */
    public Moderator(String name, String firstName, String lastName, String email, String serverAccessToken,
                     String password, String motivation, Language language, boolean locked, boolean admin,
                     boolean deleted, boolean active) {
        this.name = name;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.serverAccessToken = serverAccessToken;
        this.password = password;
        this.motivation = motivation;
        this.language = language;
        this.locked = locked;
        this.admin = admin;
        this.deleted = deleted;
        this.active = active;
    }

    /**
     * Invalidates the old password and generates new random one. Sends the new password to the Moderator and encrypts
     * it afterwards.
     */
    public void resetPassword() {
        // Generate a new password.
        String newPassword = generatePassword();

        // Send email with new plain text password to the Moderator.
        // TODO Internationalization? German/English?
        String subject = "Ulm University News - Password Reset";
        String message;
        message = "Hello " + firstName + " " + lastName + ",\n\n";
        message += "your password has been reset.\nYour new password is: ";
        message += newPassword + "\n\n";
        message += "Regards, the team of Ulm University News";
        EmailManager.getInstance().sendMail(email, subject, message);

        // Hashes the plain text password.
        // TODO hash password

        // Encrypt password.
        encryptPassword();
    }

    /**
     * Generates a new random password for the Moderator.
     *
     * @return The generated password.
     */
    private String generatePassword() {
        // Define possible characters of the generated password.
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        // Define length of the generated password.
        int len = 12;

        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(len);
        // Choose len random characters of the alphabet as password.
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * Checks if the unencrypted password matches the one that has previously been encrypted.
     *
     * @param password The unencrypted password candidate which should be verified.
     * @return true if password is correct.
     */
    public boolean verifyPassword(String password) {
        return BCrypt.checkpw(password, this.password);
    }

    /**
     * Creates an random access token for this Moderator and stores it in this instance. The access token is then an
     * unique identifier for the Moderator in the system.
     */
    public void createModeratorToken() {
        try {
            // Create a random sequence of bytes.
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG", "SUN");
            byte[] randomBytes = new byte[256];
            sr.nextBytes(randomBytes);

            // Calculate hash on the randomly generated byte sequence.
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] token = sha256.digest(randomBytes);

            // Transform the bytes (8 bit signed) into a hexadecimal format.
            StringBuilder tokenString = new StringBuilder();
            for (int i = 0; i < token.length; i++) {
                /*
                Format parameters: %[flags][width]conversion
                Flag '0' - The result will be zero padded.
                Width '2' - The width is 2 as 1 byte is represented by two hex characters.
                Conversion 'x' - Result is formatted as hexadecimal integer, uppercase.
                 */
                tokenString.append(String.format("%02x", token[i]));
            }
            String accessToken = tokenString.toString();
            logger.info("Created an access token for the moderator.");

            // Set the generated access token in the serverAccessToken variable of this instance.
            this.serverAccessToken = accessToken;

        } catch (NoSuchAlgorithmException e) {
            logger.error("Could not generate an access token for the moderator. The expected digest algorithm is not " +
                    "available.", e);
        } catch (NoSuchProviderException e) {
            logger.error("Could not generate an access token for the moderator. The expected provider could not be " +
                    "localized.", e);
        }
    }

    /**
     * Encrypts the password of this Moderator instance.
     */
    public void encryptPassword() {
        // Hash and salt password. Stores encryption and salt in one field.
        password = BCrypt.hashpw(password, BCrypt.gensalt());
    }

    @Override
    public String toString() {
        return "Moderator{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", serverAccessToken='" + serverAccessToken + '\'' +
                ", password='" + password + '\'' +
                ", motivation='" + motivation + '\'' +
                ", language=" + language +
                ", locked=" + locked +
                ", admin=" + admin +
                ", deleted=" + deleted +
                ", active=" + active +
                '}';
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getServerAccessToken() {
        return serverAccessToken;
    }

    public void setServerAccessToken(String serverAccessToken) {
        this.serverAccessToken = serverAccessToken;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getMotivation() {
        return motivation;
    }

    public void setMotivation(String motivation) {
        this.motivation = motivation;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }
}
