package de.axelfaust.experiment.generic.jetty;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ShutdownHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.CachingWebAppClassLoader;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

/**
 *
 * @author Axel Faust
 */
public abstract class GenericRunner
{

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericRunner.class);

    private static final Set<String> PRIMARY_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("start", "stop")));

    public abstract static class BaseOptions
    {

        @Argument(required = false, description = "The port on which to run the server or contact the server for shutdown")
        private Integer port;

        public void setPort(final Integer port)
        {
            this.port = port;
        }

        public Integer getPort()
        {
            return this.port;
        }

        public int getEffectivePort()
        {
            final int port = this.port != null ? this.port.intValue() : 8080;
            if (port <= 0)
            {
                throw new IllegalArgumentException("'port' must be a positive integer");
            }
            return port;
        }
    }

    public static class StartOptions extends BaseOptions
    {
        // No additions yet
    }

    public static class StopOptions extends BaseOptions
    {
        // No additions yet
    }

    protected static <SO1 extends StartOptions> void runMain(final String[] args, final Supplier<SO1> startOptionsBuilder,
            final Function<SO1, Integer> starter)
    {
        if (args.length == 0 || !PRIMARY_COMMANDS.contains(args[0].toLowerCase(Locale.ENGLISH)))
        {
            System.err.println("Missing primary command");
            System.exit(1);
        }

        final String primaryCommand = args[0].toLowerCase(Locale.ENGLISH);
        final String[] remainingArgs = new String[args.length - 1];
        System.arraycopy(args, 1, remainingArgs, 0, remainingArgs.length);

        int exitCode = 0;
        switch (primaryCommand)
        {
            case "start":
                final SO1 startOptions = startOptionsBuilder.get();
                Args.parseOrExit(startOptions, remainingArgs);
                exitCode = starter.apply(startOptions);
                break;
            case "stop":
                final StopOptions stopOptions = new StopOptions();
                Args.parseOrExit(stopOptions, remainingArgs);
                exitCode = stopServer(stopOptions);
                break;
        }

        System.exit(exitCode);
    }

    protected static StartOptions defaultStartOptions()
    {
        return new StartOptions();
    }

    protected static Integer runServer(final StartOptions options, final Collection<Function<Resource, Handler>> contextHandlerSuppliers)
    {
        int exitCode;
        try
        {
            LOGGER.info("Starting server");
            final String shutdownToken = UUID.randomUUID().toString();
            final Server server = setupServer(shutdownToken, options, contextHandlerSuppliers);
            storeShutdownToken(shutdownToken);
            server.start();
            LOGGER.info("Server started");

            server.join();

            LOGGER.info("Server stopped");

            exitCode = 0;
        }
        catch (final Exception ex)
        {
            LOGGER.error("Error running server", ex);
            exitCode = 1;
        }
        finally
        {
            deleteShutdownToken();
        }
        return exitCode;
    }

    protected static Handler prepareContext(final Resource jarRootResource) throws Exception
    {
        return prepareContext(jarRootResource, null, null, null);
    }

    protected static Handler prepareContext(final Resource jarRootResource, final String contextName, final String extraClassPath)
            throws Exception
    {
        return prepareContext(jarRootResource, contextName, null, extraClassPath);
    }

    protected static Handler prepareContext(final Resource jarRootResource, final String contextName, final String rootResourcePath,
            final String extraClassPath) throws Exception
    {
        final WebAppContext context = new WebAppContext();
        context.setContextPath("/" + (contextName != null ? contextName : ""));

        final List<String> serverClasses = new ArrayList<>(Arrays.asList(WebAppContext.__dftServerClasses));
        // hide logging classes from webapp
        serverClasses.add("org.slf4j.");
        serverClasses.add("org.apache.log4j.");
        // hide CLI classes from webapp
        serverClasses.add("com.sampullara.");
        context.setServerClasses(serverClasses.toArray(new String[0]));

        final String webappPath = "webapps/" + (contextName != null ? contextName : "ROOT");
        final Resource baseResource = jarRootResource.addPath(webappPath);
        context.setBaseResource(baseResource);
        context.setParentLoaderPriority(false);
        final CachingWebAppClassLoader cl = new CachingWebAppClassLoader(context);
        context.setClassLoader(cl);
        cl.addClassPath(baseResource.addPath("WEB-INF/classes/"));
        if (extraClassPath != null)
        {
            cl.addClassPath(extraClassPath);
        }

        return context;
    }

    private static Server setupServer(final String shutdownToken, final StartOptions options,
            final Collection<Function<Resource, Handler>> contextHandlerSuppliers) throws IOException
    {
        final Server server = new Server(options.getEffectivePort());

        final Configuration.ClassList classlist = Configuration.ClassList.setServerDefault(server);
        classlist.addBefore(org.eclipse.jetty.webapp.JettyWebXmlConfiguration.class.getName(),
                org.eclipse.jetty.annotations.AnnotationConfiguration.class.getName());
        classlist.addAfter(org.eclipse.jetty.webapp.FragmentConfiguration.class.getName());

        final List<Handler> handlers = new ArrayList<>();
        handlers.add(new ShutdownHandler(shutdownToken));

        final URL rootResource = GenericRunner.class.getClassLoader().getResource("log4j.properties");
        String file = rootResource.getFile();
        file = file.substring(0, file.lastIndexOf('/') + 1);
        final URL jarResourceURL = new URL(rootResource.getProtocol(), rootResource.getHost(), rootResource.getPort(), file);
        final String jarResourceURLExternal = jarResourceURL.toExternalForm();
        final Resource jarRootResource = jarResourceURLExternal.startsWith("jar:file:")
                ? new EntryNamesCachingJarFileResource(jarResourceURL)
                : Resource.newResource(jarResourceURL);

        contextHandlerSuppliers.forEach(handlerSupplier -> handlers.add(handlerSupplier.apply(jarRootResource)));

        final HandlerList handlerList = new HandlerList(handlers.toArray(new Handler[0]));

        final StatisticsHandler statisticsHandler = new StatisticsHandler();
        statisticsHandler.setHandler(handlerList);

        server.setHandler(statisticsHandler);
        return server;
    }

    private static void storeShutdownToken(final String shutdownToken)
    {
        final Path path = Paths.get("shutdown_token");
        LOGGER.debug("Storing shutdown token in {}", path.toAbsolutePath());
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE),
                StandardCharsets.UTF_8)))
        {
            pw.println(shutdownToken);
        }
        catch (final IOException ioEx)
        {
            LOGGER.error("Failed to store shutdown token", ioEx);
        }
    }

    private static void deleteShutdownToken()
    {
        final Path path = Paths.get("shutdown_token");
        final File file = path.toFile();
        if (file.exists())
        {
            LOGGER.debug("Deleting shutdown token in {}", path.toAbsolutePath());
            if (!file.delete())
            {
                file.deleteOnExit();
            }
        }
    }

    private static int stopServer(final StopOptions options)
    {
        int exitCode;
        try
        {
            LOGGER.info("Sending request to stop Webhook Hub");
            final String shutdownToken = loadShutdownToken();

            final StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("http://localhost:");
            urlBuilder.append(options.getEffectivePort());
            urlBuilder.append("/shutdown?token=");
            urlBuilder.append(shutdownToken);

            final URL url = new URL(urlBuilder.toString());
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");

            final int responseCode = connection.getResponseCode();
            if (responseCode == 200)
            {
                LOGGER.info("Request to stop server completed");
                exitCode = 0;
            }
            else
            {
                LOGGER.warn("Request to stop server did not complete as expected with {}", connection.getResponseMessage());
                exitCode = 2;
            }
        }
        catch (final Exception ex)
        {
            LOGGER.error("Error stopping server", ex);
            exitCode = 1;
        }
        return exitCode;
    }

    private static String loadShutdownToken() throws IOException
    {
        final Path path = Paths.get("shutdown_token");
        LOGGER.info("Loading shutdown token from {}", path.toAbsolutePath());
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(Files.newInputStream(path, StandardOpenOption.READ), StandardCharsets.UTF_8)))
        {
            final String shutdownToken = br.readLine();
            return shutdownToken;
        }
        catch (final IOException ioEx)
        {
            LOGGER.error("Failed to load shutdown token", ioEx);
            throw ioEx;
        }
    }
}
