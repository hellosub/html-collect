package html.collect;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.parser.HtmlParseData;
import edu.uci.ics.crawler4j.url.WebURL;

public class BasicCrawler extends WebCrawler {

    private static final Pattern IMAGE_EXTENSIONS = Pattern.compile(".*\\.(bmp|gif|jpg|png)$");

    private final AtomicInteger numSeenImages;

    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    
    private static String[] strShouldVisit;
    
    private static String strTargetUrl;
    
    /**
     * Creates a new crawler instance.
     *
     * @param numSeenImages This is just an example to demonstrate how you can pass objects to crawlers. In this
     * example, we pass an AtomicInteger to all crawlers and they increment it whenever they see a url which points
     * to an image.
     */
    public BasicCrawler(AtomicInteger numSeenImages) {
        this.numSeenImages = numSeenImages;
        
        String strAppConfigPath = Thread.currentThread().getContextClassLoader().getResource("application.properties").getFile();
        System.out.println("strAppConfigPath = " + strAppConfigPath);
        
        Properties propsApp = new Properties();
        try {
			propsApp.load(new FileInputStream(strAppConfigPath));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

        strTargetUrl = propsApp.get("targetUrl").toString();
        System.out.println("strTargetUrl = "  + strTargetUrl);

        strShouldVisit = (String[])(propsApp.get("shouldVisit").toString().split("#")).clone();
        System.out.println("strShouldVisit = " + Arrays.toString(strShouldVisit));
    }

    /**
     * You should implement this function to specify whether the given url
     * should be crawled or not (based on your crawling logic).
     */
    @Override
    public boolean shouldVisit(Page referringPage, WebURL url) {
        String href = url.getURL().toLowerCase();
        logger.debug("***********" + href);
        
        // Ignore the url if it has an extension that matches our defined set of image extensions.
        if (IMAGE_EXTENSIONS.matcher(href).matches()) {
            numSeenImages.incrementAndGet();
            return false;
        }

        if (href.startsWith(strTargetUrl)) {
            HttpGet request = new HttpGet(href);

            try (CloseableHttpResponse response = httpClient.execute(request)) {

                // Get HttpResponse Status
                HttpEntity entity = response.getEntity();
                Header headers = entity.getContentType();

                if (entity != null) {
                    // return it as a String
                    String result = EntityUtils.toString(entity);

                    // Tried to compare HTML String with the previous one, but the resource id was continuously changed even though they are the same page.
                    // Therefore, comparing 2 HTML pages didn't work.
                    // writeResultToFile(href, "result.txt", result);
                    
                    // Send mail if html contains the String like "Register"
                    if (ShouldNotifyUser(result)) {
                    	sendEmail(href);
                    }
                }

            } catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        for (int i = 0; i < strShouldVisit.length; i++) {
        	 if (href.startsWith(strShouldVisit[i])) {
        		 return true;
        	 }
        }
        return false;
    }
    
    @SuppressWarnings("unused")
    private void writeResultToFile(String strUrl, String strFileName, String result) {
    	File file;
    	FileOutputStream fosWrite;
    	FileInputStream fisRead;
    	String strPrevResult = "";
    	int count = 0;
    	
    	try {
    		file = new File(strFileName);
    		if (!file.exists()) {
    			file.createNewFile();
    		} else {
	    		fisRead = new FileInputStream(file);
		    	DataInputStream reader = new DataInputStream(fisRead);
		    	count = reader.available(); 
		    	if (count > 0) {
		    		byte[] byteRead = new byte[count];
		    		reader.read(byteRead);
		    		strPrevResult = new String(byteRead);
		    	}
		    	reader.close();
    		}
    		
	    	if (result.equals(strPrevResult)) {
	    		logger.debug("******** No Change");
	    	} else {
	    		logger.debug("******** Changed");
		    	fosWrite = new FileOutputStream("result_new.txt");
		    	
				DataOutputStream outStream = new DataOutputStream(new BufferedOutputStream(fosWrite));
				byte[] byteWrite = result.getBytes();
				outStream.write(byteWrite);
				outStream.close();
	    	}
    	} catch (IOException e) {
    		StringWriter sw = new StringWriter();
    		e.printStackTrace(new PrintWriter(sw));
    		logger.debug("******** IOException : " + sw);
    	}     	
    }
    
    private boolean ShouldNotifyUser (String strContents) {
    	boolean bResult = false;
    	
    	if (strContents.contains("Sold Out")) {
    		logger.debug("******** Sold Out");
    	} else if (strContents.contains("Register")) {
    		logger.debug("******** Register");
    		bResult = true;
    	}
    	
    	return bResult;
    }
    
    
    /**
     * This function is called when a page is fetched and ready to be processed
     * by your program.
     */
    @Override
    public void visit(Page page) {
        int docid = page.getWebURL().getDocid();
        String url = page.getWebURL().getURL();
        String domain = page.getWebURL().getDomain();
        String path = page.getWebURL().getPath();
        String subDomain = page.getWebURL().getSubDomain();
        String parentUrl = page.getWebURL().getParentUrl();
        String anchor = page.getWebURL().getAnchor();

        logger.debug("Docid: {}", docid);
        logger.info("URL: {}", url);
        logger.debug("Domain: '{}'", domain);
        logger.debug("Sub-domain: '{}'", subDomain);
        logger.debug("Path: '{}'", path);
        logger.debug("Parent page: {}", parentUrl);
        logger.debug("Anchor text: {}", anchor);

        if (page.getParseData() instanceof HtmlParseData) {
            HtmlParseData htmlParseData = (HtmlParseData) page.getParseData();
            String text = htmlParseData.getText();
            String html = htmlParseData.getHtml();
            Set<WebURL> links = htmlParseData.getOutgoingUrls();

            logger.debug("Text length: {}", text.length());
            logger.debug("Html length: {}", html.length());
            logger.debug("Number of outgoing links: {}", links.size());
        }

        Header[] responseHeaders = page.getFetchResponseHeaders();
        if (responseHeaders != null) {
            logger.debug("Response headers:");
            for (Header header : responseHeaders) {
                logger.debug("\t{}: {}", header.getName(), header.getValue());
            }
        }

        logger.debug("=============");
    }
    
    private void sendEmail (String strUrl) {
		String host;
    	String un; 
    	String pw; 
    	int port=587;
    	String recipient;
    	String subject;
    	String body; 

        String strAppConfigPath = Thread.currentThread().getContextClassLoader().getResource("application.properties").getFile();
        System.out.println("strAppConfigPath = " + strAppConfigPath);
        
        Properties propsApp = new Properties();
        try {
			propsApp.load(new FileInputStream(strAppConfigPath));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

        host = propsApp.get("senderEmailHost").toString();
        System.out.println("host = "  + host);

        un = propsApp.get("senderEmailAddr").toString();
        System.out.println("un = "  + un);

        pw = propsApp.get("senderEmailPasswd").toString();
        System.out.println("pw = "  + pw);

        recipient = propsApp.get("receiverEmailAddr").toString();
        System.out.println("recipient = "  + recipient);

    	SimpleDateFormat formatter= new SimpleDateFormat("HH:mm dd/MM/yyyy");
    	Date date = new Date(System.currentTimeMillis());
    	subject = "Web site notification at " + formatter.format(date);
    	System.out.println(subject);
    	
    	body = un + " sent a mail." + "\nPlease check the web site as below.\n" + strUrl;
    	System.out.println("body = "  + body);
    	
    	Properties props = System.getProperties();
    	props.put("mail.smtp.host", host); 
    	props.put("mail.smtp.port", port); 
    	props.put("mail.smtp.auth", "true"); 
    	
    	Session session = Session.getDefaultInstance(props, new javax.mail.Authenticator() { 
    		protected javax.mail.PasswordAuthentication getPasswordAuthentication() { 
    			return new javax.mail.PasswordAuthentication(un, pw); 
    		} 
    	});
    	session.setDebug(true); 
 	
    	try {
	    	Message mimeMessage = new MimeMessage(session); 
	    	mimeMessage.setFrom(new InternetAddress("hellobull@naver.com"));
	    	mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
	    	mimeMessage.setSubject(subject);
	    	mimeMessage.setText(body);
	    	
	    	Transport t = session.getTransport("smtp");
			try {
				t.connect();
				t.sendMessage(mimeMessage, mimeMessage.getAllRecipients());
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
			  t.close();
			}
			System.out.println("Success");
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
    }
}
