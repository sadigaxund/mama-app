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
}
