# Design

This page says what the app does and why. `PLAN.md` says what is built so far.

## Purpose

A tailor tracks customer orders from acceptance to hand-over. Each order
moves through a fixed sequence of stages and carries its own money ledger.
The app runs on one phone, offline, for one user.

## Order kinds

| Kind | RU | EN | AZ | Card color |
|---|---|---|---|---|
| `TAILORING` | Пошив | Tailoring | Tikiş | red |
| `REPAIR` | Ремонт | Repair | Təmir | green |

"Tailoring" means a new garment made to measure. Both kinds follow the same
stages. A repair that needs no fitting skips it with one extra tap.

## Stages

| Stage | RU | EN | AZ |
|---|---|---|---|
| `ACCEPTED` | Принят | Accepted | Qəbul edildi |
| `CUTTING` | Крой | Cutting | Biçim |
| `FITTING_1` | Примерка 1 | Fitting 1 | Ölçü 1 |
| `FITTING_2` | Примерка 2 | Fitting 2 | Ölçü 2 |
| `ALTERATIONS` | Доработка | Final alterations | Son düzəliş |
| `DONE` | Готов | Done | Hazır |

### Allowed moves

```
ACCEPTED -> CUTTING -> FITTING_1 -> FITTING_2 -> DONE
                           |            |        ^  ^
                           |            v        |  |
                           |       ALTERATIONS --+  |
                           +------------------------+

any stage -> DONE      any stage -> previous stage
```

- `FITTING_1` goes to `FITTING_2`, or to `DONE` when the garment already fits.
- `FITTING_2` goes to `DONE`, or to `ALTERATIONS` when it still needs changes.
- `ALTERATIONS` goes only to `DONE`.
- Any stage can go straight to `DONE`. This is the one-tap way to close an
  order, from the board or from a reminder.
- Any stage can step back one stage to undo a mistaken move.

`DONE` is the order history. Orders stay there and are never cleared
automatically.

## Money

Each order has a work price and a ledger of entries. The balance is derived,
never stored.

```
balance due = work price + expenses - payments
```

| Term | RU | EN | AZ | Meaning |
|---|---|---|---|---|
| work price | Стоимость работы | Work price | İşin qiyməti | the tailor's fee, agreed at acceptance |
| expense | Расход | Expense | Xərc | money the tailor paid for the customer, such as fabric or buttons; the customer owes it back |
| payment | Оплата | Payment | Ödəniş | money the customer paid; a deposit is the first payment |
| balance due | Остаток | Balance due | Qalıq | what the customer still owes; negative means overpaid |

A ledger entry has an amount and an optional title.

### Sharing an order

The detail screen has a share button. It offers three forms, each handed to
the system share sheet, so it can go to a messenger, mail, print or storage.

| Form | Content | Use |
|---|---|---|
| Text | a plain summary: order number, customer, garment, stage, deadline, money lines, balance | a quick message |
| Image | the same summary drawn as a PNG | a messenger preview that reads without opening a file |
| PDF | the same summary on one page | a bill to print or keep |

Image and PDF come from one drawing routine on the framework `Canvas`, so the
two never disagree. Sharing a file needs a small own `ContentProvider`,
since `FileProvider` is part of AndroidX.

## Phone

The phone number is optional. It is typed, or picked from contacts through the
system picker. The picker grants access to the one chosen contact, so the app
needs no contacts permission.

The call button opens the dialer with the number filled in. The user taps
call there. This needs no phone permission. Calling straight from the app
would need the `CALL_PHONE` permission and saves one tap.

## Photos

An order holds any number of photos, added at any stage. A photo comes from
the camera or the gallery. The app copies it into its private storage, so
deleting it from the gallery does not remove it from the order.

Every card reserves a square slot on its left edge. It holds the newest
photo, or a placeholder with the garment type's first letter when the order
has none. Text starts at the same indent on every card, and every card has
the same height. The detail screen shows
all photos in a row. Tapping one opens it full screen.

Photos and the database live in app storage. Uninstalling the app deletes
them, which the Drive backup protects against.

## Appointments

An appointment is a visit booked with the customer: a fitting, a pickup, or
anything else. An order has any number of them, added at any time from the
order detail screen. An appointment is a date, a time and an optional note.

When an order moves into `FITTING_1`, `FITTING_2` or `DONE`, a short banner
offers to book a visit. Ignoring it costs nothing. The card shows the next
upcoming appointment.

## Reminders

Reminders are notifications. The app never rings like an alarm clock.

A notification plays the phone's notification sound once and stays in the
notification shade until dismissed. An alarm-clock ring repeats until the user
stops it and takes over the screen. Android 14 limits that to alarm-clock and
calling apps, and the user would have to grant it by hand in system settings.
Each kind of reminder has its own notification channel, so its sound,
vibration and importance are set per kind in Android's own settings.

