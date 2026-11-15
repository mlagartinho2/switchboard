# Build plan — Switchboard

Repo 1 of 3 (build this first). Switchboard routes one API contract to the right use-case implementation (Strategy + resolver chain).

This plan drives GitHub Copilot in VS Code with Spec Kit 1.2.2 step by step. Each step has the exact prompt to use, what to check afterwards, and when to commit. Do not skip the checks: the next step builds on the previous result.

## Layout

```text
switchboard/                         <- git repo root; the Maven project is generated here
├── switchboard.code-workspace       <- open this in VS Code
├── .gitignore
└── copilot/                    <- Spec Kit lives here
    ├── .github/skills/         <- the /speckit-* commands for Copilot
    ├── .specify/               <- Spec Kit templates, scripts, memory
    ├── input/                  <- your pre-written specs (constitution, specify, plan, full spec)
    ├── specs/                  <- created by Spec Kit as you go
    └── BUILD_PLAN.md           <- this file
```

Spec Kit works relative to `copilot/`, so source code does **not** go there. Every plan prompt below tells Copilot to generate the Maven project at the repo root, one level up.

## Before you start

- [ ] Open `switchboard.code-workspace` in VS Code (not the folder), so Copilot sees both `copilot/` and the repo root.
- [ ] GitHub Copilot Chat is in **Agent** mode. Type `/speckit-` in the chat box: the commands should appear. If they don't, open only the `copilot/` folder instead, which is a setup problem to sort out before continuing.
- [ ] Java 17+ and Maven are on your PATH (`java -version`, `mvn -v`).
- [ ] Nothing else must exist first. Directory (repo 3) depends on this library, so finish and install this one before starting Directory.
- [ ] Open question for you: the group ID. The specs use the placeholder `com.yourorg`. Decide the real one before step 4, and replace it in the plan prompt below.

The specs in `input/` use dots in command names (`/speckit.constitution`). With Spec Kit 1.2.2 on Copilot the commands are hyphenated (`/speckit-constitution`). Use the hyphenated form from this plan.

## Steps

### Step 1 — Constitution

```text
/speckit-constitution Use the full content of copilot/input/constitution.md as the project constitution. Keep its rules and wording; only adapt it to the template structure.
```

**Check:** `.specify/memory/constitution.md` has no `[PLACEHOLDER]` text left, and still contains the rules about Spring Boot 4.x only, the two-module release, and the Effective Java baseline.
**Commit:** `git add -A && git commit -m "Add constitution"` (from the repo root).

### Step 2 — Specify

```text
/speckit-specify Use the full content of copilot/input/specify.md as the feature description.
```

**Check:** a `specs/001-*/spec.md` exists. Read it against `input/specify.md`: nothing from "What the library must do" or "Behavior requirements" is lost, and the out-of-scope list is kept.
**Commit.**

### Step 3 — Clarify

```text
/speckit-clarify
```

Answer its questions yourself. The answers are recorded in the spec. If it asks something already decided in `input/switchboard-spec.md`, point it there instead of inventing an answer.

**Check:** the spec has a clarifications section and no open "NEEDS CLARIFICATION" markers.
**Commit.**

### Step 4 — Plan

```text
/speckit-plan Use copilot/input/plan.md as the technical plan, and copilot/input/switchboard-spec.md as the detailed reference. Build constraints: Maven; a parent pom.xml at the REPO ROOT (one level above copilot/) with two modules, switchboard-autoconfigure (all code) and switchboard-spring-boot-starter (dependencies only); Java 17+; Spring Boot 4.x; group ID com.yourorg (replace with the real group ID). All source code, poms and tests go under the repo root, never inside copilot/.
```

**Check:** `specs/001-*/plan.md` follows `input/plan.md` (classes, auto-configuration, config schema, error handling); the Maven layout is at the repo root; no Spring Boot 2/3 compatibility code is planned.
**Commit.**

### Step 5 — Tasks

```text
/speckit-tasks
```

**Check:** `specs/001-*/tasks.md` is grouped in phases, and every task names a real file path under the repo root.
**Commit.**

### Step 6 — Analyze

```text
/speckit-analyze
```

**Check:** no CRITICAL findings. Fix real inconsistencies in the spec/plan/tasks before implementing; don't implement around them.
**Commit.**

### Step 7 — Implement, one phase at a time

Do not run implement for everything at once. Run one phase, review, build, commit, then the next.

```text
/speckit-implement Implement Phase 1 (setup) of tasks.md only, then stop so I can review.
```

Repeat with `Phase 2`, `Phase 3`, and so on until tasks.md is done.

**Check after every phase:** from the repo root, `mvn -q verify` passes; mark finished tasks in tasks.md; review the diff for the constitution's rules (null checks, immutable collections, no business logic in the library).
**Commit** each phase separately.

### Step 8 — Add the two changes Directory needs (second feature)

Directory cannot compile until Switchboard has two small additive changes: optional `endpoint`/`method`
attributes on `@SwitchboardCase`, a `describeActivation()` default method on `SwitchboardResolver`
(plus the `ActivationCondition` record), and a bidirectional `RouteMap`. They are specified in Directory's plan,
not in this repo's `input/plan.md`, so they run as their own small feature after the core is done.

This path assumes the three repos sit side by side in the same parent folder:

```text
/speckit-specify Add discovery metadata to Switchboard so a separate library can describe it. Read the section "Prerequisite: two additive changes must already exist in Switchboard" in ../../directory/copilot/input/plan.md and the "Required additive Switchboard changes" section of ../../directory/copilot/input/directory-spec.md. Changes must be additive only: no existing behaviour or signature may change.
```

Then run `/speckit-plan`, `/speckit-tasks`, `/speckit-analyze` and `/speckit-implement` again as in steps 4–7.

**Check:** existing tests still pass untouched; `mvn -q install` succeeds so Directory can resolve the artifact.

### Release (not planned yet)

Publishing to your internal Nexus needs the repository URL, credentials setup and the `distributionManagement` section. None of that has been provided, so it is left out on purpose. Add it as its own Spec Kit feature when you have those details.
