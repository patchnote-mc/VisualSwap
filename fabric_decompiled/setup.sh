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

REPO="https://github.com/FabricMC/fabric-api.git"
TAG="0.145.1+26.1"
DEST="fabric_decompiled/sources/$TAG"

step "Remove Existing ... "
run rm -rf "$DEST"

step "Cloning ... "
run git clone \
    --branch "$TAG" \
    --depth 1 \
    --single-branch \
    "$REPO" \
    "$DEST"

step "Removing Git ... "
run rm -rf "$DEST/.git"

success "Fabric API $TAG cloned into $DEST"
