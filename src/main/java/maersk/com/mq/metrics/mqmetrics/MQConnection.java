package maersk.com.mq.metrics.mqmetrics;

/*
 * Copyright 2019
 * Mick Moriarty - Maersk
 *
 * Connect to a queue manager
 * 
 * 22/10/2019 - Capture the return code when the queue manager throws an error so multi-instance queue
 *              managers can be checked
 */

import java.io.IOException;
import java.text.ParseException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.ibm.mq.headers.pcf.PCFException;

import java.util.concurrent.Executor;

import maersk.com.mq.pcf.queuemanager.pcfQueueManager;
import maersk.com.mq.pcf.listener.pcfListener;
import maersk.com.mq.json.controller.JSONController;
import maersk.com.mq.metrics.mqmetrics.MQBase.LEVEL;

@Component
@EnableAsync
public class MQConnection {

	protected static final String MQPREFIX = "mq:";

	static Logger log = Logger.getLogger(MQConnection.class);

	@Value("${application.debug:false}")
    protected boolean _debug;
	
	@Value("${application.debugLevel:NONE}")
	protected String _debugLevel;
    
	@Value("${application.save.metrics.required:false}")
    private boolean summaryRequired;

	@Value("${ibm.mq.multiInstance:false}")
	private boolean multiInstance;
	public boolean isMultiInstance() {
		return this.multiInstance;
	}
	
	@Value("${ibm.mq.queueManager}")
	private String queueManager;
		
	@Value("${ibm.mq.local:false}")
	private boolean local;
	public boolean isRunningLocal() {
		return this.local;
	}
	
	@Value("${ibm.mq.keepMetricsWhenQueueManagerIsDown:false}")
	private boolean keepMetricsWhenQueueManagerIsDown;
	
	//
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
	
    //MQ reset
    @Value("${ibm.mq.event.delayInMilliSeconds:10000}")
    private long resetIterations;

    //@Autowired
    //private MQQueueManager queManager;
    //= null;
    //private PCFMessageAgent messageAgent = null;
    //private PCFAgent agent = null;
    
    // MAP details for the metrics
    //private Map<String,AtomicInteger>runModeMap = new HashMap<String,AtomicInteger>();
	protected static final String runMode = MQPREFIX + "runMode";

	@Autowired
	public MQBase base;
	
    //
    @Autowired
    public pcfQueueManager pcfQueueManager;
    @Autowired
    public pcfListener pcfListener;

    @Autowired
    public MQMetricsQueueManager mqMetricsQueueManager;
    
    @Bean
    public pcfQueueManager QueueManager() {
    	log.info("pcf Queue manager object created");
    	return new pcfQueueManager();
    }
    @Bean
    public pcfListener Listener() {
    	log.info("Listener object created");
    	return new pcfListener();
    }
    
    @Bean
    public MQMetricsQueueManager CreateMetricsQueueManager() {
    	log.info("Queue manager object created");
    	return new MQMetricsQueueManager();
    }
    
    // 
    @Bean
    public JSONController JSONController() {
    	return new JSONController();
    }
    @Autowired
    private JSONController jsonapi;
        
	// Constructor
	public MQConnection() {
	}
	
	@PostConstruct
	private void setProperties() {
		
		if (!(base.getDebugLevel() == LEVEL.NONE)) { log.info("MQConnection: Object created"); }
		setDebugLevel();
	
		try {
			processMessages();

		} catch (MQException | MQDataException e) {
			log.error("Error processing messages : " + e.getMessage());
		}
	}
	
	private void processMessages() throws MQException, MQDataException {
		
		log.info("Processing messages ...");
		connectToQueueManager();

		readMessages();
		
		disconnect();
		
	}
	
	private void readMessages() {

		
		if (this.mqMetricsQueueManager != null) {
			String msg = this.mqMetricsQueueManager.readMessages();
			log.info("Reading messages ...." + msg);
		}
	}
	
	/*
	@Scheduled(fixedDelayString="${ibm.mq.event.delayInMilliSeconds}")
    public void scheduler() {
	
		resetIterations();

		try {
			if (this.messageAgent != null) {
				getMetrics();
				
			} else {
				conectToQueueManager();
				
			}
			
		} catch (PCFException p) {
			if (base.getDebugLevel() == LEVEL.WARN
					|| base.getDebugLevel() == LEVEL.TRACE 
					|| base.getDebugLevel() == LEVEL.ERROR
					|| base.getDebugLevel() == LEVEL.DEBUG) { 
				log.error("PCFException " + p.getMessage());
			}
			if (base.getDebugLevel() == LEVEL.WARN
				|| base.getDebugLevel() == LEVEL.TRACE 
				|| base.getDebugLevel() == LEVEL.ERROR
				|| base.getDebugLevel() == LEVEL.DEBUG) { 
					log.warn("PCFException: ReasonCode " + p.getReason());
			}
			if (base.getDebugLevel() == LEVEL.TRACE) { p.printStackTrace(); }
			closeQMConnection(p.getReason());
			queueManagerIsNotRunning(p.getReason());
			
		} catch (MQException m) {
			if (base.getDebugLevel() == LEVEL.WARN
					|| base.getDebugLevel() == LEVEL.TRACE 
					|| base.getDebugLevel() == LEVEL.ERROR
					|| base.getDebugLevel() == LEVEL.DEBUG) { 
				log.error("MQException " + m.getMessage());
			}
			if (base.getDebugLevel() == LEVEL.TRACE) { m.printStackTrace(); }
			closeQMConnection(m.getReason());
			queueManagerIsNotRunning(m.getReason());
			this.messageAgent = null;
			
		} catch (IOException i) {
			if (base.getDebugLevel() == LEVEL.WARN
					|| base.getDebugLevel() == LEVEL.TRACE 
					|| base.getDebugLevel() == LEVEL.ERROR
					|| base.getDebugLevel() == LEVEL.DEBUG) { 
				log.error("IOException " + i.getMessage());
			}
			if (base.getDebugLevel() == LEVEL.TRACE) { i.printStackTrace(); }
			closeQMConnection();
			queueManagerIsNotRunning(MQPCFConstants.PCF_INIT_VALUE);
			
		} catch (Exception e) {
			if (base.getDebugLevel() == LEVEL.WARN
					|| base.getDebugLevel() == LEVEL.TRACE 
					|| base.getDebugLevel() == LEVEL.ERROR
					|| base.getDebugLevel() == LEVEL.DEBUG) { 
				log.error("Exception " + e.getMessage());
			}
			if (base.getDebugLevel() == LEVEL.TRACE) { e.printStackTrace(); }
			closeQMConnection();
			queueManagerIsNotRunning(MQPCFConstants.PCF_INIT_VALUE);
		}
    }
    */
	
