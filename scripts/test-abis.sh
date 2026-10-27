#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
API="${NDK_API:-21}"
OUT="$ROOT/build/test-libs"

log() { printf '==> %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

if [ -z "$NDK" ] && [ -n "$SDK" ]; then
    NDK="$(ls -1d "$SDK"/ndk/* 2>/dev/null | sort -V | tail -1 || true)"
fi
[ -n "$NDK" ] && [ -d "$NDK" ] || die "the Android NDK was not found; set ANDROID_NDK_HOME"

if [ -d "$NDK/toolchains/llvm/prebuilt/linux-x86_64" ]; then
    HOST_TAG="linux-x86_64"
elif [ -d "$NDK/toolchains/llvm/prebuilt/darwin-x86_64" ]; then
    HOST_TAG="darwin-x86_64"
elif [ -d "$NDK/toolchains/llvm/prebuilt/darwin-arm64" ]; then
    HOST_TAG="darwin-arm64"
else
    die "no supported LLVM prebuilt in $NDK"
fi

BIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
rm -rf "$OUT"
mkdir -p "$OUT"

ABIS=(arm64-v8a armeabi-v7a x86 x86_64)
TRIPLES=(aarch64-linux-android armv7a-linux-androideabi i686-linux-android x86_64-linux-android)
EXPECTED=("ARM aarch64" "ARM, EABI5" "Intel i386" "x86-64")

printf '%-14s %-10s %s\n' "ABI" "RESULT" "FILE"
for i in "${!ABIS[@]}"; do
    abi="${ABIS[$i]}"
    triple="${TRIPLES[$i]}"
    compiler="$BIN/${triple}${API}-clang"
    [ -x "$compiler" ] || die "compiler not found: $compiler"
    mkdir -p "$OUT/$abi"
    output="$OUT/$abi/libndktest.so"
    "$compiler" -shared -fPIC -O2 -o "$output" src/test/native/ndktest.c
    description="$(file -b "$output")"
    case "$description" in
        *"${EXPECTED[$i]}"*) result="ok" ;;
        *) result="BAD" ;;
    esac
    printf '%-14s %-10s %s (%s bytes)\n' "$abi" "$result" "$description" "$(wc -c < "$output")"
    [ "$result" = "ok" ] || die "$abi has an unexpected ELF machine"
done

log "all four Android ABIs built and verified"
