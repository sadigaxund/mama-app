package com.mama.app.ui;

import android.content.Context;
import android.content.SharedPreferences;

import com.mama.app.model.Kind;
import com.mama.app.model.OrderFilter;
import com.mama.app.model.ReminderDays;

import java.util.List;

/** The SharedPreferences file "settings": its name, keys and defaults. */
public final class Prefs {
    public static final String FILE = "settings";
    public static final String KEY_LANGUAGE = "language";
    public static final String DEFAULT_LANGUAGE = "ru";
    public static final String KEY_CURRENCY = "currency";
    public static final String DEFAULT_CURRENCY = "AZN";
    /** Comma list of days before the deadline, for example "3,1". */
    public static final String KEY_REMINDER_DAYS = "reminder_days";
    public static final String DEFAULT_REMINDER_DAYS = "3,1";

    /** Morning notification switch. */
    public static final String KEY_MORNING_ON = "morning_on";
    public static final boolean DEFAULT_MORNING_ON = true;
    /** Morning notification time, minutes after midnight. */
    public static final String KEY_MORNING_MINUTES = "morning_minutes";
    public static final int DEFAULT_MORNING_MINUTES = 9 * 60;
    /** Date of the last morning post, as days since 1970-01-01. Absent when none. */
    public static final String KEY_MORNING_LAST_POSTED = "morning_last_posted";

    /** Visit reminder lead time in minutes, 0 for off. */
    public static final String KEY_VISIT_LEAD_MINUTES = "visit_lead_minutes";
    public static final int DEFAULT_VISIT_LEAD_MINUTES = 60;

    /** True once the notification permission dialog has been shown. */
    public static final String KEY_NOTIFICATIONS_ASKED = "notifications_asked";

    /** Board filter: kind name, absent for any kind. */
    public static final String KEY_FILTER_KIND = "filter_kind";
    /** Board filter: garment type id, 0 or absent for any type. */
    public static final String KEY_FILTER_TYPE = "filter_type";

    private Prefs() {}

    /** The saved board filter. An unreadable kind counts as any kind. */
    public static OrderFilter filter(Context context) {
        SharedPreferences p = get(context);
        Kind kind = null;
        String name = p.getString(KEY_FILTER_KIND, null);
        if (name != null) {
            try {
                kind = Kind.valueOf(name);
            } catch (IllegalArgumentException e) {
                kind = null;
            }
        }
        return new OrderFilter(kind, p.getLong(KEY_FILTER_TYPE, 0));
    }

    public static void saveFilter(Context context, OrderFilter f) {
        SharedPreferences.Editor e = get(context).edit();
        if (f.kind == null) {
            e.remove(KEY_FILTER_KIND);
        } else {
            e.putString(KEY_FILTER_KIND, f.kind.name());
        }
        e.putLong(KEY_FILTER_TYPE, f.garmentTypeId);
        e.apply();
    }

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The default reminder days, largest first. Falls back to "3,1" when unset or unreadable. */
    public static List<Integer> reminderDays(Context context) {
        String csv = get(context).getString(KEY_REMINDER_DAYS, DEFAULT_REMINDER_DAYS);
        List<Integer> days = ReminderDays.parse(csv);
        return days.isEmpty() ? ReminderDays.parse(DEFAULT_REMINDER_DAYS) : days;
    }

    public static String language(Context context) {
        return get(context).getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    public static String currency(Context context) {
        return get(context).getString(KEY_CURRENCY, DEFAULT_CURRENCY);
    }

    public static boolean morningOn(Context context) {
        return get(context).getBoolean(KEY_MORNING_ON, DEFAULT_MORNING_ON);
    }

    /** Minutes after midnight, always in 0..1439. */
    public static int morningMinutes(Context context) {
        int m = get(context).getInt(KEY_MORNING_MINUTES, DEFAULT_MORNING_MINUTES);
        return m < 0 || m > 24 * 60 - 1 ? DEFAULT_MORNING_MINUTES : m;
    }

    /** The date of the last morning post, or null. */
    public static java.time.LocalDate morningLastPosted(Context context) {
        SharedPreferences p = get(context);
        return p.contains(KEY_MORNING_LAST_POSTED)
                ? java.time.LocalDate.ofEpochDay(p.getLong(KEY_MORNING_LAST_POSTED, 0)) : null;
    }

    public static void setMorningLastPosted(Context context, java.time.LocalDate date) {
        if (date == null) {
            get(context).edit().remove(KEY_MORNING_LAST_POSTED).commit();
        } else {
            get(context).edit().putLong(KEY_MORNING_LAST_POSTED, date.toEpochDay()).commit();
        }
    }

    /** Minutes before a visit to remind, 0 for off. */
    public static int visitLeadMinutes(Context context) {
        return get(context).getInt(KEY_VISIT_LEAD_MINUTES, DEFAULT_VISIT_LEAD_MINUTES);
    }
}
