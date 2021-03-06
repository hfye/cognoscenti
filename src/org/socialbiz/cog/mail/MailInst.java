package org.socialbiz.cog.mail;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import org.socialbiz.cog.AddressListEntry;
import org.socialbiz.cog.EmailRecord;
import org.socialbiz.cog.MemFileDataSource;
import org.socialbiz.cog.MimeTypes;

import com.purplehillsbooks.json.JSONArray;
import com.purplehillsbooks.json.JSONException;
import com.purplehillsbooks.json.JSONObject;
import com.purplehillsbooks.streams.MemFile;

/**
 * Represents a single email message to a single recipient.
 * The reason only one addressee is because this is 'automated'
 * email right?  Each message should be customized to the user
 * so there is no real need for addressing multiple people
 * with the same message.  If you need to send to 5 people,
 * just create 5 of these messages.
 *
 */
public class MailInst extends JSONWrapper {

    public static final String READY_TO_GO = "Ready";
    public static final String SENT = "Sent";
    public static final String FAILED = "Failed";
    public static final String SKIPPED = "Skipped";




    public MailInst(JSONObject _kernel) {
        super(_kernel);
    }

    public String getStatus() throws Exception {
        return kernel.getString("Status");
    }
    public void setStatus(String val) throws Exception {
        kernel.put("Status", val);
    }

    public long getLastSentDate() throws Exception {
        return kernel.getLong("LastSentDate");
    }
    public void setLastSentDate(long val) throws Exception {
        kernel.put("LastSentDate", val);
    }
    public long getCreateDate() throws Exception {
        return kernel.getLong("CreateDate");
    }
    public void setCreateDate(long val) throws Exception {
        kernel.put("CreateDate", val);
    }

    public String getAddressee() throws Exception {
        return kernel.getString("Addressee");
    }
    public void setAddressee(String val) throws Exception {
        kernel.put("Addressee", val);
    }

    public String getFromAddress() throws Exception {
        return kernel.getString("From");
    }
    public void setFromAddress(String val) throws Exception {
        //there were some email addresses floating around that had 'angle quotes' in them.
        //This is an attempt to keep them from ever being used in this class.
        //Can probable remove after 2018 is over
        if (val.indexOf(AddressListEntry.LAQUO)>=0) {
            throw new Exception("MailInst.setFromAddress requires a straight SMTP email address and should not have LAQUO in it");
        }
        kernel.put("From", val);
    }
    public String getFromName() throws Exception {
        return kernel.getString("FromName");
    }
    public void setFromName(String val) throws Exception {
        kernel.put("FromName", val);
    }

    public String getSubject() throws Exception {
        return kernel.getString("Subject");
    }
    public void setSubject(String val) throws Exception {
        kernel.put("Subject", val);
    }

    public String getBodyText() throws Exception {
        return kernel.getString("BodyText");
    }
    public void setBodyText(String val) throws Exception {
        kernel.put("BodyText", val);
    }

    public String getExceptionMessage() throws Exception {
        return kernel.getString("Exception");
    }
    public void setExceptionMessage(Exception e, String context) throws Exception {
        kernel.put("exception", JSONException.convertToJSON(e, context));
    }

    public boolean containsValue(String s) throws Exception {
        if ((s==null) || s.length()==0) {
            return true;
        }
        s = s.toLowerCase();
        if (getSubject().toLowerCase().contains(s)) {
            return true;
        }
        if (this.getAddressee().toLowerCase().contains(s)) {
            return true;
        }
        if (this.getFromAddress().toLowerCase().contains(s)) {
            return true;
        }
        if (this.getFromName().toLowerCase().contains(s)) {
            return true;
        }
        return false;
    }


    /**
     * This holds a collection of File objects for the attachment
     * files that will go with the email message.  They will be
     * attached only if they still exist at the time that the
     * email is being sent, otherwise they will be ignored.
     */
    public List<File> getAttachmentFiles() throws Exception {
        ArrayList<File> ret = new ArrayList<File>();
        JSONArray attachmentFiles = getRequiredArray("AttachmentFiles");
        int last = attachmentFiles.length();
        for (int i=0; i<last; i++) {
            ret.add(new File(attachmentFiles.getString(i)));
        }
        return ret;
    }
    public void setAttachmentFiles(List<File> atts) throws Exception {
        JSONArray attachmentFiles = getRequiredArray("AttachmentFiles");
        for (File att : atts) {
            attachmentFiles.put(att.toString());
        }
        kernel.put("AttachmentFiles", attachmentFiles);
    }


