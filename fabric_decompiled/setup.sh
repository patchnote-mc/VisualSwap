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

REPO="https://github.com/FabricMC/fabric-api.git"
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd -- "$SCRIPT_DIR/.." && pwd)
TAG=$(grep -E '^fabric_api_version[[:space:]]*=' "$REPO_ROOT/gradle.properties" | head -1 | cut -d= -f2- | tr -d '[:space:]')
[[ -n "$TAG" ]] || {
  fail "fabric_api_version is missing from $REPO_ROOT/gradle.properties"
  exit 1
}
DEST="$SCRIPT_DIR/sources/$TAG"

step "Step 1/3 — Remove existing Fabric API $TAG sources"
run rm -rf "$DEST"

step "Step 2/3 — Clone Fabric API $TAG"
run git clone \
    --branch "$TAG" \
    --depth 1 \
    --single-branch \
    "$REPO" \
    "$DEST"

step "Step 3/3 — Remove nested Git metadata"
run rm -rf "$DEST/.git"

success "Fabric API $TAG cloned into $DEST"
