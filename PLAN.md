# Plan

What is built, what is next. `docs/design.md` says what the app does. Each
step lands as one commit and is checked on a device before it is ticked.

## Done

- [x] Toolchain: SDK, Gradle wrapper, debug APK installs and logs on Waydroid
- [x] Design final: `docs/design.md` is locked; changes to it need the maintainer

## Next

1. [x] Repo setup: `.gitignore`, first commit, push
2. [x] Database: all six tables, stage-move rules, balance math, JVM tests
3. [ ] Board: stage tabs, cards with kind colors, read-only
4. [ ] Order form: create and edit, garment type picker with "+ New type"
5. [ ] Moving orders: arrow button and long-press menu
6. [ ] Order detail with ledger: add payment, add expense, balance
7. [ ] Phone: contacts picker and call button
8. [ ] Search, filter, sort
9. [ ] Settings: language (RU default, AZ, EN) and currency (AZN default)
10. [ ] Photos: camera and gallery, card thumbnail, full-screen view
11. [ ] Appointments: add, edit, delete, banner on fitting and done moves
12. [ ] Reminders: morning notification group, actions, appointment alarms, reboot
13. [ ] Share: text, image and PDF
14. [ ] Backup: Google Cloud setup, sign-in, daily Drive folder sync, restore
15. [ ] Check every step on the physical phone

## Possible features

- Direct call without the dialer, which needs the `CALL_PHONE` permission.
- Free-form tags.
- Alarm-clock ringing for appointments, which needs the full-screen intent
  permission granted by hand on Android 14 and newer.
- A calendar view of appointments.

## How each step is done

1. A worker implements the step from `docs/design.md` and this file.
2. The orchestrator reruns `./gradlew assembleDebug test`, reads the whole
   diff, and checks it against `AGENTS.md`.
3. The orchestrator installs it on Waydroid, drives it with
   `adb shell input`, and reads `adb exec-out screencap -p` screenshots and
   logcat. A UI step is judged on the screenshots: alignment, spacing, touch
   targets of 48 dp or more, text that fits in all three languages.
4. The step is committed and ticked here.

A step that needs the maintainer (a device prompt, the phone, Google Cloud)
stops only that step. Work moves on to the next step that does not depend on
it, and the question goes below.

## Questions for the maintainer

1. JUnit 4 was added for tests only, under the orchestrator's judgment while
   the maintainer was away. It never ships in the app. Keep it?
2. Stepping back from Done returns to Fitting 2, whichever stage the order
   came from. Good enough, or should the app remember the exact stage?
3. The new-type picker seeds seven Russian garment types on first launch:
   Костюм, Платье, Брюки, Юбка, Пиджак, Рубашка, Пальто. Change the list?
4. There is no `README.md`, which `AGENTS.md` says the maintainer owns.
   `docs/architecture.md` is not linked from anywhere until one exists. May
   the orchestrator write a short one?
