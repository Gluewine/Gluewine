/**************************************************************************
 *
 * Gluewine Launcher Module
 *
 * Copyright (C) 2013 FKS bvba               http://www.fks.be/
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ***************************************************************************/
package org.gluewine.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.gluewine.launcher.sources.DirectoryCodeSource;
import org.gluewine.launcher.sources.JarCodeSource;
import org.gluewine.launcher.sources.MissingCodeSource;
import org.gluewine.launcher.sources.SourceDirCodeSource;
import org.gluewine.launcher.sources.URLCodeSource;
import org.gluewine.launcher.utils.FileUtils;

/**
 * Launches the Gluewine framework. It acceps one parameter: The name of the class to launch. If ommitted, it will use the org.gluewine.core.glue.Gluer class.
 *
 * <p>
 * Beware that this class may not import any class other than java classes, as it acts as the base classloader.
 *
 * <p>
 * By default it will locate the directory where it was started from and look for a lib subdirectory from the parent directory. All jar/zip files stored there will be loaded. <br>
 * The directory where the jars are stored can be overriden using the -Dgluewine.libdir property.
 *
 * <p>
 * Configuration files should be stored in the cfg directory, located as a subdir of the parent directory the class was loaded from. <br>
 * This directory can be explicitely specified using the -Dgluewine.cfgdir property.
 *
 * @author fks/Serge de Schaetzen
 *
 */
public final class Launcher implements Runnable, DirectoryAnnotations
{
    // ===========================================================================
    /**
     * The codesources indexed on the directory.
     */
    private Map<String, List<CodeSource>> codeSources = new HashMap<String, List<CodeSource>>();

    /**
     * The map of sources indexed on their shortname.
     */
    private Map<String, CodeSource> sourcesMap = new TreeMap<String, CodeSource>();

    /**
     * The directory containing the configuration file(s).
     */
    private File configDirectory = null;

    /**
     * The file used to store the persistent map.
     */
    private File persistentFile = null;

    /**
     * The singleton instance.
     */
    private static Launcher instance = null;

    /**
     * The root directory.
     */
    private File root = null;

    /**
     * The map of persistence objects indexed on their id.
     */
    private HashMap<String, Serializable> persistentMap = new HashMap<String, Serializable>();

    /**
     * The set of property file names that have been requested.
     */
    private Set<String> propertiesUsed = new HashSet<String>();

    /**
     * The set of registered listeners.
     */
    private Set<CodeSourceListener> listeners = new HashSet<CodeSourceListener>();

    /**
     * The set of directories that have a single classloader.
     */
    private Set<File> singleLoaderDirectories = new HashSet<File>();

    /**
     * The set of registered shutdown listeners.
     */
    private Set<ShutdownListener> shutdownListeners = new HashSet<ShutdownListener>();

    /**
     * The class to start.
     */
    private String classToStart = null;

    /**
     * Boolean flag indicating that stdin should 'routed'.
     */
    private boolean initStdIn = false;

    /**
     * The array of parameters to pass to the initialized class.
     */
    private String[] parameters = null;

    /**
     * The set of partial jar names to be filtered. If empty all jars are loaded.
     */
    private Set<String> jarFilter = new HashSet<String>();

    /**
     * The log to use.
     */
    private Log log = Logging.getInstance();

    // ===========================================================================
    /**
     * Creates an instance.
     */
    public Launcher()
    {
        setInstance(this);
    }

    // ===========================================================================
    /**
     * Sets the instance.
     *
     * @param l The instance.
     */
    protected static void setInstance(Launcher l)
    {
        instance = l;
    }

