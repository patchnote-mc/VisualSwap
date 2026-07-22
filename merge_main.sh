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
  # --http1.1 dodges intermittent "HTTP2 framing layer" errors (curl exit 16);
  # --retry rides out transient network/5xx failures. Non-transient codes we
  # handle ourselves (e.g. 422) aren't errors to curl, so they aren't retried.
  local args=(
    --http1.1
    --retry 5
    --retry-delay 2
    --retry-all-errors
    -sS
    -o "$TMP_BODY"
    -w '%{http_code}'
    -X "$method"
    -H "Authorization: Bearer ${GH_TOKEN}"
    -H "Accept: application/vnd.github+json"
    -H "X-GitHub-Api-Version: 2022-11-28")
  if [ -n "$body" ]; then args+=(-d "$body"); fi
  HTTP_CODE="$(curl "${args[@]}" "${API}${path}")"
}

# gh_logs JOB_ID -> plain-text log of a single Actions job on stdout. The API
# 302-redirects to a signed blob URL on a different host; curl -L follows it and
# (by design) drops the Authorization header there, which is what the blob wants.
# Diagnostics only, so any failure is swallowed.
gh_logs() {
  curl --http1.1 -sSL \
    -H "Authorization: Bearer ${GH_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "${API}/actions/jobs/$1/logs" 2>/dev/null || true
}

# show_job_log_tail SHA JOB_NAME -> print the tail of the failing job's log. Each
# workflow's job name matches its check-run name, so we find the job by the same
# name we polled the check under.
show_job_log_tail() {
  local sha="$1" name="$2" run_ids rid job_id=""
  api GET "/actions/runs?head_sha=${sha}&per_page=100" || return 0
  run_ids="$(jq -r '.workflow_runs[]?.id' "$TMP_BODY" 2>/dev/null)"
  for rid in $run_ids; do
    api GET "/actions/runs/${rid}/jobs?per_page=100" || continue
    job_id="$(jq -r --arg n "$name" \
      '[.jobs[]? | select(.name==$n) | select(.conclusion!=null and .conclusion!="success" and .conclusion!="skipped")] | last | .id // empty' \
      "$TMP_BODY" 2>/dev/null)"
    [ -n "$job_id" ] && break
  done
  [ -n "$job_id" ] || return 0
  printf '%s\n' "${BOLD}  --- ${name}: last 60 log lines ---${RESET}"
  gh_logs "$job_id" | tail -n 60 | sed 's/^/  /'
}

