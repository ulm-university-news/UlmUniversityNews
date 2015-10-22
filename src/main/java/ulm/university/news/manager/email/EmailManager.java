package ulm.university.news.manager.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The EmailManager class is a Singleton class which offers the possibility to send emails.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class EmailManager {

    /** Reference for the EmailManager Singleton class. */
    private static EmailManager _instance;

    /** An instance of the Logger class which performs logging for the EmailManager class. */
    private static final Logger logger = LoggerFactory.getLogger(EmailManager.class);

    /** Username for the gmail smtp server. */
    private String username = null;
    /** Password for the gmail smtp server. */
    private String password = null;

    /**
     * Creates an instance of the EmailManager class.
     */
    public EmailManager() {

    }

    /**
     * Get an instance of the EmailManager class.
     *
     * @return Instance of EmailManager.
     */
    public static synchronized EmailManager getInstance() {
        if (_instance == null) {
            _instance = new EmailManager();
        }
        return _instance;
    }

    /**
     * Send an email to a given recipient.
     *
     * @param recipientMailAddress The email address of the recipient.
     * @param subject              The subject of the email.
     * @param message              The content of the email.
     * @return Returns true if email has been sent successfully, false otherwise.
     */
    public boolean sendMail(String recipientMailAddress, String subject, String message) {
        // Create a Properties object to contain settings for the SMTP protocol provider.
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        // Read the account credentials for the gmail account.
        Properties accountCredentialsGmail = retrieveGmailCredentials();
        if(accountCredentialsGmail != null && accountCredentialsGmail.getProperty("username") != null &&
                accountCredentialsGmail.getProperty("password") != null){
            username = accountCredentialsGmail.getProperty("username");
            password = accountCredentialsGmail.getProperty("password");
        }
        else{
            logger.error("EmailManager was unable to read the account credentials of the University News Gmail " +
                    "account from the properties object.");
            return false;
        }

        /* If SMTP authentication is required the mail.smtp.auth property must be set to true and an Authenticator
        instance needs to be created which returns a PasswordAuthentication instance with your username and password.*/
        Authenticator authenticator = null;
        authenticator = new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };

        // Create a session instance using the properties object and the Authenticator object. Debug is set to true.
        Session session = Session.getInstance(props, authenticator);
        session.setDebug(true);

        boolean isSentSuccessfully = false;
        int transmissionAttempts = 0;
        while(isSentSuccessfully == false && transmissionAttempts < 3){
            // Create a message with the given subject and content. Send it to the recipient.
            isSentSuccessfully = createAndSendMimeMessage(session, username, recipientMailAddress, subject, message);
            transmissionAttempts++;
        }

        return isSentSuccessfully;
    }

    /**
     * Creates a new MIME-style message with the given parameters for the specified session and sends it to the
     * declared receiver.
     *
     * @param session The session which should be used to send the message.
     * @param from The email address of the sender.
     * @param to The email address of the recipient of the message.
     * @param subject The subject of the message.
     * @param message The actual content of the message.
     * @return Returns true if message has been sent successfully, false otherwise.
     */
    private boolean createAndSendMimeMessage(Session session, String from, String to, String subject, String message){
        // Create the message object.
        Message mailMessage = new MimeMessage(session);
        try {
            mailMessage.setFrom(new InternetAddress(from));
            mailMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            mailMessage.setSubject(subject);
            mailMessage.setText(message);

            Transport.send(mailMessage);

            logger.info("Mail has been sent successfully to the address {} with the subject {}.", to, subject);
            return true;

        } catch (AddressException e) {
            logger.error("EmailManager wasn't able to send the mail. The specified address is not a valid address. " +
                    "The receiver address is {}.", to);
        } catch (MessagingException e) {
            logger.error("EmailManager wasn't able to send the mail. A MessagingException has occurred. Exception " +
                    "message is: {}.", e.getMessage());
        }

        return false;
    }

    /**
     * Reads the properties file which contains the account credentials for the University News Gmail account.
     * Returns the properties in a Properties object.
     *
     * @return Returns Properties object, or null if reading of the properties file has failed.
     */
    private Properties retrieveGmailCredentials(){
        Properties userCredentialsGmail = new Properties();
        InputStream input = getClass().getClassLoader().getResourceAsStream("EmailManager.properties");
        if (input == null) {
            logger.error("EmailManager could not localize the file EmailManager.properties.");
            return null;
        }
        try {
            userCredentialsGmail.load(input);
        } catch (IOException e) {
            logger.error("Failed to load the properties of the EmailManager Gmail account credentials.");
            return null;
        }
        return userCredentialsGmail;
    }

}
