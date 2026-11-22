## Knowledge Loop Conventions

### Before editing code

1. Resolve every module the change touches through `.agents/MODULES.md`; map `/` in an ID to
   subdirectories.
2. Read `.agents/maps/<module>.md` and `.agents/decisions/<module>.md` when present for every
   affected module. Do not load unrelated module files; if output is truncated, continue with
   focused ranges until each required file is complete.
3. Expand source context only to answer a named unresolved question. Stop when the behavior, change
   boundary, constraints, and verification path are clear.
4. Run `git log --oneline -10 -- <path>` for files about to change. Use `git show` only for recent
   commits relevant to the current behavior, including those with matching
   `MODULE: <current module>` Decision blocks.

### After finishing a task

- If implementation changes invalidate an existing module map, use the `map-module` skill: targeted
  refresh when the impact is bounded, otherwise full refresh. If unavailable, report the map as stale.
- In every Decision block created by the `commit-context` skill, fill `MODULE`, `WHY`,
  `ALTERNATIVES`, `CHOSEN`, `TRADEOFFS`, and `RISKS`; add `SUPERSEDES` when replacing a prior
  Decision.
