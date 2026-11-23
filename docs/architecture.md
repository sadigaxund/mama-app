# Architecture

This page says how the code is organised. `docs/design.md` says what the app does.

## Packages

The package root is `com.mama.app`.

| Package | Holds | Android imports |
|---|---|---|
| `model` | enums, value classes, `Money` | none |
| `data` | `DbHelper` and one repository per aggregate | `android.database`, `android.content` |
| `ui` | `BaseActivity`, `LocaleSupport`, `Format`, `OrderCardAdapter` | `android.*` |
| `debug` | `DemoData`, demo orders for visual checks | `android.*` through the `data` package |

Activities such as `MainActivity` sit in the package root. Services get their own packages in later steps. They call repositories and never write SQL.

## Rules

- `model` has no `android.*` import. JVM tests in `app/src/test` run it without a device.
- Repositories own all SQL. No other class opens a database or builds a query.
- A repository takes a `DbHelper` in its constructor.
- Time is injected. Every method that stamps a time takes `nowMillis` from its caller, so tests set the clock.
- A repository validates its input and throws `IllegalArgumentException` with the reason.
- Moving an order to a stage `Stage.canMoveTo` forbids throws `IllegalStateException`.
- Money is a `long` in minor units. A deadline is a `LocalDate`, stored as days since 1970-01-01.

## UI

- Every Activity extends `BaseActivity`. It wraps its base context with `LocaleSupport.wrap`, which reads `language` ("ru", "az" or "en", default "ru") from the SharedPreferences file `settings`.
- `Format` holds money, date and weekday text. The currency symbol comes from the `currency` preference, default AZN, and AZN always prints as the manat sign because Android prints the code.
- `OrderCardAdapter` is a `BaseAdapter` over fully loaded `Row` objects. `MainActivity` loads the rows, so `getView` never queries the database.
- The board loads in `onResume`. A swipe on the list area is read in `MainActivity.dispatchTouchEvent` with a `GestureDetector`.

## Debug package

`com.mama.app.debug` holds code that only runs in a debuggable build. A caller checks `FLAG_DEBUGGABLE` first. `MainActivity` calls `DemoData.seedIfEmpty` when the launch intent carries the boolean extra `seed_demo`, and only while the orders table is empty. Nothing outside `MainActivity` imports the package.

## Database

`DbHelper` creates `mama.db`, version 1, with the six tables from `docs/design.md`. Foreign keys are on. Every child table deletes with its order. A null name opens an in-memory database for tests.

SQLite `COLLATE NOCASE` folds ASCII letters only. `GarmentTypeRepository.add` therefore also compares names in Java, so "костюм" matches "Костюм". `OrderRepository.search` folds case in Java for the same reason.

## Tests

| Kind | Where | Run |
|---|---|---|
| JVM unit tests for `model` | `app/src/test` | `./gradlew testDebugUnitTest` |
| Repository tests on a device | `app/src/androidTest` | `./gradlew connectedDebugAndroidTest` |

The device tests use the framework `android.test` classes and an in-memory database per test. No AndroidX test library is involved.
