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

	private static final Logger _logger = Logger.getLogger(RabbitBundleActivator.class);
	
	private RabbitApi _rabbitApi;	
	
	@Override
	public void start(BundleContext bundleContext) throws Exception {
		_logger.info("Starting rabbit bundle...");
		
        ConfigurationService cfg = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        
        List<String> props = cfg.getAllPropertyNames();
        
        for (String d: props)
        {
        	_logger.info("Configuration option: " + d);
        }
        
        _logger.info("Configuration file filename: " + cfg.getConfigurationFilename());
        
        _rabbitApi = new RabbitApi(bundleContext);
        _rabbitApi.setDaemon(false);
        _rabbitApi.start();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		_logger.info("Stopping rabbit bundle...");

	}

}
