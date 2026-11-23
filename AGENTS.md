# AGENTS.md: working agreements for AI agents in this repo

Read this before touching code. It says how to work here. `docs/design.md`
says what to build and why, once it exists. If the two disagree about design,
`docs/design.md` wins and this file gets fixed.

This file is the one instruction file for every agent and tool. `CLAUDE.md`
is a pointer stub. Never add content there.

---

## 1. Project context

An Android app. Plain Java on the framework `Activity` API, built from the
command line with the Gradle wrapper. No Android Studio is required.

### Stack (fixed: do not add alternatives)

| Piece | Version | Where it is pinned |
|---|---|---|
| JDK | 17 | the developer machine |
| Gradle | 8.11.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.7.3 | `build.gradle` |
| `compileSdk` / `targetSdk` | 35 | `app/build.gradle` |
| `minSdk` | 26 | `app/build.gradle` |
| `com.google.android.gms:play-services-auth` | latest stable at adoption | `app/build.gradle` |

- Language is Java. No Kotlin, no Jetpack Compose, no third-party library.
  Adding any requires explicit approval from the maintainer, and the
  approval goes into this table in the same commit.
- `play-services-auth` is the one approved library. It exists only for Google
  sign-in and the Drive token (`docs/design.md`, Backup). The AndroidX
  modules it pulls in transitively are allowed. Code outside the backup
  package never imports `com.google.*` or `androidx.*` directly.
- AGP and Gradle move together. Bump one only to a version the AGP release
  notes list as compatible with the other, and bump both in one commit.
- Always build with `./gradlew`, never a system `gradle`. The system one is a
  different major version.

### Commands (repo root)

- `./gradlew assembleDebug`: build `app/build/outputs/apk/debug/app-debug.apk`
- `./gradlew test`: unit tests on the JVM
- `adb devices -l`: list attached devices
- `adb install -r <apk>`: install or update the app
- `adb shell am start -n com.mama.app/.MainActivity`: launch it
- `adb logcat -s MamaApp`: read the app's own log tag

---

## 2. Devices and verification

### 2.1 Two targets, both real

Waydroid is the desktop target. Start it with `waydroid show-full-ui`, read
its address from `waydroid status`, then `adb connect <ip>:5555`. The
physical phone connects over USB with USB debugging enabled.

The first `adb` connection to either target shows an authorization prompt on
that device. Until a human taps Allow, `adb devices` reports `unauthorized`.
An agent cannot tap it. Stop and ask.

### 2.2 A green build does not mean it works

A feature works when it has been installed on a device, launched, and its
effect seen on screen or in logcat. A successful `assembleDebug` proves the
code compiles and nothing more. "I could not verify X on a device" is a
useful result. A false "works" costs a session.

Every user-visible change ships with the exact `adb` commands used and the
relevant logcat lines. Waydroid is the default check. A change touching
hardware, sensors, permissions or screen size is also checked on the phone,
because Waydroid is x86_64 and behaves differently from the phone in those
areas.

### 2.3 Never parse human-format output

Read the exit code of `./gradlew` and `adb`, never their prose. A
`grep FAILED` over test output once matched test data and reported a wrong
count twice. For test results read the JUnit XML under `app/build/test-results/`.

### 2.4 A guard is not done until you have watched it fail

Break the thing a test or check protects, and confirm the run goes red
naming what you broke. A guard is easy to write in a form that never fails,
and its green runs then read as evidence forever. Commit before you attack
your own work, so the restore has something to restore to.

### 2.5 Decisions that belong to the maintainer

- Accepting an SDK license. Never pipe `yes` into `sdkmanager --licenses`.
- Tapping a prompt on a device.
- Release signing, store listing, and release timing.

---

## 3. Coding rules

- Fix the defect you find. Do not write it up. A known bug that you cannot
  fix now becomes a failing test marked as expected-to-fail with its reason,
  never a bullet in a document.
- No silent no-ops. Trace every new field, callback, flag and manifest entry
  to the code that consumes it.
- Trace every consumer before changing a type, a method signature or a
  resource name. Grep every call site first.
- When moving or splitting a class, copy the bodies verbatim. Never rebuild
  them from memory. A rebuilt move compiles, looks right in review, and fails
  at the call site.
- For a closed set, classify by what you exclude. An include-list silently
  drops every new member.
- A null check is `x == null`. Do not let an empty string or zero stand in
  for "not set" unless every such value means exactly that.
- A broad `catch (Exception e)` carries a comment saying why it is safe.
  Never swallow an exception without a log line under the app tag.
- A number the code cannot derive is a number the code must not state. No
  invented timeouts, limits or defaults without a measurement behind them.
- Every permission in `AndroidManifest.xml` is justified in
  `docs/design.md`. Request runtime permissions at the moment of use, never
  at launch.
- No secrets in the tree. Signing keys, keystore passwords and API keys
  stay out of git. `local.properties` holds a machine-local SDK path and is
  never committed.
- Prefer making a mistake impossible over documenting it. A `private`
  field, a `final`, a lint rule or a failing test is worth more than a
  paragraph here.

---

## 4. Git and GitHub

