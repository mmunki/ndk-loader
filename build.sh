#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

VERSION="1.0.0"
BUILD="$ROOT/build"
DIST="$ROOT/dist"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

log() { printf '==> %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

command -v javac >/dev/null 2>&1 || die "javac is required (JDK 17 or newer)"
command -v jar >/dev/null 2>&1 || die "jar is required (JDK 17 or newer)"

if [ -z "${D8:-}" ]; then
    if [ -n "$SDK" ]; then
        D8="$(ls -1d "$SDK"/build-tools/*/d8 2>/dev/null | sort -V | tail -1 || true)"
    fi
fi
if [ -z "${D8:-}" ]; then
    D8="$(command -v d8 2>/dev/null || true)"
fi
[ -n "${D8:-}" ] && [ -x "$D8" ] || die "d8 was not found; install Android build-tools or set D8"

if [ -z "${ANDROID_JAR:-}" ] && [ -n "$SDK" ]; then
    ANDROID_JAR="$(ls -1d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1 || true)"
fi

rm -rf "$BUILD" "$DIST"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/meta/META-INF" "$DIST"

log "compiling the Java sources with $(javac -version 2>&1)"
javac -encoding UTF-8 --release 8 -d "$BUILD/classes" \
    $(find "$ROOT/src/main/java" -name '*.java' | sort)

log "dexing with $D8"
jar --create --file "$BUILD/classes.jar" -C "$BUILD/classes" .
D8_ARGS=(--release --min-api 21 --output "$BUILD/dex")
if [ -n "${ANDROID_JAR:-}" ] && [ -f "$ANDROID_JAR" ]; then
    D8_ARGS+=(--lib "$ANDROID_JAR")
fi
"$D8" "${D8_ARGS[@]}" "$BUILD/classes.jar"

[ -f "$BUILD/dex/classes.dex" ] || die "d8 did not write classes.dex"

cp "$ROOT/LICENSE" "$BUILD/meta/META-INF/LICENSE"

cat > "$BUILD/MANIFEST.MF" <<EOF
Manifest-Version: 1.0
Created-By: ndk-loader
Main-Class: com.mmunki.ndkloader.Main
Implementation-Title: ndk-loader
Implementation-Version: $VERSION
EOF

log "packing $DIST/ndk-loader.jar"
jar --create --file "$DIST/ndk-loader.jar" \
    --manifest "$BUILD/MANIFEST.MF" \
    -C "$BUILD/classes" . \
    -C "$BUILD/dex" classes.dex \
    -C "$BUILD/meta" .

log "built $DIST/ndk-loader.jar ($(wc -c < "$DIST/ndk-loader.jar") bytes)"
