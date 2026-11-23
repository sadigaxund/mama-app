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
    /** Symbols the Android locale data prints as the bare code in some languages, so they are fixed. */
    private static final java.util.Map<String, String> SYMBOLS = java.util.Map.of(
            "AZN", "₼", "RUB", "₽", "TRY", "₺", "USD", "$", "EUR", "€", "GBP", "£");

    private Format() {}

    public static Locale locale(Context context) {
        return context.getResources().getConfiguration().getLocales().get(0);
    }

    /** The currency symbol for the saved code. */
    public static String currencySymbol(Context context) {
        return currencySymbol(context, Prefs.currency(context));
    }

    /** The symbol for a currency code. */
    public static String currencySymbol(Context context, String code) {
        String fixed = SYMBOLS.get(code);
        if (fixed != null) {
            return fixed;
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

    /** "05.03.2026", the form field's date text. */
    public static String dateFull(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
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

    /** A time of day from minutes after midnight, in the phone's 12 or 24 hour style. */
    public static String timeOfDay(Context context, int minutes) {
        java.time.LocalTime t = java.time.LocalTime.of(minutes / 60, minutes % 60);
        String pattern = android.text.format.DateFormat.is24HourFormat(context) ? "HH:mm" : "h:mm a";
        return t.format(DateTimeFormatter.ofPattern(pattern, locale(context)));
    }
}
