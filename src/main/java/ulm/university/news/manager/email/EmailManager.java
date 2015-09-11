package ulm.university.news.manager.email;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * The EmailManager class is a Singleton class which offers the possibility to send emails.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
public class EmailManager {

    /** Reference for the EmailManager Singleton class. */
    private static EmailManager _instance;

    /** Username for the gmail smtp server. */
    private String username = null;
    /** Password for the gmail smtp server. */
    private String password = null;

    /**
     * Create an instance of the EmailManager class.
     */
    public EmailManager() {

    }

    /**
     * Get an instance of the EmailManager class.
     *
     * @return Instance of EmailManager.
     */
    public static EmailManager getInstance() {
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

        // Read the user credentials for the gmail account.
        Properties userCredentialsGmail = new Properties();
        InputStream input = getClass().getClassLoader().getResourceAsStream("EmailManager.properties");
        if (input == null) {
            // TODO
            return false;
        }
        try {
            userCredentialsGmail.load(input);

            username = userCredentialsGmail.getProperty("username");
            password = userCredentialsGmail.getProperty("password");
        } catch (IOException e) {
            // TODO
            e.printStackTrace();
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

        // Create the message object.
        Message mailMessage = new MimeMessage(session);
        try {
            mailMessage.setFrom(new InternetAddress(username));
            mailMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientMailAddress));
            mailMessage.setSubject(subject);
            mailMessage.setText(message);

            Transport.send(mailMessage);

            System.out.println("Mail sent!");
            return true;

        } catch (AddressException e) {
            // TODO
            System.err.println("Address error! Not a valid address!");
            e.printStackTrace();
        } catch (MessagingException e) {
            // TODO
            System.err.println("MessagingException occured! Could not send mail!");
            e.printStackTrace();
        }

        return false;
    }

}
