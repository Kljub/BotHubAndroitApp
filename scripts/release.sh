#!/usr/bin/env bash
# Builds, signs and publishes a release of the app. The app updates itself
# only from published GitHub releases (tag v<version>, asset BotHub-<version>.apk).
#
#   scripts/release.sh 1.0.0 [notes-file]
#
# Needs Docker (image bothub-android, see docker/Dockerfile), the GitHub CLI
# and the signing key outside the repo: $BOTHUB_SIGNING_DIR (default
# ../BotHubAndroitApp-signing) with bothub-release.jks and
# keystore-password.txt. Every release must be signed with this same key, or
# Android refuses the update.
set -euo pipefail
version="${1:?version, e.g. 1.0.0}"
notes="${2:-}"
root="$(cd "$(dirname "$0")/.." && pwd)"
signing="${BOTHUB_SIGNING_DIR:-$root/../BotHubAndroitApp-signing}"
[[ -f "$signing/bothub-release.jks" && -f "$signing/keystore-password.txt" ]] || { echo "signing key not found in $signing" >&2; exit 1; }
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must be x.y.z" >&2; exit 1; }
password="$(cat "$signing/keystore-password.txt")"
# Docker Desktop on Windows (Git Bash) wants D:/… paths.
winpath() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }

MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$(winpath "$root"):/app" -v "$(winpath "$signing"):/signing:ro" -v bothub-gradle:/root/.gradle -w /app \
  -e BOTHUB_KEYSTORE=/signing/bothub-release.jks -e BOTHUB_KEYSTORE_PASSWORD="$password" -e BOTHUB_KEY_ALIAS=bothub \
  bothub-android sh -c "./gradlew --console=plain :core:test :app:testDebugUnitTest :app:assembleRelease -PappVersion=$version \
    && \$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk"

mkdir -p "$root/dist"
apk="$root/dist/BotHub-$version.apk"
cp "$root/app/build/outputs/apk/release/app-release.apk" "$apk"
echo "built $apk"

args=(release create "v$version" "$apk" --repo Kljub/BotHubAndroitApp --title "BotHub $version")
if [[ -n "$notes" ]]; then args+=(--notes-file "$notes"); else args+=(--generate-notes); fi
gh "${args[@]}"
