/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.videobridge.osgi;

import net.java.sip.communicator.util.*;

import org.jitsi.service.configuration.*;
import org.jitsi.util.Logger;
import org.osgi.framework.*;

/**
 * Implements a <tt>BundleActivator</tt> for <tt>OSGi</tt> which starts and
 * stops it in a <tt>BundleContext</tt>.
 * <p>
 * <b>Warning</b>: The class <tt>OSGiBundleActivator</tt> is to be considered
 * internal, its access modifier is public in order to allow the OSGi framework
 * to find it by name and instantiate it.
 * </p>
 *
 * @author Lyubomir Marinov
 */
public class OSGiBundleActivator
    implements BundleActivator
{
    /**
     * The <tt>Logger</tt> used by the <tt>OSGiBundleActivator</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(OSGiBundleActivator.class);

    /**
     * Logs the properties of the <tt>ConfigurationService</tt> for the purposes
     * of debugging.
     *
     * @param bundleContext
     */
    private void logConfigurationServiceProperties(BundleContext bundleContext)
    {
        if (!logger.isInfoEnabled())
            return;

        boolean interrupted = false;

        try
        {
            if (bundleContext != null)
            {
                ConfigurationService cfg
                    = ServiceUtils.getService(
                            bundleContext,
                            ConfigurationService.class);

                if (cfg != null)
                {
                    for (String p : cfg.getAllPropertyNames())
                    {
                        Object v = cfg.getProperty(p);

                        if (v != null)
                            logger.info(p + "=" + v);
                    }
                }
            }
        }
        catch (Throwable t)
        {
            if (t instanceof InterruptedException)
                interrupted = true;
            else if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
        }
        finally
        {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }

    /**
     * Starts the <tt>OSGi</tt> class in a <tt>BundleContext</tt>.
     *
     * @param bundleContext the <tt>BundleContext</tt> in which the
     * <tt>OSGi</tt> class is to start
     */
    @Override
    public void start(BundleContext bundleContext)
        throws Exception
    {
        logConfigurationServiceProperties(bundleContext);

        OSGi.start(bundleContext);
    }

    /**
     * Stops the <tt>OSGi</tt> class in a <tt>BundleContext</tt>.
     *
     * @param bundleContext the <tt>BundleContext</tt> in which the
     * <tt>OSGi</tt> class is to stop
     */
    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        OSGi.stop(bundleContext);
    }
}
