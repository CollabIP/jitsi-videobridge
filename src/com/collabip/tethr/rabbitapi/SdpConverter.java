package com.collabip.tethr.rabbitapi;

import gov.nist.javax.sip.header.TimeStamp;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.sdp.SdpFactory;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ColibriConferenceIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ColibriIQProvider;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.SourcePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ParameterPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;

import org.jitsi.util.Logger;
import org.jivesoftware.smack.provider.ProviderManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.tools.shell.Global;
import org.mozilla.javascript.tools.shell.Main;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;


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
	
	public SdpConverter(ColibriConferenceIQ iq)
	{
		_sdp = CreateSDP(iq);
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
		
	@SuppressWarnings("unchecked")
	public ColibriConferenceIQ get_Content(String conferenceId, String participantId) throws ParseException
	{
		ColibriConferenceIQ iq = new ColibriConferenceIQ();
		//iq.setID(channelID);
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
		
		// Convert SDP to jingle json
		// For now we are faking it with a literal
		String json = "{\r\n" + 
				"  \"contents\": [\r\n" + 
				"    {\r\n" + 
				"      \"name\": \"video\",\r\n" + 
				"      \"description\": {\r\n" + 
				"        \"descType\": \"rtp\",\r\n" + 
				"        \"media\": \"video\",\r\n" + 
				"        \"payloads\": [\r\n" + 
				"          {\r\n" + 
				"            \"id\": \"100\",\r\n" + 
				"            \"name\": \"VP8\",\r\n" + 
				"            \"clockrate\": \"90000\",\r\n" + 
				"            \"channels\": \"1\",\r\n" + 
				"            \"feedback\": [\r\n" + 
				"              {\r\n" + 
				"                \"id\": \"100\",\r\n" + 
				"                \"type\": \"ccm\",\r\n" + 
				"                \"subtype\": \"fir\",\r\n" + 
				"                \"parameters\": []\r\n" + 
				"              },\r\n" + 
				"              {\r\n" + 
				"                \"id\": \"100\",\r\n" + 
				"                \"type\": \"nack\",\r\n" + 
				"                \"subtype\": \"\",\r\n" + 
				"                \"parameters\": []\r\n" + 
				"              },\r\n" + 
				"              {\r\n" + 
				"                \"id\": \"100\",\r\n" + 
				"                \"type\": \"nack\",\r\n" + 
				"                \"subtype\": \"pli\",\r\n" + 
				"                \"parameters\": []\r\n" + 
				"              },\r\n" + 
				"              {\r\n" + 
				"                \"id\": \"100\",\r\n" + 
				"                \"type\": \"goog-remb\",\r\n" + 
				"                \"subtype\": \"\",\r\n" + 
				"                \"parameters\": []\r\n" + 
				"              }\r\n" + 
				"            ]\r\n" + 
				"          },\r\n" + 
				"          {\r\n" + 
				"            \"id\": \"116\",\r\n" + 
				"            \"name\": \"red\",\r\n" + 
				"            \"clockrate\": \"90000\",\r\n" + 
				"            \"channels\": \"1\",\r\n" + 
				"            \"feedback\": []\r\n" + 
				"          },\r\n" + 
				"          {\r\n" + 
				"            \"id\": \"117\",\r\n" + 
				"            \"name\": \"ulpfec\",\r\n" + 
				"            \"clockrate\": \"90000\",\r\n" + 
				"            \"channels\": \"1\",\r\n" + 
				"            \"feedback\": []\r\n" + 
				"          }\r\n" + 
				"        ],\r\n" + 
				"        \"encryption\": [],\r\n" + 
				"        \"feedback\": [],\r\n" + 
				"        \"headerExtensions\": [\r\n" + 
				"          {\r\n" + 
				"            \"id\": \"2\",\r\n" + 
				"            \"senders\": \"both\",\r\n" + 
				"            \"uri\": \"urn:ietf:params:rtp-hdrext:toffset\"\r\n" + 
				"          },\r\n" + 
				"          {\r\n" + 
				"            \"id\": \"3\",\r\n" + 
				"            \"senders\": \"both\",\r\n" + 
				"            \"uri\": \"http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\"\r\n" + 
				"          }\r\n" + 
				"        ],\r\n" + 
				"        \"ssrc\": \"4147365359\",\r\n" + 
				"        \"mux\": true,\r\n" + 
				"        \"sourceGroups\": [],\r\n" + 
				"        \"sources\": [\r\n" + 
				"          {\r\n" + 
				"            \"ssrc\": \"4147365359\",\r\n" + 
				"            \"parameters\": [\r\n" + 
				"              {\r\n" + 
				"                \"key\": \"cname\",\r\n" + 
				"                \"value\": \"I/v+cK6ar/H2BT/G\"\r\n" + 
				"              },\r\n" + 
				"              {\r\n" + 
				"                \"key\": \"msid\",\r\n" + 
				"                \"value\": \"h2tQm9BnNF7WRZ0oRAZvn1ecRkomjU3MhhLh 774b4025-b6bf-4be4-8d48-fa70076a46aa\"\r\n" + 
				"              },\r\n" + 
				"              {\r\n" + 
				"                \"key\": \"mslabel\",\r\n" + 
				"                \"value\": \"h2tQm9BnNF7WRZ0oRAZvn1ecRkomjU3MhhLh\"\r\n" + 
				"              },\r\n" + 
				"              {\r\n" + 
				"                \"key\": \"label\",\r\n" + 
				"                \"value\": \"774b4025-b6bf-4be4-8d48-fa70076a46aa\\t  \"\r\n" + 
				"              }\r\n" + 
				"            ]\r\n" + 
				"          }\r\n" + 
				"        ]\r\n" + 
				"      },\r\n" + 
				"      \"transport\": {\r\n" + 
				"        \"transType\": \"iceUdp\",\r\n" + 
				"        \"candidates\": [],\r\n" + 
				"        \"fingerprints\": [\r\n" + 
				"          {\r\n" + 
				"            \"hash\": \"sha-256\",\r\n" + 
				"            \"value\": \"0A:DB:D8:9D:98:21:E1:A6:B7:4C:FF:8F:A3:12:C7:F7:13:BE:9C:21:0B:0A:F0:10:C8:0F:D9:EA:69:5F:49:A7\",\r\n" + 
				"            \"setup\": \"actpass\"\r\n" + 
				"          }\r\n" + 
				"        ],\r\n" + 
				"        \"ufrag\": \"gokuxg+koEMgRyaQ\",\r\n" + 
				"        \"pwd\": \"thWa1R+H4GHp8XhPz4ALWEY7\"\r\n" + 
				"      },\r\n" + 
				"      \"senders\": \"both\"\r\n" + 
				"    }\r\n" + 
				"  ],\r\n" + 
				"  \"groups\": [\r\n" + 
				"    {\r\n" + 
				"      \"semantics\": \"BUNDLE\",\r\n" + 
				"      \"contents\": [\r\n" + 
				"        \"video\"\r\n" + 
				"      ]\r\n" + 
				"    }\r\n" + 
				"  ]\r\n" + 
				"}";
		
		JSONObject jsonObject = null;
		
		jsonObject = (JSONObject) new JSONParser().parse(json);
		
		JSONArray array = (JSONArray) jsonObject.get("contents");
		
		JSONObject jscontent = (JSONObject) array.get(0);

		JSONObject transport = (JSONObject) jscontent.get("transport");
		transport.put("xmlns", IceUdpTransportPacketExtension.NAMESPACE);
		
		JSONDeserializer.deserializeTransport(transport, channel);		
		
		//channel.setID(channelID);
		channel.setEndpoint("internal");		
		content.addChannel(channel);
		iq.addContent(content);
		
		_logger.info(iq.toXML());
		
		return iq;
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
a=ssrc:4147365359 cname:I/v+cK6ar/H2BT/G
a=ssrc:4147365359 msid:h2tQm9BnNF7WRZ0oRAZvn1ecRkomjU3MhhLh 774b4025-b6bf-4be4-8d48-fa70076a46aa
a=ssrc:4147365359 mslabel:h2tQm9BnNF7WRZ0oRAZvn1ecRkomjU3MhhLh
a=ssrc:4147365359 label:774b4025-b6bf-4be4-8d48-fa70076a46aa	  
	 */
	
	public String CreateSDP(ColibriConferenceIQ iq)
	{
		StringBuilder sdp = new StringBuilder("v=0\n");
		
		// Origin Line
		TimeStamp time = new TimeStamp();
		long timestamp = time.getTime();
		sdp.append("o=- ");
		sdp.append(timestamp);
		sdp.append(" 1 IN IP4 127.0.0.1");
		sdp.append("\n");
		
		// Session name
		sdp.append("s=-\n");
		
		// Start/End times
		sdp.append("t=0 0\n");
		sdp.append("a=group:BUNDLE video\n");
		
		
		
		
		return sdp.toString();		
	}
	
	public ColibriConferenceIQ get_ContentScript(String conferenceId, String participantId) throws Exception
	{
		ColibriConferenceIQ iq = new ColibriConferenceIQ();
		
		String result = ColibriClientLib.getInstance().SdpToIq(_sdp);
		
		XmlPullParserFactory xppf = XmlPullParserFactory.newInstance();
		xppf.setNamespaceAware(true);			
		XmlPullParser xpp = xppf.newPullParser();
		xpp.setInput(new StringReader(result));
		ProviderManager man = ProviderManager.getInstance();
//		ColibriIQProvider prov = (ColibriIQProvider) man.getExtensionProvider("conference", "http://jitsi.org/protocol/colibri");
//		ColibriConferenceIQ iq = (ColibriConferenceIQ) prov.parseIQ(xpp);
		return iq;			
	}
	
}


