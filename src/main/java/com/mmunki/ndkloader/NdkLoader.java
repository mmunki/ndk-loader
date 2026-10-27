package com.mmunki.ndkloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NdkLoader {
    public static final String VERSION = "1.0.0";

    private static final Set<String> LOADED = new LinkedHashSet<String>();

    private NdkLoader() {
    }

    public static String version() {
        return VERSION;
    }

    public static void load(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new UnsatisfiedLinkError("library path is empty");
        }
        load(new File(path));
    }

    public static synchronized void load(File library) {
        File file = requireFile(library);
        String key = canonical(file);
        if (LOADED.contains(key)) {
            return;
        }
        try {
            System.load(file.getAbsolutePath());
        } catch (UnsatisfiedLinkError error) {
            UnsatisfiedLinkError failure = new UnsatisfiedLinkError(
                    "failed to load " + key + " for ABI " + abi() + ": " + error.getMessage());
            failure.initCause(error);
            throw failure;
        }
        LOADED.add(key);
    }

    public static synchronized void loadLibrary(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new UnsatisfiedLinkError("library name is empty");
        }
        try {
            System.loadLibrary(name);
        } catch (UnsatisfiedLinkError error) {
            UnsatisfiedLinkError failure = new UnsatisfiedLinkError(
                    "failed to load library \"" + name + "\" for ABI " + abi() + ": " + error.getMessage());
            failure.initCause(error);
            throw failure;
        }
    }

    public static synchronized void loadDirectory(File directory) {
        if (directory == null) {
            throw new UnsatisfiedLinkError("library directory is null");
        }
        if (!directory.isDirectory()) {
            throw new UnsatisfiedLinkError("not a library directory: " + directory.getAbsolutePath());
        }
        File[] entries = directory.listFiles();
        if (entries == null) {
            throw new UnsatisfiedLinkError("cannot read library directory: " + directory.getAbsolutePath());
        }
        List<File> libraries = new ArrayList<File>();
        for (File entry : entries) {
            if (entry.isFile() && entry.getName().endsWith(".so")) {
                libraries.add(entry);
            }
        }
        Collections.sort(libraries, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });
        for (File library : libraries) {
            load(library);
        }
    }

    public static synchronized boolean isLoaded(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        return isLoaded(new File(path));
    }

    public static synchronized boolean isLoaded(File library) {
        if (library == null) {
            return false;
        }
        return LOADED.contains(canonical(library.getAbsoluteFile()));
    }

    public static String abi() {
        String androidAbi = androidAbi();
        if (androidAbi != null && !androidAbi.isEmpty()) {
            return androidAbi;
        }
        return hostAbi(System.getProperty("os.arch", "unknown"));
    }

    public static File extractResource(String resource, File directory) throws IOException {
        if (resource == null || resource.trim().isEmpty()) {
            throw new IllegalArgumentException("resource is empty");
        }
        if (directory == null) {
            throw new IllegalArgumentException("directory is null");
        }
        String normalized = resource.startsWith("/") ? resource : "/" + resource;
        int slash = normalized.lastIndexOf('/');
        String name = normalized.substring(slash + 1);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("resource has no file name: " + resource);
        }
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("cannot create directory: " + directory.getAbsolutePath());
        }
        File target = new File(directory, name);
        File temporary = File.createTempFile("ndk-loader-", ".tmp", directory);
        try {
            InputStream input = NdkLoader.class.getResourceAsStream(normalized);
            if (input == null) {
                throw new IOException("resource not found: " + normalized);
            }
            try {
                OutputStream output = new FileOutputStream(temporary);
                try {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                } finally {
                    output.close();
                }
            } finally {
                input.close();
            }
            if (!temporary.renameTo(target)) {
                if (target.exists() && !target.delete()) {
                    throw new IOException("cannot replace " + target.getAbsolutePath());
                }
                if (!temporary.renameTo(target)) {
                    throw new IOException("cannot create " + target.getAbsolutePath());
                }
            }
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
        return target;
    }

    public static void loadResource(String resource, File directory) throws IOException {
        load(extractResource(resource, directory));
    }

    private static File requireFile(File library) {
        if (library == null) {
            throw new UnsatisfiedLinkError("library is null");
        }
        File file = library.getAbsoluteFile();
        if (!file.exists()) {
            throw new UnsatisfiedLinkError("library does not exist: " + file);
        }
        if (!file.isFile()) {
            throw new UnsatisfiedLinkError("library is not a regular file: " + file);
        }
        if (!file.canRead()) {
            throw new UnsatisfiedLinkError("library is not readable: " + file);
        }
        return file;
    }

    private static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return file.getAbsolutePath();
        }
    }

    private static String androidAbi() {
        try {
            Class<?> build = Class.forName("android.os.Build");
            Object value = build.getField("SUPPORTED_ABIS").get(null);
            if (value instanceof String[]) {
                String[] abis = (String[]) value;
                if (abis.length > 0) {
                    return abis[0];
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String hostAbi(String arch) {
        String value = arch == null ? "" : arch.toLowerCase(Locale.ROOT);
        if ("aarch64".equals(value) || "arm64".equals(value)) {
            return "arm64-v8a";
        }
        if (value.startsWith("arm")) {
            return "armeabi-v7a";
        }
        if ("x86_64".equals(value) || "amd64".equals(value)) {
            return "x86_64";
        }
        if ("x86".equals(value) || "i386".equals(value) || "i686".equals(value)) {
            return "x86";
        }
        return value.isEmpty() ? "unknown" : value;
    }
}