# show_check_failure SHA CHECK_NAME -> dump everything useful about a red check to
# the console: its URL, the output summary, the ::error:: annotations, and the
# failing job's log tail. This is the "show Actions errors in the console"
# surface. Wrapped in a subshell with set +e / no ERR trap so a hiccup in any
# diagnostic call can never abort the release script.
show_check_failure() {
  ( set +e
    sha="$1"; name="$2"
    if api GET "/commits/${sha}/check-runs?per_page=100"; then
      crid="$(jq -r --arg c "$name"    '[.check_runs[]? | select(.name==$c)] | last | .id // empty'             "$TMP_BODY" 2>/dev/null)"
      url="$(jq -r --arg c "$name"     '[.check_runs[]? | select(.name==$c)] | last | .html_url // empty'       "$TMP_BODY" 2>/dev/null)"
      ctitle="$(jq -r --arg c "$name"  '[.check_runs[]? | select(.name==$c)] | last | .output.title // empty'   "$TMP_BODY" 2>/dev/null)"
      summary="$(jq -r --arg c "$name" '[.check_runs[]? | select(.name==$c)] | last | .output.summary // empty' "$TMP_BODY" 2>/dev/null)"
    fi
    if [ -n "${url:-}" ];     then printf '%s\n' "  ${GRAY}${url}${RESET}"; fi
    if [ -n "${ctitle:-}" ];  then printf '%s\n' "  ${YELLOW}${ctitle}${RESET}"; fi
    if [ -n "${summary:-}" ]; then printf '%s\n' "$summary" | sed 's/^/  /'; fi
    if [ -n "${crid:-}" ] && api GET "/check-runs/${crid}/annotations"; then
      ann="$(jq -r '.[]? | "  [\(.annotation_level)] \(.path):\(.start_line) \(.title // "")\n      \(.message)"' "$TMP_BODY" 2>/dev/null)"
      if [ -n "$ann" ]; then
        printf '%s\n' "${BOLD}  annotations:${RESET}"
        printf '%s\n' "$ann"
      fi
    fi
    show_job_log_tail "$sha" "$name"
  ) || true
}

# watch_publish MERGE_SHA -> poll publish.yml on the merge commit until it finishes.
# Returns 0 on success (or a skipped run), 1 on failure/timeout after dumping the
# Actions errors. Called from an if-condition so its internal non-zero returns
# never trip set -e.
watch_publish() {
  local sha="$1" attempt=0 status concl
  while :; do
    if ! api GET "/commits/${sha}/check-runs?per_page=100"; then
      warn "GitHub API temporarily unavailable - retrying…"
      sleep 5
      continue
    fi
    status="$(jq -r '[.check_runs[]? | select(.name=="publish")] | last | .status // empty'     "$TMP_BODY")"
    concl="$(jq -r  '[.check_runs[]? | select(.name=="publish")] | last | .conclusion // empty' "$TMP_BODY")"
    if [ "$status" = "completed" ]; then
      if [ "$concl" = "success" ]; then success "'publish' passed - release published from ${MAIN}"; return 0; fi
      if [ "$concl" = "skipped" ]; then warn "'publish' reported skipped - no release was cut"; return 0; fi
      fail "'publish' concluded '${concl}' - the release did NOT (fully) publish."
      show_check_failure "$sha" "publish"
      return 1
    fi
    attempt=$((attempt + 1))
    if [ "$attempt" -ge "$PUBLISH_POLL_MAX" ]; then
      fail "Timed out waiting for 'publish'. Check the Actions tab for ${sha:0:7}."
      return 1
    fi
    printf '%s\n' "${GRAY}  … publish ${status:-queued} (attempt ${attempt}/${PUBLISH_POLL_MAX})${RESET}"
    sleep "$POLL_INTERVAL"
  done
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

# read a property from gradle.properties (script must be run from the repo root)
prop() { grep -E "^$1[[:space:]]*=" gradle.properties 2>/dev/null | head -1 | cut -d= -f2- | tr -d '[:space:]'; }
# Mod version string, matching build.gradle's format:
#   version = "v${mod_version}+mc-${minecraft_version}"   (e.g. v1.1.4+mc-26.1)
# Read from gradle.properties so the PR title, the release tag, and the
# version-guard check all derive from the same source and stay in sync.
MOD_VERSION="$(prop mod_version || true)"
MC_VERSION_PROP="$(prop minecraft_version || true)"
LOADER_VERSION="$(prop loader_version || true)"
FABRIC_API_VERSION="$(prop fabric_api_version || true)"
[ -n "$MOD_VERSION" ] && [ -n "$MC_VERSION_PROP" ] || { fail "Could not read mod_version / minecraft_version from gradle.properties - run this from the repo root."; exit 1; }
FULL_VERSION="v${MOD_VERSION}+mc-${MC_VERSION_PROP}"
PR_TITLE="$FULL_VERSION"

# release build metadata, rendered into the PR body
PR_META="$(printf '| Key | Info |\n|---|---|\n| Version | `%s` |\n| Minecraft | `%s` |\n| Fabric Loader | `%s` |\n| Fabric API | `%s` |\n| Merge | `%s` → `%s` |\n' \
  "$FULL_VERSION" "$MC_VERSION_PROP" "${LOADER_VERSION:-?}" "${FABRIC_API_VERSION:-?}" "$STAGING" "$MAIN")"

# variables
# Every one of these must be green before the PR can merge - they are also the
# required status checks on the *-main ruleset (.github/rulesets/protect-main-branches.json):
#   build                   -> .github/workflows/build.yml         (project compiles)
#   block duplicate version -> .github/workflows/version-guard.yml (git tag not taken)
#   publish dry-run         -> .github/workflows/publish-dryrun.yml (release WILL publish)
# The names must match each workflow's JOB name exactly.
REQUIRED_CHECKS=("build" "block duplicate version" "publish dry-run")
# polling config for the required checks
POLL_INTERVAL=15
POLL_MAX=40
# publish.yml runs post-merge (build + upload to Modrinth/CurseForge/GitHub), so
# give it a longer ceiling than the pre-merge checks.
PUBLISH_POLL_MAX=80

# arguments - CLI flags
usage() {
  cat <<EOF
Usage: ${0##*/} [--skip-publish]

  -s, --skip-publish   Merge ${STAGING} into ${MAIN} WITHOUT cutting a release.
                       Adds "[skip publish]" to the PR title and merge commit, so
                       version-guard skips (reports green) and publish.yml does not
                       run - nothing is pushed to Modrinth, CurseForge or GitHub.
                       Also skips the changelog / sources-jar prompts.
  -h, --help           Show this help and exit.

Releasing (without --skip-publish) prompts for:
  * whether to upload the -sources jar alongside the main jar (default: no)
  * the changelog, as Markdown, terminated with Ctrl-D

Both are stamped into the merge commit - the "[sources]" marker in its subject,
the changelog in its body - and read back by .github/workflows/publish.yml.
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

# Collected here, carried to publish.yml in the merge commit: "[sources]" in the
# subject, the changelog in the body.
CHANGELOG=""
INCLUDE_SOURCES=false
COMMIT_TITLE="$PR_TITLE"

step "Step 1/7 - Release details"
if [ "$SKIP_PUBLISH" = true ]; then
  PR_BODY="$(printf 'Automated release PR — **no release will be published** (`[skip publish]`).\n\n## Build info\n\n%s\n| Sources | `%s` |\n' \
    "$PR_META" "Not included")"
  log "skip-publish mode - no changelog or artifact selection needed"
else
  [ -t 0 ] || { fail "No terminal on stdin - the changelog must be entered interactively (or pass --skip-publish)."; exit 1; }

  printf '%s' "${BOLD}Upload the -sources jar too? [y/N] ${RESET}"
  read -r reply || reply=""
  case "$reply" in
    [yY]|[yY][eE][sS]) INCLUDE_SOURCES=true ;;
    *)                 INCLUDE_SOURCES=false ;;
  esac
  if [ "$INCLUDE_SOURCES" = true ]; then
    COMMIT_TITLE="${PR_TITLE} [sources]"
    success "will upload the main jar AND the -sources jar"
  else
    log "will upload the main jar only"
  fi

  git fetch --quiet origin "$MAIN" 2>/dev/null || true
  printf '\n%s\n' "${BOLD}Changelog for ${PR_TITLE} (Markdown).${RESET}"
  if git rev-parse --verify --quiet "origin/${MAIN}" >/dev/null; then
    printf '%s\n' "${GRAY}Commits on ${STAGING} not yet in ${MAIN}:${RESET}"
    git log --oneline --no-decorate --no-merges "origin/${MAIN}..${STAGING}" 2>/dev/null | sed 's/^/  /' || true
  fi
  printf '%s\n' "${GRAY}Type or paste the release notes, then press Ctrl-D on a blank line:${RESET}"
  CHANGELOG="$(cat)"
  CHANGELOG="$(printf '%s\n' "$CHANGELOG" | sed -e '/./,$!d')"

  [ -n "$CHANGELOG" ] || { fail "Empty changelog - aborting. Re-run and provide release notes (or pass --skip-publish)."; exit 1; }
  case "$CHANGELOG" in
    *"[skip publish]"*) fail "The changelog contains '[skip publish]', which would suppress the release. Remove it and re-run."; exit 1 ;;
  esac

  if [ "$INCLUDE_SOURCES" = true ]; then SOURCES_LABEL="Included"; else SOURCES_LABEL="Not included"; fi
  PR_BODY="$(printf '## Changelog\n\n%s\n\n## Build info\n\n%s\n| Sources | `%s` |\n' \
    "$CHANGELOG" "$PR_META" "$SOURCES_LABEL")"
  printf '\n'
  success "changelog captured"
fi

step "Step 2/7 - Validate project"
run ./gradlew build
success "project validated"

step "Step 3/7 - Push ${STAGING} to remote"
run git switch "$STAGING"
run git push origin "$STAGING"
success "${STAGING} pushed"

step "Step 4/7 - Open PR ${STAGING} → ${MAIN} and wait for required checks"
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

# Wait for every required check to go green. On [skip publish] PRs the version
# guard and the publish dry-run's publish-specific steps are skipped, so those
# jobs still report success here and the merge proceeds. Any check that concludes
# non-success dumps its GitHub Actions errors (annotations + failing job log tail)
# to the console before we bail.
api GET "/pulls/${PR_NUMBER}"
HEAD_SHA="$(jq -r '.head.sha' "$TMP_BODY")"
CHECK_LIST="$(printf "'%s' " "${REQUIRED_CHECKS[@]}")"
log "polling ${CHECK_LIST}on ${HEAD_SHA:0:7} (every ${POLL_INTERVAL}s, up to $((POLL_INTERVAL * POLL_MAX))s)"
attempt=0
while :; do
  # A single failed poll shouldn't kill the release - just retry the loop.
  if ! api GET "/commits/${HEAD_SHA}/check-runs?per_page=100"; then
    warn "GitHub API temporarily unavailable - retrying…"
    sleep 5
    continue
  fi
  pending=""
  failed=false
  for chk in "${REQUIRED_CHECKS[@]}"; do
    cstatus="$(jq -r --arg c "$chk" '[.check_runs[]? | select(.name==$c)] | last | .status // empty' "$TMP_BODY")"
    cconcl="$(jq -r --arg c "$chk" '[.check_runs[]? | select(.name==$c)] | last | .conclusion // empty' "$TMP_BODY")"
    if [ "$cstatus" != "completed" ]; then
      pending="${pending}${chk} [${cstatus:-queued}]  "
      continue
    fi
    if [ "$cconcl" = "success" ]; then
      continue
    fi
    fail "'${chk}' concluded '${cconcl}' - refusing to merge. See ${PR_URL}"
    show_check_failure "$HEAD_SHA" "$chk"
    failed=true
  done
  if [ "$failed" = true ]; then
    exit 1
  fi
  if [ -z "$pending" ]; then
    success "all required checks passed: ${CHECK_LIST}"
    break
  fi
  attempt=$((attempt + 1))
  if [ "$attempt" -ge "$POLL_MAX" ]; then
    fail "Timed out waiting for required checks. Still pending: ${pending}. Merge later from ${PR_URL}"
    exit 1
  fi
  printf '%s\n' "${GRAY}  … waiting: ${pending}(attempt ${attempt}/${POLL_MAX})${RESET}"
  sleep "$POLL_INTERVAL"
done

if [ "$SKIP_PUBLISH" = true ]; then
  step "Step 5/7 - Merge PR #${PR_NUMBER} into ${MAIN} (publish skipped)"
  MERGE_BODY="$(jq -n --arg m merge --arg t "$PR_TITLE" '{merge_method:$m, commit_title:$t}')"
else
  step "Step 5/7 - Merge PR #${PR_NUMBER} into ${MAIN} (triggers publish)"
  MERGE_BODY="$(jq -n --arg m merge --arg t "$COMMIT_TITLE" --arg b "$CHANGELOG" \
    '{merge_method:$m, commit_title:$t, commit_message:$b}')"
fi
api PUT "/pulls/${PR_NUMBER}/merge" "$MERGE_BODY"
if [ "$HTTP_CODE" = "200" ]; then
  # The merge commit SHA is what publish.yml runs against; we watch it below.
  MERGE_SHA="$(jq -r '.sha // empty' "$TMP_BODY")"
  if [ "$SKIP_PUBLISH" = true ]; then
    success "merged - publish skipped ([skip publish] in merge commit); no release cut"
  else
    success "merged ${MERGE_SHA:0:7} - publish.yml will now build and release from ${MAIN}"
  fi
else
  fail "Merge failed (HTTP ${HTTP_CODE}). See ${PR_URL}"
  cat "$TMP_BODY"
  exit 1
fi

# Publish runs post-merge (on push to ${MAIN}), so it can't gate the merge itself -
# but we watch it here and surface its Actions errors so a failed release is loud,
# not silent. The pre-merge 'publish dry-run' check already proved it should pass.
PUBLISH_FAILED=false
if [ "$SKIP_PUBLISH" = true ]; then
  step "Step 6/7 - Publish (skipped)"
  log "skip-publish mode - publish.yml will not run; nothing to watch"
else
  step "Step 6/7 - Watch publish.yml build & release from ${MAIN}"
  if [ -z "${MERGE_SHA:-}" ]; then
    warn "could not read the merge commit SHA from the merge response - watch the release manually at ${PR_URL}"
    PUBLISH_FAILED=true
  else
    log "polling 'publish' on ${MERGE_SHA:0:7} (every ${POLL_INTERVAL}s, up to $((POLL_INTERVAL * PUBLISH_POLL_MAX))s)"
    if ! watch_publish "$MERGE_SHA"; then
      PUBLISH_FAILED=true
    fi
  fi
fi

step "Step 7/7 - Fast-forward ${STAGING} to ${MAIN}"
run git fetch
run git switch "$STAGING"
run git merge "origin/${MAIN}" --ff-only
run git push origin "$STAGING"
success "${STAGING} fast-forwarded to ${MAIN}"


if [ "$SKIP_PUBLISH" = true ]; then
  printf '\n%s\n' "${GREEN}${BOLD}Done. ${STAGING} merged into ${MAIN}; publish was skipped (no release).${RESET}"
elif [ "$PUBLISH_FAILED" = true ]; then
  fail "Merged into ${MAIN}, but publish.yml did NOT succeed - the release was not (fully) published. See the Actions errors above and ${PR_URL}."
  exit 1
else
  printf '\n%s\n' "${GREEN}${BOLD}Done. ${STAGING} merged into ${MAIN}; the release published.${RESET}"
fi
