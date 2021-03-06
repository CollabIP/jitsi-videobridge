/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package com.collabip.tethr.rabbitapi;

import java.lang.reflect.*;
import java.util.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

import org.jitsi.service.neomedia.*;
import org.json.simple.*;

/**
 * Implements (utility) functions to deserialize instances of
 * {@link ColibriConferenceIQ} and related classes from JSON instances.
 *
 * @author Lyubomir Marinov
 */
final class JSONDeserializer
{
    public static void deserializeAbstractPacketExtensionAttributes(
            JSONObject jsonObject,
            AbstractPacketExtension abstractPacketExtension)
    {
        @SuppressWarnings("unchecked")
        Iterator<Map.Entry<Object,Object>> i = jsonObject.entrySet().iterator();

        while (i.hasNext())
        {
            Map.Entry<Object,Object> e = i.next();
            Object key = e.getKey();

            if (key != null)
            {
                String name = key.toString();

                if (name != null)
                {
                    Object value = e.getValue();

                    if (!(value instanceof JSONObject)
                            && !(value instanceof JSONArray))
                    {
                        abstractPacketExtension.setAttribute(name, value);
                    }
                }
            }
        }
    }

    public static <T extends CandidatePacketExtension> T deserializeCandidate(
            JSONObject candidate,
            Class<T> candidateIQClass,
            IceUdpTransportPacketExtension transportIQ)
    {
        T candidateIQ;

        if (candidate == null)
        {
            candidateIQ = null;
        }
        else
        {
            try
            {
                candidateIQ = candidateIQClass.newInstance();
            }
            catch (IllegalAccessException iae)
            {
                throw new UndeclaredThrowableException(iae);
            }
            catch (InstantiationException ie)
            {
                throw new UndeclaredThrowableException(ie);
            }
            // attributes
            deserializeAbstractPacketExtensionAttributes(
                    candidate,
                    candidateIQ);

            transportIQ.addChildExtension(candidateIQ);
        }
        return candidateIQ;
    }

    public static void deserializeCandidates(
            JSONArray candidates,
            IceUdpTransportPacketExtension transportIQ)
    {
        if ((candidates != null) && !candidates.isEmpty())
        {
            for (Object candidate : candidates)
            {
                deserializeCandidate(
                        (JSONObject) candidate,
                        CandidatePacketExtension.class,
                        transportIQ);
            }
        }
    }

    public static ColibriConferenceIQ.Channel deserializeChannel(
            JSONObject channel,
            ColibriConferenceIQ.Content contentIQ)
    {
        ColibriConferenceIQ.Channel channelIQ;

        if (channel == null)
        {
            channelIQ = null;
        }
        else
        {
            Object direction
                = channel.get(ColibriConferenceIQ.Channel.DIRECTION_ATTR_NAME);
            Object endpoint
                = channel.get(ColibriConferenceIQ.Channel.ENDPOINT_ATTR_NAME);
            Object expire
                = channel.get(ColibriConferenceIQ.Channel.EXPIRE_ATTR_NAME);
            Object id = channel.get(ColibriConferenceIQ.Channel.ID_ATTR_NAME);
            Object initiator
                = channel.get(ColibriConferenceIQ.Channel.INITIATOR_ATTR_NAME);
            Object lastN
                = channel.get(ColibriConferenceIQ.Channel.LAST_N_ATTR_NAME);
            Object payloadTypes = channel.get(JSONSerializer.PAYLOAD_TYPES);
            Object rtpLevelRelayType
                = channel.get(
                        ColibriConferenceIQ.Channel
                            .RTP_LEVEL_RELAY_TYPE_ATTR_NAME);
            Object sources = channel.get(JSONSerializer.SOURCES);
            Object ssrcs = channel.get(JSONSerializer.SSRCS);
            Object transport
                = channel.get(IceUdpTransportPacketExtension.ELEMENT_NAME);

            channelIQ = new ColibriConferenceIQ.Channel();
            // direction
            if (direction != null)
            {
                channelIQ.setDirection(
                        MediaDirection.parseString(direction.toString()));
            }
            // endpoint
            if (endpoint != null)
                channelIQ.setEndpoint(endpoint.toString());
            // expire
            if (expire != null)
            {
                int i;

                if (expire instanceof Number)
                    i = ((Number) expire).intValue();
                else
                    i = Integer.parseInt(expire.toString());
                if (i != ColibriConferenceIQ.Channel.EXPIRE_NOT_SPECIFIED)
                    channelIQ.setExpire(i);
            }
            // id
            if (id != null)
                channelIQ.setID(id.toString());
            // initiator
            if (initiator != null)
            {
                Boolean b;

                if (initiator instanceof Boolean)
                    b = (Boolean) initiator;
                else
                    b = Boolean.valueOf(initiator.toString());
                channelIQ.setInitiator(b);
            }
            // lastN
            if (lastN != null)
            {
                Integer i;

                if (lastN instanceof Integer)
                    i = (Integer) lastN;
                else if (lastN instanceof Number)
                    i = Integer.valueOf(((Number) lastN).intValue());
                else
                    i = Integer.valueOf(lastN.toString());
                channelIQ.setLastN(i);
            }
            // payloadTypes
            if (payloadTypes != null)
                deserializePayloadTypes((JSONArray) payloadTypes, channelIQ);
            // rtpLevelRelayType
            if (rtpLevelRelayType != null)
                channelIQ.setRTPLevelRelayType(rtpLevelRelayType.toString());
            // sources
            if (sources != null)
                deserializeSources((JSONArray) sources, channelIQ);
            // ssrcs
            if (ssrcs != null)
                deserializeSSRCs((JSONArray) ssrcs, channelIQ);
            // transport
            if (transport != null)
                deserializeTransport((JSONObject) transport, channelIQ);

            contentIQ.addChannel(channelIQ);
        }
        return channelIQ;
    }

