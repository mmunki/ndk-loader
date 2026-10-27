#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

BUILD="$ROOT/build"
DIST="$ROOT/dist"
NATIVE="$BUILD/test-native"
RESOURCES="$BUILD/test-resources"
CACHE="$BUILD/test-cache"
JAR="$DIST/ndk-loader.jar"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
CC="${CC:-cc}"

log() { printf '==> %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

[ -f "$JAR" ] || bash "$ROOT/build.sh"
command -v "$CC" >/dev/null 2>&1 || die "a C compiler is required for the host load test"

rm -rf "$NATIVE" "$RESOURCES" "$CACHE" "$BUILD/test-classes"
mkdir -p "$NATIVE" "$RESOURCES" "$CACHE" "$BUILD/test-classes"

log "building the host test libraries"
"$CC" -shared -fPIC -O2 -o "$NATIVE/libndktest.so" "$ROOT/src/test/native/ndktest.c"
"$CC" -shared -fPIC -O2 -o "$NATIVE/libndkother.so" "$ROOT/src/test/native/ndktest.c"
"$CC" -shared -fPIC -O2 -o "$NATIVE/libndkres.so" "$ROOT/src/test/native/ndktest.c"
cp "$NATIVE/libndkres.so" "$RESOURCES/libndkres.so"

log "compiling and running the loader tests"
javac -encoding UTF-8 --release 8 -cp "$BUILD/classes" -d "$BUILD/test-classes" \
    $(find "$ROOT/src/test/java" -name '*.java' | sort)
java -Djava.library.path="$NATIVE" \
    -cp "$BUILD/classes:$BUILD/test-classes:$RESOURCES" \
    com.mmunki.ndkloader.NdkLoaderTest \
    "$NATIVE/libndktest.so" "$NATIVE/libndkother.so" "$NATIVE" "/libndkres.so" "$CACHE"

log "testing the packaged jar"
jar tf "$JAR" | grep -qx 'classes.dex' || die "the jar has no classes.dex"
jar tf "$JAR" | grep -qx 'com/mmunki/ndkloader/NdkLoader.class' \
    || die "the jar has no NdkLoader.class"
jar tf "$JAR" | grep -qx 'com/mmunki/ndkloader/Main.class' \
    || die "the jar has no Main.class"
unzip -p "$JAR" META-INF/MANIFEST.MF | grep -q '^Main-Class: com.mmunki.ndkloader.Main' \
    || die "the manifest has no Main-Class"

unzip -p "$JAR" classes.dex > "$BUILD/jar-classes.dex"
magic="$(od -An -tx1 -N8 "$BUILD/jar-classes.dex" | tr -d ' \n')"
case "$magic" in
    6465780a*) ;;
    *) die "classes.dex does not start with the dex magic (got $magic)" ;;
esac
log "classes.dex magic: $magic"

if [ -z "${DEXDUMP:-}" ] && [ -n "$SDK" ]; then
    DEXDUMP="$(ls -1d "$SDK"/build-tools/*/dexdump 2>/dev/null | sort -V | tail -1 || true)"
fi
if [ -n "${DEXDUMP:-}" ] && [ -x "$DEXDUMP" ]; then
    log "verifying classes.dex with $DEXDUMP"
    "$DEXDUMP" -f "$BUILD/jar-classes.dex" > "$BUILD/dexdump.txt"
    grep -q 'Lcom/mmunki/ndkloader/NdkLoader;' "$BUILD/dexdump.txt" \
        || die "dexdump did not find NdkLoader in classes.dex"
    grep -q 'Lcom/mmunki/ndkloader/Main;' "$BUILD/dexdump.txt" \
        || die "dexdump did not find Main in classes.dex"
fi

java -jar "$JAR" --version | grep -q '^ndk-loader 1.0.0$' || die "jar --version failed"
java -jar "$JAR" --abi >/dev/null || die "jar --abi failed"
java -Djava.library.path="$NATIVE" -jar "$JAR" --lib ndktest \
    | grep -q '^loaded library ndktest$' || die "jar --lib failed"
java -jar "$JAR" "$NATIVE/libndktest.so" \
    | grep -q "^loaded $NATIVE/libndktest.so$" || die "jar file load failed"
java -jar "$JAR" --dir "$NATIVE" >/dev/null || die "jar --dir failed"

log "all tests passed"
