package org.mule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Properties;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.json.JSONObject;
import org.mule.api.Constants;
import org.mule.api.Scriptable;
import org.mule.util.AddAuthorizationHeaderFilter;
import org.mule.util.Zipper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Test class defining several methods that avoid duplicating code for real integrating tests.
 * @author Miroslav Rusin
 *
 */
public abstract class AbstractTemplateTest {

	private static final String ALIAS_PASSWORD = "secret";
	public static final String HTTP_PROXY_URL = "http://localhost:8081";
	private static final String HTTP_REQUEST = "http:request";
	private static final String HTTP_REQUEST_CONFIG = "http:request-config";
	private static final String HTTP_LISTENER = "http:listener";
	private static final String HTTP_LISTENER_CONFIG = "http:listener-config";
	
	protected static final String KEY_STORE_PASSWORD = "keyStorePassword";
	protected static final String KEYSTORE_NAME = "mule.jks";
	protected static final String EXPORT_FOLDER = "export";
	private static final String INPUT_FOLDER = "input";
	protected String IMPLEMENTATION_URI = "implementation.uri";
	
	protected static final String TMP_FOLDER = "tmp";
	protected final HashMap<String, String> input = new HashMap<String, String>();
	protected String apiNameId;
	protected String apiVersionId;
	private static String PASSWORD;
	private static String USER;
	protected static final String TEST_RESOURCES_FOLDER = "./src/test/resources";
	private String access_token;
	private static String PROXY_URI;
	private static String LOGIN_URI;
	private static String DOWNLOAD_PROXY_URI;
	private static final String HTTPS_PROTOCOL = "https";	
	protected String MESSAGE = "HTTPS connection established";
	protected String proxyAppZip;
	protected static String GATEWAY_APPS_FOLDER;
	protected static String GATEWAY_VERSION;
	protected static String GATEWAY_DOWNLOAD_VERSION;
	protected static Logger LOGGER = LogManager.getLogger(AbstractTemplateTest.class);
	protected final String PROXY_URI_KEY = "proxy.uri";
	protected final String PROXY_HOST_KEY = "implementation.host";
	protected final String PROXY_PORT_KEY = "implementation.port";
	protected final String PROXY_PATH_KEY = "implementation.path";
	protected boolean TEST_WITH_GATEWAY;
	
	/**
	 * prepares test data:
	 * 1. fills in input parameter map
	 * 2. reads test data from test.properties file
	 * 3. authenticates to Anypoint Platform and downloads a proxy app 
	 * @throws IOException thrown by FileUtils.deleteDirectory or downloadProxy method
	 */
	public void prepare() throws IOException{
		input.put(Constants.KEY_PASSWORD, KEY_STORE_PASSWORD);
		input.put(Constants.KEY_FILE, TEST_RESOURCES_FOLDER + File.separator + KEYSTORE_NAME);
		input.put(Constants.KEY_ALIAS_PASSWORD, ALIAS_PASSWORD);
		input.put(Constants.EXPORT, TEST_RESOURCES_FOLDER + File.separator + EXPORT_FOLDER);
				
		FileUtils.deleteDirectory(new File(TEST_RESOURCES_FOLDER + File.separator + EXPORT_FOLDER));
		FileUtils.deleteDirectory(new File(TEST_RESOURCES_FOLDER + File.separator + TMP_FOLDER));
		

        final Properties props = new Properties();
    	try {
    	props.load(new FileInputStream(TEST_RESOURCES_FOLDER + File.separator + "test.properties"));
    	} catch (final Exception e) {
    		LOGGER.info("Error occured while reading test.properties" + e);
    	} 
    	
    	USER = props.getProperty("username");
    	PASSWORD = props.getProperty("password");
    	PROXY_URI = props.getProperty("proxyUri");
    	LOGIN_URI = props.getProperty("loginUri");
    	DOWNLOAD_PROXY_URI = props.getProperty("downloadProxyUri");
    	GATEWAY_VERSION = props.getProperty("gatewayVersion");
    	GATEWAY_DOWNLOAD_VERSION = props.getProperty("gatewayDownloadVersion");
    	TEST_WITH_GATEWAY = Boolean.valueOf(props.getProperty("testWithGateway"));
    	LOGGER.info("Signing in to Anypoint Platform with a user:" + USER);
    	final ResteasyClient client = new ResteasyClientBuilder().build();
        final ResteasyWebTarget target = client.target(PROXY_URI + LOGIN_URI);
        final Response response = target.request().post(Entity.entity("{ \"username\": \"" + USER + "\", \"password\": \"" + PASSWORD + "\" }", "application/json"));
        
        //Read output in string format        
        final JSONObject jso = new JSONObject(response.readEntity(String.class));
        response.close();
        access_token = jso.getString("access_token");
        client.register(new AddAuthorizationHeaderFilter(access_token));
        LOGGER.info("Downloading proxy app: Api Name Id " + apiNameId + " Api Version Id " + apiVersionId);
        downloadProxy(client, apiNameId, apiVersionId);
        
        input.put(Constants.PROXY, TEST_RESOURCES_FOLDER + File.separator + INPUT_FOLDER + File.separator + proxyAppZip);
        LOGGER.info("Proxy app saved to: " + TEST_RESOURCES_FOLDER + File.separator + INPUT_FOLDER + File.separator + proxyAppZip);
                 
	}
	
	
	protected void prepareToConnectToAP(Properties props, String apiName, String apiVersionName){
		input.put(Constants.USER, props.getProperty("username"));
		input.put(Constants.PASSWORD, props.getProperty("password"));
		input.put(Constants.KEY_FILE, TEST_RESOURCES_FOLDER + File.separator + KEYSTORE_NAME);
		input.put(Constants.KEY_PASSWORD, KEY_STORE_PASSWORD);
		input.put(Constants.KEY_ALIAS_PASSWORD, ALIAS_PASSWORD);
		input.put(Constants.EXPORT, TEST_RESOURCES_FOLDER + File.separator + EXPORT_FOLDER);
		input.put(Constants.API_NAME, props.getProperty(apiName));
		input.put(Constants.API_VERSION_NAME, props.getProperty(apiVersionName));
		
	}

