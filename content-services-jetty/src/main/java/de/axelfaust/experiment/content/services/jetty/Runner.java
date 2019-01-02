package de.axelfaust.experiment.content.services.jetty;

import java.util.Arrays;

import org.eclipse.jetty.server.Handler;

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

    protected static Handler prepareRootContextHandler()
    {
        try
        {
            return prepareContext();
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

    protected static Handler prepareAlfrescoContextHandler()
    {
        try
        {
            return prepareContext("alfresco", "./config/content-services");
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
