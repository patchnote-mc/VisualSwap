# Git & Release Guide

How this repository is branched, versioned, and released. Read this before
pushing or cutting a release.

## Branches

Branches follow the convention **`<mc-version>-<type>`**, where `type` is either:

- **`main`** — the release branch. Pushing here publishes a release.
- **`staging`** — the development branch. All work happens here.

Each supported Minecraft version has a `main` + `staging` pair, e.g.:

| Branch         | Purpose             |
| -------------- | ------------------- |
| `26.2-staging` | develop for MC 26.2 |
| `26.2-main`    | release for MC 26.2 |

> The current `main` branch predates this convention. To turn on releases,
> create `26.2-staging` and `26.2-main` from it (see _Migrating from `main`_ below).

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
- **A required status check** (`block duplicate version`) that must pass before
  the merge is allowed (see Duplicate protection).

These apply to everyone, including admins (no bypass).

## Versioning

The published version is built in [`build.gradle`](build.gradle) as:

```
v${mod_version}+mc-${minecraft_version}
```

e.g. `v1.0.0+mc-26.2`. Both values come from
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
3. Open a PR: `…-staging → …-main`. Wait for the **`block duplicate version`**
   check to pass. If it fails, the version already exists — bump `mod_version`.
4. Merge the PR. The push to `…-main` runs
   [`publish.yml`](.github/workflows/publish.yml), which builds and publishes to
   **Modrinth + CurseForge + a GitHub Release** (tag = the full version string).

### Merging without publishing (`--skip-publish`)

Sometimes you want to promote `-staging` into `-main` **without** cutting a release
(landing CI/doc changes, or when the version is already published). Run:

```
./merge_main.sh --skip-publish
```

It stamps `[skip publish]` into **both** the PR title and the merge commit, which:

- makes [`version-guard.yml`](.github/workflows/version-guard.yml) skip its
  duplicate-version check and report **green** — so the required status check still
  passes and the PR can merge even if the version already exists, and
- makes [`publish.yml`](.github/workflows/publish.yml) skip entirely — nothing goes
  to Modrinth, CurseForge or GitHub Releases.

To do it by hand instead: put `[skip publish]` anywhere in the **PR title** (for the
guard) and in the **merge commit message** (for publish).

## Duplicate protection

Two layers stop an accidental double-publish:

1. **`version-guard.yml`** runs on every PR into `*-main` and fails if a git tag
   for the version already exists — this blocks the merge.
2. **`publish.yml`** independently hard-fails if the version is already on
   Modrinth (it never overwrites/replaces).

Both layers are intentionally skipped when the merge carries the `[skip publish]`
marker (see _Merging without publishing_ above) — there is no release to protect.

The consequence: **you must bump `mod_version` for each release.**

## Migrating from `main`

The repo currently has a single `main` branch. To adopt the release flow once:

```
git switch main
git switch -c 26.2-staging
git push -u origin 26.2-staging
git switch -c 26.2-main
git push -u origin 26.2-main
```

Then set `26.2-main` (or `*-main`) protection via the ruleset above, and make
`block duplicate version` a required status check.

## Adding a new Minecraft version

1. Create a new pair from the closest existing one, e.g.:
   ```
   git switch -c 26.3-staging 26.2-staging
   git switch -c 26.3-main    26.2-main
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
| [`build.yml`](.github/workflows/build.yml)                 | push / PR (all branches) | builds + uploads artifacts (CI)                                                  |
| [`version-guard.yml`](.github/workflows/version-guard.yml) | PR into `*-main`         | blocks merging a duplicate version (skipped for `[skip publish]` PRs)            |
| [`publish.yml`](.github/workflows/publish.yml)             | push to `*-main`         | publishes to Modrinth, CurseForge, GitHub (skipped for `[skip publish]` commits) |

## Maintainer configuration

Set once at the repository level (Settings → Secrets and variables → Actions):

- Secret **`MODRINTH_TOKEN`** — Modrinth PAT with _Create versions_ scope.
- Secret **`CURSEFORGE_TOKEN`** — CurseForge upload API token.

`GITHUB_TOKEN` is provided automatically. The Modrinth/CurseForge **project IDs
are hardcoded** in [`publish.yml`](.github/workflows/publish.yml) (same project
for every Minecraft version) — replace the `REPLACE_WITH_VISUALSWAP_*`
placeholders before the first release.
