package com.atelier.app;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.atelier.app.backup.BackupPrefs;
import com.atelier.app.backup.BackupRunner;
import com.atelier.app.model.ReminderDays;
import com.atelier.app.ui.BackupUi;
import com.atelier.app.ui.BaseActivity;
import com.atelier.app.ui.Format;
import com.atelier.app.reminders.AppointmentScheduler;
import com.atelier.app.reminders.MorningScheduler;
import com.atelier.app.ui.NotificationPermission;
import com.atelier.app.ui.Prefs;
import com.atelier.app.ui.ReminderDaysEditor;

import java.util.Currency;
import java.util.List;

/** Language, currency, reminder defaults and the Drive backup rows. Values live in Prefs. */
public class SettingsActivity extends BaseActivity {
    private static final String[] LANGUAGE_CODES = {"ru", "az", "en"};
    private static final int[] LANGUAGE_NAMES = {R.string.lang_ru, R.string.lang_az, R.string.lang_en};
    private static final String[] COMMON_CURRENCIES = {"AZN", "RUB", "TRY", "USD", "EUR", "GBP"};
    private static final int[] VISIT_MINUTES = {0, 15, 30, 60, 120, 24 * 60};
    private static final int[] VISIT_LABELS = {R.string.visit_off, R.string.visit_15m,
            R.string.visit_30m, R.string.visit_1h, R.string.visit_2h, R.string.visit_1d};

    private Switch morningSwitch;
    private View rowTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        morningSwitch = findViewById(R.id.switch_morning);
        rowTime = findViewById(R.id.row_time);

        setTitle(R.id.row_lang, R.string.row_language);
        setTitle(R.id.row_cur, R.string.row_currency);
        setTitle(R.id.row_time, R.string.morning_time);
        setTitle(R.id.row_days, R.string.row_reminder_days);
        setTitle(R.id.row_visit, R.string.row_visit);
        ((TextView) findViewById(R.id.row_backup_last_title)).setText(R.string.backup_last_title);
        ((TextView) findViewById(R.id.row_backup_now_title)).setText(R.string.backup_now_title);
        ((TextView) findViewById(R.id.row_backup_restore_title)).setText(R.string.backup_restore_title);
        ((TextView) findViewById(R.id.row_backup_restore_sub)).setText(R.string.backup_restore_sub);
        ((TextView) findViewById(R.id.version)).setText(getString(R.string.settings_version, versionName()));