    public static void deserializeChannels(
            JSONArray channels,
            ColibriConferenceIQ.Content contentIQ)
    {
        if ((channels != null) && !channels.isEmpty())
        {
            for (Object channel : channels)
                deserializeChannel((JSONObject) channel, contentIQ);
        }
    }

    public static ColibriConferenceIQ deserializeConference(
            JSONObject conference)
    {
        ColibriConferenceIQ conferenceIQ;

        if (conference == null)
        {
            conferenceIQ = null;
        }
        else
        {
            Object id = conference.get(ColibriConferenceIQ.ID_ATTR_NAME);
            Object contents = conference.get(JSONSerializer.CONTENTS);

            conferenceIQ = new ColibriConferenceIQ();
            // id
            if (id != null)
                conferenceIQ.setID(id.toString());
            // contents
            if (contents != null)
                deserializeContents((JSONArray) contents, conferenceIQ);
        }
        return conferenceIQ;
    }

    public static ColibriConferenceIQ.Content deserializeContent(
            JSONObject content,
            ColibriConferenceIQ conferenceIQ)
    {
        ColibriConferenceIQ.Content contentIQ;

        if (content == null)
        {
            contentIQ = null;
        }
        else
        {
            Object name
                = content.get(ColibriConferenceIQ.Content.NAME_ATTR_NAME);
            Object channels = content.get(JSONSerializer.CHANNELS);

            contentIQ
                = conferenceIQ.getOrCreateContent(
                        (name == null) ? null : name.toString());
            // channels
            if (channels != null)
                deserializeChannels((JSONArray) channels, contentIQ);

            conferenceIQ.addContent(contentIQ);
        }
        return contentIQ;
    }

    public static void deserializeContents(
            JSONArray contents,
            ColibriConferenceIQ conferenceIQ)
    {
        if ((contents != null) && !contents.isEmpty())
        {
            for (Object content : contents)
                deserializeContent((JSONObject) content, conferenceIQ);
        }
    }

    public static DtlsFingerprintPacketExtension deserializeFingerprint(
            JSONObject fingerprint,
            IceUdpTransportPacketExtension transportIQ)
    {
        DtlsFingerprintPacketExtension fingerprintIQ;

        if (fingerprint == null)
        {
            fingerprintIQ = null;
        }
        else
        {
            Object theFingerprint
                = fingerprint.get(DtlsFingerprintPacketExtension.ELEMENT_NAME);

            fingerprintIQ = new DtlsFingerprintPacketExtension();
            // fingerprint
            if (theFingerprint != null)
                fingerprintIQ.setFingerprint(theFingerprint.toString());
            // attributes
            deserializeAbstractPacketExtensionAttributes(
                    fingerprint,
                    fingerprintIQ);
            /*
             * XXX The fingerprint is stored as the text of the
             * DtlsFingerprintPacketExtension instance. But it is a Java String
             * and, consequently, the
             * deserializeAbstractPacketExtensionAttributes method will
             * deserialize it into an attribute of the
             * DtlsFingerprintPacketExtension instance.
             */
            fingerprintIQ.removeAttribute(
                    DtlsFingerprintPacketExtension.ELEMENT_NAME);

            transportIQ.addChildExtension(fingerprintIQ);
        }
        return fingerprintIQ;
    }

    public static void deserializeFingerprints(
            JSONArray fingerprints,
            IceUdpTransportPacketExtension transportIQ)
    {
        if ((fingerprints != null) && !fingerprints.isEmpty())
        {
            for (Object fingerprint : fingerprints)
                deserializeFingerprint((JSONObject) fingerprint, transportIQ);
        }
    }

    public static void deserializeParameters(
            JSONObject parameters,
            PayloadTypePacketExtension payloadTypeIQ)
    {
        if (parameters != null)
        {
            @SuppressWarnings("unchecked")
            Iterator<Map.Entry<Object,Object>> i
                = parameters.entrySet().iterator();

            while (i.hasNext())
            {
                Map.Entry<Object,Object> e = i.next();
                Object name = e.getKey();
                Object value = e.getValue();

                if ((name != null) || (value != null))
                {
                    payloadTypeIQ.addParameter(
                            new ParameterPacketExtension(
                                    (name == null) ? null : name.toString(),
                                    (value == null) ? null : value.toString()));
                }
            }
        }
    }

