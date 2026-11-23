package com.atelier.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Build;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.view.ViewOutlineProvider;
import android.graphics.Bitmap;
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

import com.atelier.app.data.AppointmentRepository;
import com.atelier.app.data.DbHelper;
import com.atelier.app.data.GarmentTypeRepository;
import com.atelier.app.data.LedgerRepository;
import com.atelier.app.data.OrderRepository;
import com.atelier.app.data.PhotoRepository;
import com.atelier.app.model.Appointment;
import com.atelier.app.model.GarmentType;
import com.atelier.app.model.Kind;
import com.atelier.app.model.LedgerEntry;
import com.atelier.app.model.LedgerType;
import com.atelier.app.model.Money;
import com.atelier.app.model.Order;
import com.atelier.app.model.OrderPhoto;
import com.atelier.app.model.Stage;
import com.atelier.app.photo.AppFileProvider;
import com.atelier.app.photo.PhotoStore;
import com.atelier.app.reminders.Notifications;
import com.atelier.app.share.OrderSharer;
import com.atelier.app.share.OrderSummary;
import com.atelier.app.reminders.Reminders;
import com.atelier.app.ui.AppointmentFlow;
import com.atelier.app.ui.BaseActivity;
import com.atelier.app.ui.Format;
import com.atelier.app.ui.Intents;
import com.atelier.app.ui.OrderCardAdapter;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One order: header, money ledger, notes and the stage moves. Reloads in onResume. */
public class OrderDetailActivity extends BaseActivity {
    private static final String TAG = "Atelier";
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
    private AppointmentRepository appointments;
    private AppointmentFlow appointmentFlow;
    private boolean showPastAll;
    private boolean bookPrompt;
    private PhotoStore photoStore;
    private long orderId;
    private AlertDialog openDialog;
    private final ExecutorService shareWorker = Executors.newSingleThreadExecutor();
    private static final int REQ_CAMERA = 1;
    private static final int REQ_GALLERY = 2;
    private static final String STATE_CAMERA_FILE = "camera_file";
    /** Photos being imported by background jobs started from this screen. */
    private int pendingImports;
    private File cameraFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        db = new DbHelper(this);
        orders = new OrderRepository(db);
        garmentTypes = new GarmentTypeRepository(db);
        ledger = new LedgerRepository(db);
        photos = new PhotoRepository(db);
        appointments = new AppointmentRepository(db);
        appointmentFlow = new AppointmentFlow(this, appointments, this::refresh);
        photoStore = new PhotoStore(this);
        if (savedInstanceState != null && savedInstanceState.getString(STATE_CAMERA_FILE) != null) {
            cameraFile = new File(new File(getCacheDir(), AppFileProvider.DIR_CAMERA),
                    savedInstanceState.getString(STATE_CAMERA_FILE));
        }
        orderId = getIntent().getLongExtra(EXTRA_ORDER_ID, 0);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_edit).setOnClickListener(v ->
                startActivity(OrderFormActivity.editIntent(this, orderId)));
        findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDeleteOrder());
        findViewById(R.id.btn_call).setOnClickListener(v -> dial());
        findViewById(R.id.btn_add_photo).setOnClickListener(v -> showPhotoChooser());
        findViewById(R.id.btn_add_visit).setOnClickListener(v -> appointmentFlow.add(orderId));
        findViewById(R.id.btn_book).setOnClickListener(v -> {
            bookPrompt = false;
            refresh();
            appointmentFlow.add(orderId);
        });
        findViewById(R.id.btn_book_dismiss).setOnClickListener(v -> {
            bookPrompt = false;
            refresh();
        });
        findViewById(R.id.btn_share).setOnClickListener(v -> showShareChooser());
        findViewById(R.id.btn_add_payment).setOnClickListener(v -> showEntryDialog(LedgerType.PAYMENT));
        findViewById(R.id.btn_add_expense).setOnClickListener(v -> showEntryDialog(LedgerType.EXPENSE));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onStop() {
        // Leaving the screen drops the prompt.
        bookPrompt = false;
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (cameraFile != null) {
            out.putString(STATE_CAMERA_FILE, cameraFile.getName());
        }
    }

    @Override
    protected void onDestroy() {
        if (openDialog != null) {
            openDialog.dismiss();
            openDialog = null;
        }
        shareWorker.shutdown();
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

    // ---- sharing ----

    private static final int SHARE_TEXT = 0;
    private static final int SHARE_IMAGE = 1;
    private static final int SHARE_PDF = 2;

    private void showShareChooser() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.share)
                .setView(list)
                .create();
        int[] labels = {R.string.share_text, R.string.share_image, R.string.share_pdf};
        int[] icons = {R.drawable.ic_share_text, R.drawable.ic_share_image, R.drawable.ic_share_pdf};
        for (int i = 0; i < labels.length; i++) {
            final int form = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.form_touch));
            row.setPadding(dp(16), 0, dp(16), 0);
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextColor(getColor(R.color.text_primary));
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.form_text_size));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(20));
            row.addView(label, lp);
            row.setOnClickListener(v -> {
                showSharePreparing(dialog, list);
                startShare(form);
            });
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        showDialog(dialog);
    }

    /** Swaps the dialog content for a spinner while the file is made. */
    private void showSharePreparing(AlertDialog dialog, LinearLayout list) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        box.addView(new ProgressBar(this), new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView t = new TextView(this);
        t.setText(R.string.share_preparing);
        t.setTextColor(getColor(R.color.text_primary));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.form_text_size));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(20));
        box.addView(t, lp);
        list.removeAllViews();
        list.addView(box);
        dialog.setCancelable(false);
    }

    /** Loads on this thread, renders on the worker, and opens the chooser back on this thread. */
    private void startShare(final int form) {
        final OrderSharer sharer = new OrderSharer(this);
        final OrderSummary summary = sharer.load(db, orderId, System.currentTimeMillis());
        if (summary == null) {
            finishShare(null);
            return;
        }
        shareWorker.execute(() -> {
            Intent chooser = null;
            try {
                sharer.cleanOld(System.currentTimeMillis());
                if (form == SHARE_TEXT) {
                    chooser = sharer.textIntent(summary);
                } else if (form == SHARE_IMAGE) {
                    chooser = sharer.fileIntent(sharer.writePng(summary), "image/png", summary);
                } else {
                    chooser = sharer.fileIntent(sharer.writePdf(summary), "application/pdf", summary);
                }
            } catch (IOException | RuntimeException e) {
                // A render or write failure must not crash the screen: log it and tell the user.
                Log.w(TAG, "share: failed form " + form + ": " + e);
            }
            final Intent result = chooser;
            runOnUiThread(() -> finishShare(result));
        });
    }

    private void finishShare(Intent chooser) {
        if (openDialog != null) {
            openDialog.dismiss();
        }
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (chooser == null) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(chooser);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "share: no target app");
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_LONG).show();
        }
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

        bindPhotos();
        bindVisits();
        findViewById(R.id.book_banner).setVisibility(bookPrompt ? View.VISIBLE : View.GONE);
        bindActions(o);
    }

    // ---- visits ----

    private static final int PAST_COLLAPSED = 3;

    private void bindVisits() {
        LinearLayout box = findViewById(R.id.visits);
        box.removeAllViews();
        long now = System.currentTimeMillis();
        List<Appointment> upcoming = new ArrayList<>();
        List<Appointment> past = new ArrayList<>();
        for (Appointment a : appointments.listForOrder(orderId)) {
            (a.startsAt >= now ? upcoming : past).add(a);
        }
        if (upcoming.isEmpty() && past.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.visits_empty);
            empty.setTextColor(getColor(R.color.text_muted));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.body_text_size));
            box.addView(empty);
            return;
        }
        for (Appointment a : upcoming) {
            box.addView(visitRow(a, false));
        }
        if (!past.isEmpty()) {
            TextView label = new TextView(this);
            label.setText(R.string.appt_past);
            label.setTextColor(getColor(R.color.text_muted));
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.chip_text_size));
            label.setTypeface(null, Typeface.BOLD);
            label.setPadding(0, upcoming.isEmpty() ? 0 : dp(8), 0, dp(2));
            box.addView(label);
            int from = showPastAll ? 0 : Math.max(0, past.size() - PAST_COLLAPSED);
            for (int i = from; i < past.size(); i++) {
                box.addView(visitRow(past.get(i), true));
            }
            if (from > 0) {
                Button more = new Button(this, null, 0, R.style.DetailTextButton);
                more.setText(R.string.appt_show_all);
                more.setOnClickListener(v -> {
                    showPastAll = true;
                    refresh();
                });
                box.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        getResources().getDimensionPixelSize(R.dimen.detail_button_height)));
            }
        }
    }

    private View visitRow(final Appointment a, boolean muted) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.form_touch));
        row.setPadding(0, dp(4), 0, dp(4));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        TextView when = new TextView(this);
        when.setText(Format.appointment(this, a.startsAt, LocalDate.now()));
        when.setTextColor(getColor(muted ? R.color.text_muted : R.color.text_primary));
        when.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.form_text_size));
        row.addView(when);
        if (a.note != null && !a.note.trim().isEmpty()) {
            TextView note = new TextView(this);
            note.setText(a.note);
            note.setTextColor(getColor(R.color.text_muted));
            note.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.body_text_size));
            row.addView(note);
        }
        row.setOnClickListener(v -> appointmentFlow.showActions(a));
        return row;
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
        // SQLite cascades the appointments without telling the listener, so their alarms are
        // cancelled here, with the ids read before the delete.
        List<Appointment> visits = appointments.listForOrder(orderId);
        orders.delete(orderId);
        for (Appointment a : visits) {
            Reminders.appointmentListener().onDeleted(a.id);
        }
        Notifications.cancelOrder(this, orderId);
        for (OrderPhoto p : rows) {
            photoStore.delete(p.fileName);
        }
        Log.d(TAG, "order #" + orderId + " deleted");
        finish();
    }

    private void showDialog(AlertDialog d) {
        openDialog = d;
        d.setOnDismissListener(x -> openDialog = null);
        d.show();
    }

    // ---- photos ----

    private void bindPhotos() {
        final List<OrderPhoto> list = photos.listForOrder(orderId);
        View scroll = findViewById(R.id.photo_scroll);
        View empty = findViewById(R.id.photos_empty_box);
        boolean any = !list.isEmpty() || pendingImports > 0;
        scroll.setVisibility(any ? View.VISIBLE : View.GONE);
        empty.setVisibility(any ? View.GONE : View.VISIBLE);
        LinearLayout strip = findViewById(R.id.photo_strip);
        strip.removeAllViews();
        if (!any) {
            return;
        }
        int size = getResources().getDimensionPixelSize(R.dimen.photo_thumb_size);
        int gap = getResources().getDimensionPixelSize(R.dimen.photo_thumb_gap);

        TextView add = new TextView(this);
        add.setText(R.string.photos_add);
        add.setGravity(Gravity.CENTER);
        add.setTextColor(getColor(R.color.accent));
        add.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.body_text_size));
        add.setTypeface(null, Typeface.BOLD);
        add.setBackgroundResource(R.drawable.field_bg);
        add.setOnClickListener(v -> showPhotoChooser());
        strip.addView(add, new LinearLayout.LayoutParams(size, size));

        if (pendingImports > 0) {
            ProgressBar bar = new ProgressBar(this);
            bar.setIndeterminate(true);
            bar.setContentDescription(getString(R.string.photo_importing_desc));
            android.widget.FrameLayout tile = new android.widget.FrameLayout(this);
            tile.setBackgroundResource(R.drawable.field_bg);
            tile.addView(bar, new android.widget.FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginStart(gap);
            strip.addView(tile, lp);
        }

        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            final String name = list.get(i).fileName;
            final ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setContentDescription(getString(R.string.photo_thumb_desc));
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(getColor(R.color.tab_divider));
            shape.setCornerRadius(getResources().getDimension(R.dimen.photo_slot_radius));
            thumb.setBackground(shape);
            thumb.setClipToOutline(true);
            thumb.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            thumb.setOnClickListener(v -> startActivity(PhotoViewerActivity.intent(this, orderId, index)));
            Bitmap hit = photoStore.cachedThumbnail(name, size);
            if (hit != null) {
                thumb.setImageBitmap(hit);
            } else {
                thumb.setTag(name);
                photoStore.loadThumbnailAsync(name, size, bmp -> {
                    // The strip is rebuilt on refresh. A view that was replaced keeps a stale tag.
                    if (bmp != null && name.equals(thumb.getTag())) {
                        thumb.setImageBitmap(bmp);
                    }
                });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginStart(gap);
            strip.addView(thumb, lp);
        }
    }

    private void showPhotoChooser() {
        final boolean hasCamera = cameraIntent(null) != null;
        final List<CharSequence> labels = new ArrayList<>();
        if (hasCamera) {
            labels.add(getString(R.string.photo_source_camera));
        }
        labels.add(getString(R.string.photo_source_gallery));
        showDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.section_photos)
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> {
                    if (hasCamera && which == 0) {
                        startCamera();
                    } else {
                        startGallery();
                    }
                })
                .create());
    }

    /** The capture intent, or null when no camera app resolves. {@code output} may be null to probe. */
    private Intent cameraIntent(Uri output) {
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (i.resolveActivity(getPackageManager()) == null) {
            return null;
        }
        if (output != null) {
            i.putExtra(MediaStore.EXTRA_OUTPUT, output);
            i.setClipData(ClipData.newRawUri("", output));
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return i;
    }

    private void startCamera() {
        File dir = new File(getCacheDir(), AppFileProvider.DIR_CAMERA);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "camera folder not created");
        }
        cameraFile = new File(dir, "capture-" + orderId + "-" + System.currentTimeMillis() + ".jpg");
        Uri out = AppFileProvider.uriFor(this, AppFileProvider.DIR_CAMERA, cameraFile.getName());
        Intent i = cameraIntent(out);
        if (i == null) {
            return;
        }
        try {
            startActivityForResult(i, REQ_CAMERA);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "detail: no camera app");
            cameraFile = null;
        }
    }

    private void startGallery() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 33) {
            i = new Intent(MediaStore.ACTION_PICK_IMAGES);
            i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 10);
        } else {
            i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        try {
            startActivityForResult(i, REQ_GALLERY);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "detail: no gallery app");
            Toast.makeText(this, R.string.no_gallery_app, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA) {
            File f = cameraFile;
            cameraFile = null;
            if (f == null) {
                return;
            }
            if (resultCode == Activity.RESULT_OK && f.length() > 0) {
                List<Uri> one = new ArrayList<>();
                one.add(Uri.fromFile(f));
                startImport(one, f);
            } else if (f.exists() && !f.delete()) {
                Log.w(TAG, "camera temp not deleted: " + f.getName());
            }
        } else if (requestCode == REQ_GALLERY && resultCode == Activity.RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) {
                startImport(uris, null);
            }
        }
    }

    private void startImport(List<Uri> uris, File temp) {
        pendingImports += uris.size();
        bindPhotos();
        final int n = uris.size();
        photoStore.importAsync(orderId, uris, temp, (added, failed) -> {
            pendingImports -= n;
            if (failed > 0) {
                Toast.makeText(this, getString(R.string.photo_import_failed, failed), Toast.LENGTH_LONG).show();
            }
            if (!isDestroyed()) {
                bindPhotos();
            }
        });
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
            bookPrompt = target.offersVisit();
        } catch (IllegalStateException | IllegalArgumentException e) {
            // The screen was stale (moved or deleted elsewhere). Reload and show the truth.
            Log.w(TAG, "move failed for #" + o.id + ": " + e.getMessage());
        }
        refresh();
    }
}
