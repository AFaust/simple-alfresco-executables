package de.axelfaust.experiment.content.services.jetty;

import java.util.Arrays;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.resource.Resource;

import de.axelfaust.experiment.generic.jetty.GenericRunner;

/**
 * @author Axel Faust
 */
public class Runner extends GenericRunner
{

    public static void main(final String[] args) throws Exception
    {
        runMain(args, GenericRunner::defaultStartOptions,
                options -> runServer(options, Arrays.asList(Runner::prepareAlfrescoContextHandler, Runner::prepareRootContextHandler)));
    }

    protected static Handler prepareRootContextHandler(final Resource jarRootResource)
    {
        try
        {
            return prepareContext(jarRootResource);
        }
        catch (final Exception e)
        {
            if (e instanceof RuntimeException)
            {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

    protected static Handler prepareAlfrescoContextHandler(final Resource jarRootResource)
    {
        try
        {
            return prepareContext(jarRootResource, "alfresco", "./config/content-services");
        }
        catch (final Exception e)
        {
            if (e instanceof RuntimeException)
            {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }
}
