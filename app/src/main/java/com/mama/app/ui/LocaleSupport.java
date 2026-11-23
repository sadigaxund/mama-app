package com.mama.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/** Applies the in-app language from SharedPreferences "settings", key "language". */
public final class LocaleSupport {
    public static final String PREFS = "settings";
    public static final String KEY_LANGUAGE = "language";
    public static final String DEFAULT_LANGUAGE = "ru";

    private LocaleSupport() {}

    /** The saved language code: "ru", "az" or "en". Anything else reads as the default. */
    public static String language(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String code = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
        if ("ru".equals(code) || "az".equals(code) || "en".equals(code)) {
            return code;
        }
        return DEFAULT_LANGUAGE;
    }

    /** Returns a context whose resources use the saved language. */
    public static Context wrap(Context base) {
        Locale locale = new Locale(language(base));
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
}
