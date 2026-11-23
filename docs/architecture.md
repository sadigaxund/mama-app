# Architecture

This page says how the code is organised. `docs/design.md` says what the app does.

## Packages

The package root is `com.mama.app`.

| Package | Holds | Android imports |
|---|---|---|
| `model` | enums, value classes, `Money`, `ReminderDays` | none |
| `data` | `DbHelper` and one repository per aggregate | `android.database`, `android.content` |
| `ui` | `BaseActivity`, `LocaleSupport`, `Format`, `Prefs`, `Intents`, `OrderCardAdapter`, `ReminderDaysEditor` | `android.*` |
| `photo` | `PhotoStore` (import, thumbnails, delete), `AppFileProvider` | `android.*` |
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
- `Prefs` holds the name of the SharedPreferences file `settings`, its keys (`language`, `currency`, `reminder_days`, `morning_on`, `morning_minutes`, `visit_lead_minutes`) and their defaults. No other class writes those literals.
- `SettingsActivity` holds language, currency and reminder defaults. A language change restarts the task from `MainActivity`. `ReminderDaysEditor` is the days list shared with the order form's reminders dialog.
- `OrderFormActivity` creates and edits orders. `newOrderIntent` and `editIntent` build its intents. It returns `RESULT_OK` with the saved order's stage, and the board shows that stage's tab.
- `Format` holds money, date and weekday text. The currency symbol comes from the `currency` preference, default AZN. AZN, RUB, TRY, USD, EUR and GBP use fixed symbols, because Android prints the bare code for some of them in some languages.
- `Intents` builds the contacts picker intent (`pickPhoneContact`) and the dialer intent (`dial`). Callers catch `ActivityNotFoundException` and show a Toast. The manifest `<queries>` block lists both intents for Android 11 package visibility. The app holds no contacts or phone permission.
- `OrderCardAdapter` is a `BaseAdapter` over fully loaded `Row` objects. `MainActivity` loads the rows, so `getView` never queries the database.
- The board loads in `onResume`. A swipe on the list area is read in `MainActivity.dispatchTouchEvent` with a `GestureDetector`.

## Photos

`PhotoStore` keeps photos in `filesDir/photos` as `<orderId>-<epochMillis>.jpg`. Import shrinks the long edge to 2048 px, applies the EXIF orientation and saves JPEG quality 85, on a single background thread. The same job adds the `order_photo` rows. Thumbnails share one `LruCache`.

`AppFileProvider` (authority `<applicationId>.files`, not exported) serves files under `cacheDir/shared` and `cacheDir/camera`. It refuses any path whose canonical form leaves those folders. The camera app writes its picture into `cacheDir/camera` through it. No camera permission is declared.

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
