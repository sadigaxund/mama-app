# Architecture

This page says how the code is organised. `docs/design.md` says what the app does.

## Packages

The package root is `com.mama.app`.

| Package | Holds | Android imports |
|---|---|---|
| `model` | enums, value classes, `Money` | none |
| `data` | `DbHelper` and one repository per aggregate | `android.database`, `android.content` |

Screens and services get their own packages in later steps. They call repositories and never write SQL.

## Rules

- `model` has no `android.*` import. JVM tests in `app/src/test` run it without a device.
- Repositories own all SQL. No other class opens a database or builds a query.
- A repository takes a `DbHelper` in its constructor.
- Time is injected. Every method that stamps a time takes `nowMillis` from its caller, so tests set the clock.
- A repository validates its input and throws `IllegalArgumentException` with the reason.
- Moving an order to a stage `Stage.canMoveTo` forbids throws `IllegalStateException`.
- Money is a `long` in minor units. A deadline is a `LocalDate`, stored as days since 1970-01-01.

## Database

`DbHelper` creates `mama.db`, version 1, with the six tables from `docs/design.md`. Foreign keys are on. Every child table deletes with its order. A null name opens an in-memory database for tests.

SQLite `COLLATE NOCASE` folds ASCII letters only. `GarmentTypeRepository.add` therefore also compares names in Java, so "костюм" matches "Костюм". `OrderRepository.search` folds case in Java for the same reason.

## Tests

| Kind | Where | Run |
|---|---|---|
| JVM unit tests for `model` | `app/src/test` | `./gradlew testDebugUnitTest` |
| Repository tests on a device | `app/src/androidTest` | `./gradlew connectedDebugAndroidTest` |

The device tests use the framework `android.test` classes and an in-memory database per test. No AndroidX test library is involved.
