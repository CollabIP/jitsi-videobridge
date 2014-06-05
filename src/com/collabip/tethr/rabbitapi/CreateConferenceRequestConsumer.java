package com.collabip.tethr.rabbitapi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ColibriConferenceIQ;

import org.jitsi.util.Logger;
import org.jitsi.videobridge.Conference;
import org.jitsi.videobridge.Videobridge;
import org.json.simple.JSONObject;

import com.collabip.tethr.rabbitapi.messages.CreateVideoConferenceRequest;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.*;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class CreateConferenceRequestConsumer extends DefaultConsumer {
	
	private static final Logger _logger = Logger.getLogger(CreateConferenceRequestConsumer.class);
	private Channel _channel;
	private RabbitApi _rabbitApi;

	public CreateConferenceRequestConsumer(Channel channel, RabbitApi rabbitApi) {
		super(channel);
		
		_channel = channel;
		_rabbitApi = rabbitApi;
	}
	
	
	@Override
	public void handleDelivery(String consumerTag, Envelope envelope,
			BasicProperties properties, byte[] body) throws IOException {
		
		String jsondata = new String(body, "UTF-8");
		
		CreateVideoConferenceRequest rq = null;
		
		try {
			Gson gson = new Gson();
			rq = gson.fromJson(jsondata, CreateVideoConferenceRequest.class);
			
			_logger.info("Received Create Conference message: " + rq.MeetingId);
			
			Videobridge videoBridge = _rabbitApi.getVideobridge();
	        
	        Conference conf = videoBridge.createConference(null);
	        
	        _rabbitApi.get_conferences().put(rq.MeetingId, conf);
	        
		} catch (JsonSyntaxException e)
		{
			e.printStackTrace();
		}
		        
		_channel.basicAck(envelope.getDeliveryTag(), false);
	}
}