The repository is `sadigaxund/mama-app`. The default branch is `main`.

- Commit format is `type(scope): message`, where type is one of `feat`,
  `fix`, `chore`, `docs`, `refactor`, `test`, `release`. The body has three
  lines at most.
- Stage files by explicit path. Never `git add -A` or `git add -u`. Read
  `git status` and `git diff --staged` before every commit.
- Never `--amend`, never force-push, never an empty commit, never rewrite
  published history. If something leaks into history, tell the maintainer
  and wait.
- The orchestrator commits and pushes to `main` after each finished step.
  Workers run no git at all (§6). Tags, releases and merges of outside pull
  requests need the maintainer each time.
- Commit per unit of work, not per session. A session limit once killed 220
  uncommitted lines.
- Never put a session URL, chat link or `Claude-Session:` trailer in a
  commit, pull request, issue or comment. A tool default that adds one is
  overridden by this rule. No `Co-Authored-By:` line and no AI attribution
  of any kind. Commits carry the maintainer's git identity only.
- Never address other people on GitHub without the maintainer's consent.
  The account speaks with the maintainer's voice.
- A docs-only change goes straight to `main`. A code change goes through one
  pull request per bundle of work, not one per file.

---

## 5. Writing

These rules cover every committed sentence: docs, READMEs, CHANGELOG
entries, code comments and commit messages.

- One fact per sentence, about twenty words, plain verbs.
- No em dashes. No "not X but Y". No bold mini-headings inside bullets.
- Give the number or say nothing. A comparative word with no measurement
  behind it is a claim nobody made.
- Name the source. "Testing showed" needs the test.
- Delete the words that survive their own deletion: crucial, delve,
  pivotal, showcase, tapestry, testament, underscore, vibrant, landscape,
  key (as an adjective), robust, leverage, seamless, comprehensive.
- No "-ing" clause added to a fact to make it sound deeper.
- No dates in docs or READMEs. Dates live in `CHANGELOG.md`.
- Public text states facts a reader can check from the repository alone. It
  never cites a chat, a session, a work round, a `tmp/` file, or an
  absolute path from a developer's machine. It describes the change, never
  the conversation that produced it.
- Final pass on any authored page: apply the humanizer rules.

### 5.1 Documents and their jobs

- `README.md` is the front door and belongs to the maintainer. It says what
  the app is, how to build and install it, and links into `docs/`. It never
  holds the material itself.
- `docs/` is prose for humans. One audience per page, plain language, code
  in short display blocks. Docs state what holds today, never how it came to
  hold. Every status claim is checked against the code in the same commit.
- `CHANGELOG.md` is release notes. It records what a user of a release would
  notice. Process, CI and agent-rule changes never go in it. A released
  section is not edited afterwards.
- Code comments state the rule and point at the test or doc that holds it.
  Reasoning belongs in the commit message.
- While rewriting anything, collect gaps and misdesigns and report them.
  Never change a design decision silently as part of a rewrite.

---

## 6. Agents and orchestration

- Subagents run no `git` command, read-only ones included. The orchestrator
  commits. A subagent reports what it changed.
- Parallel agents own disjoint directories, each in its own `git worktree`
  created before the agent starts.
- Gradle builds and device runs go one at a time. Parallel Gradle daemons
  and emulator traffic exhaust memory on the development machine.
- A worker's report is a claim, not evidence. Before committing worker
  output, the orchestrator reruns the build, reads the whole diff, and
  repeats any "verified" claim with its own command.
- Progress lives in tracked files, so a context compaction loses nothing.
  Anything remembered that is not in a tracked file is not evidence. After a
  compaction, rebuild and reread this file before continuing.

### 6.1 Issues

- Work that nobody needs now and that blocks nothing becomes a GitHub issue
  titled `Possible feature: ...`. It says what exists, why it is not done,
  and what doing it would take. Small polish does not earn an issue.
- An issue is a report, not a spec. Check its claims against the code and
  reproduce the failure before any work starts.

---

## 7. Change-trigger matrix

Touch the left column and the right column moves in the same commit.

| If you change… | You must also… |
|---|---|
| A dependency, plugin, or SDK level | update the Stack table in §1 |
| `AndroidManifest.xml` permissions | justify each one in `docs/design.md` |
| Anything a user of a release would notice | add one line under `CHANGELOG.md` `## [Unreleased]` |
| A screen or user-visible behavior | attach the `adb` commands and logcat lines from a device run (§2.2) |
| A file path this document names | fix the path here |
| A new file under `docs/` | link it from `README.md` |
| A design decision | record it in `docs/design.md` |
| A step of work started, finished, or a question answered | update `PLAN.md`, the one home for status |

---

## 8. Maintaining this file

This file fails by growing into a junk drawer that nobody reads.

Add an entry only when something went wrong that a reasonable agent would
repeat, and the lesson is not already discoverable from `docs/design.md`,
the compiler or a failing test. Every entry states the failure it prevents.
An instruction without a reason cannot be judged later, so it never gets
deleted.

Delete an entry once its cause is fixed. When a script or test can hold a
rule, write the check and shrink the paragraph to a pointer at it. Do not
duplicate `docs/design.md`. Link to it.
