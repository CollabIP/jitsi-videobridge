package com.collabip.tethr.rabbitapi;

import java.io.IOException;

import org.jitsi.util.Logger;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class IceCandidateConsumer extends DefaultConsumer {
	
	private static final Logger _logger = Logger.getLogger(IceCandidateConsumer.class);
	private Channel _channel;
	private RabbitApi _rabbitApi;

	public IceCandidateConsumer(Channel channel, RabbitApi rabbitApi) {
		super(channel);
		
		_channel = channel;
		_rabbitApi = rabbitApi;
	}
	
	@Override
	public void handleDelivery(String consumerTag, Envelope envelope,
			BasicProperties properties, byte[] body) throws IOException {
		
		_logger.info("Received ice candidate");
		
		_channel.basicAck(envelope.getDeliveryTag(), false);
	}


}
