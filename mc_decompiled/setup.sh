#!/bin/bash
set -euo pipefail

clear || true
clear || true
clear || true

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

usage() {
  cat <<'EOF'
Usage: mc_decompiled/setup.sh [--gradle-cache] [--version VERSION]

Without flags, downloads and decompiles Minecraft 26.2 as before.

  --gradle-cache     Copy Loom-mapped jars and generated sources from the
                     repository's local Gradle cache. Uses minecraft_version
                     from gradle.properties unless --version is also supplied.
  --version VERSION  Override the Minecraft version.
  -h, --help         Show this help.
EOF
}

project_minecraft_version() {
  local properties="$REPO_ROOT/gradle.properties"
  local key value

  while IFS='=' read -r key value; do
    if [[ "$key" == "minecraft_version" && -n "$value" ]]; then
      printf '%s\n' "$value"
      return 0
    fi
  done < "$properties"

  fail "minecraft_version is missing from $properties"
  return 1
}

find_latest_cache_jar() {
  local kind="$1"
  local want_sources="$2"
  local cache_root="$REPO_ROOT/.gradle/loom-cache/minecraftMaven/net/minecraft"
  local candidate candidate_version latest=""

  [[ -d "$cache_root" ]] || {
    fail "Gradle Minecraft cache not found: $cache_root"
    return 1
  }

  while IFS= read -r -d '' candidate; do
    candidate_version=$(basename "$(dirname "$candidate")")
    [[ "$candidate_version" == "$VERSION" || "$candidate_version" == "$VERSION"-* ]] || continue

    if [[ "$want_sources" == "true" ]]; then
      [[ "$candidate" == *-sources.jar ]] || continue
    else
      [[ "$candidate" != *-sources.jar ]] || continue
    fi

    if [[ -z "$latest" || "$candidate" -nt "$latest" ]]; then
      latest="$candidate"
    fi
  done < <(find "$cache_root" -type f -name "minecraft-${kind}-*.jar" -print0)

  [[ -n "$latest" ]] || {
    fail "No cached Minecraft $VERSION ${kind} jar found (sources=$want_sources). Run ./gradlew genSources first."
    return 1
  }

  printf '%s\n' "$latest"
}

# Decompile $1 into $2, turning CFR's per-class "Processing" lines into a bar.
# CFR emits one "Processing" per top-level class, so the denominator counts
# .class entries excluding inner classes (those containing '$').
decompile() {
  local jar="$1" outdir="$2"
  local total count=0 percent filled width=40
  local FULL='########################################'
  local BLANK='........................................'

  total=$(jar tf "$jar" | grep '\.class$' | grep -vc '\$' || true)
  (( total > 0 )) || total=1

  printf '%s\n' "${GRAY}\$${BLUE} java -jar cfr.jar $jar --outputdir $outdir${RESET}"

  java -jar cfr.jar "$jar" --outputdir "$outdir" 2>&1 |
  while IFS= read -r line; do
    [[ $line == Processing\ * ]] || continue
    count=$((count + 1))
    percent=$((count * 100 / total))
    (( percent > 100 )) && percent=100
    filled=$((percent * width / 100))
    printf '\r%s[%s%s]%s %3d%% (%d/%d)' \
      "$GREEN" "${FULL:0:filled}" "${BLANK:0:width-filled}" "$RESET" \
      "$percent" "$count" "$total"
  done
  printf '\n'
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

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd -- "$SCRIPT_DIR/.." && pwd)
MODE="download"
VERSION="26.2"
VERSION_SET="false"

while (( $# > 0 )); do
  case "$1" in
    --gradle-cache)
      MODE="gradle-cache"
      shift
      ;;
    --version)
      [[ $# -ge 2 && -n "$2" ]] || {
        fail "--version requires a value"
        usage
        exit 2
      }
      VERSION="$2"
      VERSION_SET="true"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      usage
      exit 2
      ;;
  esac
done

if [[ "$MODE" == "gradle-cache" && "$VERSION_SET" == "false" ]]; then
  VERSION=$(project_minecraft_version)
fi

DEST="$SCRIPT_DIR/sources/$VERSION"

if [[ "$MODE" == "gradle-cache" ]]; then
  step "Step 1/3 — Locate Minecraft $VERSION in the Gradle cache"
  COMMON_JAR=$(find_latest_cache_jar common false)
  CLIENT_JAR=$(find_latest_cache_jar clientOnly false)
  COMMON_SOURCES=$(find_latest_cache_jar common true)
  CLIENT_SOURCES=$(find_latest_cache_jar clientOnly true)

  log "Common jar: $COMMON_JAR"
  log "Client jar: $CLIENT_JAR"
  log "Common sources: $COMMON_SOURCES"
  log "Client sources: $CLIENT_SOURCES"

  step "Step 2/3 — Copy mapped jars and source archives"
  run mkdir -p "$DEST"
  run cp "$COMMON_JAR" "$DEST/common.jar"
  run cp "$CLIENT_JAR" "$DEST/client.jar"
  run cp "$COMMON_SOURCES" "$DEST/common-sources.jar"
  run cp "$CLIENT_SOURCES" "$DEST/client-sources.jar"

  step "Step 3/3 — Extract mapped sources"
  if [[ -d "$DEST/common_src" || -d "$DEST/client_src" ]]; then
    warn "Replacing existing extracted sources for Minecraft $VERSION"
    run rm -rf "$DEST/common_src" "$DEST/client_src"
  fi
  run mkdir -p "$DEST/common_src" "$DEST/client_src"
  run unzip -q "$DEST/common-sources.jar" -d "$DEST/common_src"
  run unzip -q "$DEST/client-sources.jar" -d "$DEST/client_src"

  success "Minecraft $VERSION Gradle cache copied into $DEST"
  exit 0
fi

step "Step 1/4 — Prepare Minecraft $VERSION destination"
run mkdir -p "$DEST"
run cd "$DEST"

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

step "Step 2/4 — Download jars"

if [[ -f client.jar ]]; then
    warn "Deleting existing client.jar"
    rm client.jar
fi
download '.downloads.client.url' client.jar

if [[ ! -f cfr.jar ]]; then
    log "Downloading CFR ..."
    run curl -L "https://www.benf.org/other/cfr/cfr-0.152.jar" -o cfr.jar
fi

step "Step 3/4 — Decompile client"

if [[ -f client.jar ]]; then
    log "Decompiling client ..."
    decompile client.jar client_src
fi

step "Step 4/4 — Clean up"
run rm cfr.jar

success "Minecraft $VERSION sources are ready in $DEST"
