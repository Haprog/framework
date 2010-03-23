/*
@ITMillApache2LicenseForJavaFiles@
 */
package com.vaadin.terminal.gwt.widgetsetutils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.event.dd.acceptcriteria.ClientCriterion;
import com.vaadin.terminal.Paintable;
import com.vaadin.ui.ClientWidget;

/**
 * Utility class to collect widgetset related information from classpath.
 * Utility will seek all directories from classpaths, and jar files having
 * "Vaadin-Widgetsets" key in their manifest file.
 * <p>
 * Used by WidgetMapGenerator and ide tools to implement some monkey coding for
 * you.
 * <p>
 * Developer notice: If you end up reading this comment, I guess you have faced
 * a sluggish performance of widget compilation or unreliable detection of
 * components in your classpaths. The thing you might be able to do is to use
 * annotation processing tool like apt to generate the needed information. Then
 * either use that information in {@link WidgetMapGenerator} or create the
 * appropriate monkey code for gwt directly in annotation processor and get rid
 * of {@link WidgetMapGenerator}. Using annotation processor might be a good
 * idea when dropping Java 1.5 support (integrated to javac in 6).
 * 
 */
public class ClassPathExplorer {

    private static Logger logger = Logger
            .getLogger("com.vaadin.terminal.gwt.widgetsetutils");

    private final static FileFilter DIRECTORIES_ONLY = new FileFilter() {
        public boolean accept(File f) {
            if (f.exists() && f.isDirectory()) {
                return true;
            } else {
                return false;
            }
        }
    };

    private static List<String> rawClasspathEntries = getRawClasspathEntries();
    private static Map<URL, String> classpathLocations = getClasspathLocations(rawClasspathEntries);

    private ClassPathExplorer() {
    }

    /**
     * Finds server side widgets with {@link ClientWidget} annotation.
     */
    public static Collection<Class<? extends Paintable>> getPaintablesHavingWidgetAnnotation() {

        Collection<Class<? extends Paintable>> paintables = new HashSet<Class<? extends Paintable>>();
        Set<URL> keySet = classpathLocations.keySet();
        for (URL url : keySet) {
            searchForPaintables(url, classpathLocations.get(url), paintables);
        }
        return paintables;

    }

    public static Collection<Class<? extends AcceptCriterion>> getCriterion() {
        if (acceptCriterion.isEmpty()) {
            // accept criterion are searched as a side effect, normally after
            // paintable detection
            getPaintablesHavingWidgetAnnotation();
        }
        return acceptCriterion;
    }

