/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.videobridge;

import java.beans.*;
import java.lang.ref.*;
import java.util.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;

import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.util.event.*;
import org.osgi.framework.*;

/**
 * Represents a conference in the terms of Jitsi Videobridge.
 *
 * @author Lyubomir Marinov
 */
public class Conference
     extends PropertyChangeNotifier
     implements PropertyChangeListener
{
    /**
     * The name of the <tt>Conference</tt> property <tt>endpoints</tt> which
     * lists the <tt>Endpoint</tt>s participating in/contributing to the
     * <tt>Conference</tt>.
     */
    public static final String ENDPOINTS_PROPERTY_NAME
        = Conference.class.getName() + ".endpoints";

    /**
     * The <tt>Logger</tt> used by the <tt>Conference</tt> class and its
     * instances to print debug information.
     */
    private static final Logger logger = Logger.getLogger(Conference.class);

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
     * The <tt>Content</tt>s of this <tt>Conference</tt>.
     */
    private final List<Content> contents = new LinkedList<Content>();

    /**
     * The <tt>Endpoint</tt>s participating in this <tt>Conference</tt>.
     */
    private final List<WeakReference<Endpoint>> endpoints
        = new LinkedList<WeakReference<Endpoint>>();

    /**
     * The indicator which determines whether {@link #expire()} has been called
     * on this <tt>Conference</tt>.
     */
    private boolean expired = false;

    /**
     * The JID of the conference focus who has initialized this instance and
     * from whom requests to manage this instance must come or they will be
     * ignored. If <tt>null</tt> value is assigned we don't care who modifies
     * the conference.
     */
    private final String focus;

    /**
     * The (unique) identifier/ID of this instance.
     */
    private final String id;

    /**
     * The time in milliseconds of the last activity related to this
     * <tt>Conference</tt>. In the time interval between the last activity and
     * now, this <tt>Conference</tt> is considered inactive.
     */
    private long lastActivityTime;

    private final PropertyChangeListener propertyChangeListener;

    /**
     * The speech activity (representation) of the <tt>Endpoint</tt>s of this
     * <tt>Conference</tt>.
     */
    private final ConferenceSpeechActivity speechActivity;

    /**
     * The <tt>Videobridge</tt> which has initialized this <tt>Conference</tt>.
     */
    private final Videobridge videobridge;

    /**
     * Initializes a new <tt>Conference</tt> instance which is to represent a
     * conference in the terms of Jitsi Videobridge which has a specific
     * (unique) ID and is managed by a conference focus with a specific JID.
     *
     * @param videobridge the <tt>Videobridge</tt> on which the new
     * <tt>Conference</tt> instance is to be initialized
     * @param id the (unique) ID of the new instance to be initialized
     * @param focus the JID of the conference focus who has requested the
     * initialization of the new instance and from whom further/future requests
     * to manage the new instance must come or they will be ignored.
     * Pass <tt>null</tt> to override this safety check.
     */
    public Conference(Videobridge videobridge, String id, String focus)
    {
        if (videobridge == null)
            throw new NullPointerException("videobridge");
        if (id == null)
            throw new NullPointerException("id");

        this.videobridge = videobridge;
        this.id = id;
        this.focus = focus;

        propertyChangeListener = new WeakReferencePropertyChangeListener(this);
        speechActivity = new ConferenceSpeechActivity(this);
        speechActivity.addPropertyChangeListener(propertyChangeListener);
    }

    /**
     * Sets the values of the properties of a specific
     * <tt>ColibriConferenceIQ</tt> to the values of the respective
     * properties of this instance. Thus, the specified <tt>iq</tt> may be
     * thought of as a description of this instance.
     * <p>
     * <b>Note</b>: The copying of the values is deep i.e. the
     * <tt>Contents</tt>s of this instance are described in the specified
     * <tt>iq</tt>.
     * </p>
     *
     * @param iq the <tt>ColibriConferenceIQ</tt> to set the values of the
     * properties of this instance on
     */
    public void describeDeep(ColibriConferenceIQ iq)
    {
        describeShallow(iq);

        for (Content content : getContents())
        {
            ColibriConferenceIQ.Content contentIQ
                = iq.getOrCreateContent(content.getName());

            for (Channel channel : content.getChannels())
            {
                ColibriConferenceIQ.Channel channelIQ
                    = new ColibriConferenceIQ.Channel();

                channel.describe(channelIQ);
                contentIQ.addChannel(channelIQ);
            }
        }
    }

    /**
     * Sets the values of the properties of a specific
     * <tt>ColibriConferenceIQ</tt> to the values of the respective
     * properties of this instance. Thus, the specified <tt>iq</tt> may be
     * thought of as a description of this instance.
     * <p>
     * <b>Note</b>: The copying of the values is shallow i.e. the
     * <tt>Content</tt>s of this instance are not described in the specified
     * <tt>iq</tt>.
     * </p>
     *
     * @param iq the <tt>ColibriConferenceIQ</tt> to set the values of the
     * properties of this instance on
     */
    public void describeShallow(ColibriConferenceIQ iq)
    {
        iq.setID(getID());
    }

    /**
     * Notifies this instance that {@link #speechActivity} has identified a
     * speaker switch event in this multipoint conference and there is now a new
     * dominant speaker.
     */
    private void dominantSpeakerChanged()
    {
        Endpoint dominantSpeaker = speechActivity.getDominantEndpoint();

        logd(
                "The dominant speaker in conference " + getID()
                    + " is now the endpoint "
                    + ((dominantSpeaker == null)
                        ? "(null)"
                        : dominantSpeaker.getID())
                    + ".");
    }

    /**
     * Expires this <tt>Conference</tt>, its <tt>Content</tt>s and their
     * respective <tt>Channel</tt>s. Releases the resources acquired by this
     * instance throughout its life time and prepares it to be garbage
     * collected.
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

        Videobridge videobridge = getVideobridge();

        try
        {
            videobridge.expireConference(this);
        }
        finally
        {
            // Expire the Contents of this Conference.
            for (Content content : getContents())
            {
                try
                {
                    content.expire();
                }
                catch (Throwable t)
                {
                    logger.warn(
                            "Failed to expire content " + content.getName()
                                + " of conference " + getID() + "!",
                            t);
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                }
            }

            logd(
                    "Expired conference " + getID() + ". The total number of"
                        + " conferences is now "
                        + videobridge.getConferenceCount() + ", channels "
                        + videobridge.getChannelCount() + ".");
        }
    }

    /**
     * Expires a specific <tt>Content</tt> of this <tt>Conference</tt> (i.e. if
     * the specified <tt>content</tt> is not in the list of <tt>Content</tt>s of
     * this <tt>Conference</tt>, does nothing).
     *
     * @param content the <tt>Content</tt> to be expired by this
     * <tt>Conference</tt>
     */
    public void expireContent(Content content)
    {
        boolean expireContent;

        synchronized (contents)
        {
            if (contents.contains(content))
            {
                contents.remove(content);
                expireContent = true;
            }
            else
                expireContent = false;
        }
        if (expireContent)
            content.expire();
    }

    /**
     * Finds a <tt>Channel</tt> of this <tt>Conference</tt> which receives a
     * specific SSRC and is with a specific <tt>MediaType</tt>.
     *
     * @param receiveSSRC the SSRC of a received RTP stream whose receiving
     * <tt>Channel</tt> in this <tt>Conference</tt> is to be found
     * @param mediaType the <tt>MediaType</tt> of the <tt>Channel</tt> to be
     * found
     * @return the <tt>Channel</tt> in this <tt>Conference</tt> which receives
     * the specified <tt>ssrc</tt> and is with the specified <tt>mediaType</tt>;
     * otherwise, <tt>null</tt>
     */
    Channel findChannelByReceiveSSRC(long receiveSSRC, MediaType mediaType)
    {
        for (Content content : getContents())
        {
            if (mediaType.equals(content.getMediaType()))
            {
                Channel channel = content.findChannelByReceiveSSRC(receiveSSRC);
                if (channel != null)
                    return channel;
            }
        }
        return null;
    }

    /**
     * Finds an <tt>Endpoint</tt> of this <tt>Conference</tt> which sends an RTP
     * stream with a specific SSRC and with a specific <tt>MediaType</tt>.
     *
     * @param receiveSSRC the SSRC of an RTP stream received by this
     * <tt>Conference</tt> whose sending <tt>Endpoint</tt> is to be found
     * @param mediaType the <tt>MediaType</tt> of the RTP stream identified by
     * the specified <tt>ssrc</tt>
     * @return <tt>Endpoint</tt> of this <tt>Conference</tt> which sends an RTP
     * stream with the specified <tt>ssrc</tt> and with the specified
     * <tt>mediaType</tt>; otherwise, <tt>null</tt>
     */
    Endpoint findEndpointByReceiveSSRC(long receiveSSRC, MediaType mediaType)
    {
        Channel channel = findChannelByReceiveSSRC(receiveSSRC, mediaType);

        return (channel == null) ? null : channel.getEndpoint();
    }

    /**
     * Returns the OSGi <tt>BundleContext</tt> in which this Conference is
     * executing.
     *
     * @return the OSGi <tt>BundleContext</tt> in which the Conference is
     * executing.
     */
    public BundleContext getBundleContext()
    {
        return getVideobridge().getBundleContext();
    }

    /**
     * Gets the <tt>Content</tt>s of this <tt>Conference</tt>.
     *
     * @return the <tt>Content</tt>s of this <tt>Conference</tt>
     */
    public Content[] getContents()
    {
        synchronized (contents)
        {
            return contents.toArray(new Content[contents.size()]);
        }
    }

    /**
     * Gets the <tt>Endpoint</tt>s participating in/contributing to this
     * <tt>Conference</tt>.
     *
     * @return the <tt>Endpoint</tt>s participating in/contributing to this
     * <tt>Conference</tt>
     */
    public List<Endpoint> getEndpoints()
    {
        List<Endpoint> endpoints;
        boolean changed = false;

        synchronized (this.endpoints)
        {
            endpoints = new ArrayList<Endpoint>(this.endpoints.size());

            for (Iterator<WeakReference<Endpoint>> i
                        = this.endpoints.iterator();
                    i.hasNext();)
            {
                Endpoint endpoint = i.next().get();

                if (endpoint == null)
                {
                    i.remove();
                    changed = true;
                }
                else
                {
                    endpoints.add(endpoint);
                }
            }
        }

        if (changed)
            firePropertyChange(ENDPOINTS_PROPERTY_NAME, null, null);

        return endpoints;
    }

    /**
     * Gets the JID of the conference focus who has initialized this instance
     * and from whom requests to manage this instance must come or they will be
     * ignored.
     *
     * @return the JID of the conference focus who has initialized this instance
     * and from whom requests to manage this instance must come or they will be
     * ignored
     */
    public final String getFocus()
    {
        return focus;
    }

    /**
     * Gets the (unique) identifier/ID of this instance.
     *
     * @return the (unique) identifier/ID of this instance
     */
    public final String getID()
    {
        return id;
    }

    /**
     * Gets the time in milliseconds of the last activity related to this
     * <tt>Conference</tt>.
     *
     * @return the time in milliseconds of the last activity related to this
     * <tt>Conference</tt>
     */
    public long getLastActivityTime()
    {
        synchronized (this)
        {
            return lastActivityTime;
        }
    }

    /**
     * Gets a <tt>Content</tt> of this <tt>Conference</tt> which has a specific
     * name. If a <tt>Content</tt> of this <tt>Conference</tt> with the
     * specified <tt>name</tt> does not exist at the time the method is invoked,
     * the method initializes a new <tt>Content</tt> instance with the specified
     * <tt>name</tt> and adds it to the list of <tt>Content</tt>s of this
     * <tt>Conference</tt>.
     *
     * @param name the name of the <tt>Content</tt> which is to be returned
     * @return a <tt>Content</tt> of this <tt>Conference</tt> which has the
     * specified <tt>name</tt>
     */
    public Content getOrCreateContent(String name)
    {
        Content content;

        synchronized (contents)
        {
            for (Content aContent : contents)
            {
                if (aContent.getName().equals(name))
                {
                    aContent.touch(); // It seems the content is still active.
                    return aContent;
                }
            }

            content = new Content(this, name);
            contents.add(content);
        }

        /*
         * The method Videobridge.getChannelCount() should better be executed
         * outside synchronized blocks in order to reduce the risks of causing
         * deadlocks.
         */
        Videobridge videobridge = getVideobridge();

        logd(
                "Created content " + name + " of conference " + getID()
                    + ". The total number of conferences is now "
                    + videobridge.getConferenceCount() + ", channels "
                    + videobridge.getChannelCount() + ".");

        return content;
    }

    /**
     * Gets an <tt>Endpoint</tt> participating in this <tt>Conference</tt> which
     * has a specific identifier/ID. If an <tt>Endpoint</tt> participating in
     * this <tt>Conference</tt> with the specified <tt>id</tt> does not exist at
     * the time the method is invoked, the method initializes a new
     * <tt>Endpoint</tt> instance with the specified <tt>id</tt> and adds it to
     * the list of <tt>Endpoint</tt>s participating in this <tt>Conference</tt>.
     *
     * @param id the identifier/ID of the <tt>Endpoint</tt> which is to be
     * returned
     * @return an <tt>Endpoint</tt> participating in this <tt>Conference</tt>
     * which has the specified <tt>id</tt>
     */
    public Endpoint getOrCreateEndpoint(String id)
    {
        Endpoint endpoint = null;
        boolean changed = false;

        synchronized (endpoints)
        {
            for (Iterator<WeakReference<Endpoint>> i = endpoints.iterator();
                    i.hasNext();)
            {
                Endpoint e = i.next().get();
                if (e == null)
                {
                    i.remove();
                    changed = true;
                }
                else if (e.getID().equals(id))
                {
                    endpoint = e;
                }
            }

            if (endpoint == null)
            {
                endpoint = new Endpoint(id);
                /*
                 * The propertyChangeListener will weakly reference this
                 * Conference and will unregister itself from the endpoint
                 * sooner or later.
                 */
                endpoint.addPropertyChangeListener(propertyChangeListener);

                endpoints.add(new WeakReference<Endpoint>(endpoint));
                changed = true;
            }
        }

        if (changed)
            firePropertyChange(ENDPOINTS_PROPERTY_NAME, null, null);

        return endpoint;
    }

    /**
     * Gets the speech activity (representation) of the <tt>Endpoint</tt>s of
     * this <tt>Conference</tt>.
     *
     * @return the speech activity (representation) of the <tt>Endpoint</tt>s of
     * this <tt>Conference</tt>
     */
    ConferenceSpeechActivity getSpeechActivity()
    {
        return speechActivity;
    }

    /**
     * Gets the <tt>Videobridge</tt> which has initialized this
     * <tt>Conference</tt>.
     *
     * @return the <tt>Videobridge</tt> which has initialized this
     * <tt>Conference</tt>
     */
    public final Videobridge getVideobridge()
    {
        return videobridge;
    }

    /**
     * Notifies this instance that there was a change in the value of a property
     * of an object in which this instance is interested.
     *
     * @param ev a <tt>PropertyChangeEvent</tt> which specifies the object of
     * interest, the name of the property and the old and new values of that
     * property
     */
    public void propertyChange(PropertyChangeEvent ev)
    {
        Object source = ev.getSource();

        if (speechActivity == source)
        {
            String propertyName = ev.getPropertyName();

            if (ConferenceSpeechActivity.DOMINANT_ENDPOINT_PROPERTY_NAME.equals(
                    propertyName))
            {
                dominantSpeakerChanged();
            }
            else if (ConferenceSpeechActivity.ENDPOINTS_PROPERTY_NAME.equals(
                    propertyName))
            {
                speechActivityEndpointsChanged();
            }
        }
    }

    private void speechActivityEndpointsChanged()
    {
        List<Endpoint> endpoints = null;

        for (Content content : getContents())
        {
            if (MediaType.VIDEO.equals(content.getMediaType()))
            {
                Set<Endpoint> endpointsToAskForKeyframes = null;

                endpoints = speechActivity.getEndpoints();
                for (Channel channel : content.getChannels())
                {
                    List<Endpoint> channelEndpointsToAskForKeyframes
                        = channel.lastNEndpointsChanged(endpoints);

                    if ((channelEndpointsToAskForKeyframes != null)
                            && !channelEndpointsToAskForKeyframes.isEmpty())
                    {
                        if (endpointsToAskForKeyframes == null)
                        {
                            endpointsToAskForKeyframes
                                = new HashSet<Endpoint>();
                        }
                        endpointsToAskForKeyframes.addAll(
                                channelEndpointsToAskForKeyframes);
                    }
                }

                if ((endpointsToAskForKeyframes != null)
                        && !endpointsToAskForKeyframes.isEmpty())
                {
                    content.askForKeyframes(endpointsToAskForKeyframes);
                }
            }
        }
    }

    /**
     * Sets the time in milliseconds of the last activity related to this
     * <tt>Conference</tt> to the current system time.
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
}
