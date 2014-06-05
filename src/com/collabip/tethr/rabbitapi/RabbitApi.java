package com.collabip.tethr.rabbitapi;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.util.ServiceUtils;

import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.util.Logger;
import org.jitsi.videobridge.Videobridge;
import org.jitsi.videobridge.Conference;
import org.osgi.framework.BundleContext;

import com.rabbitmq.client.*;

public class RabbitApi extends Thread{

	/*
	 * Termination sync object for this thread
	 */
    private Object _exitSync = new Object();
		
	private static final Logger _logger = Logger.getLogger(RabbitApi.class);
	
	/**
	 * Prefix to use for config file item names.
	 */
	public static final String CONFIG_PNAME = "com.collabip.tethr.rabbitapi.";
	
	/**
	 * Queue name for incoming rabbit messages.
	 */
	public static final String CREATE_QUEUE_PNAME = CONFIG_PNAME + "CREATE_QUEUE_NAME";
	public static final String ICE_CANDIDATE_TO_PNAME = CONFIG_PNAME + "ICE_CANDIDATE_TO_NAME";
	public static final String OFFER_TO_PNAME = CONFIG_PNAME + "OFFER_TO_NAME";
	
	
	/**
	 * Exchange name for outgoing rabbit messages.
	 */
	public static final String ICE_CANDIDATE_EXCHANGE_PNAME = CONFIG_PNAME + "ICE_CANDIDATE_EXCHANGE_NAME";
	public static final String ANSWER_EXCHANGE_PNAME = CONFIG_PNAME + "ANSWER_EXCHANGE_NAME";
	
	/**
	 * Rabbit connect string.
	 */
	public static final String CONNECT_STRING_PNAME = CONFIG_PNAME + "CONNECT_STRING";
	
	private ConfigurationService _config;
	private Connection _conn;
	private BundleContext _bundleContext;
	
	private Map<String, Conference> _conferences = new HashMap<String, Conference>();
	
	public RabbitApi(BundleContext bundleContext) throws KeyManagementException, NoSuchAlgorithmException, URISyntaxException, IOException
	{
		_bundleContext = bundleContext;
        		
		_config = ServiceUtils.getService(bundleContext, ConfigurationService.class);
	}
	
    public Videobridge getVideobridge()
    {
        Videobridge videobridge;

        if (_bundleContext == null)
        {
            videobridge = null;
        }
        else
        {
            videobridge
                = ServiceUtils.getService(_bundleContext, Videobridge.class);
        }
        return videobridge;
    }
	
	@Override
	public void run() {		
		try {
			
			ConnectionFactory factory = new ConnectionFactory();
			
			String rabbitUri = _config.getString(CONNECT_STRING_PNAME);
			factory.setUri(rabbitUri);
			
			_logger.info("Connecting to " + rabbitUri + "...");
			_conn = factory.newConnection();
			
			Channel channel = _conn.createChannel();
			
			int prefetchCount = 1;
			channel.basicQos(prefetchCount);
			
			channel.exchangeDeclare(_config.getString(ICE_CANDIDATE_EXCHANGE_PNAME), "topic", true);
			channel.exchangeDeclare(_config.getString(ANSWER_EXCHANGE_PNAME), "topic", true);
			
			String uniqueId = ManagementFactory.getRuntimeMXBean().getName();
			
			String iceCandidateQueue = _config.getString(ICE_CANDIDATE_TO_PNAME) + "_" + uniqueId;
			channel.queueDeclare(iceCandidateQueue, false, false, true, null);
			channel.queueBind(iceCandidateQueue, _config.getString(ICE_CANDIDATE_TO_PNAME), "#");
			
			String offerQueue = _config.getString(OFFER_TO_PNAME) + "_" + uniqueId;
			channel.queueDeclare(offerQueue, false, false, true, null);
			channel.queueBind(offerQueue, _config.getString(OFFER_TO_PNAME), "#");
			
			boolean autoAck = false;
			channel.basicConsume(_config.getString(CREATE_QUEUE_PNAME), autoAck, new CreateConferenceRequestConsumer(channel, this));
			channel.basicConsume(iceCandidateQueue, autoAck, new IceCandidateConsumer(channel, this));
			channel.basicConsume(offerQueue, autoAck, new OfferConsumer(channel, this));
			
			_logger.info("Connection to rabbit established, consumers consuming.");
			
            do
            {
                boolean interrupted = false;
    
                synchronized (_exitSync)
                {
                    try
                    {
                        _exitSync.wait();
                    }
                    catch (InterruptedException ie)
                    {
                        interrupted = true;
                    }
                }
                if (interrupted)
                    Thread.currentThread().interrupt();
            }
            while (true);
			
		
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public Map<String, Conference> get_conferences() {
		return _conferences;
	}
}
