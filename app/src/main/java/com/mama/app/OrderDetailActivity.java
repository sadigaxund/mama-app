package com.mama.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mama.app.data.DbHelper;
import com.mama.app.data.GarmentTypeRepository;
import com.mama.app.data.LedgerRepository;
import com.mama.app.data.OrderRepository;
import com.mama.app.data.PhotoRepository;
import com.mama.app.model.GarmentType;
import com.mama.app.model.Kind;
import com.mama.app.model.LedgerEntry;
import com.mama.app.model.LedgerType;
import com.mama.app.model.Money;
import com.mama.app.model.Order;
import com.mama.app.model.OrderPhoto;
import com.mama.app.model.Stage;
import com.mama.app.ui.BaseActivity;
import com.mama.app.ui.Format;
import com.mama.app.ui.Intents;
import com.mama.app.ui.OrderCardAdapter;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** One order: header, money ledger, notes and the stage moves. Reloads in onResume. */
public class OrderDetailActivity extends BaseActivity {
    private static final String TAG = "MamaApp";
    public static final String EXTRA_ORDER_ID = "order_id";

    /** Opens the detail screen of an order. */
    public static Intent intent(Context context, long orderId) {
        return new Intent(context, OrderDetailActivity.class).putExtra(EXTRA_ORDER_ID, orderId);
    }

