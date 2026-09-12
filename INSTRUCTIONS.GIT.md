# Git & Release Guide

_Last updated 2026-07-22._

How this repository is branched, versioned, and released. Read this before
pushing or cutting a release.

## Branches

Branches follow the convention **`<mc-version>-<type>`**, where `type` is either:

- **`main`** — the release branch. Pushing here publishes a release.
- **`staging`** — the development branch. All work happens here.

Each supported Minecraft version has a `main` + `staging` pair, e.g.:

| Branch         | Purpose             |
| -------------- | ------------------- |
| `1.21.11-staging` | develop for MC 1.21.11 |
| `1.21.11-main`    | release for MC 1.21.11 |

> The current `main` branch predates this convention. To turn on releases,
> create `1.21.11-staging` and `1.21.11-main` from it (see _Migrating from `main`_ below).

The **mod version is deliberately not in the branch name.** Branches are
long-lived, but the mod version changes every release — the exact released
version lives in the git tag (`v<mod>+mc-<mc>`) and in `gradle.properties` at
that commit, so putting it in the branch name would only go stale. The Minecraft
version _is_ in the name because it identifies a stable, parallel maintenance
line.

### Flow is one-directional

```
      work + commits              open PR, merge
  ── ──────────────▶  staging ───────────────────▶  main ──▶ publishes
```

- **Do all work on `-staging`.** Commit and push freely.
- **Release by opening a PR `…-staging → …-main` and merging it.** The merge is
  what triggers publishing.
- **Never commit directly to a `-main` branch** — it is protected and will
  reject direct pushes (see below).
- **Never merge `main` back into `staging`.** Main only ever receives changes
  _from_ staging, so a back-merge is unnecessary. In particular, **never delete
  a workflow file from `staging`** — merging that into `main` would delete it
  there and silently break releases.

## Branch protection (`*-main`)

A ruleset targeting `*-main` enforces (checked-in copy:
[`.github/rulesets/protect-main-branches.json`](.github/rulesets/protect-main-branches.json)):

- **Pull request required** — no direct pushes/commits; everything lands via a
  merged PR.
- **No deletions** and **no force-pushes**.
- **Three required status checks** that must all be green before the merge is
  allowed:
  - **`build`** — [`build.yml`](.github/workflows/build.yml); the project compiles.
  - **`block duplicate version`** — [`version-guard.yml`](.github/workflows/version-guard.yml);
    the version's git tag is not already taken (see Duplicate protection).
  - **`publish dry-run`** — [`publish-dryrun.yml`](.github/workflows/publish-dryrun.yml);
    the release _will_ publish (same build + Modrinth checks as `publish.yml`, no
    upload), so a broken build or bad/expired `MODRINTH_TOKEN` fails **before** the
    merge instead of after.

These apply to everyone, including admins (no bypass).

## Versioning

The published version is built in [`build.gradle`](build.gradle) as:

```
v${mod_version}+mc-${minecraft_version}
```

e.g. `v1.0.0+mc-1.21.11`. Both values come from
[`gradle.properties`](gradle.properties) (`mod_version`, `minecraft_version`).

- The `+mc-<version>` suffix keeps the same mod version from colliding across
  Minecraft versions (one Modrinth/CurseForge project serves all of them).
- The leading `v` is intentional but is **not** valid semver, so Fabric logs a
  harmless "non-semantic version" warning at startup. The mod still loads.

**Bump `mod_version` (in `gradle.properties`) on `-staging` before every
release.** Publishing the same version twice is blocked (see below).

## Releasing

1. On the `-staging` branch, bump `mod_version` in `gradle.properties` (and make
   your other changes).
2. Commit and push `-staging`.
3. Open a PR: `…-staging → …-main`. Wait for the **`build`**, **`block duplicate
   version`**, and **`publish dry-run`** checks to pass. If `block duplicate
   version` fails, the version already exists — bump `mod_version`.
4. Merge the PR. The push to `…-main` runs
   [`publish.yml`](.github/workflows/publish.yml), which builds and publishes to
   **Modrinth + CurseForge + a GitHub Release** (tag = the full version string).

In practice, run [`merge_main.sh`](merge_main.sh) — it does steps 2-4, waits for
every required check (dumping the failing job's GitHub Actions errors to the
console if any goes red), and, after merging, **watches `publish.yml`** and
surfaces its errors too (publish runs post-merge, so it can't gate the merge, but
a failed release is reported loudly and the script exits non-zero). It first
prompts for the two per-release inputs:

- **the `-sources` jar** — `[y/N]`, default **no**. Only the main jar is uploaded
  unless you opt in.
- **the changelog** — Markdown, typed or pasted, terminated with `Ctrl-D`.
  It may not be empty, and may not contain `[skip publish]`.

### How the release inputs reach the workflow

`publish.yml` is triggered by a *push*, so it cannot take parameters directly.
`merge_main.sh` therefore stamps both answers into the **merge commit message**
(the same channel `[skip publish]` already uses):

```
Release 1.21.11-main [sources]  <- subject: markers

- Master toggles collapsed …     <- body: the changelog
- Config rows disabled …
```

`publish.yml` splits that message back apart: the subject is scanned for
`[sources]`, and the body becomes the Modrinth changelog and the GitHub Release
body. Pushing to `-main` by hand still works — you just get an empty changelog
and no sources jar.

### Merging without publishing (`--skip-publish`)

Sometimes you want to promote `-staging` into `-main` **without** cutting a release
(landing CI/doc changes, or when the version is already published). Run:

