package com.atelier.app.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atelier.app.R;
import com.atelier.app.model.ReminderDays;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A list of "days before the deadline" with a remove button per row and an add field. */
public final class ReminderDaysEditor extends LinearLayout {
    private final List<Integer> days;
    private final LinearLayout list;
    private final EditText dayInput;
    private final TextView error;

    public ReminderDaysEditor(Context context, List<Integer> initial) {
        super(context);
        setOrientation(VERTICAL);
        days = new ArrayList<>(initial);

        list = new LinearLayout(context);
        list.setOrientation(VERTICAL);
        addView(list);

        LinearLayout addRow = new LinearLayout(context);
        addRow.setOrientation(HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        dayInput = new EditText(context);
        dayInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        dayInput.setSingleLine(true);
        dayInput.setHint(R.string.reminders_days_hint);
        addRow.addView(dayInput, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button add = new Button(context, null, android.R.attr.borderlessButtonStyle);
        add.setText(R.string.action_add);
        add.setTextColor(context.getColor(R.color.accent));
        addRow.addView(add);
        addView(addRow);
        error = new TextView(context, null, 0, R.style.FormError);
        addView(error);

        add.setOnClickListener(v -> commitInput());
        rebuild();
    }

    /** The days now in the list, largest first. */
    public List<Integer> days() {
        return new ArrayList<>(days);
    }

    public void showError(int resId) {
        error.setText(resId);
        error.setVisibility(View.VISIBLE);
    }

    public void clearError() {
        error.setVisibility(View.GONE);
    }

    /** Adds the number typed in the field. Returns false and shows the reason when it is invalid. */
    public boolean commitInput() {
        String text = dayInput.getText().toString().trim();
        if (text.isEmpty()) {
            return true;
        }
        List<Integer> parsed = ReminderDays.parse(text);
        if (parsed.size() != 1) {
            showError(R.string.reminders_error_range);
            return false;
        }
        if (!days.contains(parsed.get(0))) {
            days.add(parsed.get(0));
            days.sort(Collections.reverseOrder());
        }
        dayInput.setText("");
        clearError();
        rebuild();
        return true;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void rebuild() {
        Context context = getContext();
        list.removeAllViews();
        for (final Integer d : days) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(context);
            t.setText(getResources().getQuantityString(R.plurals.reminder_days, d, d));
            t.setTextColor(context.getColor(R.color.text_primary));
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(R.dimen.form_text_size));
            row.addView(t, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ImageButton rm = new ImageButton(context);
            rm.setImageResource(R.drawable.ic_remove);
            rm.setBackgroundResource(R.drawable.icon_button_ripple);
            rm.setContentDescription(context.getString(R.string.reminders_remove_desc));
            rm.setOnClickListener(v -> {
                days.remove(d);
                clearError();
                rebuild();
            });
            row.addView(rm, new LayoutParams(dp(48), dp(48)));
            list.addView(row);
        }
    }
}