	/**
	 * downloads a proxy app in ZIP format from Anypoint Platform
	 * @param client ResteasyClient instance used to make REST calls
	 * @param apiNameId defines which API proxy to download 
	 * @param apiVersionId defines which API version proxy to download
	 * @throws FileNotFoundException thrown during saving a file to a disk
	 * @throws IOException if I/O error occurs
	 */
	private void downloadProxy(final ResteasyClient client, String apiNameId, String apiVersionId)
			throws FileNotFoundException, IOException {
		final ResteasyWebTarget target = client.target(PROXY_URI + DOWNLOAD_PROXY_URI + "apis/" + apiNameId + "/versions/" + apiVersionId + "/proxy?gatewayVersion=" + GATEWAY_DOWNLOAD_VERSION);		
        final Response response = target.request().get();
        
        final InputStream inputStream = response.readEntity(InputStream.class);
        
        if (response.getHeaderString("content-disposition") == null) // No API was found
        	throw new IllegalArgumentException("There is no API with API Name Id: " + apiNameId + " and API Version Id: " + apiVersionId);
        
        proxyAppZip = response.getHeaderString("content-disposition").substring(response.getHeaderString("content-disposition").indexOf("filename=") + "filename=".length()).replace("\"", "");
        new File(TEST_RESOURCES_FOLDER + File.separator + INPUT_FOLDER).mkdirs();
                
        saveToFile(inputStream, TEST_RESOURCES_FOLDER + File.separator + INPUT_FOLDER + File.separator + proxyAppZip);
        response.close();
	}

	
	/**
	 * reads from inputStream and writes to a file using path parameter
	 * @param inputStream inputStream to read data from
	 * @param path defines a location to store a file to
	 * @throws FileNotFoundException thrown by FileOutputStream 
	 * @throws IOException if I/O error occurs
	 */
	private void saveToFile(final InputStream inputStream, String path) throws FileNotFoundException, IOException {		
        final OutputStream outputStream = new FileOutputStream(path);
        final byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        outputStream.close();		
	}
	
	/**
	 * main testing method shared by all end point proxy tests for HTTP outbound modifications
	 * @throws IOException thrown if I/O error occurs
	 * @throws ParserConfigurationException thrown by readXML method
	 * @throws SAXException thrown by readXML method
	 * @throws InterruptedException thrown by Thread.sleep
	 */
	public void testOutboundProcessing(Scriptable script) throws IOException, ParserConfigurationException, SAXException, InterruptedException{
		LOGGER.info("Testing outbound modified proxy application...");
		try {
			script.process(input);
		} catch (final Exception e) {
			e.printStackTrace();
		}
		
		final File file = new File(input.get(Constants.EXPORT) + File.separator + proxyAppZip);
		
		assertTrue("Modified zip file should be created", file.exists());
		final String tmpFolder = TEST_RESOURCES_FOLDER + File.separator + TMP_FOLDER + File.separator;
		if (TEST_WITH_GATEWAY){
			FileUtils.copyFile(file, new File(GATEWAY_APPS_FOLDER + file.getName()));
			// need to wait till the API gateway starts a proxy app
			Thread.sleep(15000);
		}
		else{
			LOGGER.info("Skipping testing with Gateway..");
		}
		
		Zipper.unZip(file, TEST_RESOURCES_FOLDER + File.separator + TMP_FOLDER);
		
		final Document proxy = readXML(tmpFolder + "proxy.xml");
		final Element httpRequestConfig = (Element) proxy.getElementsByTagName(HTTP_REQUEST_CONFIG).item(0);
		assertNotNull("Proxy file should contain HTTPS request config element", httpRequestConfig);		
		assertNotNull("Proxy file should contain HTTPS connector element", httpRequestConfig.getAttribute("protocol").equals(HTTPS_PROTOCOL));		
		
		final Element httpsOutbound = (Element) proxy.getElementsByTagName(HTTP_REQUEST).item(0);
		
		assertNotNull("Proxy file should contain HTTPS outbound element", httpsOutbound);		
		
		assertTrue("Keystore should be packaged", new File(tmpFolder + KEYSTORE_NAME).exists());
									       
	}

