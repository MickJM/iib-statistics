package maersk.com.mq.metrics.mqmetrics;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFMessageAgent;

import io.micrometer.core.instrument.Tags;
//import maersk.com.mq.metrics.mqmetrics.MQBase.MQPCFConstants;
import maersk.com.mq.metrics.mqmetrics.MQBase.LEVEL;

@Component
public class MQMetricsQueueManager {

	private static final String MQPREFIX = "mq:";

	static Logger log = Logger.getLogger(MQMetricsQueueManager.class);
				
	private boolean onceOnly = true;
	public void setOnceOnly(boolean v) {
		this.onceOnly = v;
	}
	public boolean getOnceOnly() {
		return this.onceOnly;
	}
	
	// taken from connName
	private String hostName;
	public void setHostName(String v) {
		this.hostName = v;
	}
	public String getHostName() { return this.hostName; }
	
	@Value("${ibm.mq.queueManager}")
	private String queueManager;
	public void setQueueManager(String v) {
		this.queueManager = v;
	}
	public String getQueueManager() { return this.queueManager; }
	
	// hostname(port)
	@Value("${ibm.mq.connName}")
	private String connName;
	public void setConnName(String v) {
		this.connName = v;
	}
	public String getConnName() { return this.connName; }
	
	@Value("${ibm.mq.channel}")
	private String channel;
	public void setChannelName(String v) {
		this.channel = v;
	}
	public String getChannelName() { return this.channel; }

	// taken from connName
	private int port;
	public void setPort(int v) {
		this.port = v;
	}
	public int getPort() { return this.port; }

	@Value("${ibm.mq.user}")
	private String userId;
	public void setUserId(String v) {
		this.userId = v;
	}
	public String getUserId() { return this.userId; }

	@Value("${ibm.mq.password}")
	private String password;
	public void setPassword(String v) {
		this.password = v;
	}
	public String getPassword() { return this.password; }
	
	@Value("${ibm.mq.sslCipherSpec}")
	private String cipher;
	
	@Value("${ibm.mq.useSSL:false}")
	private boolean bUseSSL;
	public boolean usingSSL() {
		return this.bUseSSL;
	}
	
	@Value("${ibm.mq.security.truststore:}")
	private String truststore;
	@Value("${ibm.mq.security.truststore-password:}")
	private String truststorepass;
	@Value("${ibm.mq.security.keystore:}")
	private String keystore;
	@Value("${ibm.mq.security.keystore-password:}")
	private String keystorepass;
	
	@Value("${ibm.mq.multiInstance:false}")
	private boolean multiInstance;
	public boolean isMultiInstance() {
		return this.multiInstance;
	}
	
	@Value("${ibm.mq.local:false}")
	private boolean local;
	public boolean isRunningLocal() {
		return this.local;
	}
	
	@Autowired
	public MQBase base;
	
    /*
     *  MAP details for the metrics
     */
    private Map<String,AtomicInteger>runModeMap = new HashMap<String,AtomicInteger>();
	protected static final String runMode = MQPREFIX + "runMode";
	
	/*
	 * Validate connection name and userID
	 */
	private boolean validConnectionName() {
		return (getConnName().equals(""));
	}
	private boolean validateUserId() {
		return (getUserId().equals(""));		
	}
	private boolean validateUserId(String v) {
		boolean ret = false;
		if (getUserId().equals(v)) {
			ret = true;
		}
		return ret;
	}
	
	@Autowired
    private MQQueueManager queManager;
	
	@Autowired
    private MQGetMessageOptions gmo;
	
    //private PCFMessageAgent messageAgent = null;
    
    /*
     * Constructor
     */
	public MQMetricsQueueManager() {
	}
	
