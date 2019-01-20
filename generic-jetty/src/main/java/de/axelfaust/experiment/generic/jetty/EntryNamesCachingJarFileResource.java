package de.axelfaust.experiment.generic.jetty;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.JarFileResource;
import org.eclipse.jetty.util.resource.Resource;

/**
 * This specialised implementation of a JAR file resource is meant to improve the performance of any functionality listing the contents of a
 * directory inside the JAR file, such as Jetty's annotation scanning, by caching the list of entry names both globally as well as on the
 * various internal directory levels. This optimisation is necessary to deal with the fat JAR use case which contains an abhorrent number of
 * resources in one JAR, for which the base class has not been optimised.
 *
 * @author Axel Faust
 */
public class EntryNamesCachingJarFileResource extends JarFileResource
{

    // need reflection to avoid duplicating state (+ handling)
    static
    {
        try
        {
            final Field jarFileField = JarFileResource.class.getDeclaredField("_jarFile");
            jarFileField.setAccessible(true);
            final Field jarUrlField = JarFileResource.class.getDeclaredField("_jarUrl");
            jarUrlField.setAccessible(true);
            _JAR_FILE_REFLECT_LOOKUP = jarFileResource -> {
                try
                {
                    return (JarFile) jarFileField.get(jarFileResource);
                }
                catch (final Exception e)
                {
                    throw new RuntimeException(e);
                }
            };
            _JAR_URL_REFLECT_LOOKUP = jarFileResource -> {
                try
                {
                    return (String) jarUrlField.get(jarFileResource);
                }
                catch (final Exception e)
                {
                    throw new RuntimeException(e);
                }
            };
        }
        catch (final Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private static final Function<JarFileResource, JarFile> _JAR_FILE_REFLECT_LOOKUP;

    private static final Function<JarFileResource, String> _JAR_URL_REFLECT_LOOKUP;

    private static final Logger LOG = Log.getLogger(EntryNamesCachingJarFileResource.class);

    private final List<String> entryNames;

    private final Map<String, List<String>> entryNamesByBaseDir;

    private final Map<String, List<String>> entryNamesByDir;

    // duplicated from super class due to visibility constraints
    private String[] _list;

    public EntryNamesCachingJarFileResource(final URL url)
    {
        super(url, true);
        this.entryNames = new ArrayList<>();
        this.entryNamesByBaseDir = new HashMap<>();
        this.entryNamesByDir = new HashMap<>();
    }

    public EntryNamesCachingJarFileResource(final URL url, final boolean useCaches)
    {
        super(url, useCaches);
        this.entryNames = new ArrayList<>();
        this.entryNamesByBaseDir = new HashMap<>();
        this.entryNamesByDir = new HashMap<>();
    }

    private EntryNamesCachingJarFileResource(final URL url, final List<String> entryNames,
            final Map<String, List<String>> entryNamesByBaseDir, final Map<String, List<String>> entryNamesByDir)
    {
        super(url);
        this.entryNames = entryNames;
        this.entryNamesByBaseDir = entryNamesByBaseDir;
        this.entryNamesByDir = entryNamesByDir;
    }

    private EntryNamesCachingJarFileResource(final URL url, final boolean useCaches, final List<String> entryNames,
            final Map<String, List<String>> entryNamesByBaseDir, final Map<String, List<String>> entryNamesByDir)
    {
        super(url, useCaches);
        this.entryNames = entryNames;
        this.entryNamesByBaseDir = entryNamesByBaseDir;
        this.entryNamesByDir = entryNamesByDir;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Resource addPath(String path) throws IOException, MalformedURLException
    {
        if (path == null)
        {
            return null;
        }

        path = URIUtil.canonicalPath(path);

        return new EntryNamesCachingJarFileResource(new URL(URIUtil.addEncodedPaths(this._url.toExternalForm(), URIUtil.encodePath(path))),
                this.getUseCaches(), this.entryNames, this.entryNamesByBaseDir, this.entryNamesByDir);
    }

    // cannot override private listEntries() so we must override its single user (list()) too
    /**
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized String[] list()
    {
        if (this.isDirectory() && this._list == null)
        {
            List<String> list = null;
            try
            {
                list = this.listEntries();
            }
            catch (final Exception e)
            {
                // Sun's JarURLConnection impl for jar: protocol will close a JarFile in its connect() method if
                // useCaches == false (eg someone called URLConnection with defaultUseCaches==true).
                // As their sun.net.www.protocol.jar package caches JarFiles and/or connections, we can wind up in
                // the situation where the JarFile we have remembered in our _jarFile member has actually been closed
                // by other code.
                // So, do one retry to drop a connection and get a fresh JarFile
                LOG.warn("Retrying list:" + e);
                LOG.debug(e);
                this.close();
                list = this.listEntries();
            }

            if (list != null)
            {
                this._list = new String[list.size()];
                list.toArray(this._list);
            }
        }
        return this._list;
    }

    private List<String> listEntries()
    {
        if (this.entryNames.isEmpty())
        {
            this.checkConnection();
            JarFile jarFile = _JAR_FILE_REFLECT_LOOKUP.apply(this);
            if (jarFile == null)
            {
                try
                {
                    final JarURLConnection jc = (JarURLConnection) ((new URL(_JAR_URL_REFLECT_LOOKUP.apply(this))).openConnection());
                    jc.setUseCaches(this.getUseCaches());
                    jarFile = jc.getJarFile();
                }
                catch (final Exception e)
                {
                    LOG.ignore(e);
                }
                if (jarFile == null)
                {
                    throw new IllegalStateException();
                }
            }

            final Enumeration<JarEntry> e = jarFile.entries();
            while (e.hasMoreElements())
            {
                final JarEntry entry = e.nextElement();
                final String name = entry.getName();
                if (!this.entryNames.contains(name))
                {
                    this.entryNames.add(name.replace('\\', '/'));
                }
            }
        }

        final String dir = this._urlString.substring(this._urlString.lastIndexOf("!/") + 2);
        final List<String> list = this.listViaFor(dir);

        return list;
    }

    protected List<String> listViaFor(final String dir)
    {
        final List<String> matches;

        if (this.entryNamesByDir.containsKey(dir))
        {
            matches = this.entryNamesByDir.get(dir);
        }
        else
        {
            matches = new ArrayList<>(64);
            final List<String> entryNamesForBaseDir = this.getEntryNamesForBaseDir(dir);
            for (final String entryName : entryNamesForBaseDir)
            {
                final String listName = entryName.substring(dir.length());
                final int dashIdx = listName.indexOf('/');
                if (dashIdx >= 0 && !(dashIdx == 0 && listName.length() == 1))
                {
                    final String name = dashIdx == 0 ? listName.substring(dashIdx + 1) : listName.substring(0, dashIdx + 1);
                    if (!matches.contains(name))
                    {
                        matches.add(name);
                    }
                }
            }
            this.entryNamesByDir.put(dir, matches);
        }
        return matches;
    }

    protected List<String> getEntryNamesForBaseDir(final String dir)
    {
        List<String> entryNamesForBaseDir;
        if (this.entryNamesByBaseDir.containsKey(dir))
        {
            entryNamesForBaseDir = this.entryNamesByBaseDir.get(dir);
        }
        else
        {
            int idxLastDash = dir.lastIndexOf('/');
            if (idxLastDash > 0 && idxLastDash == dir.length() - 1)
            {
                idxLastDash = dir.lastIndexOf('/', idxLastDash - 1);
            }

            final String parentBaseDir = idxLastDash < 0 ? "" : dir.substring(0, idxLastDash);
            final List<String> entryNamesForParentBaseDir = parentBaseDir.isEmpty() ? this.entryNames
                    : this.getEntryNamesForBaseDir(parentBaseDir);

            entryNamesForBaseDir = new ArrayList<>();
            for (final String entryName : entryNamesForParentBaseDir)
            {
                if (entryName.startsWith(dir) && entryName.length() != dir.length())
                {
                    entryNamesForBaseDir.add(entryName);
                }
            }
            this.entryNamesByBaseDir.put(dir, entryNamesForBaseDir);
        }
        return entryNamesForBaseDir;
    }
}