    public static PayloadTypePacketExtension deserializePayloadType(
            JSONObject payloadType,
            ColibriConferenceIQ.Channel channelIQ)
    {
        PayloadTypePacketExtension payloadTypeIQ;

        if (payloadType == null)
        {
            payloadTypeIQ = null;
        }
        else
        {
            Object parameters = payloadType.get(JSONSerializer.PARAMETERS);

            payloadTypeIQ = new PayloadTypePacketExtension();
            // attributes
            deserializeAbstractPacketExtensionAttributes(
                    payloadType,
                    payloadTypeIQ);
            // parameters
            if (parameters != null)
                deserializeParameters((JSONObject) parameters, payloadTypeIQ);

            channelIQ.addPayloadType(payloadTypeIQ);
        }
        return payloadTypeIQ;
    }

    public static void deserializePayloadTypes(
            JSONArray payloadTypes,
            ColibriConferenceIQ.Channel channelIQ)
    {
        if ((payloadTypes != null) && !payloadTypes.isEmpty())
        {
            for (Object payloadType : payloadTypes)
                deserializePayloadType((JSONObject) payloadType, channelIQ);
        }
    }

    public static SourcePacketExtension deserializeSource(
            Object source,
            ColibriConferenceIQ.Channel channelIQ)
    {
        SourcePacketExtension sourceIQ;

        if (source == null)
        {
            sourceIQ = null;
        }
        else
        {
            long ssrc;

            try
            {
                ssrc = deserializeSSRC(source);
            }
            catch (NumberFormatException nfe)
            {
                ssrc = -1;
            }
            if (ssrc == -1)
            {
                sourceIQ = null;
            }
            else
            {
                sourceIQ = new SourcePacketExtension();
                sourceIQ.setSSRC(ssrc);

                channelIQ.addSource(sourceIQ);
            }
        }
        return sourceIQ;
    }

    public static void deserializeSources(
            JSONArray sources,
            ColibriConferenceIQ.Channel channelIQ)
    {
        if ((sources != null) && !sources.isEmpty())
        {
            for (Object source : sources)
                deserializeSource(source, channelIQ);
        }
    }

    public static int deserializeSSRC(Object o)
        throws NumberFormatException
    {
        int i = 0;

        if (o != null)
        {
            if (o instanceof Number)
            {
                i = ((Number) o).intValue();
            }
            else
            {
                String s = o.toString();

                if (s.startsWith("-"))
                    i = Integer.parseInt(s);
                else
                    i = (int) Long.parseLong(s);
            }
        }
        return i;
    }

    public static void deserializeSSRCs(
            JSONArray ssrcs,
            ColibriConferenceIQ.Channel channelIQ)
    {
        if ((ssrcs != null) && !ssrcs.isEmpty())
        {
            for (Object ssrc : ssrcs)
            {
                int ssrcIQ;

                try
                {
                    ssrcIQ = deserializeSSRC(ssrc);
                }
                catch (NumberFormatException nfe)
                {
                    continue;
                }

                channelIQ.addSSRC(ssrcIQ);
            }
        }
    }

    public static IceUdpTransportPacketExtension deserializeTransport(
            JSONObject transport,
            ColibriConferenceIQ.Channel channelIQ)
    {
        IceUdpTransportPacketExtension transportIQ;

        if (transport == null)
        {
            transportIQ = null;
        }
        else
        {
            Object xmlns = transport.get(JSONSerializer.XMLNS);
            Object fingerprints = transport.get(JSONSerializer.FINGERPRINTS);
            Object candidateList = transport.get(JSONSerializer.CANDIDATE_LIST);
            Object remoteCandidate
                = transport.get(RemoteCandidatePacketExtension.ELEMENT_NAME);

            if (IceUdpTransportPacketExtension.NAMESPACE.equals(xmlns))
                transportIQ = new IceUdpTransportPacketExtension();
            else if (RawUdpTransportPacketExtension.NAMESPACE.equals(xmlns))
                transportIQ = new RawUdpTransportPacketExtension();
            else
                transportIQ = null;
            if (transportIQ != null)
            {
                // attributes
                deserializeAbstractPacketExtensionAttributes(
                        transport,
                        transportIQ);
                // fingerprints
                if (fingerprints != null)
                {
                    deserializeFingerprints(
                            (JSONArray) fingerprints,
                            transportIQ);
                }
                // candidateList
                if (candidateList != null)
                {
                    deserializeCandidates(
                            (JSONArray) candidateList,
                            transportIQ);
                }
                // remoteCandidate
                if (remoteCandidate != null)
                {
                    deserializeCandidate(
                            (JSONObject) remoteCandidate,
                            RemoteCandidatePacketExtension.class,
                            transportIQ);
                }

                channelIQ.setTransport(transportIQ);
            }
        }
        return transportIQ;
    }

    /**
     * Prevents the initialization of new <tt>JSONDeserializer</tt> instances.
     */
    private JSONDeserializer()
    {
    }
}
