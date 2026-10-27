# ndk-loader

A small JAR that loads Android native libraries (`.so`) at runtime. The JAR
contains a `classes.dex` at its root, so it can be used with `app_process`,
`DexClassLoader` or `InMemoryDexClassLoader` on a device without converting
anything first.

```
ndk-loader.jar
├── META-INF/MANIFEST.MF
├── META-INF/LICENSE
├── classes.dex
└── com/mmunki/ndkloader/*.class
```

The `.class` files are kept next to `classes.dex` so the same JAR also runs on
a normal desktop JVM for testing.

## Usage on Android

Load a library from an absolute path with `app_process`:

```bash
adb push ndk-loader.jar /data/local/tmp/
adb push libhello.so /data/local/tmp/

adb shell CLASSPATH=/data/local/tmp/ndk-loader.jar \
    app_process /system/bin com.mmunki.ndkloader.Main /data/local/tmp/libhello.so
```

Load a library by name (the normal `System.loadLibrary` search path):

```bash
adb shell CLASSPATH=/data/local/tmp/ndk-loader.jar \
    app_process /system/bin com.mmunki.ndkloader.Main --lib hello
```

Load every `.so` in a directory:

```bash
adb shell CLASSPATH=/data/local/tmp/ndk-loader.jar \
    app_process /system/bin com.mmunki.ndkloader.Main --dir /data/local/tmp/libs
```

Load a library that is stored inside the JAR:

```bash
adb shell CLASSPATH=/data/local/tmp/ndk-loader.jar \
    app_process /system/bin com.mmunki.ndkloader.Main \
    --resource /lib/x86_64/libhello.so --cache /data/local/tmp/cache
```

`--resource` extracts the entry to `--cache` and then loads it. The directory
must be writable by the process.

## Usage as a library

```java
import com.mmunki.ndkloader.NdkLoader;

NdkLoader.load("/data/local/tmp/libhello.so");
NdkLoader.loadLibrary("hello");
NdkLoader.loadDirectory(new File("/data/local/tmp/libs"));
NdkLoader.loadResource("/lib/x86_64/libhello.so", cacheDir);

System.out.println(NdkLoader.abi());      // arm64-v8a, armeabi-v7a, x86, x86_64
System.out.println(NdkLoader.version());  // 1.0.0
```

`load` is idempotent per canonical path, verifies that the target exists and
is readable, and wraps failures in an `UnsatisfiedLinkError` that names the
library and the ABI. `loadLibrary` delegates to `System.loadLibrary`.

From Java code the JAR can be used as a secondary DEX:

```java
DexClassLoader loader = new DexClassLoader(
        jarPath, cacheDir.getAbsolutePath(), nativeDir.getAbsolutePath(),
        ClassLoader.getSystemClassLoader());
Class<?> type = loader.loadClass("com.mmunki.ndkloader.NdkLoader");
```

## Command line

```
usage: ndk-loader [options] <library.so>...

options:
  --lib <name>        load a library by name (System.loadLibrary)
  --dir <directory>   load every .so in a directory
  --resource <path>   load a .so stored inside the JAR
  --cache <directory> the directory --resource is extracted to
  --abi               print the current Android ABI
  --version           print the version
  --quiet             do not print the loaded libraries
  --help              print this text
```

Exit codes: `0` success, `2` bad command line, `3` a library could not be
loaded, `4` an I/O error while extracting a resource.

## Build

Requirements:

- JDK 17 or newer (`javac`, `jar`, `java`)
- Android SDK build-tools with `d8`
- `android.jar` from an Android platform (recommended, passed to d8 with
  `--lib`)

The build finds `d8` and `android.jar` under `${ANDROID_HOME}` or
`${ANDROID_SDK_ROOT}`. Override them with `D8=/path/to/d8` and
`ANDROID_JAR=/path/to/android.jar`.

```bash
bash build.sh          # -> dist/ndk-loader.jar
bash test.sh           # host JVM load tests, dex checks, jar checks
bash scripts/test-abis.sh   # build and verify all four Android ABIs
```

`build.sh` compiles the Java sources, runs `d8` with `--min-api 21`, and packs
`classes.dex` into the root of `dist/ndk-loader.jar`.

`test.sh` builds real host shared libraries with the system C compiler, loads
them through `NdkLoader`, checks the JAR layout and the DEX magic, and runs the
packaged CLI. When `dexdump` is available it also verifies the DEX classes.

`scripts/test-abis.sh` builds the test library with the NDK for
`arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64` and checks the ELF machine of
each one. The loader itself is pure DEX/Java; the operating system's linker
performs the final ABI match for the device it runs on.

| Target | Check | Result |
| --- | --- | --- |
| Host JVM x86_64 | 29 loader checks, DEX magic, `dexdump`, JAR CLI | pass |
| Android 11 (API 30) x86_64 emulator | `classes.dex` through `app_process`, `.so` by path and by name | pass |
| arm64-v8a | test `.so` built with NDK r27d (API 21), ELF machine verified | pass |
| armeabi-v7a | test `.so` built with NDK r27d (API 21), ELF machine verified | pass |
| x86 | test `.so` built with NDK r27d (API 21), ELF machine verified | pass |
| x86_64 | test `.so` built with NDK r27d (API 21), ELF machine verified | pass |

The `.so` itself must match the ABI of the device, the same way it does for
any other Android native library.

## Releases

Releases: https://github.com/mmunki/ndk-loader/releases

The release contains `ndk-loader.jar` and the source archive. The SHA-256 of
every asset is in the release notes.

## License

Apache License 2.0, see [LICENSE](LICENSE).
