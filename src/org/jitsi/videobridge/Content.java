/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.videobridge;

import java.lang.ref.*;
import java.util.*;

import net.java.sip.communicator.util.*;

import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.RTPTranslator.WriteFilter;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.util.Logger;
import org.osgi.framework.*;

/**
 * Represents a content in the terms of Jitsi Videobridge.
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public class Content
    implements RTPTranslator.WriteFilter
{
    /**
     * The <tt>Logger</tt> used by the <tt>Content</tt> class and its instances
     * to print debug information.
     */
    private static final Logger logger = Logger.getLogger(Content.class);

    /**
     * Logs a specific <tt>String</tt> at debug level.
     *
     * @param s the <tt>String</tt> to log at debug level 
     */
    private static void logd(String s)
    {
        /*
         * FIXME Jitsi Videobridge uses the defaults of java.util.logging at the
         * time of this writing but wants to log at debug level at all times for
         * the time being in order to facilitate early development.
         */
        logger.info(s);
    }

    /**
     * The <tt>Channel</tt>s of this <tt>Content</tt> mapped by their IDs.
     */
    private final Map<String, Channel> channels
        = new HashMap<String, Channel>();

    /**
     * The <tt>Conference</tt> which has initialized this <tt>Content</tt>.
     */
    private final Conference conference;

    /**
     * The indicator which determines whether {@link #expire()} has been called
     * on this <tt>Content</tt>.
     */
    private boolean expired = false;

    /**
     * The time in milliseconds of the last activity related to this
     * <tt>Content</tt>. In the time interval between the last activity and now,
     * this <tt>Content</tt> is considered inactive.
     */
    private long lastActivityTime;

    /**
     * The <tt>MediaType</tt> of this <tt>Content</tt>. The implementation
     * detects the <tt>MediaType</tt> by looking at the {@link #name} of this
     * instance.
     */
    private final MediaType mediaType;

    /**
     * The <tt>MediaDevice</tt> which mixes the media received by those of
     * {@link #channels} which use a mixer as their RTP-level relay.
     */
    private MediaDevice mixer;

    /**
     * The name of this <tt>Content</tt>.
     */
    private final String name;

    private RTCPFeedbackMessageSender rtcpFeedbackMessageSender;

    /**
     * The <tt>Object</tt> which synchronizes the access to the RTP-level relays
     * (i.e. {@link #mixer} and {@link #rtpTranslator}) provided by this
     * <tt>Content</tt>.
     */
    private final Object rtpLevelRelaySyncRoot = new Object();

    /**
     * The <tt>RTPTranslator</tt> which forwards the RTP and RTCP traffic
     * between those {@link #channels} which use a translator as their RTP-level
     * relay.
     */
    private RTPTranslator rtpTranslator;

    /**
     * Initializes a new <tt>Content</tt> instance which is to be a part of a
     * specific <tt>Conference</tt> and which is to have a specific name.
     *
     * @param conference the <tt>Conference</tt> which is initializing the new
     * instance
     * @param name the name of the new instance
     */
    public Content(Conference conference, String name)
    {
        if (conference == null)
            throw new NullPointerException("conference");
        if (name == null)
            throw new NullPointerException("name");

        this.conference = conference;
        this.name = name;

        mediaType = MediaType.parseString(this.name);

        touch();
    }

    @Override
    public boolean accept(
            MediaStream source,
            byte[] buffer, int offset, int length,
            MediaStream destination,
            boolean data)
    {
        boolean accept = true;

        if (destination != null)
        {
            Channel dst = Channel.getChannel(destination);

            if (dst != null)
            {
                Channel src
                    = (source == null) ? null : Channel.getChannel(source);

                accept
                    = dst.rtpTranslatorWillWrite(
                            data,
                            buffer, offset, length,
                            src);
            }
        }
        return accept;
    }

    void askForKeyframes(Set<Endpoint> endpoints)
    {
        for (Endpoint endpoint : endpoints)
        {
            for (Channel channel : endpoint.getChannels(MediaType.VIDEO))
                channel.askForKeyframes();
        }
    }

    /**
     * Initializes a new <tt>Channel</tt> instance and adds it to the list of
     * <tt>Channel</tt>s of this <tt>Content</tt>. The new <tt>Channel</tt>
     * instance has an ID which is unique within the list of <tt>Channel</tt>s
     * of this <tt>Content</tt>.
     *
     * @return
     * @throws Exception
     */
    public Channel createChannel()
        throws Exception
    {
        Channel channel = null;

        do
        {
            String id = generateChannelID();

            synchronized (channels)
            {
                if (!channels.containsKey(id))
                {
                    channel = new Channel(this, id);
                    channels.put(id, channel);
                }
            }
        }
        while (channel == null);

        /*
         * The method Videobridge.getChannelCount() should better be executed
         * outside synchronized blocks in order to reduce the risks of causing
         * deadlocks.
         */
        Conference conference = getConference();
        Videobridge videobridge = conference.getVideobridge();

        logd(
                "Created channel " + channel.getID() + " of content "
                    + getName() + " of conference " + conference.getID()
                    + ". The total number of conferences is now "
                    + videobridge.getConferenceCount() + ", channels "
                    + videobridge.getChannelCount() + ".");

        return channel;
    }

    /**
     * Expires this <tt>Content</tt> and its associated <tt>Channel</tt>s.
     * Releases the resources acquired by this instance throughout its life time
     * and prepares it to be garbage collected.
     */
    public void expire()
    {
        synchronized (this)
        {
            if (expired)
                return;
            else
                expired = true;
        }

        Conference conference = getConference();

        try
        {
            conference.expireContent(this);
        }
        finally
        {
            // Expire the Channels of this Content.
            for (Channel channel : getChannels())
            {
                try
                {
                    channel.expire();
                }
                catch (Throwable t)
                {
                    logger.warn(
                            "Failed to expire channel " + channel.getID()
                                + " of content " + getName() + " of conference "
                                + conference.getID() + "!",
                            t);
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                }
            }

            synchronized (rtpLevelRelaySyncRoot)
            {
                if (rtpTranslator != null)
                    rtpTranslator.dispose();
                rtcpFeedbackMessageSender = null;
            }

            Videobridge videobridge = conference.getVideobridge();

            logd(
                    "Expired content " + getName() + " of conference "
                        + conference.getID()
                        + ". The total number of conferences is now "
                        + videobridge.getConferenceCount() + ", channels "
                        + videobridge.getChannelCount() + ".");
        }
    }

    /**
     * Expires a specific <tt>Channel</tt> of this <tt>Content</tt> (i.e. if the
     * specified <tt>channel</tt> is not in the list of <tt>Channel</tt>s of
     * this <tt>Content</tt>, does nothing).
     *
     * @param channel the <tt>Channel</tt> to be expired by this
     * <tt>Content</tt>
     */
    public void expireChannel(Channel channel)
    {
        String id = channel.getID();
        boolean expireChannel;

        synchronized (channels)
        {
            if (channel.equals(channels.get(id)))
            {
                channels.remove(id);
                expireChannel = true;
            }
            else
                expireChannel = false;
        }
        if (expireChannel)
            channel.expire();
    }

    /**
     * Generates a new <tt>Channel</tt> ID which is not guaranteed to be unique.
     *
     * @return a new <tt>Channel</tt> ID which is not guaranteed to be unique
     */
    private String generateChannelID()
    {
        return
            Long.toHexString(
                    System.currentTimeMillis() + Videobridge.RANDOM.nextLong());
    }

    /**
     * Returns a <tt>Channel</tt> of this <tt>Content</tt>, which has
     * <tt>receiveSSRC</tt> in its list of received SSRCs, or <tt>null</tt> in
     * case no such <tt>Channel</tt> exists.
     *
     * @param receiveSSRC the SSRC to search for.
     * @return a <tt>Channel</tt> of this <tt>Content</tt>, which has
     * <tt>receiveSSRC</tt> in its list of received SSRCs, or <tt>null</tt> in
     * case no such <tt>Channel</tt> exists.
     */
    Channel findChannelByReceiveSSRC(long receiveSSRC)
    {
        for (Channel channel : getChannels())
        {
            for (int channelReceiveSSRC : channel.getReceiveSSRCs())
            {
                if (receiveSSRC == (0xFFFFFFFFL & channelReceiveSSRC))
                    return channel;
            }
        }
        return null;
    }

    /**
     * Gets the <tt>BundleContext</tt> associated with this <tt>Content</tt>.
     * The method is a convenience which gets the <tt>BundleContext</tt>
     * associated with the XMPP component implementation in which the
     * <tt>Videobridge</tt> associated with this instance is executing.
     *
     * @return the <tt>BundleContext</tt> associated with this <tt>Content</tt>
     */
    public BundleContext getBundleContext()
    {
        return getConference().getBundleContext();
    }

    /**
     * Returns a <tt>Channel</tt> from the list of <tt>Channel</tt>s of this
     * <tt>Content</tt> which has a specific ID.
     *
     * @param id the ID of the <tt>Channel</tt> to be returned
     * @return a <tt>Channel</tt> from the list of <tt>Channel</tt>s of this
     * <tt>Content</tt> which has the specified <tt>id</tt> if such a
     * <tt>Channel</tt> exists; otherwise, <tt>null</tt>
     */
    public Channel getChannel(String id)
    {
        Channel channel;

        synchronized (channels)
        {
            channel = channels.get(id);
        }

        // It seems the channel is still active.
        if (channel != null)
            channel.touch();

        return channel;
    }

    /**
     * Gets the number of <tt>Channel</tt>s of this <tt>Content</tt>.
     *
     * @return the number of <tt>Channel</tt>s of this <tt>Content</tt>
     */
    public int getChannelCount()
    {
        synchronized (channels)
        {
            return channels.size();
        }
    }

    /**
     * Gets the <tt>Channel</tt>s of this <tt>Content</tt>.
     *
     * @return the <tt>Channel</tt>s of this <tt>Content</tt>
     */
    public Channel[] getChannels()
    {
        synchronized (channels)
        {
            Collection<Channel> values = channels.values();

            return values.toArray(new Channel[values.size()]);
        }
    }

    /**
     * Gets the <tt>Conference</tt> which has initialized this <tt>Content</tt>.
     *
     * @return the <tt>Conference</tt> which has initialized this
     * <tt>Content</tt>
     */
    public final Conference getConference()
    {
        return conference;
    }

    /**
     * Gets the time in milliseconds of the last activity related to this
     * <tt>Content</tt>.
     *
     * @return the time in milliseconds of the last activity related to this
     * <tt>Content</tt>
     */
    public long getLastActivityTime()
    {
        synchronized (this)
        {
            return lastActivityTime;
        }
    }

    /**
     * Returns a <tt>MediaService</tt> implementation (if any).
     *
     * @return a <tt>MediaService</tt> implementation (if any)
     */
    MediaService getMediaService()
    {
        MediaService mediaService
            = ServiceUtils.getService(getBundleContext(), MediaService.class);

        /*
         * TODO For an unknown reason, ServiceUtils.getService fails to retrieve
         * the MediaService implementation. In the form of a temporary
         * workaround, get it through LibJitsi.
         */
        if (mediaService == null)
            mediaService = LibJitsi.getMediaService();

        return mediaService;
    }

    /**
     * Gets the <tt>MediaType</tt> of this <tt>Content</tt>. The implementation
     * detects the <tt>MediaType</tt> by looking at the <tt>name</tt> of this
     * instance.
     *
     * @return the <tt>MediaType</tt> of this <tt>Content</tt>
     */
    public MediaType getMediaType()
    {
        return mediaType;
    }

    /**
     * Gets the <tt>MediaDevice</tt> which mixes the media received by the
     * <tt>Channels</tt>  of this <tt>Content</tt> which use a mixer as their
     * RTP-level relay.
     *
     * @return the <tt>MediaDevice</tt> which mixes the media received by the
     * <tt>Channels</tt>  of this <tt>Content</tt> which use a mixer as their
     * RTP-level relay
     */
    public MediaDevice getMixer()
    {
        if (mixer == null)
        {
            MediaType mediaType = getMediaType();
            MediaDevice device;

            if (MediaType.AUDIO.equals(mediaType))
                device = new AudioSilenceMediaDevice();
            else
                device = null;

            if (device == null)
            {
                throw new UnsupportedOperationException(
                        "The mixer type of RTP-level relay is not supported"
                                + " for " + mediaType);
            }
            else
                mixer = getMediaService().createMixer(device);
        }
        return mixer;
    }

    /**
     * Gets the name of this <tt>Content</tt>.
     *
     * @return the name of this <tt>Content</tt>
     */
    public final String getName()
    {
        return name;
    }

    RTCPFeedbackMessageSender getRTCPFeedbackMessageSender()
    {
        return rtcpFeedbackMessageSender;
    }

    /**
     * Gets the <tt>RTPTranslator</tt> which forwards the RTP and RTCP traffic
     * between the <tt>Channel</tt>s of this <tt>Content</tt> which use a
     * translator as their RTP-level relay.
     *
     * @return the <tt>RTPTranslator</tt> which forwards the RTP and RTCP
     * traffic between the <tt>Channel</tt>s of this <tt>Content</tt> which use
     * a translator as their RTP-level relay
     */
    public RTPTranslator getRTPTranslator()
    {
        synchronized (rtpLevelRelaySyncRoot)
        {
            if (rtpTranslator == null)
            {
                rtpTranslator = getMediaService().createRTPTranslator();
                if (rtpTranslator != null)
                {
                    new RTPTranslatorWriteFilter(rtpTranslator, this);
                    if (rtpTranslator instanceof RTPTranslatorImpl)
                    {
                        rtcpFeedbackMessageSender
                            = new RTCPFeedbackMessageSender(
                                    (RTPTranslatorImpl) rtpTranslator);
                    }
                }
            }
            return rtpTranslator;
        }
    }

    /**
     * Sets the time in milliseconds of the last activity related to this
     * <tt>Content</tt> to the current system time.
     */
    public void touch()
    {
        long now = System.currentTimeMillis();

        synchronized (this)
        {
            if (getLastActivityTime() < now)
                lastActivityTime = now;
        }
    }

    private static class RTPTranslatorWriteFilter
        implements RTPTranslator.WriteFilter
    {
        private final WeakReference<RTPTranslator> rtpTranslator;

        private final WeakReference<RTPTranslator.WriteFilter> writeFilter;

        public RTPTranslatorWriteFilter(
                RTPTranslator rtpTranslator,
                RTPTranslator.WriteFilter writeFilter)
        {
            this.rtpTranslator
                = new WeakReference<RTPTranslator>(rtpTranslator);
            this.writeFilter
                = new WeakReference<RTPTranslator.WriteFilter>(writeFilter);

            rtpTranslator.addWriteFilter(this);
        }

        @Override
        public boolean accept(
                MediaStream source,
                byte[] buffer, int offset, int length,
                MediaStream destination,
                boolean data)
        {
            WriteFilter writeFilter = this.writeFilter.get();
            boolean accept = true;

            if (writeFilter == null)
            {
                RTPTranslator rtpTranslator = this.rtpTranslator.get();

                if (rtpTranslator != null)
                    rtpTranslator.removeWriteFilter(this);
            }
            else
            {
                accept
                    = writeFilter.accept(
                            source,
                            buffer, offset, length,
                            destination,
                            data);
            }
            return accept;
        }
    }
}