```
./merge_main.sh --skip-publish
```

It stamps `[skip publish]` into **both** the PR title and the merge commit, which:

- makes [`version-guard.yml`](.github/workflows/version-guard.yml) skip its
  duplicate-version check and report **green** — so the required status check still
  passes and the PR can merge even if the version already exists,
- makes [`publish-dryrun.yml`](.github/workflows/publish-dryrun.yml) skip its
  publish-specific steps (it still builds) and report **green**, and
- makes [`publish.yml`](.github/workflows/publish.yml) skip entirely — nothing goes
  to Modrinth, CurseForge or GitHub Releases.

`build` is **not** skipped by the marker — the project must still compile.

To do it by hand instead: put `[skip publish]` anywhere in the **PR title** (for the
guard) and in the **merge commit message** (for publish).

## Duplicate protection

Three layers stop an accidental double-publish:

1. **`version-guard.yml`** runs on every PR into `*-main` and fails if a git tag
   for the version already exists — this blocks the merge.
2. **`publish-dryrun.yml`** runs on the same PR and fails if the version is
   already on Modrinth — this also blocks the merge (catches a duplicate even if
   the git tag is missing, and validates `MODRINTH_TOKEN` up front).
3. **`publish.yml`** independently hard-fails if the version is already on
   Modrinth (it never overwrites/replaces).

All three are intentionally skipped when the merge carries the `[skip publish]`
marker (see _Merging without publishing_ above) — there is no release to protect.

The consequence: **you must bump `mod_version` for each release.**

## Migrating from `main`

The repo currently has a single `main` branch. To adopt the release flow once:

```
git switch main
git switch -c 1.21.11-staging
git push -u origin 1.21.11-staging
git switch -c 1.21.11-main
git push -u origin 1.21.11-main
```

Then set `1.21.11-main` (or `*-main`) protection via the ruleset above, and make
`build`, `block duplicate version`, and `publish dry-run` required status checks.

## Adding a new Minecraft version

1. Create a new pair from the closest existing one, e.g.:
   ```
   git switch -c 1.21.12-staging 1.21.11-staging
   git switch -c 1.21.12-main    1.21.11-main
   ```
2. Update `gradle.properties` (`minecraft_version`, `fabric_api_version`,
   `loader_version`, and the mod-dependency versions as needed) and the
   `minecraft` entry in
   [`fabric.mod.json`](src/main/resources/fabric.mod.json).
3. Push. The `*-main` triggers and ruleset apply automatically by pattern — no
   workflow changes needed.

## Workflows

| File                                                       | Trigger                  | Does                                                                             |
| ---------------------------------------------------------- | ------------------------ | -------------------------------------------------------------------------------- |
| [`build.yml`](.github/workflows/build.yml)                 | push / PR (all branches) | builds + uploads artifacts (CI); **required check** `build`                       |
| [`version-guard.yml`](.github/workflows/version-guard.yml) | PR into `*-main`         | blocks merging a duplicate version (skipped for `[skip publish]` PRs); **required check** `block duplicate version` |
| [`publish-dryrun.yml`](.github/workflows/publish-dryrun.yml) | PR into `*-main`       | dry-runs the publish build + Modrinth checks, no upload (publish steps skipped for `[skip publish]` PRs); **required check** `publish dry-run` |
| [`publish.yml`](.github/workflows/publish.yml)             | push to `*-main`         | publishes to Modrinth, CurseForge, GitHub (skipped for `[skip publish]` commits) |

## Maintainer configuration

Set once at the repository level (Settings → Secrets and variables → Actions):

- Secret **`MODRINTH_TOKEN`** — Modrinth PAT with _Create versions_ **and _Read
  projects_** scopes. The read scope is what lets the duplicate check list the
  project's existing versions; it is required because an unpublished (draft)
  project 404s for anonymous callers. _Write projects_ is **no longer needed.**
- Secret **`CURSEFORGE_TOKEN`** — CurseForge upload API token.

`GITHUB_TOKEN` is provided automatically. The Modrinth/CurseForge **project IDs
are hardcoded** in [`publish.yml`](.github/workflows/publish.yml) (same project
for every Minecraft version).

### Mod environment

Each Modrinth version is published with **`environment: client_only`**, set from
`MODRINTH_ENVIRONMENT` in [`publish.yml`](.github/workflows/publish.yml).

This is why `publish.yml` uploads to Modrinth with `curl` instead of letting
`mc-publish` do it: `environment` is only accepted **at version creation**. It
exists on `CreatableVersion` (`POST /v2/version`) but not on `EditableVersion`
(`PATCH /v2/version/{id}`), and there is no project-level equivalent — so a
version created without it can never be corrected. `mc-publish` has no
`environment` input and never sends the field.

`mc-publish` still owns CurseForge and the GitHub Release (it auto-detects
CurseForge dependencies from `fabric.mod.json`); its Modrinth leg is disabled
simply by not passing it any `modrinth-*` inputs. The Modrinth **dependencies**
are therefore declared explicitly in `publish.yml`, resolved from slugs
(`fabric-api`, `cloth-config` required; `modmenu` optional) so they mirror the
`depends`/`suggests` blocks of `fabric.mod.json`. **Keep the two in sync by hand.**

The project-level `client_side`/`server_side` fields are the older, coarser
system and are no longer written by CI. Set them once on the Modrinth project.

CurseForge's **Client**/**Server** tag has no equivalent automation —
`mc-publish` never sends the environment tag, and CurseForge only accepts it at
file-upload time. Set it by hand on each uploaded file, or leave it untagged.
