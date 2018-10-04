package de.axelfaust.experiment.share.jetty;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 *
 * @author Axel Faust
 */
public class Runner
{

    public static void main(final String[] args) throws Exception
    {
        final Server server = new Server(8080);

        final Configuration.ClassList classlist = Configuration.ClassList.setServerDefault(server);
        classlist.addBefore(org.eclipse.jetty.webapp.JettyWebXmlConfiguration.class.getName(),
                org.eclipse.jetty.annotations.AnnotationConfiguration.class.getName());
        classlist.addAfter(org.eclipse.jetty.webapp.FragmentConfiguration.class.getName(),
                org.eclipse.jetty.plus.webapp.EnvConfiguration.class.getName(),
                org.eclipse.jetty.plus.webapp.PlusConfiguration.class.getName());

        final Handler handlers = prepareHandlers();
        server.setHandler(handlers);

        server.start();

        server.join();
    }

    protected static Handler prepareHandlers()
    {
        final List<Supplier<Handler>> contextHandlerSuppliers = Arrays.asList(Runner::prepareShareContextHandler);

        final List<Handler> handlers = new ArrayList<>();
        contextHandlerSuppliers.forEach(handlerSupplier -> handlers.add(handlerSupplier.get()));
        final HandlerList handlerList = new HandlerList(handlers.toArray(new Handler[0]));

        final StatisticsHandler statisticsHandler = new StatisticsHandler();
        statisticsHandler.setHandler(handlerList);
        return statisticsHandler;
    }

    protected static Handler prepareShareContextHandler()
    {
        try
        {
            final WebAppContext context = new WebAppContext();
            context.setContextPath("/share");

            // resolve concrete file to determine directory URL
            final URL rootFaviconResource = Runner.class.getClassLoader().getResource("webapps/share/favicon.ico");

            String file = rootFaviconResource.getFile();
            file = file.substring(0, file.lastIndexOf('/') + 1);
            final URL webAppResource = new URL(rootFaviconResource.getProtocol(), rootFaviconResource.getHost(),
                    rootFaviconResource.getPort(), file);

            final Resource webApp = Resource.newResource(webAppResource);
            context.setBaseResource(webApp);
            context.setExtraClasspath("./config/share");

            final WebAppClassLoader webAppClassLoader = new WebAppClassLoader(context);
            // TODO We need to separate webapp class loader (and classes) from the base for Jetty et al
            // Currently we have conflicts relating to Jetty / org.apache.juli.logging
            final URL rootResource = new URL(rootFaviconResource.getProtocol(), rootFaviconResource.getHost(),
                    rootFaviconResource.getPort(), file.substring(0, file.indexOf("/webapps") + 1));
            webAppClassLoader.addClassPath(Resource.newResource(rootResource));
            context.setClassLoader(webAppClassLoader);

            return context;
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
