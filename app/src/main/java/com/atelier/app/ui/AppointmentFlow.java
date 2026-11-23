package com.atelier.app.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.text.InputType;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.atelier.app.R;
import com.atelier.app.data.AppointmentRepository;
import com.atelier.app.model.Appointment;
import com.atelier.app.reminders.AppointmentListener;
import com.atelier.app.reminders.Reminders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Add, edit and delete an appointment: date, then time, then note. Cancelling any step stops the flow. */
public final class AppointmentFlow {
    private static final String TAG = "Atelier";

    /** Called after a change is stored. */
    public interface Changed {
        void onChanged();
    }

    private final Context context;
    private final AppointmentRepository repo;
    private final Changed changed;

    public AppointmentFlow(Context context, AppointmentRepository repo, Changed changed) {
        this.context = context;
        this.repo = repo;
        this.changed = changed;
    }

    /** Books a new visit for the order. Defaults: today, the next full hour, no note. */
    public void add(long orderId) {
        LocalDateTime next = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
        pickDate(LocalDate.now(), next.toLocalTime(), "", (when, note) -> {
            long id = repo.add(orderId, Format.toMillis(when), note);
            Log.d(TAG, "appointment " + id + " added to #" + orderId);
            notifySaved(new Appointment(id, orderId, Format.toMillis(when), note));
        });
    }

    /** Changes date, time and note of an existing visit, starting from its current values. */
    public void edit(Appointment a) {
        LocalDateTime t = Format.toLocal(a.startsAt);
        pickDate(t.toLocalDate(), t.toLocalTime(), a.note == null ? "" : a.note, (when, note) -> {
            repo.update(a.id, Format.toMillis(when), note);
            Log.d(TAG, "appointment " + a.id + " updated");
            notifySaved(new Appointment(a.id, a.orderId, Format.toMillis(when), note));
        });
    }

    /** The tap menu of a row: Edit or Delete. */
    public void showActions(Appointment a) {
        String title = Format.appointment(context, a.startsAt, LocalDate.now());
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setItems(new CharSequence[]{context.getString(R.string.appt_edit),
                        context.getString(R.string.action_delete)}, (d, which) -> {
                    if (which == 0) {
                        edit(a);
                    } else {
                        confirmDelete(a);
                    }
                })
                .show();
    }

    private void confirmDelete(Appointment a) {
        new AlertDialog.Builder(context)
                .setMessage(R.string.appt_delete_confirm)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    repo.delete(a.id);
                    Log.d(TAG, "appointment " + a.id + " deleted");
                    AppointmentListener l = Reminders.appointmentListener();
                    l.onDeleted(a.id);
                    changed.onChanged();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void notifySaved(Appointment a) {
        Reminders.appointmentListener().onSaved(a);
        if (context instanceof android.app.Activity) {
            NotificationPermission.requestOnce((android.app.Activity) context);
        }
        changed.onChanged();
    }

    private interface Result {
        void accept(LocalDateTime when, String note);
    }

    private void pickDate(LocalDate date, LocalTime time, String note, Result result) {
        new DatePickerDialog(context, (view, y, m, d) ->
                pickTime(LocalDate.of(y, m + 1, d), time, note, result),
                date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show();
    }

    private void pickTime(LocalDate date, LocalTime time, String note, Result result) {
        new TimePickerDialog(context, (view, h, m) ->
                askNote(LocalDateTime.of(date, LocalTime.of(h, m)), note, result),
                time.getHour(), time.getMinute(),
                android.text.format.DateFormat.is24HourFormat(context)).show();
    }

    private void askNote(LocalDateTime when, String note, Result result) {
        float density = context.getResources().getDisplayMetrics().density;
        FrameLayout box = new FrameLayout(context);
        int pad = Math.round(24 * density);
        box.setPadding(pad, Math.round(8 * density), pad, 0);
        EditText field = new EditText(context);
        field.setHint(R.string.appt_note_hint);
        field.setText(note);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setSelection(field.getText().length());
        box.addView(field, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.appt_note_title)
                .setView(box)
                .setPositiveButton(R.string.appt_save, (d, w) -> {
                    String n = field.getText().toString().trim();
                    result.accept(when, n.isEmpty() ? null : n);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                        | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
    }
}
