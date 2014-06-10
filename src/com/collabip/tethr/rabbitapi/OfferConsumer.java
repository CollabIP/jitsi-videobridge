package com.collabip.tethr.rabbitapi;

import java.io.IOException;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ColibriConferenceIQ;

import org.jitsi.util.Logger;
import org.jitsi.videobridge.Conference;
import org.jitsi.videobridge.Videobridge;

import com.collabip.tethr.rabbitapi.messages.CreateVideoConferenceRequest;
import com.collabip.tethr.rabbitapi.messages.WebRtcVideoOffer;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class OfferConsumer extends DefaultConsumer {
	
	private static final Logger _logger = Logger.getLogger(OfferConsumer.class);
	private Channel _channel;
	private RabbitApi _rabbitApi;

	public OfferConsumer(Channel channel, RabbitApi rabbitApi) {
		super(channel);
		
		_channel = channel;
		_rabbitApi = rabbitApi;
	}
	
	
	@Override
	public void handleDelivery(String consumerTag, Envelope envelope,
			BasicProperties properties, byte[] body) throws IOException {
		
		String jsondata = new String(body, "UTF-8");
		WebRtcVideoOffer offer = null;
		
		_channel.basicAck(envelope.getDeliveryTag(), false);
		
		try {
			Gson gson = new Gson();
			offer = gson.fromJson(jsondata, WebRtcVideoOffer.class);
			
			// Check if we are the server hosting this conference,
			// and if not ignore the offer
			String confId = _rabbitApi.get_conferences().get(offer.MeetingId);
			if(confId == null)
			{
				return;
			}
			
			Conference conf = _rabbitApi.getVideobridge().getConference(confId, null);
			if(conf == null)
			{
				// Conference expired, we need to recreate it
				conf = _rabbitApi.getVideobridge().createConference(null);
				_rabbitApi.get_conferences().put(offer.MeetingId, conf.getID());
			}
			
			_logger.info(String.format("Processing offer from %1$s", offer.ParticipantId));
			SdpConverter sdp = new SdpConverter(offer.Offer);
			ColibriConferenceIQ iq = sdp.get_OtherContent(conf);
			_rabbitApi.getVideobridge().handleColibriConferenceIQ(iq);
			
		} catch (Throwable e)
		{
			e.printStackTrace();
		}		
	}
}
