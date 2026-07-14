#!/bin/bash
set -euo pipefail

clear
clear
clear

RED=$'\033[0;31m'
GREEN=$'\033[0;32m'
YELLOW=$'\033[0;33m'
BLUE=$'\033[0;34m'
GRAY=$'\033[0;90m'
BOLD=$'\033[1m'
RESET=$'\033[0m'

log()     { printf '%s\n' "${GRAY}> $* ${RESET}"; }
step()    { printf '\n%s\n' "${BOLD}${BLUE}==> ${BOLD}$*${RESET}"; }
success() { printf '%s\n' "${GREEN}> $* ${RESET}"; }
warn()    { printf '%s\n' "${YELLOW}> $* ${RESET}"; }
fail()    { printf '%s\n' "${RED}> $* ${RESET}" >&2; }

trap 'fail "Failed at line $LINENO. Aborting - nothing further will run."' ERR

run() {
  printf '%s\n' "${GRAY}\$${BLUE} $*${RESET}"
  "$@"
}

download() {
    local key="$1"
    local outfile="$2"

    # filter URL from version.json
    local url
    url=$(jq -r "$key // empty" version.json)

    # if url exists
    if [[ -n "$url" ]]; then
        log "Downloading $outfile"
        run curl -L "$url" -o "$outfile"
    else
        warn "$outfile not present"
    fi
}

VERSION="26.2"

run mkdir -p sources/$VERSION
run cd sources/$VERSION

log "Fetching Version Manifest ..."

MANIFEST="https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
VERSION_JSON_URL=$(
    curl -s "$MANIFEST" |
    jq -r --arg v "$VERSION" '
        .versions[]
        | select(.id==$v)
        | .url
    '
)

if [[ "$VERSION_JSON_URL" == "null" ]]; then
    fail "Unknown Minecraft version: $VERSION"
    run exit 1
fi

run curl -s "$VERSION_JSON_URL" -o version.json

step "Downloading JARs ..."

if [[ -f client.jar ]]; then
    warn "Deleting existing client.jar"
    rm client.jar
fi
download '.downloads.client.url' client.jar

if [[ -f server.jar ]]; then
    warn "Deleting existing server.jar"
    rm server.jar
fi
download '.downloads.server.url' server.jar

if [[ ! -f cfr.jar ]]; then
    log "Downloading CFR ..."
    run curl -L "https://www.benf.org/other/cfr/cfr-0.152.jar" -o cfr.jar
fi

step "Decompiling ..."

if [[ -f client.jar ]]; then
    log "Decompiling client ..."
    run java -jar cfr.jar client.jar --outputdir client_src
fi

if [[ -f server.jar ]]; then
    log "Decompiling server ..."
    run java -jar cfr.jar server.jar --outputdir server_src
fi

step "Cleaning up ..."
run rm cfr.jar

success "Done"
