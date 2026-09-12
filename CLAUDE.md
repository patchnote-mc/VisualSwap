<!-- last updated: 2026-08-24 -- tracks the codebase; refresh (and bump the date) when the workflow/doc layout below changes. -->

# CLAUDE.md — instructions for Claude Code

This is the committed, pushable copy of the working instructions for this repo.
The detailed sources of truth (keep these authoritative; this file just points
to them and states the essentials):

- **`AGENTS.md`** (repo root) — arch itecture, runtime flows, project-specific patterns.
- **`.github/copilot-instructions.md`** — full working strategy.
- **`.llm/`** — agent/design docs.

## Stack (see `AGENTS.md` for detail)

- Minecraft `1.21.11`, Fabric (Loader `0.19.3`, API `0.141.6+1.21.11`, Loom `1.17.19`), Mojang mappings (never Yarn),
  Java 21. (Gradle wrapper `9.5.1` — Loom 1.17 needs Gradle ≥9.5.)
- Local decompiled/source trees (ignored by Git):
    - `mc_decompiled/sources/1.21.11/common_src/` + `mc_decompiled/sources/1.21.11/client_src/`. Run
      `./gradlew genSources`, then `./mc_decompiled/setup.sh --gradle-cache` to copy and extract Loom's mapped jars and
      sources from the repository-local Gradle cache.
    - `fabric_decompiled/sources/0.141.6+1.21.11/`, cloned by `fabric_decompiled/setup.sh` from the
      `fabric_api_version` configured in `gradle.properties`.

## How to work here (essentials)

1. **Implement first** from prior knowledge + the local caches (`*_decompiled/.index/`,
   `*_decompiled/.knowledge/`). Do NOT browse decompiled sources up front by default.
2. **Verify by compiling:** `./gradlew build`. No need to verify GUI/runtime —
   the user provides screenshots/output when needed.
3. **Only inspect the decompiled `*_src/` (MC) or `src/` (Fabric) if it does not
   compile** or behavior is genuinely unclear. Up-front browsing is allowed for
   niche APIs, but not preferred.
4. **Cache as you go:** when you DO consult sources, record just-enough verified
   facts (plain text, terse) so the research isn't repeated:
    - MC/Fabric: `*_decompiled/.index/{version}/{fully.qualified.ClassName}.txt`
      (flat, no package dirs; version `1.21.11` for MC, `0.141.6+1.21.11` for
      Fabric) and `*_decompiled/.knowledge/{topic}.txt`. See each dir's `_GUIDE.txt`.
5. **Match Mojang mappings exactly**; never use Yarn names or mix namespaces;
   don't invent APIs — verify against the decompilation if unsure.

## Keep docs fresh

Every doc that tracks the codebase (`AGENTS.md`, this file, `.llm/**`,
`.github/copilot-instructions.md`) carries a date. When you change code
a doc describes, update the doc and bump its date in the same pass.
Park features as `DISABLED` with a date instead of deleting their docs.
