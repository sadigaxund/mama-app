package com.mama.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/** Applies the in-app language from Prefs.KEY_LANGUAGE. */
public final class LocaleSupport {

    private LocaleSupport() {}

    /** The saved language code: "ru", "az" or "en". Anything else reads as the default. */
    public static String language(Context context) {
        SharedPreferences prefs = Prefs.get(context);
        String code = prefs.getString(Prefs.KEY_LANGUAGE, Prefs.DEFAULT_LANGUAGE);
        if ("ru".equals(code) || "az".equals(code) || "en".equals(code)) {
            return code;
        }
        return Prefs.DEFAULT_LANGUAGE;
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