    /**
     * Finds available widgetset names.
     * 
     * @return
     */
    public static Map<String, URL> getAvailableWidgetSets() {
        Map<String, URL> widgetsets = new HashMap<String, URL>();
        Set<URL> keySet = classpathLocations.keySet();
        for (URL url : keySet) {
            searchForWidgetSets(url, widgetsets);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Widgetsets found from classpath:\n");
        for (String ws : widgetsets.keySet()) {
            sb.append("\t");
            sb.append(ws);
            sb.append(" in ");
            sb.append(widgetsets.get(ws));
            sb.append("\n");
        }
        logger.info(sb.toString());
        return widgetsets;
    }

    private static void searchForWidgetSets(URL location,
            Map<String, URL> widgetsets) {

        File directory = new File(location.getFile());

        if (directory.exists() && !directory.isHidden()) {
            // Get the list of the files contained in the directory
            String[] files = directory.list();
            for (int i = 0; i < files.length; i++) {
                // we are only interested in .gwt.xml files
                if (files[i].endsWith(".gwt.xml")) {
                    // remove the extension
                    String classname = files[i].substring(0,
                            files[i].length() - 8);
                    classname = classpathLocations.get(location) + "."
                            + classname;
                    if (!widgetsets.containsKey(classname)) {
                        String packageName = classpathLocations.get(location);
                        String packagePath = packageName.replaceAll("\\.", "/");
                        String basePath = location.getFile().replaceAll(
                                "/" + packagePath + "$", "");
                        try {
                            URL url = new URL(location.getProtocol(), location
                                    .getHost(), location.getPort(), basePath);
                            widgetsets.put(classname, url);
                        } catch (MalformedURLException e) {
                            // should never happen as based on an existing URL,
                            // only changing end of file name/path part
                            e.printStackTrace();
                        }
                    }
                }
            }
        } else {

            try {
                // check files in jar file, entries will list all directories
                // and files in jar

                URLConnection openConnection = location.openConnection();
                if (openConnection instanceof JarURLConnection) {
                    JarURLConnection conn = (JarURLConnection) openConnection;

                    JarFile jarFile = conn.getJarFile();

                    Manifest manifest = jarFile.getManifest();
                    if (manifest == null) {
                        // No manifest so this is not a Vaadin Add-on
                        return;
                    }
                    String value = manifest.getMainAttributes().getValue(
                            "Vaadin-Widgetsets");
                    if (value != null) {
                        String[] widgetsetNames = value.split(",");
                        for (int i = 0; i < widgetsetNames.length; i++) {
                            String widgetsetname = widgetsetNames[i].trim()
                                    .intern();
                            if (!widgetsetname.equals("")) {
                                widgetsets.put(widgetsetname, location);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error parsing jar file", e);
            }

        }
    }

    /**
     * Determine every URL location defined by the current classpath, and it's
     * associated package name.
     */
    private final static List<String> getRawClasspathEntries() {
        // try to keep the order of the classpath
        List<String> locations = new ArrayList<String>();

        String pathSep = System.getProperty("path.separator");
        String classpath = System.getProperty("java.class.path");

        if (classpath.startsWith("\"")) {
            classpath = classpath.substring(1);
        }
        if (classpath.endsWith("\"")) {
            classpath = classpath.substring(0, classpath.length() - 1);
        }

        logger.fine("Classpath: " + classpath);

        String[] split = classpath.split(pathSep);
        for (int i = 0; i < split.length; i++) {
            String classpathEntry = split[i];
            if (acceptClassPathEntry(classpathEntry)) {
                locations.add(classpathEntry);
            }
        }

        return locations;
    }

    /**
     * Determine every URL location defined by the current classpath, and it's
     * associated package name.
     */
    private final static Map<URL, String> getClasspathLocations(
            List<String> rawClasspathEntries) {
        // try to keep the order of the classpath
        Map<URL, String> locations = new LinkedHashMap<URL, String>();
        for (String classpathEntry : rawClasspathEntries) {
            File file = new File(classpathEntry);
            include(null, file, locations);
        }
        return locations;
    }

    private static boolean acceptClassPathEntry(String classpathEntry) {
        if (!classpathEntry.endsWith(".jar")) {
            // accept all non jars (practically directories)
            return true;
        } else {
            // accepts jars that comply with vaadin-component packaging
            // convention (.vaadin. or vaadin- as distribution packages),
            if (classpathEntry.contains("vaadin-")
                    || classpathEntry.contains(".vaadin.")) {
                return true;
            } else {
                URL url;
                try {
                    url = new URL("file:"
                            + new File(classpathEntry).getCanonicalPath());
                    url = new URL("jar:" + url.toExternalForm() + "!/");
                    JarURLConnection conn = (JarURLConnection) url
                            .openConnection();
                    logger.fine(url.toString());
                    JarFile jarFile = conn.getJarFile();
                    Manifest manifest = jarFile.getManifest();
                    if (manifest != null) {
                        Attributes mainAttributes = manifest
                                .getMainAttributes();
                        if (mainAttributes.getValue("Vaadin-Widgetsets") != null) {
                            return true;
                        }
                    }
                } catch (MalformedURLException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                return false;
            }
        }
    }

    /**
     * Recursively add subdirectories and jar files to classpathlocations
     * 
     * @param name
     * @param file
     * @param locations
     */
    private final static void include(String name, File file,
            Map<URL, String> locations) {
        if (!file.exists()) {
            return;
        }
        if (!file.isDirectory()) {
            // could be a JAR file
            includeJar(file, locations);
            return;
        }

        if (file.isHidden() || file.getPath().contains(File.separator + ".")) {
            return;
        }

        if (name == null) {
            name = "";
        } else {
            name += ".";
        }

        // add all directories recursively
        File[] dirs = file.listFiles(DIRECTORIES_ONLY);
        for (int i = 0; i < dirs.length; i++) {
            try {
                // add the present directory
                locations.put(new URL("file://" + dirs[i].getCanonicalPath()),
                        name + dirs[i].getName());
            } catch (Exception ioe) {
                return;
            }
            include(name + dirs[i].getName(), dirs[i], locations);
        }
    }

    private static void includeJar(File file, Map<URL, String> locations) {
        try {
            URL url = new URL("file:" + file.getCanonicalPath());
            url = new URL("jar:" + url.toExternalForm() + "!/");
            JarURLConnection conn = (JarURLConnection) url.openConnection();
            JarFile jarFile = conn.getJarFile();
            if (jarFile != null) {
                locations.put(url, "");
            }
        } catch (Exception e) {
            // e.printStackTrace();
            return;
        }

    }

    private final static void searchForPaintables(URL location,
            String packageName,
            Collection<Class<? extends Paintable>> paintables) {

        // Get a File object for the package
        File directory = new File(location.getFile());

        if (directory.exists() && !directory.isHidden()) {
            // Get the list of the files contained in the directory
            String[] files = directory.list();
            for (int i = 0; i < files.length; i++) {
                // we are only interested in .class files
                if (files[i].endsWith(".class")) {
                    // remove the .class extension
                    String classname = files[i].substring(0,
                            files[i].length() - 6);
                    classname = packageName + "." + classname;
                    tryToAdd(classname, paintables);
                }
            }
        } else {
            try {
                // check files in jar file, entries will list all directories
                // and files in jar

                URLConnection openConnection = location.openConnection();

                if (openConnection instanceof JarURLConnection) {
                    JarURLConnection conn = (JarURLConnection) openConnection;

                    JarFile jarFile = conn.getJarFile();

                    Enumeration<JarEntry> e = jarFile.entries();
                    while (e.hasMoreElements()) {
                        JarEntry entry = e.nextElement();
                        String entryname = entry.getName();
                        if (!entry.isDirectory()
                                && entryname.endsWith(".class")
                                && !entryname.contains("$")) {
                            String classname = entryname.substring(0, entryname
                                    .length() - 6);
                            if (classname.startsWith("/")) {
                                classname = classname.substring(1);
                            }
                            classname = classname.replace('/', '.');
                            tryToAdd(classname, paintables);
                        }
                    }
                }
            } catch (IOException e) {
                logger.warning(e.toString());
            }
        }

    }

    // Hide possible errors, exceptions from static initializers from
    // classes we are inspecting
    private static PrintStream devnull = new PrintStream(new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            // NOP
        }
    });

    private static Set<Class<? extends AcceptCriterion>> acceptCriterion = new HashSet<Class<? extends AcceptCriterion>>();

    private static void tryToAdd(final String fullclassName,
            Collection<Class<? extends Paintable>> paintables) {
        try {
            PrintStream out = System.out;
            PrintStream err = System.err;
            System.setErr(devnull);
            System.setOut(devnull);

            Class<?> c = Class.forName(fullclassName);

            System.setErr(err);
            System.setOut(out);

            if (c.getAnnotation(ClientWidget.class) != null) {
                paintables.add((Class<? extends Paintable>) c);
                // System.out.println("Found paintable " + fullclassName);
            } else if (c.getAnnotation(ClientCriterion.class) != null) {
                acceptCriterion.add((Class<? extends AcceptCriterion>) c);
            }

        } catch (ClassNotFoundException e) {
            // e.printStackTrace();
        } catch (LinkageError e) {
            // NOP
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Find and return the default source directory where to create new
     * widgetsets.
     * 
     * Return the first directory (not a JAR file etc.) on the classpath by
     * default.
     * 
     * TODO this could be done better...
     * 
     * @return URL
     */
    public static URL getDefaultSourceDirectory() {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("classpathLocations keys:");
            ArrayList<URL> locations = new ArrayList<URL>(classpathLocations
                    .keySet());
            for (URL location : locations) {
                logger.fine(location.toString());
            }
        }

        Iterator<String> it = rawClasspathEntries.iterator();
        while (it.hasNext()) {
            String entry = it.next();

            File directory = new File(entry);
            if (directory.exists() && !directory.isHidden()
                    && directory.isDirectory()) {
                try {
                    return new URL("file://" + directory.getCanonicalPath());
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    // ignore: continue to the next classpath entry
                } catch (IOException e) {
                    e.printStackTrace();
                    // ignore: continue to the next classpath entry
                }
            }
        }
        return null;
    }

    /**
     * Test method for helper tool
     */
    public static void main(String[] args) {
        Collection<Class<? extends Paintable>> paintables = ClassPathExplorer
                .getPaintablesHavingWidgetAnnotation();
        logger.info("Found annotated paintables:");
        for (Class<? extends Paintable> cls : paintables) {
            logger.info(cls.getCanonicalName());
        }

        logger.info("");
        logger.info("Searching available widgetsets...");

        Map<String, URL> availableWidgetSets = ClassPathExplorer
                .getAvailableWidgetSets();
        for (String string : availableWidgetSets.keySet()) {

            logger.info(string + " in " + availableWidgetSets.get(string));
        }
    }

}