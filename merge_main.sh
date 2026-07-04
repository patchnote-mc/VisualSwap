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

log()     { printf '%s\n' "${BLUE}> $* ${RESET}"; }
step()    { printf '\n%s\n' "${BOLD}${BLUE}==> ${BOLD}$*${RESET}"; }
success() { printf '%s\n' "${GREEN}$* ${RESET}"; }
warn()    { printf '%s\n' "${YELLOW}$* ${RESET}"; }
fail()    { printf '%s\n' "${RED}$* ${RESET}" >&2; }

trap 'fail "Failed at line $LINENO. Aborting - nothing further will run."' ERR

TMP_BODY="$(mktemp)"
trap 'rm -f "$TMP_BODY"' EXIT
# api METHOD PATH [JSON_BODY] -> HTTP status in $HTTP_CODE, response body in $TMP_BODY
api() {
  local method="$1" path="$2" body="${3:-}"
  local args=(-sS -o "$TMP_BODY" -w '%{http_code}' -X "$method"
    -H "Authorization: Bearer ${GH_TOKEN}"
    -H "Accept: application/vnd.github+json"
    -H "X-GitHub-Api-Version: 2022-11-28")
  if [ -n "$body" ]; then args+=(-d "$body"); fi
  HTTP_CODE="$(curl "${args[@]}" "${API}${path}")"
}

run() {
  log "${GRAY}\$ $*${RESET}"
  "$@"
}

require() {
  # check if command exists
  command -v "$1" >/dev/null 2>&1 || {
    fail "'$1' is required but not installed."; exit 1;
  }; 
}

require git
require curl
require jq   # json processor

# config
REPO="patchnote-mc/VisualSwap"
# release line: <MC_VERSION>-staging -> <MC_VERSION>-main
MC_VERSION="26.2"                          
STAGING="${MC_VERSION}-staging"
MAIN="${MC_VERSION}-main"
PR_TITLE="Release ${MAIN}"
PR_BODY="Automated release PR: ${STAGING} -> ${MAIN}."

# variables
REQUIRED_CHECK="block duplicate version"   
# polling config for required check
POLL_INTERVAL=15
POLL_MAX=40

# arguments - CLI flags
usage() {
  cat <<EOF
Usage: ${0##*/} [--skip-publish]

  -s, --skip-publish   Merge ${STAGING} into ${MAIN} WITHOUT cutting a release.
                       Adds "[skip publish]" to the PR title and merge commit, so
                       version-guard skips (reports green) and publish.yml does not
                       run - nothing is pushed to Modrinth, CurseForge or GitHub.
  -h, --help           Show this help and exit.
EOF
}
SKIP_PUBLISH=false
for arg in "$@"; do
  case "$arg" in
    -s|--skip-publish) SKIP_PUBLISH=true ;;
    -h|--help) usage; exit 0 ;;
    *) fail "Unknown argument: $arg"; usage >&2; exit 1 ;;
  esac
done

# in skip-publish mode the PR title carries the marker so version-guard skips
# (reports green) and the merge commit inherits it so publish.yml is skipped.
if [ "$SKIP_PUBLISH" = true ]; then
  PR_TITLE="[skip publish] ${PR_TITLE}"
fi

# token
TOKEN_FILE=".github-token"

# get token
[ -f "$TOKEN_FILE" ] || { fail "Missing ${TOKEN_FILE} at repo root - create it containing your GitHub PAT (it is gitignored)."; exit 1; }
GH_TOKEN="$(tr -d '[:space:]' < "$TOKEN_FILE")"
[ -n "$GH_TOKEN" ] || { fail "${TOKEN_FILE} is empty."; exit 1; }

OWNER="${REPO%%/*}"
API="https://api.github.com/repos/${REPO}"

# navigate to repo root
run cd "$(git rev-parse --show-toplevel)"

if [ "$SKIP_PUBLISH" = true ]; then
  warn "skip-publish mode ON - will merge ${STAGING} → ${MAIN} but NOT publish a release."
fi

step "Step 1/5 - Validate project"
run ./gradlew build
success "project validated"

step "Step 2/5 - Push ${STAGING} to remote"
run git switch "$STAGING"
run git push origin "$STAGING"
success "${STAGING} pushed"

step "Step 3/5 - Open PR ${STAGING} → ${MAIN} and wait for '${REQUIRED_CHECK}'"
api POST "/pulls" "$(jq -n --arg t "$PR_TITLE" --arg b "$PR_BODY" --arg h "${OWNER}:${STAGING}" --arg base "$MAIN" \
  '{title:$t, body:$b, head:$h, base:$base}')"
