package de.axelfaust.experiment.content.services.adaptions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.alfresco.repo.web.scripts.RepoClassPathStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.extensions.webscripts.ClassPathStoreResourceResolver;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.util.ResourceUtils;

/**
 *
 * @author Axel Faust
 */
public class FatJarAwareRepoClassPathStore extends RepoClassPathStore
{
    // due to excessive use of private methods in base class we have to duplicate a lot of code just to fix toDocumentPath(String)

    private static final Logger LOGGER = LoggerFactory.getLogger(FatJarAwareRepoClassPathStore.class);

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void init()
    {
        super.init();

        // private getDocumentResource in base class does not prefix resourcePath with classpath:, causing misses and potential unwanted
        // override via servlet resource lookup
        // indirectly, such misses cause issues with JVM handling of JarFile / JarURLConnection handling for fat JAR use case unless
        // explicitly forcing caching
        this.resolver = new ClassPathStoreResourceResolver(this.applicationContext)
        {

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public Resource getResource(final String location)
            {
                String effectiveLocation = location;
                final boolean notClassPathLocation = !location.startsWith("classpath*:") && !location.startsWith("classpath:");
                if (notClassPathLocation)
                {
                    effectiveLocation = "classpath:" + effectiveLocation;
                }

                Resource resource = null;
                final Resource r = this.getResourceLoader().getResource(effectiveLocation);
                if (r != null && r.exists())
                {
                    resource = r;
                }

                return resource;
            }

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public Resource[] getResources(final String location) throws IOException
            {
                String effectiveLocation = location;
                if (!effectiveLocation.startsWith("classpath*:") && !effectiveLocation.startsWith("classpath:"))
                {
                    effectiveLocation = "classpath*:" + effectiveLocation;
                }
                return super.getResources(effectiveLocation);
            }
        };
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String[] getAllDocumentPaths()
    {
        String[] paths;

        try
        {
            final List<String> documentPaths = this.matchDocumentPaths("/**/*");
            paths = documentPaths.toArray(new String[documentPaths.size()]);
        }
        catch (final IOException e)
        {
            // Note: Ignore: no documents found
            paths = new String[0];
        }

        return paths;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String[] getDocumentPaths(String path, final boolean includeSubPaths, String documentPattern) throws IOException
    {
        if ((path == null) || (path.length() == 0))
        {
            path = "/";
        }

        if (!path.startsWith("/"))
        {
            path = "/" + path;
        }

        if (!path.endsWith("/"))
        {
            path = path + "/";
        }

        if ((documentPattern == null) || (documentPattern.length() == 0))
        {
            documentPattern = "*";
        }

        // classpath*:
        final StringBuilder pattern = new StringBuilder(128);
        pattern.append(path).append((includeSubPaths ? "**/" : "")).append(documentPattern);

        final List<String> documentPaths = this.matchDocumentPaths(pattern.toString());
        return documentPaths.toArray(new String[documentPaths.size()]);
    }

    /**
     * Matches the given path to the full class path that is comprised of class files and resources located
     * inside of JAR files that are on the class path.
     *
     * @param pattern
     *            The pattern to match
     *
     * @return matching paths
     *
     * @throws IOException
     *             if resource resolution fails
     */
    protected List<String> matchDocumentPaths(final String pattern) throws IOException
    {
        final Resource[] resources = this.getDocumentResources(pattern);
        final List<String> documentPaths = new ArrayList<>(resources.length);
        for (final Resource resource : resources)
        {
            final String documentPath = this.toDocumentPath(resource.getURL().toExternalForm());
            documentPaths.add(documentPath);
        }
        return documentPaths;
    }

    /**
     * Gets resources that match a given location pattern. A resource in the returned array can live
     * in the class path as either a class file or as an entry within one of the JAR files in the
     * class path.
     *
     * @param locationPattern
     *            String
     *
     * @return resources that match location pattern - can be empty but never null
     *
     * @throws IOException
     *             if resource resolution fails
     */
    protected Resource[] getDocumentResources(final String locationPattern) throws IOException
    {
        final String resourcePath = this.toResourcePath(locationPattern);

        final Resource[] resources = this.resolver.getResources("classpath*:" + resourcePath);
        final ArrayList<Resource> list = new ArrayList<>(resources.length);
        for (final Resource resource : resources)
        {
            // only keep documents, not directories
            if (!resource.getURL().toExternalForm().endsWith("/"))
            {
                list.add(resource);
            }
        }

        return list.toArray(new Resource[list.size()]);
    }

    /**
     * Converts a document path to a resource path.
     *
     * A document path is relative to the base path of the store. It is what users of the store pass in when
     * they call the methods of the store.
     *
     * A resource path includes the base path and is descriptive of the resource relative to the root of the
     * resource tree.
     *
     * @param documentPath
     *            String
     *
     * @return resource path
     */
    protected String toResourcePath(final String documentPath)
    {
        return createPath(this.classPath, documentPath);
    }

    /**
     * Converts a resource path back to a document path.
     *
     * A document path is relative to the base path of the store. It is what users of the store pass in when
     * they call the methods of the store.
     *
     * A resource path includes the base path and is descriptive of the resource relative to the root of the
     * resource tree.
     *
     * @param resourcePath
     *            String
     *
     * @return document path
     */
    protected String toDocumentPath(final String resourcePath)
    {
        String documentPath = null;

        // check if this is a valid url (either a java URL or a Spring classpath prefix URL)
        try
        {
            final URL url = ResourceUtils.getURL(resourcePath);

            String urlString = resourcePath;

            // if the URL is a JAR url, trim off the reference to the JAR
            if (isJarURL(url))
            {
                // find the URL to the jar file and split off the prefix portion that references the jar file
                final String jarUrlString = extractJarFileURL(url).toExternalForm();

                final int x = urlString.indexOf(jarUrlString);
                if (x != -1)
                {
                    urlString = urlString.substring(x + jarUrlString.length());

                    // remove a prefix ! if it is found
                    if (urlString.charAt(0) == '!')
                    {
                        urlString = urlString.substring(1);
                    }

                    // remove a prefix / if it is found
                    if (urlString.charAt(0) == '/')
                    {
                        urlString = urlString.substring(1);
                    }
                }

                // MARKER: Actual fix
                // we expect the classpath in the JAR-internal path
                // need to strip any prefix path in case classloader is targeting a sub-path inside the JAR
                final int y = urlString.indexOf(this.classPath);
                if (y != -1)
                {
                    urlString = urlString.substring(y);
                }
                // MARKER: Actual fix end
            }

            // if the url string starts with the classpath: prefix, remove it
            if (urlString.startsWith(ResourceUtils.CLASSPATH_URL_PREFIX))
            {
                urlString = urlString.substring(ResourceUtils.CLASSPATH_URL_PREFIX.length());
            }

            // if the url string starts with the file: prefix, remove the storeDir path
            // this also remove the base path
            if (urlString.startsWith(ResourceUtils.FILE_URL_PREFIX))
            {
                if (this.storeDirs == null)
                {
                    throw new WebScriptException("Unable to resolve a file: resource without a storeDir.");
                }
                for (int i = 0; i < this.storeDirs.length; i++)
                {
                    if (urlString.startsWith(this.storeDirs[i]))
                    {
                        urlString = urlString.substring(this.storeDirs[i].length());
                        break;
                    }
                }
            }
            // handle the JBoss app-server virtual filesystem prefix
            else if (urlString.startsWith(VFSFILE_URL_PREFIX))
            {
                if (this.storeDirs == null)
                {
                    throw new WebScriptException("Unable to resolve a vfsfile: resource without a storeDir.");
                }
                for (int i = 0; i < this.storeDirs.length; i++)
                {
                    // handle VFS files in expanded WARs
                    if (urlString.startsWith(this.storeDirs[i], 3))
                    {
                        urlString = urlString.substring(this.storeDirs[i].length() + 3); // to account for "vfs" prefix
                        break;
                    }
                    // handle VFS files in other classpath dirs
                    else if (urlString.startsWith(this.storeDirs[i]))
                    {
                        urlString = urlString.substring(this.storeDirs[i].length());
                        break;
                    }
                }
            }
            else
            {
                // now remove the class path store base path
                if (this.classPath != null && this.classPath.length() != 0)
                {
                    // the url string should always start with the class path
                    if (urlString.startsWith(this.classPath))
                    {
                        urlString = urlString.substring(this.classPath.length());
                    }
                }
            }

            // remove extra / at the front if found
            if (urlString.charAt(0) == '/')
            {
                urlString = urlString.substring(1);
            }

            // what remains is the document path
            documentPath = urlString;
        }
        catch (final FileNotFoundException | MalformedURLException e)
        {
            LOGGER.warn("Unable to determine document path for resource: {} with base path {}", resourcePath, this.classPath, e);
        }

        return documentPath;
    }
}