### Deadline reminders

Settings holds the default list of reminders, as days before the deadline.
The list is additive: "3 days" and "1 day" means two reminders. The default
is 3 days and 1 day. Every order follows the default list unless it says
otherwise.

| Order reminder mode | Meaning |
|---|---|
| `DEFAULT` | follow the list in Settings, now and after the list changes |
| `CUSTOM` | its own list of days, edited on that order |
| `OFF` | no deadline reminders for this order |

On the deadline day, and on every day after it while the order is not
`DONE`, the order also reminds. That is the overdue reminder. It stops when
the order reaches `DONE` or its mode is set to `OFF`.

### The morning notification

All deadline reminders for a day arrive together at one time, set in
Settings, 09:00 by default. They arrive as one Android notification group.
Collapsed, the group is a summary:

```
Atelier · today
1 fitting: Ivanova 15:00
1 due today: Petrov
1 overdue: Hasanov
```

Expanded, it holds one notification per order. A deadline or overdue
notification has three actions:

| Action | Effect |
|---|---|
| Done | moves the order to `DONE` |
| Move deadline | opens the order on a date picker |
| Stop reminding | sets the order's reminder mode to `OFF` |

Swiping a notification away hides it for that day only. An overdue order
returns the next morning.

### Appointment reminders

Each appointment reminds at every lead time ticked in Settings: 1 day, 2
hours, 1 hour, 30 minutes or 15 minutes before it starts. The default is 1
hour alone. Nothing ticked turns visit reminders off. A later reminder
replaces the earlier notification of the same visit. The notification has a
call button when the order has a phone number.
This notification pops up over the screen, because a visit is close in time.

### Scheduling

- The framework `AlarmManager` wakes the app. It uses inexact windows,
  because exact alarms need a permission meant for alarm-clock apps. A
  reminder can arrive up to 10 minutes late.
- The morning job reads the database when it fires, so it always reflects
  the current orders. No alarm is stored per deadline reminder.
- Each appointment has one alarm per ticked lead time. They are rescheduled
  when the appointment or the lead times change, and cancelled when it is
  deleted.
- After a reboot, the app reschedules every alarm, using
  `RECEIVE_BOOT_COMPLETED`.
- On Android 13 and newer, the notification permission is requested the
  first time it is needed: the first appointment, or the first order with
  reminders. Settings shows when it is denied and links to the system page.

## Backup

Backup goes to the user's Google Drive and runs by itself once a day. The
app has no account of its own. Google sign-in is the identity, and it is
optional: the app works fully without it.

### Sign-in

On first launch the app offers Google sign-in for backup, with a skip
button. Settings can sign in later, change the account, or sign out. Sign-in
and the Drive token come from `AuthorizationClient` in `play-services-auth`,
the one library outside the framework (`AGENTS.md` §1).

The only scope is `drive.file`, which reaches files this app created and
nothing else. Google rates it non-sensitive, so the app needs no Google
verification. The OAuth consent screen must be set to production, because a
testing-mode grant expires after 7 days.

Backup needs two install-time permissions. `INTERNET` lets the app reach the
Drive API. `ACCESS_NETWORK_STATE` lets the daily job wait for an unmetered
network, which Android 14 and newer require for any job with a network
condition. Neither shows a prompt.

### Drive layout

```
Atelier/
├── orders.db
└── photos/
    └── <photo file name>
```

The backup is a plain folder, not an archive. Each photo uploads once,
under the same file name it has in app storage. The database is replaced on
every run. Photos upload first and the database last, so a run that stops
halfway leaves the previous database and every photo it names in place.

The app never deletes from Drive. A file the user removed by hand is
uploaded again on the next run.

### Schedule

A framework `JobScheduler` job runs once a day on an unmetered network. It
skips the run when nothing changed since the last one. Settings shows the
time of the last successful backup and a "Back up now" button. A failed run
retries on the next day and shows its reason in Settings.

### Restore

After sign-in on a phone with no orders, the app looks for the folder. When
it finds one, it offers to restore it. Restore never runs over existing
orders.

Android Auto Backup is turned off. It would restore a stale database beside
the Drive backup, and two restore sources can disagree.

### Testing

Sign-in needs Google Play Services. A Waydroid image without them cannot run
it, so backup is checked on the phone.

## Data model

Storage is SQLite through the framework `android.database.sqlite` API. The
stack in `AGENTS.md` §1 allows no Room or other library. Every amount is an
INTEGER in minor currency units, so sums never pick up rounding error.

### `garment_type`