if [ "$HTTP_CODE" = "201" ]; then
  # PR success
  PR_NUMBER="$(jq -r '.number' "$TMP_BODY")"
  PR_URL="$(jq -r '.html_url' "$TMP_BODY")"
  success "opened PR #${PR_NUMBER} - ${PR_URL}"
elif [ "$HTTP_CODE" = "422" ]; then
  # 422 = a PR for this head/base already exists, or there are no commits to merge.
  api GET "/pulls?state=open&base=${MAIN}&head=${OWNER}:${STAGING}"
  PR_NUMBER="$(jq -r '.[0].number // empty' "$TMP_BODY")"
  PR_URL="$(jq -r '.[0].html_url // empty' "$TMP_BODY")"
  if [ -z "$PR_NUMBER" ]; then
    fail "GitHub returned 422 and no open PR exists (usually: no commits between ${STAGING} and ${MAIN})."
    jq -r '.message // (.errors | tostring)' "$TMP_BODY" 2>/dev/null || cat "$TMP_BODY"
    exit 1
  fi
  warn "reusing existing PR #${PR_NUMBER} - ${PR_URL}"
  # normalise its title to the current mode (adds/removes the [skip publish] marker)
  api PATCH "/pulls/${PR_NUMBER}" "$(jq -n --arg t "$PR_TITLE" '{title:$t}')"
else
  # PR fail
  fail "PR creation failed (HTTP ${HTTP_CODE})."
  cat "$TMP_BODY"
  exit 1
fi

# wait for the required '${REQUIRED_CHECK}' check. On [skip publish] PRs the guard
# step is skipped, so the job still reports green here and the merge proceeds.
api GET "/pulls/${PR_NUMBER}"
HEAD_SHA="$(jq -r '.head.sha' "$TMP_BODY")"
log "polling '${REQUIRED_CHECK}' on ${HEAD_SHA:0:7} (every ${POLL_INTERVAL}s, up to $((POLL_INTERVAL * POLL_MAX))s)"
attempt=0
while :; do
  api GET "/commits/${HEAD_SHA}/check-runs"
  cstatus="$(jq -r --arg c "$REQUIRED_CHECK" '[.check_runs[]? | select(.name==$c)] | last | .status // empty' "$TMP_BODY")"
  cconcl="$(jq -r --arg c "$REQUIRED_CHECK" '[.check_runs[]? | select(.name==$c)] | last | .conclusion // empty' "$TMP_BODY")"
  if [ "$cstatus" = "completed" ]; then
    if [ "$cconcl" = "success" ]; then
      success "'${REQUIRED_CHECK}' passed"
      break
    fi
    fail "'${REQUIRED_CHECK}' concluded '${cconcl}' - refusing to merge. See ${PR_URL}"
    exit 1
  fi
  attempt=$((attempt + 1))
  if [ "$attempt" -ge "$POLL_MAX" ]; then
    fail "Timed out waiting for '${REQUIRED_CHECK}'. Merge later from ${PR_URL}"
    exit 1
  fi
  printf '%s\n' "${GRAY}  … ${cstatus:-queued} (attempt ${attempt}/${POLL_MAX})${RESET}"
  sleep "$POLL_INTERVAL"
done

if [ "$SKIP_PUBLISH" = true ]; then
  step "Step 4/5 - Merge PR #${PR_NUMBER} into ${MAIN} (publish skipped)"
  MERGE_BODY="$(jq -n --arg m merge --arg t "$PR_TITLE" '{merge_method:$m, commit_title:$t}')"
else
  step "Step 4/5 - Merge PR #${PR_NUMBER} into ${MAIN} (triggers publish)"
  MERGE_BODY="$(jq -n --arg m merge '{merge_method:$m}')"
fi
api PUT "/pulls/${PR_NUMBER}/merge" "$MERGE_BODY"
if [ "$HTTP_CODE" = "200" ]; then
  if [ "$SKIP_PUBLISH" = true ]; then
    success "merged - publish skipped ([skip publish] in merge commit); no release cut"
  else
    success "merged - publish.yml will now build and release from ${MAIN}"
  fi
else
  fail "Merge failed (HTTP ${HTTP_CODE}). See ${PR_URL}"
  cat "$TMP_BODY"
  exit 1
fi

step "Step 5/5 - Fast-forward ${STAGING} to ${MAIN}"
run git fetch
run git switch "$STAGING"
run git merge "origin/${MAIN}" --ff-only
run git push origin "$STAGING"
success "${STAGING} fast-forwarded to ${MAIN}"


if [ "$SKIP_PUBLISH" = true ]; then
  printf '\n%s\n' "${GREEN}${BOLD}Done. ${STAGING} merged into ${MAIN}; publish was skipped (no release).${RESET}"
else
  printf '\n%s\n' "${GREEN}${BOLD}Done. ${STAGING} merged into ${MAIN}; the release is publishing.${RESET}"
fi