    // ===========================================================================
    /**
     * Initializes the list of available jar files.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "DP_CREATE_CLASSLOADER_INSIDE_DO_PRIVILEGED")
    public void start()
    {
        String path = getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
        File currDir = new File(path).getParentFile();
        if (path.toLowerCase(Locale.getDefault()).endsWith(".jar"))
            currDir = currDir.getParentFile();

        String propLib = System.getProperty("gluewine.libdir");
        if (propLib != null)
            root = new File(propLib);
        else
            root = new File(currDir, "lib");

        String propCfg = System.getProperty("gluewine.cfgdir");
        if (propCfg != null)
            configDirectory = new File(propCfg);
        else
            configDirectory = new File(currDir, "cfg");

        String propPersist = System.getProperty("gluewine.persistfile");
        if (propPersist != null)
            persistentFile = new File(propPersist);
        else
            persistentFile = new File(configDirectory, "gluewine.state");

        try
        {
            String srcDir = System.getProperty("gluewine.srcdir");
            if (srcDir != null)
            {
                String binDir = System.getProperty("gluewine.bindir");
                if (binDir != null)
                {
                    SourceDirCodeSource srcSource = new SourceDirCodeSource(srcDir);
                    GluewineLoader ldr = new GluewineLoader(srcDir);
                    File bin = new File(binDir);
                    ldr.addURL(bin.toURI().toURL());
                    srcSource.setSourceClassLoader(ldr);
                    sourcesMap.put(srcSource.getDisplayName(), srcSource);
                    List<CodeSource> l = new ArrayList<CodeSource>();
                    l.add(srcSource);
                    codeSources.put(".", l);
                }
                else throw new Exception("You cannot use gluewine.srcdir without specifying gluewine.bindir");
            }

            if (System.getProperty("log4j.configuration") == null)
            {
                File log4j = new File(configDirectory, "log4j.properties");
                if (log4j.exists())
                    System.setProperty("log4j.configuration", log4j.toURI().toURL().toExternalForm());
            }

            if (root.exists())
                processRoot();

            loadPersistentMap();
            CodeSource rootCs = sourcesMap.get(getShortName(root));
            Class<?> cl = rootCs.getSourceClassLoader().loadClass(classToStart);

            cl.getMethod("main", String[].class).invoke(null, new Object[] {parameters});

            if (initStdIn)
                new Thread(this).start();
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }

    // ===========================================================================
    /**
     * Processes the root directory, and returns all CodeSources that were loaded. (note that CodeSources that were already loaded will not be added again. So it is safe to call this method multiple times.)
     *
     * @return The list of CodeSources that have been added.
     * @throws IOException Thrown if an IOException occurs reading files.
     */
    public List<CodeSource> processRoot() throws IOException
    {
        List<CodeSource> srcs = loadDirectories(root);
        for (CodeSource src : srcs)
            sourcesMap.put(src.getDisplayName(), src);

        mapLoaders();

        return srcs;
    }

    // ===========================================================================
    /**
     * Adds the given shutdown listener.
     *
     * @param l The listener to add.
     */
    public void addShutdownListener(ShutdownListener l)
    {
        shutdownListeners.add(l);
    }

    // ===========================================================================
    /**
     * Removes the given shutdown listener.
     *
     * @param l The listener to remove.
     */
    public void removeShutdownListener(ShutdownListener l)
    {
        shutdownListeners.remove(l);
    }

    // ===========================================================================
    /**
     * Stops the daemon.
     */
    public void stop()
    {
        Set<ShutdownListener> ls = new HashSet<ShutdownListener>(shutdownListeners.size());
        ls.addAll(shutdownListeners);
        for (ShutdownListener l : ls)
            l.shutdown();
    }

    // ===========================================================================
    /**
     * Terminates the framework.
     */
    public static void terminate()
    {
        getInstance().stop();
    }

    /**
     * Rermonates the framework, for compatibility with JSL.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "DM_EXIT")
    public static void shutdownWindowsService()
    {
        try
        {
            terminate();
            System.exit(0);
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }


    // ===========================================================================
    /**
     * Stops the daemon.
     */
    public void destroy()
    {
    }

