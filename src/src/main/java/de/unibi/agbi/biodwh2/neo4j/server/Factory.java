package de.unibi.agbi.biodwh2.neo4j.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class Factory {
    private static final Logger LOGGER = LoggerFactory.getLogger(Factory.class);
    private static final List<String> IGNORED_JARS = Arrays.asList("rt.jar", "idea_rt.jar", "aws-java-sdk-ec2",
                                                                   "proto-", "google-cloud-", "google-api-",
                                                                   "openstack4j-core", "selenium-", "google-api-client",
                                                                   "jackson-", "guava", "jetty", "netty-", "junit-",
                                                                   "com.intellij.rt");
    private static Factory instance;
    private final Set<String> allClassPaths;

    private Factory() {
        allClassPaths = new HashSet<>();
        collectAllClassPaths();
    }

    public static synchronized Factory getInstance() {
        if (instance == null)
            instance = new Factory();
        return instance;
    }

    private void collectAllClassPaths() {
        final String runtimeClassPath = ManagementFactory.getRuntimeMXBean().getClassPath();
        for (final String classPath : runtimeClassPath.split(File.pathSeparator)) {
            final File file = new File(classPath);
            if (file.isDirectory())
                iterateFileSystem(file, file.toURI().toString());
            else if (isValidJarFile(file))
                iterateJarFile(file);
        }
    }

    private static boolean isValidJarFile(final File file) {
        final String fileName = file.getName().toLowerCase(Locale.US);
        return file.isFile() && fileName.endsWith(".jar") && IGNORED_JARS.stream().noneMatch(fileName::contains);
    }

    private void iterateFileSystem(final File directory, final String rootPath) {
        final File[] files = directory.listFiles();
        if (files != null)
            iterateFiles(files, rootPath);
    }

    private void iterateFiles(final File[] files, final String rootPath) {
        for (final File file : files)
            if (file.isDirectory())
                iterateFileSystem(file, rootPath);
            else if (file.isFile())
                allClassPaths.add(getClassPathFromUri((file.toURI().toString().substring(rootPath.length()))));
    }

    private void iterateJarFile(final File file) {
        final Enumeration<JarEntry> entries = tryGetJarFileEntries(file);
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if (!entry.isDirectory())
                allClassPaths.add(getClassPathFromUri((entry.getName())));
        }
    }

    private Enumeration<JarEntry> tryGetJarFileEntries(final File file) {
        try {
            return new JarFile(file).entries();
        } catch (IOException e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to load JAR entries", e);
            return Collections.emptyEnumeration();
        }
    }

    private static String getClassPathFromUri(final String uri) {
        return uri.replace('/', '.').replace(".class", "");
    }

    public List<Class<?>> loadAllClasses(final String prefix) {
        final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        final List<Class<?>> result = new ArrayList<>();
        for (final String classPath : allClassPaths)
            if (classPath.startsWith(prefix)) {
                final Class<?> c = tryLoadClass(classLoader, classPath);
                if (c != null && !c.isInterface())
                    result.add(c);
            }
        return result;
    }

    private static Class<?> tryLoadClass(final ClassLoader classLoader, final String classPath) {
        try {
            return classLoader.loadClass(classPath);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            return null;
        }
    }
}