    private DbHelper db;
    private OrderRepository orders;
    private GarmentTypeRepository garmentTypes;
    private LedgerRepository ledger;
    private PhotoRepository photos;
    private long orderId;
    private AlertDialog openDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        db = new DbHelper(this);
        orders = new OrderRepository(db);
        garmentTypes = new GarmentTypeRepository(db);
        ledger = new LedgerRepository(db);
        photos = new PhotoRepository(db);
        orderId = getIntent().getLongExtra(EXTRA_ORDER_ID, 0);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_edit).setOnClickListener(v ->
                startActivity(OrderFormActivity.editIntent(this, orderId)));
        findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDeleteOrder());
        findViewById(R.id.btn_call).setOnClickListener(v -> dial());
        findViewById(R.id.btn_add_photo).setOnClickListener(v -> soon());
        findViewById(R.id.btn_add_visit).setOnClickListener(v -> soon());
        findViewById(R.id.btn_share).setOnClickListener(v -> soon());
        findViewById(R.id.btn_add_payment).setOnClickListener(v -> showEntryDialog(LedgerType.PAYMENT));
        findViewById(R.id.btn_add_expense).setOnClickListener(v -> showEntryDialog(LedgerType.EXPENSE));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        if (openDialog != null) {
            openDialog.dismiss();
            openDialog = null;
        }
        db.close();
        super.onDestroy();
    }

    private void dial() {
        CharSequence phone = ((TextView) findViewById(R.id.phone)).getText();
        try {
            startActivity(Intents.dial(phone.toString()));
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "detail: no dialer");
            Toast.makeText(this, R.string.no_dialer_app, Toast.LENGTH_LONG).show();
        }
    }

    private void soon() {
        Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---- loading ----

    private void refresh() {
        Order o = orders.get(orderId);
        if (o == null) {
            Log.w(TAG, "detail: no order " + orderId);
            finish();
            return;
        }
        ((TextView) findViewById(R.id.title)).setText(getString(R.string.order_number, o.id));
        bindHeader(o);
        bindLedger(o);

        TextView notes = findViewById(R.id.notes);
        boolean noNotes = o.notes == null || o.notes.trim().isEmpty();
        notes.setText(noNotes ? getString(R.string.notes_empty) : o.notes);
        notes.setTextColor(getColor(noNotes ? R.color.text_muted : R.color.text_primary));
        notes.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(
                noNotes ? R.dimen.body_text_size : R.dimen.form_text_size));

        bindActions(o);
    }

    private void bindHeader(Order o) {
        int stripe = getColor(o.kind == Kind.TAILORING ? R.color.kind_tailoring_stripe : R.color.kind_repair_stripe);
        int bg = getColor(o.kind == Kind.TAILORING ? R.color.kind_tailoring_bg : R.color.kind_repair_bg);
        findViewById(R.id.header).setBackground(OrderCardAdapter.cardBackground(this, bg, stripe));

        GarmentType type = garmentTypes.get(o.garmentTypeId);
        String kindName = getString(o.kind == Kind.TAILORING ? R.string.kind_tailoring : R.string.kind_repair)
                .toUpperCase(Format.locale(this));
        TextView kindLine = findViewById(R.id.kind_line);
        kindLine.setText(type == null ? kindName : kindName + " · " + type.name);
        kindLine.setTextColor(stripe);

        ((TextView) findViewById(R.id.customer)).setText(o.customerName);

        boolean hasPhone = o.phone != null && !o.phone.trim().isEmpty();
        findViewById(R.id.phone_row).setVisibility(hasPhone ? View.VISIBLE : View.GONE);
        if (hasPhone) {
            ((TextView) findViewById(R.id.phone)).setText(o.phone);
        }

        LocalDate today = LocalDate.now();
        ((TextView) findViewById(R.id.deadline)).setText(
                getString(R.string.detail_deadline, Format.dateFull(o.deadline)));
        View chip = findViewById(R.id.overdue_chip);
        boolean overdue = o.stage != Stage.DONE && o.deadline.isBefore(today);
        if (overdue) {
            chip.setBackground(OrderCardAdapter.chipBackground(this));
        }
        chip.setVisibility(overdue ? View.VISIBLE : View.GONE);

        LocalDate changed = Instant.ofEpochMilli(o.stageChangedAt).atZone(ZoneId.systemDefault()).toLocalDate();
        long days = Math.max(0, ChronoUnit.DAYS.between(changed, today));
        String age = days == 0 ? getString(R.string.stage_today) : getString(R.string.stage_days, (int) days);
        ((TextView) findViewById(R.id.stage_line)).setText(
                getString(R.string.detail_stage, getString(MainActivity.stageLabel(o.stage)), age));
    }

    // ---- ledger ----

    private void bindLedger(Order o) {
        LinearLayout box = findViewById(R.id.ledger);
        box.removeAllViews();
        List<LedgerEntry> entries = ledger.listForOrder(o.id);
        box.addView(ledgerRow(getString(R.string.label_price), Format.money(this, o.workPrice),
                false, null));
        for (LedgerEntry e : entries) {
            boolean expense = e.type == LedgerType.EXPENSE;
            String title = e.title == null || e.title.trim().isEmpty()
                    ? getString(expense ? R.string.ledger_expense_default : R.string.ledger_payment_default)
                    : e.title;
            String amount = getString(expense ? R.string.ledger_expense_amount : R.string.ledger_payment_amount,
                    Format.money(this, e.amount));
            box.addView(ledgerRow(title, amount, false, e));
        }
        View divider = new View(this);
        divider.setBackgroundResource(R.drawable.tab_divider);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dlp.topMargin = dp(4);
        dlp.bottomMargin = dp(4);
        box.addView(divider, dlp);

        long balance = Money.balanceDue(o.workPrice, entries);
        if (balance < 0) {
            box.addView(ledgerRow(getString(R.string.money_overpaid, Format.money(this, -balance)),
                    null, true, null));
        } else {
            box.addView(ledgerRow(getString(R.string.money_balance), Format.money(this, balance), true, null));
        }
    }

    private View ledgerRow(String label, String amount, boolean bold, final LedgerEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.detail_row_height));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(getColor(R.color.text_primary));
        l.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.form_text_size));
        l.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (amount != null) {
            TextView a = new TextView(this);
            a.setText(amount);
            a.setGravity(Gravity.END);
            a.setFontFeatureSettings("tnum");
            a.setSingleLine(true);
            a.setTextColor(getColor(R.color.text_primary));
            a.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.form_text_size));
            a.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(12));
            row.addView(a, lp);
        }
        if (entry != null) {
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setOnLongClickListener(v -> {
                confirmDeleteEntry(entry, label, amount);
                return true;
            });
        }
        return row;
    }

    private void confirmDeleteEntry(final LedgerEntry entry, String label, String amount) {
        showDialog(new AlertDialog.Builder(this)
                .setMessage(getString(R.string.ledger_delete_confirm, label, amount))
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    ledger.delete(entry.id);
                    Log.d(TAG, "ledger entry " + entry.id + " deleted from #" + orderId);
                    refresh();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .create());
    }

    /** One dialog for a payment or an expense: amount with an inline error, optional title. */
    private void showEntryDialog(final LedgerType type) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);

        final EditText amount = new EditText(this);
        amount.setHint(R.string.label_amount);
        amount.setSingleLine(true);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(amount, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView error = new TextView(this, null, 0, R.style.FormError);
        error.setText(R.string.error_amount);
        box.addView(error);
        amount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                error.setVisibility(View.GONE);
            }
        });

        final EditText title = new EditText(this);
        title.setHint(R.string.label_entry_title);
        title.setSingleLine(true);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(type == LedgerType.PAYMENT ? R.string.ledger_payment_default : R.string.ledger_expense_default)
                .setView(box)
                .setPositiveButton(R.string.action_ok, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        showDialog(dialog);
        // Replacing the click listener after show() keeps the dialog open on an error.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long minor;
            try {
                minor = Money.parseMinor(amount.getText().toString());
                if (minor <= 0) {
                    throw new IllegalArgumentException("Amount is zero");
                }
            } catch (IllegalArgumentException e) {
                error.setVisibility(View.VISIBLE);
                return;
            }
            String t = title.getText().toString().trim();
            try {
                ledger.add(orderId, type, minor, t.isEmpty() ? null : t, System.currentTimeMillis());
            } catch (RuntimeException e) {
                // Validation passed above, so this is a database failure. Tell the user, keep the dialog.
                Log.e(TAG, "ledger add failed", e);
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            refresh();
        });
    }

    // ---- delete order ----

    private void confirmDeleteOrder() {
        showDialog(new AlertDialog.Builder(this)
                .setMessage(getString(R.string.detail_delete_confirm, orderId))
                .setPositiveButton(R.string.action_delete, (d, w) -> deleteOrder())
                .setNegativeButton(R.string.action_cancel, null)
                .create());
    }

    private void deleteOrder() {
        // The rows go with the order, so the file names are read first.
        List<OrderPhoto> rows = photos.listForOrder(orderId);
        orders.delete(orderId);
        File dir = new File(getFilesDir(), "photos");
        for (OrderPhoto p : rows) {
            File f = new File(dir, p.fileName);
            if (f.exists() && !f.delete()) {
                Log.w(TAG, "photo file not deleted: " + f.getName());
            }
        }
        Log.d(TAG, "order #" + orderId + " deleted");
        finish();
    }

    private void showDialog(AlertDialog d) {
        openDialog = d;
        d.setOnDismissListener(x -> openDialog = null);
        d.show();
    }

    // ---- moves ----

    private void bindActions(final Order o) {
        LinearLayout box = findViewById(R.id.actions);
        box.removeAllViews();
        Stage back = o.stage.previous();
        for (final Stage target : o.stage.allowedMoves()) {
            boolean isBack = target == back;
            Button b = new Button(this, null, 0, R.style.DetailTextButton);
            b.setText(getString(isBack ? R.string.move_back : R.string.move_forward,
                    getString(MainActivity.stageLabel(target))));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, getResources().getDimensionPixelSize(R.dimen.detail_button_height));
            if (isBack) {
                lp.gravity = Gravity.START;
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                // Only the primary next stage is filled; other forward moves (the Done shortcut,
                // Alterations) are outlined so the usual path reads first.
                boolean primary = target == o.stage.next();
                GradientDrawable fill = new GradientDrawable();
                fill.setCornerRadius(getResources().getDimension(R.dimen.form_field_radius));
                if (primary) {
                    fill.setColor(getColor(R.color.accent));
                    b.setTextColor(getColor(R.color.on_accent));
                } else {
                    fill.setColor(getColor(R.color.field_bg));
                    fill.setStroke(dp(1), getColor(R.color.accent));
                    b.setTextColor(getColor(R.color.accent));
                }
                b.setBackground(new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(getColor(R.color.ripple)), fill, null));
                lp.bottomMargin = dp(8);
            }
            b.setOnClickListener(v -> move(o, target));
            box.addView(b, lp);
        }
    }

    private void move(Order o, Stage target) {
        try {
            orders.moveTo(o.id, target, System.currentTimeMillis());
            Log.d(TAG, "moved #" + o.id + " " + o.stage + " -> " + target);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // The screen was stale (moved or deleted elsewhere). Reload and show the truth.
            Log.w(TAG, "move failed for #" + o.id + ": " + e.getMessage());
        }
        refresh();
    }
}
