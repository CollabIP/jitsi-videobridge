package com.collabip.tethr.rabbitapi;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sdp.SdpFactory;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ColibriConferenceIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ColibriConferenceIQ.Channel;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.SourcePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ParameterPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RTPHdrExtPacketExtension;

import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.util.Logger;
import org.jitsi.videobridge.Conference;
import org.jitsi.videobridge.Content;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.util.StringUtils;

import net.java.sip.communicator.impl.protocol.sip.*;

public class SdpConverter {
	
	private static final Logger _logger = Logger.getLogger(SdpConverter.class);
	
	private String _sdp;
	
	private SessionDescription _sd;

	public SdpConverter(String sdp) {
		super();
		this._sdp = sdp;
		try {
			_sd = SdpFactory.getInstance().createSessionDescription(sdp.replace("\\r\\n", "\r\n"));
		} catch (SdpParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public String get_sdp() {
		return _sd.toString();
	}

	public void set_sdp(String sdp) {
		this._sdp = sdp;
		try {
			_sd = SdpFactory.getInstance().createSessionDescription(sdp.replace("\\r\\n", "\r\n"));
		} catch (SdpParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}

	/*
	 * Sample SDP:
		v=0
		o=- 7270805401561094614 2 IN IP4 127.0.0.1
		s=-
		t=0 0
		a=group:BUNDLE video
		a=msid-semantic: WMS h2tQm9BnNF7WRZ0oRAZvn1ecRkomjU3MhhLh
		m=video 1 RTP/SAVPF 100 116 117
		c=IN IP4 0.0.0.0
		a=rtcp:1 IN IP4 0.0.0.0
		a=ice-ufrag:gokuxg+koEMgRyaQ
		a=ice-pwd:thWa1R+H4GHp8XhPz4ALWEY7
		a=ice-options:google-ice
		a=fingerprint:sha-256 0A:DB:D8:9D:98:21:E1:A6:B7:4C:FF:8F:A3:12:C7:F7:13:BE:9C:21:0B:0A:F0:10:C8:0F:D9:EA:69:5F:49:A7
		a=setup:actpass
		a=mid:video
		b=AS:300
		a=extmap:2 urn:ietf:params:rtp-hdrext:toffset
		a=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time
		a=sendrecv
		a=rtcp-mux
		a=rtpmap:100 VP8/90000
		a=rtcp-fb:100 ccm fir
		a=rtcp-fb:100 nack
		a=rtcp-fb:100 nack pli
		a=rtcp-fb:100 goog-remb
		a=rtpmap:116 red/90000
		a=rtpmap:117 ulpfec/90000
		a=ssrc:4147365359 cname:I/v+cK6ar/H2BT/G\r\n
		a=ssrc:4147365359 msid:h2tQm9BnNF7WRZ0oRAZvn1ecRkomjU3MhhLh 774b4025-b6bf-4be4-8d48-fa70076a46aa
		a=ssrc:4147365359 mslabel:h2tQm9BnNF7WRZ0oRAZvn1ecRkomjU3MhhLh
		a=ssrc:4147365359 label:774b4025-b6bf-4be4-8d48-fa70076a46aa	  
	 */
		
	public ColibriConferenceIQ get_Content(Conference conference)
	{
		ColibriConferenceIQ iq = new ColibriConferenceIQ();
		iq.setID(conference.getID());
		iq.setFrom("internal");
		ColibriConferenceIQ.Content content = new ColibriConferenceIQ.Content("video");
		ColibriConferenceIQ.Channel channel = new ColibriConferenceIQ.Channel();
		 
		Matcher matcher;
		
		// Extract ssrc's
		Pattern ssrcPattern = Pattern.compile("a=ssrc:(\\d*)\\s(\\w*):([^\\\\]*)\\\\r\\\\n");
		matcher = ssrcPattern.matcher(_sdp); 
		Map<Long, SourcePacketExtension> sources = new HashMap<Long, SourcePacketExtension>();
		while(matcher.find())
		{
			long ssrc = Long.parseLong(matcher.group(1), 10);
			
			SourcePacketExtension source;
			source = sources.get(ssrc);
			if(source == null)
			{
				source = new SourcePacketExtension();
				source.setSSRC(ssrc);
				sources.put(ssrc, source);
			}
			
			ParameterPacketExtension parameter = new ParameterPacketExtension();
			String name = matcher.group(2);
			parameter.setName(name);
			
			String value = matcher.group(3);
			parameter.setValue(value);
			
			source.addParameter(parameter);
		}
		
		for(SourcePacketExtension source: sources.values())
		{
			channel.addSource(source);
		}
		
		// Extract payload types
		Pattern ptPattern = Pattern.compile("a=rtpmap:(\\d*)\\s(\\w*)/(\\d*)");
		matcher = ptPattern.matcher(_sdp);
		while (matcher.find())
		{
			PayloadTypePacketExtension payType = new PayloadTypePacketExtension();
			payType.setId(Integer.parseInt(matcher.group(1), 10));
			payType.setName(matcher.group(2));
			payType.setClockrate(Integer.parseInt(matcher.group(3), 10));
			
			
			channel.addPayloadType(payType);
		}
		
		// Extract rtp header extensions
		/*
		Pattern extPattern = Pattern.compile("a=extmap:(\\d*)\\s([^\\\\]*)");
		matcher = extPattern.matcher(_sdp);
		while (matcher.find())
		{
			String id = matcher.group(1);
			URI uri = null;
			try {
				uri = new URI(matcher.group(2));
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
						
		}
		*/
		
		IceUdpTransportPacketExtension transport = new IceUdpTransportPacketExtension();
		channel.setID("some big id");
		channel.setEndpoint("internal");		
		content.addChannel(channel);
		iq.addContent(content);
		
		_logger.info(iq.toXML());
		
		return iq;
	}
	
	
	public ColibriConferenceIQ get_OtherContent(Conference conference) throws SdpParseException
	{
		ColibriConferenceIQ iq = new ColibriConferenceIQ();
		iq.setID(conference.getID());
		iq.setFrom("internal");
		ColibriConferenceIQ.Content content = new ColibriConferenceIQ.Content("video");
		ColibriConferenceIQ.Channel channel = new ColibriConferenceIQ.Channel();

		
		
		channel.setID("some big id");
		channel.setEndpoint("internal");		
		content.addChannel(channel);
		iq.addContent(content);
		
		_logger.info(iq.toXML());
		
		return iq;
	}
}
