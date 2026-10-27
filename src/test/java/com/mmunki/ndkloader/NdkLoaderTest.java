package com.mmunki.ndkloader;

import java.io.File;
import java.io.IOException;

public final class NdkLoaderTest {
    private static int checks;
    private static int failures;

    private NdkLoaderTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "usage: NdkLoaderTest <lib1.so> <lib2.so> <directory> <resource> <cache>");
        }
        File first = new File(args[0]);
        File second = new File(args[1]);
        File directory = new File(args[2]);
        String resource = args[3];
        File cache = new File(args[4]);

        check("version", "1.0.0".equals(NdkLoader.version()));
        check("abi is set", NdkLoader.abi() != null && !NdkLoader.abi().isEmpty());
        check("first is not loaded", !NdkLoader.isLoaded(first));
        check("second is not loaded", !NdkLoader.isLoaded(second));

        NdkLoader.load(first);
        check("first is loaded", NdkLoader.isLoaded(first));
        NdkLoader.load(first);
        check("load is idempotent", NdkLoader.isLoaded(first));

        NdkLoader.load(second.getAbsolutePath());
        check("second is loaded", NdkLoader.isLoaded(second));

        NdkLoader.loadDirectory(directory);
        check("directory keeps first", NdkLoader.isLoaded(first));
        check("directory keeps second", NdkLoader.isLoaded(second));

        expectLinkError("null path", new Action() {
            @Override
            public void run() {
                NdkLoader.load((String) null);
            }
        });
        expectLinkError("empty path", new Action() {
            @Override
            public void run() {
                NdkLoader.load("");
            }
        });
        expectLinkError("missing file", new Action() {
            @Override
            public void run() {
                NdkLoader.load(new File(directory, "missing.so"));
            }
        });
        expectLinkError("directory is not a library", new Action() {
            @Override
            public void run() {
                NdkLoader.load(directory);
            }
        });
        expectLinkError("null library", new Action() {
            @Override
            public void run() {
                NdkLoader.load((File) null);
            }
        });
        expectLinkError("empty library name", new Action() {
            @Override
            public void run() {
                NdkLoader.loadLibrary("");
            }
        });

        File extracted = NdkLoader.extractResource(resource, cache);
        check("resource was extracted", extracted.isFile());
        check("resource has data", extracted.length() > 0);
        NdkLoader.loadResource(resource, cache);
        check("resource was loaded", NdkLoader.isLoaded(extracted));

        try {
            NdkLoader.extractResource("/does-not-exist.so", cache);
            fail("missing resource should fail");
        } catch (IOException expected) {
            pass("missing resource fails");
        }

        check("main version", Main.run(new String[] {"--version"}) == 0);
        check("main abi", Main.run(new String[] {"--abi"}) == 0);
        check("main help", Main.run(new String[] {"--help"}) == 0);
        check("main empty", Main.run(new String[] {}) == 2);
        check("main unknown option", Main.run(new String[] {"--nope"}) == 2);
        check("main missing value", Main.run(new String[] {"--lib"}) == 2);
        check("main missing cache", Main.run(new String[] {"--resource", resource}) == 2);
        check("main loads path", Main.run(new String[] {"--quiet", first.getAbsolutePath()}) == 0);
        check("main loads library", Main.run(new String[] {"--lib", "ndkother"}) == 0);
        check("main loads resource", Main.run(
                new String[] {"--quiet", "--resource", resource, "--cache", cache.getAbsolutePath()}) == 0);

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static void expectLinkError(String name, Action action) {
        try {
            action.run();
            fail(name);
        } catch (UnsatisfiedLinkError expected) {
            pass(name);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            pass(name);
        } else {
            fail(name);
        }
    }

    private static void pass(String name) {
        checks++;
        System.out.println("ok   " + name);
    }

    private static void fail(String name) {
        checks++;
        failures++;
        System.out.println("FAIL " + name);
    }

    private interface Action {
        void run();
    }
}