	/*
	 * Create an MQQueueManager object
	 */
	@Bean("queueManager")
	public MQQueueManager createQueueManager() throws MQException, MQDataException {

		Hashtable<String, Comparable> env = null;
		
		if (!isRunningLocal()) { 
			
			getEnvironmentVariables();
			if (base.getDebugLevel() == LEVEL.INFO) { log.info("Attempting to connect using a client connection"); }
			
			env = new Hashtable<String, Comparable>();
			env.put(MQConstants.HOST_NAME_PROPERTY, getHostName());
			env.put(MQConstants.CHANNEL_PROPERTY, getChannelName());
			env.put(MQConstants.PORT_PROPERTY, getPort());
			
			/*
			 * 
			 * If a username and password is provided, then use it
			 * ... if CHCKCLNT is set to OPTIONAL or RECDADM
			 * ... RECDADM will use the username and password if provided ... if a password is not provided
			 * ...... then the connection is used like OPTIONAL
			 */		
		
			if (!StringUtils.isEmpty(getUserId())) {
				env.put(MQConstants.USER_ID_PROPERTY, getUserId()); 
			}
			if (!StringUtils.isEmpty(this.password)) {
				env.put(MQConstants.PASSWORD_PROPERTY, getPassword());
			}
			env.put(MQConstants.TRANSPORT_PROPERTY,MQConstants.TRANSPORT_MQSERIES);
	
			if (isMultiInstance()) {
				if (getOnceOnly()) {
					if (base.getDebugLevel() == LEVEL.INFO) { 
						log.info("MQ Metrics is running in multiInstance mode");
					}
				}
			}
			
			if (base.getDebugLevel() == LEVEL.DEBUG) {
				log.debug("Host		: " + getHostName());
				log.debug("Channel	: " + getChannelName());
				log.debug("Port		: " + getPort());
				log.debug("Queue Man	: " + getQueueManager());
				log.debug("User		: " + getUserId());
				log.debug("Password	: **********");
				if (usingSSL()) {
					log.debug("SSL is enabled ....");
				}
			}
			
			// If SSL is enabled (default)
			if (usingSSL()) {
				if (!StringUtils.isEmpty(this.truststore)) {
					System.setProperty("javax.net.ssl.trustStore", this.truststore);
			        System.setProperty("javax.net.ssl.trustStorePassword", this.truststorepass);
			        System.setProperty("javax.net.ssl.trustStoreType","JKS");
			        System.setProperty("com.ibm.mq.cfg.useIBMCipherMappings","false");
				}
				if (!StringUtils.isEmpty(this.keystore)) {
			        System.setProperty("javax.net.ssl.keyStore", this.keystore);
			        System.setProperty("javax.net.ssl.keyStorePassword", this.keystorepass);
			        System.setProperty("javax.net.ssl.keyStoreType","JKS");
				}
				if (!StringUtils.isEmpty(this.cipher)) {
					env.put(MQConstants.SSL_CIPHER_SUITE_PROPERTY, this.cipher);
				}
			
			} else {
				if (base.getDebugLevel() == LEVEL.DEBUG) {
					log.debug("SSL is NOT enabled ....");
				}
			}
			
	        //System.setProperty("javax.net.debug","all");
			if (base.getDebugLevel() == LEVEL.DEBUG) {
				if (!StringUtils.isEmpty(this.truststore)) {
					log.debug("TrustStore       : " + this.truststore);
					log.debug("TrustStore Pass  : ********");
				}
				if (!StringUtils.isEmpty(this.keystore)) {
					log.debug("KeyStore         : " + this.keystore);
					log.debug("KeyStore Pass    : ********");
					log.debug("Cipher Suite     : " + this.cipher);
				}
			}
		} else {
			if (base.getDebugLevel() == LEVEL.DEBUG) {
				log.debug("Attemping to connect using local bindings");
				log.debug("Queue Man	: " + this.queueManager);
			}
			
		}
		
		if (getOnceOnly()) {
			log.info("Attempting to connect to queue manager " + this.queueManager);
			setOnceOnly(false);
		}
		
		/*
		 * Connect to the queue manager 
		 * ... local connection : application connection in local bindings
		 * ... client connection: application connection in client mode 
		 */
		MQQueueManager qmgr = null;
		if (isRunningLocal()) {
			qmgr = new MQQueueManager(this.queueManager);
			log.info("Local connection established ");
		} else {
			qmgr = new MQQueueManager(this.queueManager, env);
		}
		log.info("Connection to queue manager established ");
			
		return qmgr;
	}

	@Bean("setmessageoptions")
	@DependsOn("queuemanager")
	public MQGetMessageOptions setMessageOptions() {

		log.info("Creating get message options");

		MQGetMessageOptions gmo = new MQGetMessageOptions();
		gmo.options = MQConstants.MQGMO_WAIT 
			+ MQConstants.MQGMO_FAIL_IF_QUIESCING 
			+ MQConstants.MQGMO_CONVERT
			+ MQConstants.MQGMO_SYNCPOINT
			+ MQConstants.MQGMO_PROPERTIES_IN_HANDLE;
		/*
		 * if we want to process the MQRFH2 header
		 */
		//if (this.includeRFH2) {
		//		this.gmo.options += MQConstants.MQGMO_PROPERTIES_FORCE_MQRFH2;
		//}
		
		// wait 'x' milli-seconds until we get something
		gmo.waitInterval = 5000;
				//this.waitInterval;
		return gmo;
		
	}
	
