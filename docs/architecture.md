# Architecture

This page says how the code is organised. `docs/design.md` says what the app does.

## Packages

The package root is `com.mama.app`.

| Package | Holds | Android imports |
|---|---|---|
| `model` | enums, value classes, `Money`, `ReminderDays`, `ReminderPlanner` | none |
| `data` | `DbHelper` and one repository per aggregate | `android.database`, `android.content` |
| `ui` | `BaseActivity`, `LocaleSupport`, `Format`, `Prefs`, `Intents`, `OrderCardAdapter`, `ReminderDaysEditor`, `BackupUi` | `android.*` |
| `reminders` | alarms, receivers and notification builders | `android.*` |
| `photo` | `PhotoStore` (import, thumbnails, delete), `AppFileProvider` | `android.*` |
| `share` | `OrderSummary` (pure data and text), `OrderSummaryRenderer` (Canvas drawing), `OrderSharer` (files and intents) | `OrderSummary`: none; others `android.*` |
| `backup` | Drive sign-in, REST client, backup and restore runs, the daily job | `android.*`; the only package that imports `com.google.*` |
| `debug` | `DemoData`, demo orders for visual checks | `android.*` through the `data` package |

Activities such as `MainActivity` sit in the package root. Services get their own packages in later steps. They call repositories and never write SQL.

## Rules

- `model` has no `android.*` import. JVM tests in `app/src/test` run it without a device.
- Repositories own all SQL. No other class opens a database or builds a query. `backup.BackupDb` is the one exception: it copies the database file and reads downloaded copies.
- A repository takes a `DbHelper` in its constructor.
- Time is injected. Every method that stamps a time takes `nowMillis` from its caller, so tests set the clock.
- A repository validates its input and throws `IllegalArgumentException` with the reason.
- Moving an order to a stage `Stage.canMoveTo` forbids throws `IllegalStateException`.
- Money is a `long` in minor units. A deadline is a `LocalDate`, stored as days since 1970-01-01.

## UI

- Every Activity extends `BaseActivity`. It wraps its base context with `LocaleSupport.wrap`, which reads `language` ("ru", "az" or "en", default "ru") from the SharedPreferences file `settings`.
- `Prefs` holds the name of the SharedPreferences file `settings`, its keys (`language`, `currency`, `reminder_days`, `morning_on`, `morning_minutes`, `visit_lead_minutes`, `notifications_asked`) and their defaults. No other class writes those literals.
- `SettingsActivity` holds language, currency and reminder defaults. A language change restarts the task from `MainActivity`. `ReminderDaysEditor` is the days list shared with the order form's reminders dialog.
- `OrderFormActivity` creates and edits orders. `newOrderIntent` and `editIntent` build its intents. It returns `RESULT_OK` with the saved order's stage, and the board shows that stage's tab.
- `Format` holds money, date and weekday text. The currency symbol comes from the `currency` preference, default AZN. AZN, RUB, TRY, USD, EUR and GBP use fixed symbols, because Android prints the bare code for some of them in some languages.
- `Intents` builds the contacts picker intent (`pickPhoneContact`) and the dialer intent (`dial`). Callers catch `ActivityNotFoundException` and show a Toast. The manifest `<queries>` block lists both intents for Android 11 package visibility. The app holds no contacts or phone permission.
- `OrderCardAdapter` is a `BaseAdapter` over fully loaded `Row` objects. `MainActivity` loads the rows, so `getView` never queries the database.
- The board loads in `onResume`. A swipe on the list area is read in `MainActivity.dispatchTouchEvent` with a `GestureDetector`.

## Reminders

`ReminderPlanner` (in `model`) decides which deadline items an order gets on a day. `MamaApp` runs at every process start: it creates both notification channels, installs `AppointmentScheduler` into `Reminders`, and sets the morning alarm and the visit alarms.

`MorningScheduler` keeps one `setWindow` alarm for the next morning time. `MorningReceiver` calls `MorningNotifier` on a background thread, then sets the next day's alarm. `MorningNotifier` posts one summary and one notification per due order. Notification ids are `Notifications.orderId(order)`. The buttons go to `ReminderActionReceiver`, which moves the order to `DONE` or sets its mode to `OFF`.