    /**
     * send the message.
     * This routine does NOT throw an exception if the mailing fails.
     *
     * Any exception thrown in the course of sending a message is stored
     * in the exception field of the given message.  And false returned.
     *
     * @return either it returns
     *         (true) because it sent the mail and marked it so
     *         (false) if it can't sent the message
     *
     */
    public boolean sendPreparedMessageImmediately(Mailer mailer) {

        long sendTime = MailFile.getUniqueTime();
        Transport transport = null;
        String addressee = "UNSPECIFIED";

        if ("true".equals(mailer.getProperty("traceProperties"))) {
            mailer.dumpPropertiesToLog();
        }

        try {

            Authenticator authenticator = new MyAuthenticator(mailer.getProperties());
            Session mailSession = Session.getInstance(mailer.getProperties(), authenticator);
            mailSession.setDebug("true".equals(mailer.getProperty("mail.debug")));

            transport = mailSession.getTransport();
            transport.connect();

            addressee = getAddressee();

            MimeMessage message = new MimeMessage(mailSession);
            message.setSentDate(new Date(sendTime));

            //The FROM of the message gets put into the reply-to field
            //so replies go to the person who started the message.
            String rawFrom = getFromAddress();
            String fromName = getFromName();
            if (fromName==null || fromName.length()==0) {
                //must have something, for the conversion cases
                fromName = "Weaver User";
            }
            if (rawFrom!=null && rawFrom.length()>0) {
                message.setReplyTo(makeAddress(fromName, rawFrom));
            }

            //Always use a fixed from address to avoid being tagged as a spammer
            String stdFromAddress = mailer.getProperty("mail.smtp.from");
            message.setFrom(makeAddress("\u2379 "+fromName, stdFromAddress)[0]);

            String encodedSubjectLine = MimeUtility.encodeText(getSubject(), "utf-8", "B");
            message.setSubject(encodedSubjectLine);

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setHeader("Content-Type", "text/html; charset=\"utf-8\"");
            textPart.setText(getBodyText(), "UTF-8");
            textPart.setHeader("Content-Transfer-Encoding", "quoted-printable");
            // apparently using 'setText' can change the content type for
            // you automatically, so re-set it.
            textPart.setHeader("Content-Type", "text/html; charset=\"utf-8\"");

            Multipart mp = new MimeMultipart();
            mp.addBodyPart(textPart);
            message.setContent(mp);

            attachFiles(mp);

            // set the to address.
            InternetAddress[] addressTo = new InternetAddress[1];

            try {
                addressTo[0] = new InternetAddress(AddressListEntry.cleanQuotes(addressee));
            } catch (Exception ex) {
                throw new Exception("Error while attempting to send email to ("+addressee+")", ex);
            }

            message.addRecipients(Message.RecipientType.TO, addressTo);
            transport.sendMessage(message, message.getAllRecipients());

            System.out.println("MAILINST: Sent email to "+addressee+": "+getSubject());

            setStatus(EmailRecord.SENT);
            setLastSentDate(sendTime);
            return true;
        } catch (Exception me) {
            try {
                String context = "Failed ("+new Date()+") while sending a simple message ("+getSubject()+") to ("+addressee+"): ";
                setExceptionMessage(me, context);
                setLastSentDate(sendTime);
                JSONException.traceException(System.out, me, context);
                setStatus(EmailRecord.FAILED);
            }
            catch (Exception eee) {
                System.out.println("EXCEPTION within EXCEPTION: "+eee);
                JSONException.traceException(System.out, eee, "EXCEPTION within EXCEPTION");
            }
            return false;
        } finally {
            if (transport != null) {
                try {
                    transport.close();
                } catch (Exception ce) { /* ignore this exception */
                    JSONException.traceException(System.out, ce, "transport.close() threw an exception in a finally block!  Ignored!");
                }
            }
        }
    }