	/*
	 * Set debug level
	 */
	private void setDebugLevel() {
		if (this._debug) {
			if (this._debugLevel.equals("NONE")) {
					this._debugLevel = "DEBUG";
			}
		}
		if (!this._debug) {
			this._debugLevel = "NONE";
		}
		base.setDebugLevel(this._debugLevel);
		
	}
	
	
	/*
	 * Set the pcfAgent in each class
	 */
	private void setPCFParameters() {
	//	this.pcfQueueManager.setMessageAgent(this.messageAgent);
	//	this.pcfListener.setMessageAgent(this.messageAgent);

		this.base.setDebugLevel(this._debugLevel);
	}

	/*
	 * Connect to the queue manager
	 */
	private void connectToQueueManager() throws MQException, MQDataException {
		if (!(base.getDebugLevel() == LEVEL.NONE)) { log.error("No MQ queue manager object"); }

		createQueueManagerConnection();
		//setPCFParameters();		
	}
	
	/*
	 * Create an MQ connection to the queue manager
	 * ... once connected, create a messageAgent for PCF commands
	 */
	public void createQueueManagerConnection() throws MQException, MQDataException {
		
	//	this.queManager = this.mqMetricsQueueManager.createQueueManager();
	//	this.messageAgent = this.mqMetricsQueueManager.createMessageAgent(this.queManager);
	}
		
	/*
	 * When the queue manager isn't running, send back a status of inactive 
	 */
	private void queueManagerIsNotRunning(int status) {

		if (this.pcfQueueManager != null) {
			this.pcfQueueManager.notRunning(this.queueManager, isMultiInstance(), status);
		}

		/*
		 * Clear the metrics, but ...
		 * ... dont clear them if the queue manager is down 
		 */
		if (!keepMetricsWhenQueueManagerIsDown) {
			if (this.pcfListener != null) {
				this.pcfListener.resetMetrics();
			}
		}
	}

	/*
	 * Reset iterations value between capturing performance metrics
	 */
	private void resetIterations() {
		
		this.pcfQueueManager.ResetIteration(this.queueManager);
			
	}
	
	/*
	 * Get metrics
	 */
	private void getMetrics() throws PCFException, MQException, 
			IOException, MQDataException, ParseException {
		
		checkQueueManagerCluster();
		updateQMMetrics();
		updateListenerMetrics();
		updateQueueMetrics();
		updateChannelMetrics();
	}
	
	/*
	 * Check if the queue manager belongs to a cluster ...
	 */
	private void checkQueueManagerCluster() {

		this.pcfQueueManager.checkQueueManagerCluster();
				
	}
	
	/*
	 * Update the queue manager metrics 
	 */
	private void updateQMMetrics() throws PCFException, 
		MQException, 
		IOException, 
		MQDataException {

		this.pcfQueueManager.updateQMMetrics();
		
	}

	/*
	 * Update the queue manager listener metrics
	 * 
	 */
	private void updateListenerMetrics() throws MQException, 
		IOException, 
		MQDataException {

		this.pcfListener.UpdateListenerMetrics();
			
	}
		
	/*
	 * Update the Channel Metrics
	 * 
	 */
	private void updateChannelMetrics() throws MQException, IOException, 
		PCFException, 
		MQDataException, 
		ParseException {
				
	}

	/*
	 * Update queue metrics
	 * 
	 */
	private void updateQueueMetrics() throws MQException, 
		IOException, 
		MQDataException {

	}
	
	/*
	 * Disconnect cleanly from the queue manager
	 */
    @PreDestroy
    public void disconnect() {
    	closeQMConnection();
    }
    
    /*
     * Disconnect, showing the reason
     */
    public void closeQMConnection(int reasonCode) {

		log.info("Disconnected from the queue manager"); 
		log.info("Reason code: " + reasonCode);
    	this.mqMetricsQueueManager.CloseConnection(null,null);
   // 	this.queManager = null;
	//	this.messageAgent = null;
		
    }
	        
    public void closeQMConnection() {

		log.info("Disconnected from the queue manager"); 
    	this.mqMetricsQueueManager.CloseConnection(null,null);
    //	this.queManager = null;
	//	this.messageAgent = null;
		
    }
    
}