`AppointmentScheduler` sets one alarm per future appointment, with the appointment id as the request code. `AppointmentReceiver` calls `AppointmentNotifier`. `BootReceiver` sets every alarm again after boot, a time or zone change, and an app update.

SQLite deletes appointments with their order without calling `AppointmentListener`. `OrderDetailActivity` reads the appointment ids before the delete and cancels each alarm.

A debuggable build runs the posts on demand: the `MainActivity` extras `debug_morning` (boolean) and `debug_appointment` (appointment id, long).

## Photos

`PhotoStore` keeps photos in `filesDir/photos` as `<orderId>-<epochMillis>.jpg`. Import shrinks the long edge to 2048 px, applies the EXIF orientation and saves JPEG quality 85, on a single background thread. The same job adds the `order_photo` rows. Thumbnails share one `LruCache`.

`AppFileProvider` (authority `<applicationId>.files`, not exported) serves files under `cacheDir/shared` and `cacheDir/camera`. It refuses any path whose canonical form leaves those folders. The camera app writes its picture into `cacheDir/camera` through it. No camera permission is declared.

## Sharing

`OrderSummary` holds the localized lines of one order: header, customer, stage, deadline, money rows, notes and date. The caller passes the words and formatters in `OrderSummary.Labels`, so the class has no Android import. `toText()` gives the text form.

`OrderSummaryRenderer` lays the summary out as rows at a given width, drawn for 1080 px. `draw` paints all rows for the PNG. `paginate` and `drawPage` split the rows into pages for the PDF, so a page break falls between rows. Notes are split into one row per text line.

`OrderSharer` writes `cacheDir/shared/order-<id>.png` and `.pdf` (A4, 595x842 pt, scaled to the page width) and builds the chooser intents. It deletes files in that folder older than one day. A debuggable build also copies each file to `getExternalFilesDir(null)/share-debug/`. `OrderDetailActivity` loads the summary on the UI thread and renders on a single worker thread.

## Backup

The `backup` package is the only code that imports `com.google.*` (`play-services-auth`) or `androidx.*`. Its own SharedPreferences file `backup` (`BackupPrefs`) holds the signed-in flag, the account email, the cached Drive folder ids, the last success time and fingerprint, the last failure, and the first-launch prompt flag.

- `DriveAuth` signs in with `AuthorizationClient.authorize` and the one scope `drive.file`. A consent screen returns through `onActivityResult`. `accessTokenBlocking` gets a token on a background thread and never opens UI. When the user must act, it throws `AUTH`, and the run records "needs sign-in". `signOut` cancels the job, clears the local state and asks Google to revoke the grant.
- `DriveClient` talks to Drive REST v3 over `HttpURLConnection` and `org.json`. HTTP errors become `DriveException` with a reason of `AUTH`, `NETWORK`, `QUOTA` or `OTHER`. Timeouts are 30 s to connect and 60 s to read.
- `BackupRunner.run` does one run: it stops when not signed in or when the orders table is empty, skips an unchanged state unless forced, uploads photos missing in Drive, snapshots the database and uploads it as `orders.db` last. Nothing is deleted in Drive. `BackupFingerprint` holds the change check and the photo diff as pure functions.
- `BackupDb.snapshot` copies `mama.db` to `cacheDir/backup/orders.db` inside an exclusive transaction. `validate` opens a downloaded copy read-only and checks the orders table and `user_version`.
- `BackupScheduler` keeps one periodic `JobScheduler` job (24 h, unmetered network, persisted). `BackupJobService` runs `BackupRunner` on its own thread. `MamaApp` schedules the job again at each process start when signed in.
- `RestoreRunner` finds the Drive copy, downloads and validates it, downloads the photos its `order_photo` table lists, then closes every `DbHelper` on `mama.db` (`DbHelper.closeAllOnAppDatabase`) and replaces the file. It refuses when orders exist.
- `ui.BackupUi` holds the dialogs and flows for the board and Settings, so those Activities import no Google class. `MainActivity` shows the first-launch dialog once and forwards `onActivityResult`.

The manifest sets `allowBackup="false"`, `fullBackupContent="false"` and `dataExtractionRules` to `res/xml/data_extraction_rules.xml`, which excludes every domain. The one-time Google Cloud setup is in `docs/backup-setup.md`.

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