	/**
	 * main testing method shared by all end point proxy tests for HTTP inbound modifications
	 * @throws IOException thrown if I/O error occurs
	 * @throws ParserConfigurationException thrown by readXML method
	 * @throws SAXException thrown by readXML method
	 * @throws InterruptedException thrown by Thread.sleep
	 */
	public void testInboundProcessing(Scriptable script) throws IOException, ParserConfigurationException, SAXException, InterruptedException{
		LOGGER.info("Testing  inbound modified proxy application...");
		try {
			script.process(input);
		} catch (final Exception e) {
			e.printStackTrace();
		}
		
		final File file = new File(input.get(Constants.EXPORT) + File.separator + proxyAppZip);
		
		assertTrue("Modified zip file should be created", file.exists());
		final String tmpFolder = TEST_RESOURCES_FOLDER + File.separator + TMP_FOLDER + File.separator;
		if (TEST_WITH_GATEWAY){
			FileUtils.copyFile(file, new File(GATEWAY_APPS_FOLDER + file.getName()));
			// need to wait till the API gateway starts a proxy app
			Thread.sleep(15000);
		}
		else{
			LOGGER.info("Skipping testing with Gateway..");
		}
		
		Zipper.unZip(file, TEST_RESOURCES_FOLDER + File.separator + TMP_FOLDER);
		
		final Document proxy = readXML(tmpFolder + "proxy.xml");
		final Element httpRequestConfig = (Element) proxy.getElementsByTagName(HTTP_LISTENER_CONFIG).item(0);
		assertNotNull("Proxy file should contain HTTPS listener config element", httpRequestConfig);		
		assertNotNull("Proxy file should contain HTTPS connector element", httpRequestConfig.getAttribute("protocol").equals(HTTPS_PROTOCOL));		
		
		final Element httpsOutbound = (Element) proxy.getElementsByTagName(HTTP_LISTENER).item(0);
		
		assertNotNull("Proxy file should contain HTTPS inbound element", httpsOutbound);		
		
		assertTrue("Keystore should be packaged", new File(tmpFolder + KEYSTORE_NAME).exists());
									       
	}
	/**
	 * reads an XML file and returns as org.w3c.dom.Document instance 
	 * @param filepath path to XML file
	 * @return Document representing XML file
	 * @throws ParserConfigurationException thrown by DocumentBuilderFactory.newDocumentBuilder
	 * @throws SAXException thrown by DocumentBuilder.parse
	 * @throws IOException thrown by DocumentBuilder.parse
	 */
	private Document readXML(String filepath) throws ParserConfigurationException, SAXException, IOException{
		final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		return docBuilder.parse(filepath);
	}
	
