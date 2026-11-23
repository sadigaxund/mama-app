package com.mama.app.ui;

import android.content.Context;
import android.content.SharedPreferences;

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

    private Prefs() {}

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
