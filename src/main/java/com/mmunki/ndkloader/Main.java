package com.mmunki.ndkloader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    private static final String USAGE =
            "usage: ndk-loader [options] <library.so>...\n"
            + "\n"
            + "options:\n"
            + "  --lib <name>        load a library by name (System.loadLibrary)\n"
            + "  --dir <directory>   load every .so in a directory\n"
            + "  --resource <path>   load a .so stored inside the JAR\n"
            + "  --cache <directory> the directory --resource is extracted to\n"
            + "  --abi               print the current Android ABI\n"
            + "  --version           print the version\n"
            + "  --quiet             do not print the loaded libraries\n"
            + "  --help              print this text\n"
            + "\n"
            + "example:\n"
            + "  CLASSPATH=/data/local/tmp/ndk-loader.jar app_process /system/bin \\\n"
            + "      com.mmunki.ndkloader.Main /data/local/tmp/libhello.so\n";

    private Main() {
    }

    public static void main(String[] args) {
        int status = run(args);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(String[] args) {
        List<String> paths = new ArrayList<String>();
        String library = null;
        String directory = null;
        String resource = null;
        String cache = null;
        boolean quiet = false;

        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    System.out.print(USAGE);
                    return 0;
                }
                if ("--version".equals(arg) || "-v".equals(arg)) {
                    System.out.println("ndk-loader " + NdkLoader.version());
                    return 0;
                }
                if ("--abi".equals(arg)) {
                    System.out.println(NdkLoader.abi());
                    return 0;
                }
                if ("--quiet".equals(arg)) {
                    quiet = true;
                    continue;
                }
                if ("--".equals(arg)) {
                    for (i++; i < args.length; i++) {
                        paths.add(args[i]);
                    }
                    break;
                }
                if ("--lib".equals(arg)) {
                    library = value(args, ++i, "--lib");
                    continue;
                }
                if ("--dir".equals(arg)) {
                    directory = value(args, ++i, "--dir");
                    continue;
                }
                if ("--resource".equals(arg)) {
                    resource = value(args, ++i, "--resource");
                    continue;
                }
                if ("--cache".equals(arg)) {
                    cache = value(args, ++i, "--cache");
                    continue;
                }
                if (arg.startsWith("-")) {
                    System.err.println("error: unknown option: " + arg);
                    System.err.print(USAGE);
                    return 2;
                }
                paths.add(arg);
            }
        } catch (UsageException error) {
            System.err.println("error: " + error.getMessage());
            System.err.print(USAGE);
            return 2;
        }

        boolean work = !paths.isEmpty() || library != null || directory != null || resource != null;
        if (!work) {
            System.err.print(USAGE);
            return 2;
        }
        if (resource != null && cache == null) {
            System.err.println("error: --resource needs --cache <directory>");
            return 2;
        }

        try {
            for (String path : paths) {
                NdkLoader.load(path);
                report(quiet, "loaded " + path);
            }
            if (directory != null) {
                NdkLoader.loadDirectory(new File(directory));
                report(quiet, "loaded directory " + directory);
            }
            if (library != null) {
                NdkLoader.loadLibrary(library);
                report(quiet, "loaded library " + library);
            }
            if (resource != null) {
                NdkLoader.loadResource(resource, new File(cache));
                report(quiet, "loaded resource " + resource);
            }
            return 0;
        } catch (IllegalArgumentException error) {
            System.err.println("error: " + error.getMessage());
            return 2;
        } catch (UnsatisfiedLinkError error) {
            System.err.println("error: " + error.getMessage());
            return 3;
        } catch (IOException error) {
            System.err.println("error: " + error.getMessage());
            return 4;
        }
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new UsageException(option + " needs a value");
        }
        return args[index];
    }

    private static void report(boolean quiet, String message) {
        if (!quiet) {
            System.out.println(message);
        }
    }

    private static final class UsageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
