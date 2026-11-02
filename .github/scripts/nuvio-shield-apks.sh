#!/usr/bin/env bash
# Signed release APKs for Nuvio Shield (com.egyptshun.nuvio.shield). Tags use vX.Y.Z.
#
#   nuvio-shield-apks.sh build <tag>   build APKs into release-apks/
#   nuvio-shield-apks.sh list <tag>      print asset paths
set -euo pipefail

OUT="release-apks"
SOURCE_DIR="app/build/outputs/apk/full/release"
SHIELD_PACKAGE="com.egyptshun.nuvio.shield"

shield_apk() { echo "$OUT/NuvioShield-TV-$1-$2.apk"; }

apk_pairs() {
  echo "arm64-v8a:arm64 armeabi-v7a:arm32 x86_64:x64 x86:x32 universal:any"
}

build() {
  local tag="$1"
  mkdir -p "$OUT"
  ./gradlew :app:assembleFullRelease -PNUVIO_SHIELD_BUILD=true --build-cache --stacktrace
  local pair abi alias
  for pair in $(apk_pairs "$tag"); do
    abi="${pair%%:*}"
    alias="${pair##*:}"
    test -f "$SOURCE_DIR/app-full-${abi}-release.apk"
    mv "$SOURCE_DIR/app-full-${abi}-release.apk" "$(shield_apk "$tag" "$alias")"
  done
  tools
  local apk
  for apk in "$OUT"/*.apk; do
    [[ "$(package_of "$apk")" == "$SHIELD_PACKAGE" ]] || {
      echo "Unexpected package id in $apk: $(package_of "$apk")" >&2
      exit 1
    }
  done
  ls -l "$OUT"
}

tools() {
  AAPT="$(find "$ANDROID_HOME/build-tools" -type f -name aapt | sort -V | tail -n 1)"
  test -x "$AAPT"
}
package_of() { "$AAPT" dump badging "$1" | head -n 1 | sed -n "s/^package: name='\([^']*\)'.*/\1/p"; }

list() {
  local tag="$1"
  local pair abi alias
  for pair in $(apk_pairs "$tag"); do
    alias="${pair##*:}"
    echo "$(shield_apk "$tag" "$alias")"
  done
}

case "${1:-}" in
  build) build "${2:?tag required}" ;;
  list) list "${2:?tag required}" ;;
  *)
    echo "Usage: $0 build|list <tag>" >&2
    exit 1
    ;;
esac
