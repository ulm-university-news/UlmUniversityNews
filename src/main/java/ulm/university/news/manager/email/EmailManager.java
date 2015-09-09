package ulm.university.news.manager.email;

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

    //reference for the Singleton class
    private static EmailManager _instance;

    //user credentials for the gmail smtp server
    final String username = "ulm.university.news@gmail.com";
    final String password = "bi{8Q,h.SaQq";

    //constructor
    public EmailManager(){

    }

    /**
     * get an instance of the MailManager class
     * @return instance of MailManager
     */
    public static EmailManager getInstance(){
        if(_instance == null){
            _instance = new EmailManager();
        }
        return _instance;
    }


    public void sendMail(String recipientMailAddress, String subject, String message){
        //Create a Properties object to contain settings for the SMTP protocol provider
        //Properties
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        //if SMTP authentication is required you must set the mail.smtp.auth property to true and construct a Authenticator instance that returns a PasswordAuthentication
        //instance with your username and password
        Authenticator authenticator = null;
        authenticator = new Authenticator(){
            protected PasswordAuthentication getPasswordAuthentication(){
                return new PasswordAuthentication(username, password);
            }
        };

        //create a session instance using the properties object and the Authenticator object. Debug will be set to true
        Session session = Session.getInstance(props, authenticator);
        session.setDebug(true);

        //create the message object
        Message mailMessage = new MimeMessage(session);
        try {
            mailMessage.setFrom(new InternetAddress(username));
            mailMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientMailAddress));
            mailMessage.setSubject(subject);
            mailMessage.setText(message);

            Transport.send(mailMessage);

            System.out.println("Mail sent!");

        } catch (AddressException e) {
            System.err.println("Address error! Not a valid address!");
            e.printStackTrace();
        } catch (MessagingException e) {
            System.err.println("MessagingException occured! Could not send mail!");
            e.printStackTrace();
        }
    }

}