	/*
	 * Open the queue
	 */
	@Bean("openqueueforreading")
	@DependsOn("getmessageoptions")
	public MQQueue openQueueForReading(String qName) {
		
		log.info("Opening queue " + qName + " for reading");
		
		MQQueue inQueue = null;
		int openOptions = MQConstants.MQOO_FAIL_IF_QUIESCING 
				+ MQConstants.MQOO_INQUIRE 
				+ MQConstants.MQOO_INPUT_SHARED;

		try {
			inQueue = this.queManager.accessQueue(qName, openOptions);
			
		} catch (MQException e) {
			log.error("Unable to open queue : " + qName);
			log.error("Message : " + e.getMessage() );
			System.exit(1);
		}
			
		return inQueue;
		
	}

	/*
	 * Read messages ...
	 */
	public String readMessages() {
		return "New message";
	}
	
	/*
	 * Establish a PCF agent
	 */	
	public PCFMessageAgent createMessageAgent(MQQueueManager queManager) throws MQDataException {
		
		log.info("Attempting to create a PCFAgent ");
		PCFMessageAgent pcfmsgagent = new PCFMessageAgent(queManager);		
		log.info("PCFAgent created successfully");

		return pcfmsgagent;	
		
	}
	
	/*
	 * Get MQ details from environment variables
	 */
	private void getEnvironmentVariables() {
		
		/*
		 * ALL parameter are passed in the application.yaml file ...
		 *    These values can be overrrided using an application-???.yaml file per environment
		 *    ... or passed in on the command line
		 */
		
		// Split the host and port number from the connName ... host(port)
		if (!validConnectionName()) {
			Pattern pattern = Pattern.compile("^([^()]*)\\(([^()]*)\\)(.*)$");
			Matcher matcher = pattern.matcher(this.connName);	
			if (matcher.matches()) {
				this.hostName = matcher.group(1).trim();
				this.port = Integer.parseInt(matcher.group(2).trim());
			} else {
				log.error("While attempting to connect to a queue manager, the connName is invalid ");
				System.exit(MQPCFConstants.EXIT_ERROR);				
			}
		} else {
			log.error("While attempting to connect to a queue manager, the connName is missing  ");
			System.exit(MQPCFConstants.EXIT_ERROR);
			
		}

		/*
		 * If we dont have a user or a certs are not being used, then we cant connect ... unless we are in local bindings
		 */
		if (validateUserId()) {
			if (!usingSSL()) {
				log.error("Unable to connect to queue manager, credentials are missing and certificates are not being used");
				System.exit(MQPCFConstants.EXIT_ERROR);
			}
		}

		// if no user, forget it ...
		if (this.userId == null) {
			return;
		}

		/*
		 * dont allow mqm user
		 */
		if (!validateUserId()) {
			if ((validateUserId("mqm") || (validateUserId("MQM")))) {
				log.error("The MQ channel USERID must not be running as 'mqm' ");
				System.exit(MQPCFConstants.EXIT_ERROR);
			}
		} else {
			this.userId = null;
			this.password = null;
		}
	
	}
	
	/*
	 * Set 'runmode'
	 */
	private void setRunMode() {

		int mode = MQPCFConstants.MODE_LOCAL;
		if (!isRunningLocal()) {
			mode = MQPCFConstants.MODE_CLIENT;
		}
		
		AtomicInteger rMode = runModeMap.get(runMode);
		if (rMode == null) {
			runModeMap.put(runMode, base.meterRegistry.gauge(runMode, 
					Tags.of("queueManagerName", this.queueManager),
					new AtomicInteger(mode))
					);
		} else {
			rMode.set(mode);
		}
	}

	
	/*
	 * Close the connection to the queue manager
	 */
	public void CloseConnection(MQQueueManager qm, PCFMessageAgent ma) {
		
    	try {
    		if (this.queManager.isConnected()) {
	    		if (base.getDebugLevel() == LEVEL.DEBUG) { log.debug("Closing MQ Connection "); }
    			this.queManager.disconnect();
    		}
    	} catch (Exception e) {
    		// do nothing
    	}
    	
    	try {
	    	if (ma != null) {
	    		if (base.getDebugLevel() == LEVEL.DEBUG) { log.debug("Closing PCF agent "); }
	        	ma.disconnect();
	    	}
    	} catch (Exception e) {
    		// do nothing
    	}
	}
	
}
