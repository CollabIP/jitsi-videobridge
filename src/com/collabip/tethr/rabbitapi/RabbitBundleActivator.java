package com.collabip.tethr.rabbitapi;

import java.util.List;

import net.java.sip.communicator.util.ServiceUtils;

import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.util.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * This is the bundle activator for the RabbitMQ API
 * for Tethr. It instantiates a RabbitApi object after
 * setting up the configuration service for it.
 * 
 * @author chaz
 */
public class RabbitBundleActivator implements BundleActivator {

	private static final Logger logger = Logger.getLogger(RabbitBundleActivator.class);
	
	private RabbitApi rabbitApi;
	
	
	
	@Override
	public void start(BundleContext bundleContext) throws Exception {
		logger.info("Starting rabbit bundle...");
		
        ConfigurationService cfg
        = ServiceUtils.getService(
                bundleContext,
                ConfigurationService.class);
        
        List<String> props = cfg.getAllPropertyNames();
        
        for (String d: props)
        {
        	logger.info("Configuration option: " + d);
        }
        
        logger.info("Configuration file filename: " + cfg.getConfigurationFilename());
        
        rabbitApi = new RabbitApi();

	}

	@Override
	public void stop(BundleContext context) throws Exception {
		logger.info("Stopping rabbit bundle...");

	}

}