    private Address[] makeAddress(String name, String address) throws Exception {
        InternetAddress iAdd = new InternetAddress(address, name, "UTF-8");
        iAdd.validate();         //make sure there are no problems
        Address[] addressArray = new Address[1];
        addressArray[0] = iAdd;
        return addressArray;
    }


    public void setFromMessage(Message message) throws Exception {
        Address[] from = message.getFrom();
        if (from!=null && from.length>0) {
            Address thisFrom = from[0];
            String fromAddress = thisFrom.toString().trim();
            int bracketPos = fromAddress.indexOf("<");
            int endPos = fromAddress.indexOf(">");
            if (bracketPos>0 && endPos>bracketPos) {
                this.setFromName(fromAddress.substring(0,bracketPos).trim());
                this.setFromAddress(fromAddress.substring(bracketPos+1,endPos).trim());
            }
            else {
                this.setFromName(fromAddress);
                this.setFromAddress(fromAddress);
            }
        }
        Address[] to = message.getAllRecipients();
        if (to!=null && to.length>0) {
            Address thisTo = to[0];
            String toAddress = thisTo.toString().trim();
            int bracketPos = toAddress.indexOf("<");
            int endPos = toAddress.indexOf(">");
            if (bracketPos>0 && endPos>bracketPos) {
                this.setAddressee(toAddress.substring(bracketPos+1,endPos).trim());
            }
            else {
                this.setFromAddress(toAddress);
            }
        }
        this.setSubject(message.getSubject());
        MemFile mf = new MemFile();
        mf.fillWithInputStream(message.getInputStream());
        this.setBodyText(mf.toString());
        this.setCreateDate(safeGetTime(message.getSentDate()));
        this.setLastSentDate(safeGetTime(message.getReceivedDate()));
    }

    private long safeGetTime(Date d) {
        if (d==null) {
            return 0;
        }
        return d.getTime();
    }

    /**
     * Note that this method needs to work without accessing the NGPage object
     * directly.  We must use only the EmailRecord object alone, by using
     * the attachment contents inside the object.
     */
    private void attachFiles(Multipart mp) throws Exception {
        List<File> attachids = getAttachmentFiles();
        for (File path : attachids) {

            if (path.exists()) {
                MemFile thisContent = new MemFile();
                thisContent.fillWithInputStream(new FileInputStream(path));

                MimeBodyPart pat = new MimeBodyPart();
                MemFileDataSource mfds = new MemFileDataSource(thisContent, path.toString(),
                                MimeTypes.getMimeType(path.getName()));
                pat.setDataHandler(new DataHandler(mfds));
                pat.setFileName(path.getName());
                mp.addBodyPart(pat);
            }
        }
    }



    /**
     * A simple authenticator class that gets the username and password
     * from the properties object if mail.smtp.auth is set to true.
     *
     * documentation on javax.mail.Authenticator says that if you want
     * authentication, return an object, otherwise return null.  So
     * null is returned if no auth setting or user/password.
     */
    private static class MyAuthenticator extends javax.mail.Authenticator {
        private Properties props;

        public MyAuthenticator(Properties _props) {
            props = _props;
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            if ("true".equals(props.getProperty("mail.smtp.auth"))) {
                return new PasswordAuthentication(
                        props.getProperty("mail.smtp.user"),
                        props.getProperty("mail.smtp.password"));
            }
            return null;
        }
    }

    public JSONObject getListableJSON() throws Exception {
        JSONObject e2 = new JSONObject();
        e2.put("Addressee",    kernel.optString("Addressee", "unknown"));
        e2.put("CreateDate",   kernel.optLong("CreateDate",0));
        e2.put("From",         kernel.optString("From", "unknown"));
        e2.put("FromName",     kernel.optString("FromName", "unknown"));
        e2.put("LastSentDate", kernel.optLong("LastSentDate",0));
        e2.put("Status",       kernel.optString("Status", "Unknown Status"));
        e2.put("Subject",      kernel.optString("Subject", "Unknown Subject"));
        return e2;
    }



}