| Column | Type | Rule |
|---|---|---|
| `id` | INTEGER | primary key |
| `name` | TEXT | required, unique ignoring case |
| `archived` | INTEGER (0/1) | archived types are hidden from the picker but stay on old orders |

The user adds a type from the order form without leaving it. A type in use is
archived, never deleted, so no order loses its type.

### `orders`

| Column | Type | Rule |
|---|---|---|
| `id` | INTEGER | primary key, shown as the order number |
| `customer_name` | TEXT | required |
| `phone` | TEXT | optional |
| `kind` | TEXT | `TAILORING` or `REPAIR`, required |
| `garment_type_id` | INTEGER | required, references `garment_type.id` |
| `work_price` | INTEGER | required, minor units |
| `deadline` | INTEGER | required, a calendar date as days since 1970-01-01 |
| `notes` | TEXT | optional, free text for measurements and remarks |
| `stage` | TEXT | one of the stages above |
| `created_at` | INTEGER | set once, milliseconds since epoch |
| `stage_changed_at` | INTEGER | updated on every move; on a `DONE` order it is the finish date |
| `reminder_mode` | TEXT | `DEFAULT`, `CUSTOM` or `OFF`, starts at `DEFAULT` |

A deadline is a date without a time, so a time-zone change cannot shift it.

### `ledger_entry`

| Column | Type | Rule |
|---|---|---|
| `id` | INTEGER | primary key |
| `order_id` | INTEGER | references `orders.id`, deleted with the order |
| `type` | TEXT | `PAYMENT` or `EXPENSE` |
| `amount` | INTEGER | minor units, greater than zero |
| `title` | TEXT | optional |
| `created_at` | INTEGER | milliseconds since epoch |

### `order_reminder`

Rows exist only for orders in `CUSTOM` mode.

| Column | Type | Rule |
|---|---|---|
| `order_id` | INTEGER | references `orders.id`, deleted with the order |
| `days_before` | INTEGER | 1 or more, unique per order |

### `appointment`

| Column | Type | Rule |
|---|---|---|
| `id` | INTEGER | primary key |
| `order_id` | INTEGER | references `orders.id`, deleted with the order |
| `starts_at` | INTEGER | local date and time, milliseconds since epoch |
| `note` | TEXT | optional |

### `order_photo`

| Column | Type | Rule |
|---|---|---|
| `id` | INTEGER | primary key |
| `order_id` | INTEGER | references `orders.id`; the file is deleted with the order |
| `file_name` | TEXT | a file in app private storage |
| `created_at` | INTEGER | milliseconds since epoch |

## Settings

| Setting | Values | Default |
|---|---|---|
| Language | Русский, Azərbaycan, English | Русский |
| Currency | any ISO 4217 code | AZN (₼) |
| Morning notification | on or off, and a time | on, 09:00 |
| Default deadline reminders | a list of days before the deadline | 3 days, 1 day |
| Appointment reminders | any of 1 day, 2 h, 1 h, 30 min, 15 min before; none is off | 1 h |
| Google account | signed in or not | asked on first launch, skippable |
| Last backup | shown, with a "Back up now" button | |

Changing the currency changes the symbol only. Stored amounts are not
converted. The language applies inside the app regardless of the phone's
language.

## Screens

### Board

The main screen. One tab per stage, with the order count in each tab. Swipe
left and right to change stage.

- The `+` button is on every tab. A new order starts in the stage of the tab
  it was created from. This lets orders already in progress be entered on
  first use.
- Each card shows the order number, customer, garment type, deadline,
  balance due and the next appointment, tinted red or green by kind. Overdue cards carry a text badge.
  Color does not mark overdue cards, because red already means `TAILORING`.
- A card moves forward with its arrow button. A long press offers every
  allowed move, including straight to `DONE`. A phone shows one column at a time, which rules out dragging
  a card between columns.

### Search, filter, sort

- A search field above the tabs matches customer name, phone and order
  number across all stages.
- A filter row offers kind (Пошив, Ремонт) and garment type.
- Active stages sort by deadline, nearest first. `DONE` sorts by finish date,
  newest first.

Free-form tags are not built. Kind and garment type already group orders, and
notes are searchable later if needed.

### Order form

Creates or edits an order. The garment type picker ends with "+ New type",
which adds a type in place. The phone field has a contacts button. The notes
field grows with its text. A reminders row reads "Default (3 days, 1 day)"
and opens the order's own list when tapped. Every required field is checked on save, and an
invalid field shows its reason under itself.

### Order detail

Opened by tapping a card. It shows every field, the photos, and the money
section with its ledger. It lists appointments with an add button. It offers the allowed moves, call,
add payment, add expense, add photo, share, edit and delete. Delete asks for confirmation.

### Settings

Reached from the board's menu. It holds language, currency, reminders, backup and restore.
