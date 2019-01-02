package de.axelfaust.experiment.share.jetty;

import java.util.Arrays;

import org.eclipse.jetty.server.Handler;

import de.axelfaust.experiment.generic.jetty.GenericRunner;

/**
 *
 * @author Axel Faust
 */
public class Runner extends GenericRunner
{

    public static void main(final String[] args) throws Exception
    {
        runMain(args, GenericRunner::defaultStartOptions, options -> runServer(options, Arrays.asList(Runner::prepareShareContextHandler)));
    }

    protected static Handler prepareShareContextHandler()
    {
        try
        {
            return prepareContext("share", "./config/share");
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