        findViewById(R.id.row_lang).setOnClickListener(v -> chooseLanguage());
        findViewById(R.id.row_cur).setOnClickListener(v -> chooseCurrency());
        findViewById(R.id.row_morning).setOnClickListener(v -> {
            boolean on = !Prefs.morningOn(this);
            Prefs.get(this).edit().putBoolean(Prefs.KEY_MORNING_ON, on).apply();
            MorningScheduler.schedule(this);
            refresh();
        });
        rowTime.setOnClickListener(v -> chooseMorningTime());
        findViewById(R.id.row_days).setOnClickListener(v -> editReminderDays());
        findViewById(R.id.row_visit).setOnClickListener(v -> chooseVisitLead());
        findViewById(R.id.row_backup_account).setOnClickListener(v -> {
            if (!BackupPrefs.signedIn(this) || BackupPrefs.needsSignIn(this)) {
                BackupUi.signIn(this, this::refresh);
            }
        });
        findViewById(R.id.row_backup_signout).setOnClickListener(v -> BackupUi.confirmSignOut(this, this::refresh));
        findViewById(R.id.row_backup_now).setOnClickListener(v -> {
            BackupUi.backUpNow(this);
            refresh();
        });
        findViewById(R.id.row_backup_restore).setOnClickListener(v -> BackupUi.restoreFromSettings(this));
        findViewById(R.id.row_notif_blocked).setOnClickListener(v -> {
            try {
                startActivity(NotificationPermission.settingsIntent(this));
            } catch (android.content.ActivityNotFoundException e) {
                android.util.Log.w("Atelier", "no notification settings page", e);
            }
        });
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have come back from the system page.
        refresh();
        BackupRunner.setListener(this::refresh);
    }

    @Override
    protected void onPause() {
        BackupRunner.setListener(null);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        BackupUi.onActivityResult(this, requestCode, resultCode, data);
    }

    private void setTitle(int rowId, int resId) {
        ((TextView) findViewById(rowId).findViewById(titleIdOf(rowId))).setText(resId);
    }

    private static int titleIdOf(int rowId) {
        if (rowId == R.id.row_lang) return R.id.row_lang_title;
        if (rowId == R.id.row_cur) return R.id.row_cur_title;
        if (rowId == R.id.row_time) return R.id.row_time_title;
        if (rowId == R.id.row_days) return R.id.row_days_title;
        return R.id.row_visit_title;
    }

    private TextView sub(int rowId) {
        View row = findViewById(rowId);
        int id = rowId == R.id.row_lang ? R.id.row_lang_sub
                : rowId == R.id.row_cur ? R.id.row_cur_sub
                : rowId == R.id.row_time ? R.id.row_time_sub
                : rowId == R.id.row_days ? R.id.row_days_sub
                : R.id.row_visit_sub;
        return row.findViewById(id);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            // The app's own package is always installed; show nothing rather than fail.
            android.util.Log.w("Atelier", "version name unavailable", e);
            return "";
        }
    }

    private void refresh() {
        sub(R.id.row_lang).setText(languageName(languageCode()));
        String cur = Prefs.currency(this);
        sub(R.id.row_cur).setText(cur + " (" + Format.currencySymbol(this, cur) + ")");

        boolean blocked = !getSystemService(android.app.NotificationManager.class).areNotificationsEnabled();
        findViewById(R.id.row_notif_blocked).setVisibility(blocked ? View.VISIBLE : View.GONE);

        boolean on = Prefs.morningOn(this);
        morningSwitch.setChecked(on);
        rowTime.setEnabled(on);
        rowTime.setClickable(on);
        rowTime.setAlpha(on ? 1f : 0.5f);
        sub(R.id.row_time).setText(Format.timeOfDay(this, Prefs.morningMinutes(this)));

        sub(R.id.row_days).setText(daysText(Prefs.reminderDays(this)));
        int lead = Prefs.visitLeadMinutes(this);
        int label = VISIT_LABELS[0];
        for (int i = 0; i < VISIT_MINUTES.length; i++) {
            if (VISIT_MINUTES[i] == lead) {
                label = VISIT_LABELS[i];
            }
        }
        sub(R.id.row_visit).setText(label);
        refreshBackup();
    }

    private void refreshBackup() {
        boolean signedIn = BackupPrefs.signedIn(this);
        boolean running = BackupRunner.isRunning();

        TextView accountTitle = findViewById(R.id.row_backup_account_title);
        TextView accountSub = findViewById(R.id.row_backup_account_sub);
        View signOut = findViewById(R.id.row_backup_signout);
        if (signedIn) {
            String email = BackupPrefs.email(this);
            accountTitle.setText(email != null ? email : getString(R.string.backup_connected));
            if (BackupPrefs.needsSignIn(this)) {
                accountSub.setText(R.string.backup_reconnect);
                accountSub.setVisibility(View.VISIBLE);
            } else {
                accountSub.setVisibility(View.GONE);
            }
            signOut.setVisibility(View.VISIBLE);
        } else {
            accountTitle.setText(R.string.backup_prompt_sign_in);
            accountSub.setText(R.string.backup_account_sub);
            accountSub.setVisibility(View.VISIBLE);
            signOut.setVisibility(View.GONE);
        }
        findViewById(R.id.row_backup_account).setClickable(!signedIn || BackupPrefs.needsSignIn(this));

        TextView last = findViewById(R.id.row_backup_last_sub);
        BackupPrefs.Failure failure = BackupPrefs.lastFailure(this);
        long ok = BackupPrefs.lastSuccess(this);
        if (signedIn && failure != null) {
            last.setText(BackupUi.failureText(this, failure));
            last.setTextColor(getResources().getColor(R.color.error, getTheme()));
        } else {
            last.setText(ok > 0 ? BackupUi.whenText(this, ok) : getString(R.string.backup_last_never));
            last.setTextColor(getResources().getColor(R.color.text_muted, getTheme()));
        }

        View now = findViewById(R.id.row_backup_now);
        TextView nowSub = findViewById(R.id.row_backup_now_sub);
        boolean canRun = signedIn && !running;
        now.setEnabled(canRun);
        now.setClickable(canRun);
        now.setAlpha(canRun || running ? 1f : 0.5f);
        if (running) {
            nowSub.setText(BackupUi.progressText(this, BackupRunner.progress()));
            nowSub.setVisibility(View.VISIBLE);
        } else if (!signedIn) {
            nowSub.setText(R.string.backup_now_need_sign_in);
            nowSub.setVisibility(View.VISIBLE);
        } else {
            nowSub.setVisibility(View.GONE);
        }
    }

    private String languageCode() {
        return com.atelier.app.ui.LocaleSupport.language(this);
    }

    private String languageName(String code) {
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            if (LANGUAGE_CODES[i].equals(code)) {
                return getString(LANGUAGE_NAMES[i]);
            }
        }
        return code;
    }

    private String daysText(List<Integer> days) {
        StringBuilder sb = new StringBuilder();
        for (Integer d : days) {
            if (sb.length() > 0) {
                sb.append(getString(R.string.reminders_list_sep));
            }
            sb.append(getResources().getQuantityString(R.plurals.reminder_days, d, d));
        }
        return sb.toString();
    }

    private void chooseLanguage() {
        String current = languageCode();
        String[] labels = new String[LANGUAGE_CODES.length];
        int checked = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(LANGUAGE_NAMES[i]);
            if (LANGUAGE_CODES[i].equals(current)) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    if (LANGUAGE_CODES[which].equals(current)) {
                        return;
                    }
                    Prefs.get(this).edit().putString(Prefs.KEY_LANGUAGE, LANGUAGE_CODES[which]).commit();
                    // A fresh task makes every screen read the new language.
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void chooseCurrency() {
        final String current = Prefs.currency(this);
        boolean common = false;
        for (String c : COMMON_CURRENCIES) {
            common |= c.equals(current);
        }
        // A saved code outside the short list stays visible as an extra entry.
        final int extra = common ? 0 : 1;
        String[] labels = new String[COMMON_CURRENCIES.length + extra + 1];
        int checked = -1;
        for (int i = 0; i < COMMON_CURRENCIES.length; i++) {
            labels[i] = COMMON_CURRENCIES[i] + " " + Format.currencySymbol(this, COMMON_CURRENCIES[i]);
            if (COMMON_CURRENCIES[i].equals(current)) {
                checked = i;
            }
        }
        if (extra == 1) {
            labels[COMMON_CURRENCIES.length] = current + " " + Format.currencySymbol(this, current);
            checked = COMMON_CURRENCIES.length;
        }
        labels[labels.length - 1] = getString(R.string.currency_other);
        new AlertDialog.Builder(this)
                .setTitle(R.string.currency_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    if (which == labels.length - 1) {
                        askOtherCurrency();
                    } else if (which < COMMON_CURRENCIES.length) {
                        saveCurrency(COMMON_CURRENCIES[which]);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void saveCurrency(String code) {
        Prefs.get(this).edit().putString(Prefs.KEY_CURRENCY, code).apply();
        refresh();
    }

    private void askOtherCurrency() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        input.setSingleLine(true);
        input.setHint(R.string.currency_other_hint);
        LinearLayout box = new LinearLayout(this);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        final TextView error = new TextView(this, null, 0, R.style.FormError);
        error.setText(R.string.currency_error);
        box.addView(error);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.currency_other_title)
                .setView(box)
                .setPositiveButton(R.string.action_ok, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String code = input.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
            if (!isKnownCurrency(code)) {
                error.setVisibility(View.VISIBLE);
                return;
            }
            saveCurrency(code);
            dialog.dismiss();
        });
    }

    /** Android's Currency.getInstance accepts any three letters, so the code must also be in the ISO list. */
    private static boolean isKnownCurrency(String code) {
        try {
            return Currency.getAvailableCurrencies().contains(Currency.getInstance(code));
        } catch (IllegalArgumentException e) {
            // Not three letters: the same answer as an unknown code.
            return false;
        }
    }

    private void chooseMorningTime() {
        int m = Prefs.morningMinutes(this);
        new TimePickerDialog(this, (view, hour, minute) -> {
            Prefs.get(this).edit().putInt(Prefs.KEY_MORNING_MINUTES, hour * 60 + minute).apply();
            MorningScheduler.schedule(this);
            refresh();
        }, m / 60, m % 60, android.text.format.DateFormat.is24HourFormat(this)).show();
    }

    private void editReminderDays() {
        final ReminderDaysEditor editor = new ReminderDaysEditor(this, Prefs.reminderDays(this));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, Math.round(8 * getResources().getDisplayMetrics().density), pad, 0);
        box.addView(editor);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.reminder_days_title)
                .setView(scroll)
                .setPositiveButton(R.string.action_ok, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!editor.commitInput()) {
                return;
            }
            List<Integer> days = editor.days();
            if (days.isEmpty()) {
                editor.showError(R.string.reminders_error_none);
                return;
            }
            Prefs.get(this).edit().putString(Prefs.KEY_REMINDER_DAYS, ReminderDays.toCsv(days)).apply();
            refresh();
            dialog.dismiss();
        });
    }

    private void chooseVisitLead() {
        int lead = Prefs.visitLeadMinutes(this);
        String[] labels = new String[VISIT_MINUTES.length];
        int checked = -1;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(VISIT_LABELS[i]);
            if (VISIT_MINUTES[i] == lead) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.visit_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    Prefs.get(this).edit().putInt(Prefs.KEY_VISIT_LEAD_MINUTES, VISIT_MINUTES[which]).apply();
                    // The alarms read the lead from Prefs when set, so every future visit is set again.
                    new Thread(() -> AppointmentScheduler.rescheduleAll(getApplicationContext()),
                            "reschedule").start();
                    refresh();
                    d.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