	/**
	 * cleans test data, deletes all created directories
	 * @throws IOException thrown by FileUtils.deleteDirectory
	 */
	protected void tearDown() throws IOException{
		FileUtils.deleteDirectory(new File(TEST_RESOURCES_FOLDER + File.separator + INPUT_FOLDER)); 
		FileUtils.deleteDirectory(new File(TEST_RESOURCES_FOLDER + File.separator + EXPORT_FOLDER));
		FileUtils.deleteDirectory(new File(TEST_RESOURCES_FOLDER + File.separator + TMP_FOLDER));
		// undeploy a proxy
		if (TEST_WITH_GATEWAY){
			new File(GATEWAY_APPS_FOLDER + proxyAppZip.substring(0, proxyAppZip.length() - 4) + "-anchor.txt").delete();    	
	        try {
	        	LOGGER.info("Waiting for Gateway to undeploy the proxy...");
				Thread.sleep(10000);
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
	        try{
	        	FileUtils.deleteDirectory(new File(GATEWAY_APPS_FOLDER + proxyAppZip.substring(0, proxyAppZip.length() - 4)));
	        }
	        catch (final IOException io){
	        	io.printStackTrace();
	        }
        }
	}
	
	/**
	 * deploy a HTTPS endpoint app to API gateway
	 * @throws IOException  thrown by FileUtils.copyFile
	 */
	protected void deployHTTPS() throws IOException{
		if (TEST_WITH_GATEWAY){
			LOGGER.info("Deploying HTTPS endpoint to a local gateway: " + GATEWAY_APPS_FOLDER);
	    	new File(GATEWAY_APPS_FOLDER + "https-test-anchor.txt").delete();
	    	FileUtils.copyFile(new File(TEST_RESOURCES_FOLDER + File.separator + "https-test.zip"), new File(GATEWAY_APPS_FOLDER + "https-test.zip"));
	    	try {
				Thread.sleep(15000);
			} catch (final InterruptedException e) {			
				e.printStackTrace();
			}
		}
		else{
			LOGGER.info("Skipping deploying HTTPS endpoint to Gateway..");
		}
	}
	
	/**
	 * deploys HTTPS WSDL-based web service to API gateway
	 * @throws IOException thrown by FileUtils.copyFile
	 */
	protected void deployHTTPSforWSDL() throws IOException{
		if (TEST_WITH_GATEWAY){
	    	new File(GATEWAY_APPS_FOLDER + "xml-only-soap-web-service-anchor.txt").delete();
	    	FileUtils.copyFile(new File(TEST_RESOURCES_FOLDER + File.separator + "xml-only-soap-web-service.zip"), new File(GATEWAY_APPS_FOLDER + "xml-only-soap-web-service.zip"));
	    	try {
				Thread.sleep(20000);
			} catch (final InterruptedException e) {			
				e.printStackTrace();
			}
		}
    	else{
			LOGGER.info("Skipping deploying HTTPS endpoint to Gateway..");
		}		
	}
		
	/**
	 * makes a HTTP GET request using the proxy URI parameter
	 * @return HTTP response body
	 * @throws IllegalArgumentException thrown by ResteasyClient.target
	 * @throws NullPointerException thrown by ResteasyClient.target
	 * @throws IOException thrown by ResteasyClient.target
	 * @throws InterruptedException caused by Thread.sleep
	 */
	protected void makeTestRequest(String host) throws IllegalArgumentException, NullPointerException, IOException, InterruptedException{	
		if (TEST_WITH_GATEWAY){
			Thread.sleep(10000);
			final ResteasyClient client = new ResteasyClientBuilder().build();			
			final ResteasyWebTarget target = client.target(host);
	        LOGGER.info("Making HTTP call: " + host);        
	        final int response =  target.request().get().getStatus();
	        LOGGER.info("HTTP response: " + response);
			assertEquals("HTTP request should be successful", 200, response);
		}
		else{
			LOGGER.info("Skipping testing a call to the proxy with Gateway..");
		}
	}

	/**
	 * makes a HTTP GET request using the proxy URI parameter
	 * @return HTTP response body
	 * @throws IllegalArgumentException thrown by ResteasyClient.target
	 * @throws NullPointerException thrown by ResteasyClient.target
	 * @throws IOException thrown by ResteasyClient.target
	 * @throws InterruptedException caused by Thread.sleep
	 */
	protected void makeTestRequest(String host, String path, String body) throws IllegalArgumentException, NullPointerException, IOException, InterruptedException{				
		if (TEST_WITH_GATEWAY){
			Thread.sleep(5000);
			final ResteasyClient client = new ResteasyClientBuilder().build();
	        final ResteasyWebTarget target = client.target(host + path);
	        LOGGER.info("Making HTTP call: " + host + path);        
	        final Response response = target.request().post(Entity.entity(body, "text/plain"));
	        LOGGER.info("HTTP response: " + response.getStatus());
			assertEquals("HTTP request should be successful", 200, response.getStatus());
		}
		else{
			LOGGER.info("Skipping testing with Gateway..");
		}
	}
	
	protected Properties initGatewayParams() {
		final Properties props = new Properties();
    	try {
    		props.load(new FileInputStream(TEST_RESOURCES_FOLDER + File.separator + "test.properties"));
    	} catch (final Exception e) {
    		LOGGER.info("Error occured while reading test.properties" + e);
    	} 
    	TEST_WITH_GATEWAY = Boolean.valueOf(props.getProperty("testWithGateway"));
    	GATEWAY_APPS_FOLDER = props.getProperty("gatewayAppDir");
    	GATEWAY_VERSION = props.getProperty("gatewayVersion");
    	GATEWAY_DOWNLOAD_VERSION = props.getProperty("gatewayDownloadVersion");
    	
		return props;
	}
		
	
	
    
}