    // ===========================================================================
    /**
     * Initializes the the daemon.
     *
     * @param args The CLI arguments.
     * @throws Exception If an initialization error occurs.
     */
    public void init(String[] args) throws Exception
    {
        try
        {
            if (args == null || args.length < 1)
                args = new String[] {"org.gluewine.core.glue.Gluer"};

            String clazz = args[0];

            initStdIn = false;

            if (clazz.equals("console"))
            {
                clazz = "org.gluewine.console.impl.ConsoleClient";
                jarFilter.add("org.gluewine.console");
                jarFilter.add("org.gluewine.gxo.client");
                jarFilter.add("org.gluewine.gxo");
                jarFilter.add("org.gluewine.dbauth");
                jarFilter.add("org.gluewine.authentication");
                jarFilter.add("org.gluewine.sessions");
                jarFilter.add("xpp3_min");
                jarFilter.add("xstream");
                jarFilter.add("jline");
                jarFilter.add("jansi");
                jarFilter.add("log4j");
            }
            if (args.length > 1 && args[1].equals("gwt"))
                initStdIn = true;

            classToStart = clazz;

            parameters = new String[args.length - 1];
            if (args.length > 1)
                System.arraycopy(args, 1, parameters, 0, parameters.length);
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }

    // ===========================================================================
    /**
     * Loads the persistent map from the file.
     */
    @SuppressWarnings("unchecked")
    private void loadPersistentMap()
    {
        if (jarFilter.isEmpty())
        {
            if (persistentFile.exists())
            {
                ObjectInputStream in = null;
                try
                {
                    in = new GluewineObjectInputStream(new FileInputStream(persistentFile), sourcesMap.get(getShortName(root)));
                    persistentMap = (HashMap<String, Serializable>) in.readObject();
                }
                catch (Throwable e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    if (in != null)
                    {
                        try
                        {
                            in.close();
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    // ===========================================================================
    /**
     * Returns the map of persistent properties. The map will never be null. <br>
     * Remark: the map returned is the actual map, not a copy!
     *
     * @return The persistent map.
     */
    public Map<String, Serializable> getPersistentMap()
    {
        return persistentMap;
    }

    // ===========================================================================
    /**
     * Requests the launcher to persist the persistent map.
     */
    public void savePersistentMap()
    {
        synchronized (persistentMap)
        {
            if (jarFilter.isEmpty())
            {
                ObjectOutputStream out = null;
                try
                {
                    out = new ObjectOutputStream(new FileOutputStream(persistentFile));
                    out.writeObject(persistentMap);
                }
                catch (Throwable e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    if (out != null)
                    {
                        try
                        {
                            out.close();
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * Maps all classloaders.
     */
    private void mapLoaders()
    {
        List<String> sorted = new ArrayList<String>(codeSources.size());
        sorted.addAll(codeSources.keySet());
        Collections.sort(sorted);

        // Clear all existing mappings:
        for (List<CodeSource> list : codeSources.values())
            for (CodeSource cs : list)
                cs.getSourceClassLoader().clearDispatchers();

        // Remap:
        for (int i = 0; i < sorted.size(); i++)
        {
            List<CodeSource> list = codeSources.get(sorted.get(i));

            // First all CodeSources in the same directory.
            for (int j = 0; j < list.size(); j++)
                for (int k = 0; k < list.size(); k++)
                    list.get(j).getSourceClassLoader().addDispatcher(list.get(k).getSourceClassLoader());

            // Then all directories lower.
            for (int j = i - 1; j >= 0; j--)
            {
                List<CodeSource> list2 = codeSources.get(sorted.get(j));
                for (CodeSource g : list)
                    for (CodeSource g2 : list2)
                        g.getSourceClassLoader().addDispatcher(g2.getSourceClassLoader());
            }

            // Finally the subdirectories.
            for (int j = i + 1; j < sorted.size(); j++)
            {
                List<CodeSource> list2 = codeSources.get(sorted.get(j));
                for (CodeSource g : list)
                    for (CodeSource g2 : list2)
                        g.getSourceClassLoader().addDispatcher(g2.getSourceClassLoader());
            }
        }
    }

    // ===========================================================================
    /**
     * Loads the given directory and all its subdirectories.
     *
     * @param dir The directory to load.
     * @return The code sources.
     * @throws IOException If a problem occurs reading the directory.
     */
    private List<CodeSource> loadDirectories(File dir) throws IOException
    {
        List<CodeSource> sources = loadDirectory(dir);

        File[] dirs = dir.listFiles();
        if (dirs != null)
        {
            for (File file : dirs)
                if (file.isDirectory())
                    sources.addAll(loadDirectories(file));
        }

        return sources;
    }

    // ===========================================================================
    /**
     * Checks for the presence of the @Install annotation in the given directory, and if present processes it.
     *
     * @param dir The directory to process.
     * @throws IOException If an error occurs reading the annotation file, or fetching the resources.
     */
    private void checkInstallAnnotation(File dir) throws IOException
    {
        File annot = new File(dir, INSTALL);
        if (annot.exists())
        {
            List<String> content = FileUtils.readFile(annot);
            for (String url : content)
                fetch(new URL(url), dir);

            // Delete the annotation as everything was fetched successfully.
            if (!annot.delete())
                throw new IOException("Could not delete " + annot.getAbsolutePath());
        }
    }

    // ===========================================================================
    /**
     * Activates the given file. The activation consists of renaming the file by removing the '.notactivated' part.
     *
     * @param file The file to activate.
     * @throws IOException If an error occurs renaming the file.
     * @return The activated file.
     */
    public File activate(File file) throws IOException
    {
        String name = file.getAbsolutePath();
        int i = name.indexOf(".notactivated");
        if (i > -1)
        {
            name = name.substring(0, i);
            log.debug(getClass(), "Activating source: " + name);
            File target = new File(name);
            if (target.exists())
                if (!target.delete())
                    throw new IOException("The file " + target.getAbsolutePath() + " could not be deleted.");

            if (!file.renameTo(target))
                throw new IOException("Could no activate the file " + file.getAbsolutePath());

            return target;
        }
        else
            throw new IOException("The file " + file.getAbsolutePath() + " cannot be activated.");
    }

    // ===========================================================================
    /**
     * Fetches the url given and stores it in the given directory. The file receives the file name of the url, with '.notactivated' appended at the end.
     *
     * @param url The url to fetch from.
     * @param dir The directory to save it into.
     * @return The file that was fetched.
     * @throws IOException If an error occurs.
     */
    public File fetch(URL url, File dir) throws IOException
    {
        File target = new File(dir + ".notactivated");

        log.debug(getClass(), "Fetching", url.toExternalForm(), "to be stored in", dir.getAbsolutePath());

        if (target.exists())
            if (!target.delete())
                throw new IOException("Could not delete " + target.getAbsolutePath());

        if (!target.getParentFile().exists())
            if (!target.getParentFile().mkdirs())
                throw new IOException("Could not create " + target.getParentFile().getAbsolutePath());

        FileOutputStream out = null;
        InputStream in = null;
        try
        {
            in = url.openStream();
            out = new FileOutputStream(target);
            byte[] b = new byte[65536];
            int read = 0;
            read = in.read(b);
            while (read > -1)
            {
                out.write(b, 0, read);
                read = in.read(b);
            }
            return target;
        }
        finally
        {
            try
            {
                if (in != null)
                    in.close();
            }
            finally
            {
                if (out != null)
                    out.close();
            }
        }
    }

    // ===========================================================================
    /**
     * Returns true if the file specified is allowed by the filter or if there's no filter.
     *
     * @param f The file to check.
     * @return True if allowed by the filter.
     */
    private boolean isFileAllowed(File f)
    {
        boolean allowed = jarFilter.isEmpty();
        if (!allowed)
        {
            Iterator<String> iter = jarFilter.iterator();
            while (iter.hasNext() && !allowed)
                allowed = f.getName().startsWith(iter.next());
        }
        return allowed;
    }

    // ===========================================================================
    /**
     * Links the given codesource to the directory specified.
     *
     * @param dir The directory to link to.
     * @param sources The codesources to link.
     */
    private void linkCodeSourceToDir(File dir, List<CodeSource> sources)
    {
        String key = dir.getAbsolutePath();
        List<CodeSource> l = codeSources.get(key);
        if (l == null)
        {
            l = new ArrayList<CodeSource>();
            codeSources.put(key, l);
        }
        l.addAll(sources);
    }

    // ===========================================================================
    /**
     * Loads all jar/zip files located in the given directory.
     *
     * @param dir The directory to load.
     * @return The CodeSources for the given directory.
     * @throws IOException If an error occurs reading the directory.
     */
    private List<CodeSource> loadDirectory(File dir) throws IOException
    {
        List<CodeSource> sources = new ArrayList<CodeSource>();

        // File annotation = new File(dir, SINGLELOADER);
        // boolean single = annotation.exists();
        boolean single = true;
        if (single)
            singleLoaderDirectories.add(dir);
        checkInstallAnnotation(dir);

        List<File> jars = new ArrayList<File>();
        // Index the content.
        File[] files = dir.listFiles();
        if (files != null)
        {
            for (File file : files)
            {
                String name = file.getName().toLowerCase(Locale.getDefault());
                if (name.endsWith(".jar") || name.endsWith(".zip"))
                    if (isFileAllowed(file))
                        jars.add(file);
            }
        }

        URL[] annotatedURLs = loadUrls(dir);

        String key = getShortName(dir);
        GluewineLoader dirLoader = null;
        if (!sourcesMap.containsKey(key))
        {
            DirectoryCodeSource dcs = new DirectoryCodeSource(dir);
            dcs.setDisplayName(getShortName(dir));
            dirLoader = new GluewineLoader(key);
            dcs.setSourceClassLoader(dirLoader);
            sourcesMap.put(dcs.getDisplayName(), dcs);
            sources.add(dcs);
            log.trace(getClass(), "Loaded", dcs.getDisplayName());
        }
        else
            dirLoader = sourcesMap.get(key).getSourceClassLoader();

        // Create the classloader(s) for the files.
        if (single)
        {
            for (File file : jars)
            {
                key = getShortName(file);
                if (!sourcesMap.containsKey(key))
                {
                    sources.add(getCodeSource(file, dirLoader));
                    log.trace(getClass(), "Loaded", key);
                }
            }

            for (URL url : annotatedURLs)
            {
                key = url.toExternalForm();
                if (!sourcesMap.containsKey(key))
                {
                    sources.add(getCodeSource(url, dirLoader));
                    log.trace(getClass(), "Loaded", key);
                }
            }
        }
        else
        {
            // One loader per file:
            for (File file : jars)
            {
                key = getShortName(file);
                if (!sourcesMap.containsKey(key))
                {
                    sources.add(getCodeSource(file, null));
                    log.trace(getClass(), "Loaded", key);
                }
            }

            for (URL url : annotatedURLs)
            {
                key = url.toExternalForm();
                if (!sourcesMap.containsKey(key))
                {
                    sources.add(getCodeSource(url, null));
                    log.trace(getClass(), "Loaded", key);
                }
            }
        }

        linkCodeSourceToDir(dir, sources);
        return sources;
    }

    // ===========================================================================
    /**
     * Creates and returns a CodeSource for the given file. If the loader is null a new GluewineLoader is created. If it is not null it is assigned to the CodeSource.
     *
     * @param file The file to process.
     * @param loader The (possibly null) classloader to use.
     * @return The CodeSource.
     * @throws IOException Thrown if an error occurs.
     */
    private CodeSource getCodeSource(File file, GluewineLoader loader) throws IOException
    {
        String name = getShortName(file);
        if (loader == null)
            loader = new GluewineLoader(name);
        loader.addURL(file.toURI().toURL());
        CodeSource src = new JarCodeSource(file);
        src.setSourceClassLoader(loader);
        src.setDisplayName(name);
        return src;
    }

    // ===========================================================================
    /**
     * Creates and returns a CodeSource for the given url. If the loader is null a new GluewineLoader is created. If it is not null it is assigned to the CodeSource.
     *
     * @param url The url.
     * @param loader The (possibly null) classloader to use.
     * @return The CodeSource.
     * @throws IOException Thrown if an error occurs.
     */
    private CodeSource getCodeSource(URL url, GluewineLoader loader) throws IOException
    {
        String name = url.toExternalForm();
        if (loader == null)
            loader = new GluewineLoader(name);
        loader.addURL(url);
        CodeSource src = new URLCodeSource(url);
        src.setSourceClassLoader(loader);
        src.setDisplayName(name);
        return src;
    }

    // ===========================================================================
    /**
     * Reads the URL annotation file (if present) and returns an array of all urls defined in that file.
     *
     * @param dir The directory to process.
     * @return The array of defined urls.
     * @throws IOException If an error occurs reading the file. (or an invalud URL has been defined).
     */
    @SuppressWarnings("unchecked")
    private URL[] loadUrls(File dir) throws IOException
    {
        List<URL> urls = new ArrayList<URL>();

        File annot = new File(dir, URLLOADER);
        if (annot.exists())
        {
            HashSet<String> removedUrls = (HashSet<String>) persistentMap.get("GLUEWINE:REMOVEDURLS");
            if (removedUrls == null)
                removedUrls = new HashSet<String>();
            List<String> ss = FileUtils.readFile(annot);
            for (String s : ss)
                if (!removedUrls.contains(s))
                    urls.add(new URL(s));
        }

        return urls.toArray(new URL[urls.size()]);
    }

    // ===========================================================================
    /**
     * Returns the URL that is used to perform updates.
     *
     * @return The URL.
     * @throws IOException If an error occurs.
     */
    public String getSourceRepositoryURL() throws IOException
    {
        String repos = (String) persistentMap.get("GLUEWINE::REPOSITORY");
        if (repos == null)
            repos = root.toURI().toURL().toExternalForm();

        if (!repos.endsWith("/"))
            repos = repos + "/";

        return repos;
    }

    // ===========================================================================
    /**
     * Sets the url to use for updates.
     *
     * @param url The url.
     */
    public void setSourceRepositoryURL(String url)
    {
        persistentMap.put("GLUEWINE::REPOSITORY", url);
        savePersistentMap();
    }

    // ===========================================================================
    /**
     * Returns the list of source versions obtained by reading the packages.idx file, located in the currently defined source repository. If no packages.idx could be found, an empty list is returned.
     *
     * @param installMissing If true it also returns missing sources.
     * @return The list of version.
     * @throws IOException If an error occurs.
     */
    public List<SourceVersion> getSourceVersions(boolean installMissing) throws IOException
    {
        List<SourceVersion> versions = new ArrayList<SourceVersion>();

        URL url = new URL(getSourceRepositoryURL() + "packages.idx");
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
            String s;
            while ((s = reader.readLine()) != null)
            {
                if (s.trim().length() > 0)
                {
                    String[] split = s.split(";");

                    String sourceUrl = getSourceRepositoryURL() + split[0];

                    CodeSource cs = sourcesMap.get("/" + split[0]);
                    if (cs != null)
                    {
                        SourceVersion version = null;
                        if (split.length == 3)
                            version = new SourceVersion(cs, split[1], split[2], sourceUrl);
                        else if (split.length == 2)
                            version = new SourceVersion(cs, split[1], split[1], sourceUrl);
                        versions.add(version);
                    }
                    else if (installMissing)
                    {
                        SourceVersion version = null;
                        MissingCodeSource ms = new MissingCodeSource();
                        ms.setDisplayName("/" + split[0]);
                        if (split.length == 3)
                            version = new SourceVersion(ms, split[1], split[2], sourceUrl);
                        else if (split.length == 2)
                            version = new SourceVersion(ms, split[1], split[1], sourceUrl);
                        versions.add(version);
                    }
                }
            }
        }
        finally
        {
            if (reader != null)
                reader.close();
        }

        return versions;
    }

    // ===========================================================================
    /**
     * Returns the list of codesources that can be updated.
     *
     * @param installMissing If true also installs the jar missing.
     * @return The list of sources to update.
     * @throws IOException If an error occurs.
     */
    public List<SourceVersion> getCodeSourceToUpdate(boolean installMissing) throws IOException
    {
        List<SourceVersion> latest = getSourceVersions(installMissing);
        List<SourceVersion> updates = new ArrayList<SourceVersion>();

        for (SourceVersion vers : latest)
        {
            if (!vers.getSource().getChecksum().equals(vers.getVersion()))
                updates.add(vers);
        }

        return updates;
    }

    // ===========================================================================
    /**
     * Returns the list of codesources that have changed.
     *
     * @return The list of sources that changed.
     */
    public List<CodeSource> getChangedSources()
    {
        List<CodeSource> changed = new ArrayList<CodeSource>();

        for (CodeSource src : sourcesMap.values())
        {
            if (src.hasChanged())
                changed.add(src);
        }

        return changed;
    }

    // ===========================================================================
    /**
     * Reloads the sources that have changed.
     *
     * @throws IOException Thrown if an error occurs reading the sources.
     */
    public void reload() throws IOException
    {
        List<CodeSource> changed = getChangedSources();
        List<CodeSource> reload = new ArrayList<CodeSource>();

        for (CodeSourceListener l : listeners)
            l.codeSourceRemoved(changed);

        getSourcesToReload(changed, reload);

        for (CodeSourceListener l : listeners)
            l.codeSourceRemoved(reload);

        unload(changed);
        unload(reload);

        List<CodeSource> newSources = processRoot();
        for (CodeSourceListener l : listeners)
            l.codeSourceAdded(newSources);
    }

    // ===========================================================================
    /**
     * Adds the objects specified and returns a list of codesources.
     *
     * @param toadd The objects to add.
     * @return The list of new CodeSources.
     * @throws IOException Thrown if the fetch failed.
     */
    public List<CodeSource> add(List<SourceVersion> toadd) throws IOException
    {
        List<CodeSource> added = new ArrayList<CodeSource>();
        List<File> toActivate = new ArrayList<File>();
        Iterator<SourceVersion> iter = toadd.iterator();
        while (iter.hasNext())
        {
            SourceVersion s = iter.next();
            log.debug(getClass(), "Fetching new source from " + s.getUrl());
            URL url = new URL(s.getUrl());
            String jar = s.getSource().getDisplayName().substring(1);
            File f = new File(root, jar);
            toActivate.add(fetch(url, f));
            iter.remove();
        }

        List<File> activated = new ArrayList<File>();
        for (File f : toActivate)
            activated.add(activate(f));

        List<CodeSource> newSources = processRoot();
        for (CodeSourceListener l : listeners)
            l.codeSourceAdded(newSources);

        return added;
    }

    // ===========================================================================
    /**
     * Activates the list of files specified.
     *
     * @param toActivate The list to activate.
     * @return The list of activated files.
     * @throws IOException If activation failed.
     */
    public List<File> activate(List<File> toActivate) throws IOException
    {
        List<File> activated = new ArrayList<File>();
        for (File f : toActivate)
            activated.add(activate(f));

        return activated;
    }

    // ===========================================================================
    /**
     * Fetches the list of sources specified without activating them, and returns the list of files that need to be activated.
     *
     * @param toFetch The sources to fetch.
     * @return The files to activate.
     * @throws IOException If the fetch failed.
     */
    public List<File> fetch(List<SourceVersion> toFetch) throws IOException
    {
        List<File> toActivate = new ArrayList<File>();
        Iterator<SourceVersion> iter = toFetch.iterator();
        while (iter.hasNext())
        {
            SourceVersion s = iter.next();
            log.debug(getClass(), "Fetching new source from " + s.getUrl());
            URL url = new URL(s.getUrl());
            String jar = s.getSource().getDisplayName().substring(1);
            File f = new File(root, jar);
            toActivate.add(fetch(url, f));
            iter.remove();
        }
        return toActivate;
    }

    // ===========================================================================
    /**
     * Returns the short name of the given file. This is the name starting from the root library.
     *
     * @param file The file to process.
     * @return The shortname.
     */
    private String getShortName(File file)
    {
        if (file.equals(root))
            return "/";
        else
        {
            String name = file.getAbsolutePath();
            name = name.substring(root.getAbsolutePath().length());
            return name.replace('\\', '/');
        }
    }

    // ===========================================================================
    /**
     * Returns the config directory.
     *
     * @return The config directory.
     */
    public File getConfigDirectory()
    {
        return configDirectory;
    }

    // ===========================================================================
    /**
     * Returns the properties stored in the container with the given name.
     *
     * @param name The name of the property file to obtain. (unqualified).
     * @return The properties.
     * @throws IOException Thrown if the container does not exists or could not be read.
     */
    public Properties getProperties(String name) throws IOException
    {
        InputStream input = null;
        Properties props = new Properties();

        try
        {
            input = new FileInputStream(new File(configDirectory, name));
            props.load(input);
            propertiesUsed.add(name);
        }
        finally
        {
            if (input != null)
                input.close();
        }

        return props;
    }

    // ===========================================================================
    /**
     * Returns the set of property files in use.
     *
     * @return The set of file names.
     */
    public Set<String> getPropertiesUsed()
    {
        Set<String> sorted = new TreeSet<String>();
        sorted.addAll(propertiesUsed);
        return sorted;
    }

    // ===========================================================================
    /**
     * Returns the root directory.
     *
     * @return The root directory.
     */
    public File getRoot()
    {
        return root;
    }

    // ===========================================================================
    /**
     * Returns the list of available code sources.
     *
     * @return The list of codesources.
     */
    public List<CodeSource> getSources()
    {
        List<CodeSource> l = new ArrayList<CodeSource>();
        l.addAll(sourcesMap.values());
        return l;
    }

    // ===========================================================================
    /**
     * Updates the list of sources to be reloaded based on the given data.
     *
     * This method recalls itself as long as true is returned.
     *
     * @param toRemove The list of sources that are to be removed.
     * @param toReload The list to update.
     * @return True if the toReload list has been updated.
     */
    public boolean getSourcesToReload(List<CodeSource> toRemove, List<CodeSource> toReload)
    {
        boolean updated = false;

        log.debug(getClass(), "Updating codesources to be reloaded.");
        List<CodeSource> l = new ArrayList<CodeSource>(toRemove.size());
        l.addAll(toRemove);
        l.addAll(toReload);
        for (CodeSource s : l)
        {
            for (CodeSource r : sourcesMap.values())
            {
                // If r loaded classes from s, it must be added to the list of loaders to be removed.
                if (r.getSourceClassLoader().references(s.getSourceClassLoader()) && !toReload.contains(r) && !r.getDisplayName().equals("/"))
                {
                    log.debug(getClass(), "Adding codesource", r.getDisplayName(), "as it references", s.getDisplayName());
                    toReload.add(r);
                    updated = true;
                }
            }
        }

        if (updated)
            getSourcesToReload(toRemove, toReload);

        return updated;
    }

    // ===========================================================================
    /**
     * Unloads the sources specified.
     *
     * @param sources The sources to unload.
     */
    private void unload(List<CodeSource> sources)
    {
        for (CodeSource src : sources)
        {
            log.debug(getClass(), "Unloading", src.getDisplayName());
            sourcesMap.remove(src.getDisplayName());
            src.closeLoader();
        }
    }

    // ===========================================================================
    /**
     * Removes the given list of codesources. This does not do a physical remove, but only a deregistration of the associated classloaders, and returns the list of ALL codesources that have been removed.
     *
     * @param toRemove The sources to remove.
     * @param deleteFile If true deletes the file associated with the codesources.
     * @return The list of CodeSources that were removed.
     */
    @SuppressWarnings("unchecked")
    public List<CodeSource> removeSources(List<CodeSource> toRemove, boolean deleteFile)
    {
        List<CodeSource> toReload = new ArrayList<CodeSource>();

        Set<CodeSourceListener> s = new HashSet<CodeSourceListener>(listeners);
        for (CodeSourceListener l : s)
            l.codeSourceRemoved(toRemove);

        getSourcesToReload(toRemove, toReload);

        s = new HashSet<CodeSourceListener>(listeners);
        for (CodeSourceListener l : s)
            l.codeSourceRemoved(toReload);

        unload(toReload);
        unload(toRemove);

        for (CodeSource src : toRemove)
        {
            if (src instanceof JarCodeSource)
            {
                JarCodeSource jcs = (JarCodeSource) src;
                log.debug(getClass(), "Deleting", jcs.getDisplayName());
                if (deleteFile && !jcs.getFile().delete())
                    log.warn(getClass(), "Could not delete", jcs.getFile().getAbsolutePath());
            }

            else if (src instanceof URLCodeSource)
            {
                URLCodeSource ucs = (URLCodeSource) src;
                // We cannot delete the url, so we'll have to register it
                // with the set of 'removed' urls:
                HashSet<String> removedUrls = (HashSet<String>) persistentMap.get("GLUEWINE:REMOVEDURLS");
                if (removedUrls == null)
                {
                    removedUrls = new HashSet<String>();
                    persistentMap.put("GLUEWINE:REMOVEDURLS", removedUrls);
                }
                removedUrls.add(ucs.getURL().toExternalForm());
            }

            else if (src instanceof DirectoryCodeSource)
            {
                DirectoryCodeSource dcs = (DirectoryCodeSource) src;
                List<CodeSource> children = codeSources.remove(dcs.getDirectory().getAbsolutePath());
                removeSources(children, deleteFile);
                if (deleteFile && !dcs.getDirectory().delete())
                    log.warn(getClass(), "Could not delete", dcs.getDirectory().getAbsolutePath());
            }
        }

        savePersistentMap();

        return toRemove;
    }

    // ===========================================================================
    /**
     * Removes the list of objects specified.
     *
     * @param toRemove The objects to remove.
     * @return The list of removed codesources.
     */
    public List<CodeSource> remove(List<String> toRemove)
    {
        List<CodeSource> sourcesToRemove = new ArrayList<CodeSource>();

        for (String s : toRemove)
        {
            List<String> displayNames = new ArrayList<String>();
            displayNames.addAll(sourcesMap.keySet());
            for (String disp : displayNames)
            {
                if (disp.startsWith(s))
                    sourcesToRemove.add(sourcesMap.get(disp));
            }
        }
        return removeSources(sourcesToRemove, true);
    }

    // ===========================================================================
    /**
     * Returns the singleton instance.
     *
     * @return The instance.
     */
    public static synchronized Launcher getInstance()
    {
        return AccessController.doPrivileged(new PrivilegedAction<Launcher>()
        {
            @Override
            public Launcher run()
            {
                if (instance == null)
                    instance = new Launcher();
                return instance;
            }
        });
    }

    // ===========================================================================
    /**
     * Main invocation routine.
     *
     * @param args The Command line arguments.
     */
    public static void main(String[] args)
    {
        try
        {
            Launcher fw = getInstance();
            fw.init(args);
            fw.start();
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }

    // ===========================================================================
    @Override
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "DM_EXIT")
    public void run()
    {
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
            while (true)
            {
                try
                {
                    String line = in.readLine();
                    if ("shutdown".equals(line))
                    {
                        stop();
                        break;
                    }
                }
                catch (Throwable e)
                {
                    // Allowed to fail.
                }
            }
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
        System.exit(0);
    }

    // ===========================================================================
    /**
     * Adds a listener.
     *
     * @param l The listener to add.
     */
    public void addListener(CodeSourceListener l)
    {
        listeners.add(l);
    }

    // ===========================================================================
    /**
     * Removes a listener.
     *
     * @param l The listener to remove.
     */
    public void removeListener(CodeSourceListener l)
    {
        listeners.remove(l);
    }
}
