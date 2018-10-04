package de.axelfaust.experiment.share.jetty;

import java.io.InputStream;
import java.util.List;

import javax.servlet.ServletContext;

import org.springframework.extensions.config.source.ClassPathConfigSource;
import org.springframework.extensions.config.source.UrlConfigSource;

/**
 * @author Axel Faust
 */
public class ImproveWebappHandlingUrlConfigSource extends UrlConfigSource
{

    protected ServletContext servletContext;

    /**
     * Constructs a config location that figures out where to look for the config
     *
     * @param sourceLocation
     *            The location from which to get config
     *
     * @see ClassPathConfigSource#ClassPathConfigSource(java.util.List)
     */
    public ImproveWebappHandlingUrlConfigSource(final String sourceLocation)
    {
        super(sourceLocation);
    }

    /**
     * Constructs a config location that figures out where to look for the
     * config
     *
     * @param sourceLocation
     *            The location from which to get config
     * @param init
     *            indicates whether sourceLocation should be processed directly
     *            in constructor
     *
     * @see ClassPathConfigSource#ClassPathConfigSource(java.util.List)
     */
    public ImproveWebappHandlingUrlConfigSource(final String sourceLocation, final boolean init)
    {
        super(sourceLocation, init);
    }

    /**
     * Constructs a config location that figures out where to look for the config
     *
     * @param sourceLocations
     *            List of locations from which to get the config
     */
    public ImproveWebappHandlingUrlConfigSource(final List<String> sourceLocations)
    {
        super(sourceLocations);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setServletContext(final ServletContext servletContext)
    {
        super.setServletContext(servletContext);
        this.servletContext = servletContext;
    }

    /**
     * Constructs a config location that figures out where to look for the
     * config
     *
     * @param sourceLocations
     *            List of locations from which to get the config
     * @param init
     *            indicates whether sourceLocations should be processed directly
     *            in constructor
     */
    public ImproveWebappHandlingUrlConfigSource(final List<String> sourceLocations, final boolean init)
    {
        super(sourceLocations, init);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public InputStream getInputStream(final String sourceUrl)
    {
        InputStream is;
        if (sourceUrl.startsWith(PREFIX_WEBAPP))
        {
            if (this.servletContext != null)
            {
                String sourceString = sourceUrl.substring(PREFIX_WEBAPP.length());
                if (!sourceString.startsWith("/"))
                {
                    sourceString = "/" + sourceString;
                }

                is = this.servletContext.getResourceAsStream(sourceString);
                if (is == null)
                {
                    is = super.getInputStream(sourceUrl);
                }
            }
            else
            {
                is = super.getInputStream(sourceUrl);
            }
        }
        else
        {
            is = super.getInputStream(sourceUrl);
        }
        return is;
    }
}
