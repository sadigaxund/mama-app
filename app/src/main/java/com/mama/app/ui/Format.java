package com.mama.app.ui;

import android.content.Context;
import android.content.SharedPreferences;

import com.mama.app.model.Money;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Currency;
import java.util.Locale;

/** Display formatting for money, dates and weekdays. */
public final class Format {
    public static final String KEY_CURRENCY = "currency";
    public static final String DEFAULT_CURRENCY = "AZN";
    private static final String AZN_SYMBOL = "₼";

    private Format() {}

    public static Locale locale(Context context) {
        return context.getResources().getConfiguration().getLocales().get(0);
    }

    /** The currency symbol for the saved code. Android prints "AZN" for the manat, so it is forced. */
    public static String currencySymbol(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(LocaleSupport.PREFS, Context.MODE_PRIVATE);
        String code = prefs.getString(KEY_CURRENCY, DEFAULT_CURRENCY);
        if (DEFAULT_CURRENCY.equals(code)) {
            return AZN_SYMBOL;
        }
        try {
            return Currency.getInstance(code).getSymbol(locale(context));
        } catch (IllegalArgumentException e) {
            // An unknown code in the preferences: show the code itself rather than fail the screen.
            return code;
        }
    }

    /** "190 ₼" from minor units. */
    public static String money(Context context, long minor) {
        return Money.formatMinor(minor) + " " + currencySymbol(context);
    }

    /** "28.11", or "28.11.2027" when the year differs from today's. */
    public static String date(LocalDate date, LocalDate today) {
        String pattern = date.getYear() == today.getYear() ? "dd.MM" : "dd.MM.yyyy";
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }

    /** Short weekday with a capital first letter, such as "Чт". */
    public static String weekday(DayOfWeek day, Locale locale) {
        String s = day.getDisplayName(TextStyle.SHORT, locale);
        if (s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(locale) + s.substring(1);
    }

    /** "15:00". */
    public static String time(java.time.LocalTime time) {
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}
