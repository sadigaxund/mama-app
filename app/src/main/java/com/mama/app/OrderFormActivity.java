package com.mama.app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.mama.app.data.DbHelper;
import com.mama.app.data.DuplicateGarmentTypeException;
import com.mama.app.data.GarmentTypeRepository;
import com.mama.app.data.OrderRepository;
import com.mama.app.model.GarmentType;
import com.mama.app.model.Kind;
import com.mama.app.model.Money;
import com.mama.app.model.Order;
import com.mama.app.model.ReminderDays;
import com.mama.app.model.ReminderMode;
import com.mama.app.ui.ReminderDaysEditor;
import com.mama.app.model.Stage;
import com.mama.app.ui.BaseActivity;
import com.mama.app.ui.Format;
import com.mama.app.ui.Intents;
import com.mama.app.ui.Prefs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Creates a new order or edits an existing one. Full screen, because of the keyboard. */
public class OrderFormActivity extends BaseActivity {
    private static final String TAG = "MamaApp";
    public static final String EXTRA_STAGE = "stage";
    public static final String EXTRA_ORDER_ID = "order_id";

    private static final String STATE_DATE = "date";
    private static final String STATE_KIND = "kind";
    private static final String STATE_TYPE = "type_id";
    private static final String STATE_REM_MODE = "rem_mode";
    private static final String STATE_REM_DAYS = "rem_days";
    private static final String STATE_INITIAL = "initial_sig";

    private static final LocalDate MIN_DEADLINE = LocalDate.of(2000, 1, 1);
    private static final long NO_DATE = Long.MIN_VALUE;

    /** Opens the form for a new order that starts in the given stage. */
    public static Intent newOrderIntent(Context context, Stage stage) {
        return new Intent(context, OrderFormActivity.class).putExtra(EXTRA_STAGE, stage.name());
    }

    /** Opens the form for an existing order. */
    public static Intent editIntent(Context context, long orderId) {
        return new Intent(context, OrderFormActivity.class).putExtra(EXTRA_ORDER_ID, orderId);
    }

    private DbHelper db;
    private OrderRepository orders;
    private GarmentTypeRepository garmentTypes;

    private Order editing;
    private Stage newStage = Stage.ACCEPTED;

    private ScrollView scroll;
    private EditText fCustomer;
    private EditText fPhone;
    private Spinner fGarment;
    private EditText fPrice;
    private TextView fDeadline;
    private EditText fDeposit;
    private EditText fNotes;
    private TextView fReminders;
    private TextView kindTailoring;
    private TextView kindRepair;

    private Kind kind = Kind.TAILORING;
    private LocalDate deadline;
    private long typeId;
    private ReminderMode remMode = ReminderMode.DEFAULT;
    private List<Integer> remDays = new ArrayList<>();
    private final List<GarmentType> types = new ArrayList<>();
    private String initialSig;
    private Dialog openDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_form);

        db = new DbHelper(this);
        orders = new OrderRepository(db);
        garmentTypes = new GarmentTypeRepository(db);

        long orderId = getIntent().getLongExtra(EXTRA_ORDER_ID, 0);
        if (orderId != 0) {
            editing = orders.get(orderId);
            if (editing == null) {
                Log.w(TAG, "order form: no order " + orderId);
                finish();
                return;
            }
        } else {
            String stageName = getIntent().getStringExtra(EXTRA_STAGE);
            if (stageName != null) {
                try {
                    newStage = Stage.valueOf(stageName);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "order form: unknown stage " + stageName);
                }
            }
        }

        scroll = findViewById(R.id.scroll);
        fCustomer = findViewById(R.id.f_customer);
        fPhone = findViewById(R.id.f_phone);
        fGarment = findViewById(R.id.f_garment);
        fPrice = findViewById(R.id.f_price);
        fDeadline = findViewById(R.id.f_deadline);
        fDeposit = findViewById(R.id.f_deposit);
        fNotes = findViewById(R.id.f_notes);
        fReminders = findViewById(R.id.f_reminders);
        kindTailoring = findViewById(R.id.kind_tailoring);
        kindRepair = findViewById(R.id.kind_repair);

        String symbol = Format.currencySymbol(this);
        ((TextView) findViewById(R.id.price_currency)).setText(symbol);
        ((TextView) findViewById(R.id.deposit_currency)).setText(symbol);

        TextView title = findViewById(R.id.title);
        if (editing != null) {
            title.setText(getString(R.string.form_title_edit, editing.id));
            findViewById(R.id.block_deposit).setVisibility(View.GONE);
            populate(editing);
        } else {
            title.setText(getString(R.string.form_title_new, getString(MainActivity.stageLabel(newStage))));
        }

        reloadTypes();
        fGarment.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == types.size() + 1) {
                    showNewTypeDialog(typeId);
                } else {
                    typeId = position == 0 ? 0 : types.get(position - 1).id;
                    hideError(fGarment, R.id.e_garment);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        selectType(typeId);

        // The initial signature is taken before saved state is applied, so it describes the
        // form as it first opened and rotation does not hide unsaved edits.
        initialSig = signature();
        if (savedInstanceState != null) {
            restore(savedInstanceState);
        }

        updateKindButtons();
        updateDeadlineText();
        updateRemindersText();

        findViewById(R.id.btn_close).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_contacts).setOnClickListener(v -> pickContact());
        kindTailoring.setOnClickListener(v -> setKind(Kind.TAILORING));
        kindRepair.setOnClickListener(v -> setKind(Kind.REPAIR));
        fDeadline.setOnClickListener(v -> showDatePicker());
        fReminders.setOnClickListener(v -> showRemindersDialog());

        clearOnChange(fCustomer, R.id.e_customer);
        clearOnChange(fPrice, R.id.e_price);
        clearOnChange(fDeposit, R.id.e_deposit);
    }

    private void populate(Order o) {
        fCustomer.setText(o.customerName);
        fPhone.setText(o.phone);
        fPrice.setText(Money.formatMinor(o.workPrice));
        fNotes.setText(o.notes);
        kind = o.kind;
        deadline = o.deadline;
        typeId = o.garmentTypeId;
        remMode = o.reminderMode;
        if (remMode == ReminderMode.CUSTOM) {
            remDays = ReminderDays.normalize(orders.getCustomDays(o.id));
        }
    }

    private void restore(Bundle b) {
        long epoch = b.getLong(STATE_DATE, NO_DATE);
        deadline = epoch == NO_DATE ? null : LocalDate.ofEpochDay(epoch);
        kind = Kind.valueOf(b.getString(STATE_KIND, Kind.TAILORING.name()));
        remMode = ReminderMode.valueOf(b.getString(STATE_REM_MODE, ReminderMode.DEFAULT.name()));
        int[] days = b.getIntArray(STATE_REM_DAYS);
        remDays = new ArrayList<>();
        if (days != null) {
            for (int d : days) {
                remDays.add(d);
            }
        }
        initialSig = b.getString(STATE_INITIAL, initialSig);
        selectType(b.getLong(STATE_TYPE, 0));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong(STATE_DATE, deadline == null ? NO_DATE : deadline.toEpochDay());
        out.putString(STATE_KIND, kind.name());
        out.putLong(STATE_TYPE, typeId);
        out.putString(STATE_REM_MODE, remMode.name());
        int[] days = new int[remDays.size()];
        for (int i = 0; i < days.length; i++) {
            days[i] = remDays.get(i);
        }
        out.putIntArray(STATE_REM_DAYS, days);
        out.putString(STATE_INITIAL, initialSig);
    }

    @Override
    protected void onDestroy() {
        if (openDialog != null) {
            openDialog.setOnDismissListener(null);
            openDialog.dismiss();
            openDialog = null;
        }
        if (db != null) {
            db.close();
        }
        super.onDestroy();
    }

    // ---- unsaved changes ----

    private String signature() {
        return String.join("\u0001",
                fCustomer.getText().toString(), fPhone.getText().toString(), kind.name(),
                Long.toString(typeId), fPrice.getText().toString(),
                deadline == null ? "" : deadline.toString(), fDeposit.getText().toString(),
                fNotes.getText().toString(), remMode.name(), ReminderDays.toCsv(remDays));
    }

    @Override
    public void onBackPressed() {
        if (signature().equals(initialSig)) {
            super.onBackPressed();
            return;
        }
        showDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.discard_title)
                .setPositiveButton(R.string.discard_yes, (d, w) -> finish())
                .setNegativeButton(R.string.discard_no, null)
                .create());
    }

    private void showDialog(Dialog d) {
        openDialog = d;
        d.setOnDismissListener(x -> openDialog = null);
        d.show();
    }

    // ---- kind ----

    private void setKind(Kind k) {
        kind = k;
        updateKindButtons();
    }

    private void updateKindButtons() {
        styleKind(kindTailoring, kind == Kind.TAILORING, R.color.kind_tailoring_bg, R.color.kind_tailoring_stripe);
        styleKind(kindRepair, kind == Kind.REPAIR, R.color.kind_repair_bg, R.color.kind_repair_stripe);
    }

    private void styleKind(TextView v, boolean on, int bgRes, int strokeRes) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(getResources().getDimension(R.dimen.form_field_radius));
        if (on) {
            d.setColor(getColor(bgRes));
            d.setStroke(getResources().getDimensionPixelSize(R.dimen.form_stroke_active), getColor(strokeRes));
            v.setTextColor(getColor(strokeRes));
            v.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            d.setColor(getColor(R.color.field_bg));
            d.setStroke(getResources().getDimensionPixelSize(R.dimen.form_stroke), getColor(R.color.field_stroke));
            v.setTextColor(getColor(R.color.text_muted));
            v.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        v.setBackground(d);
        v.setSelected(on);
    }

    // ---- garment type ----

    /** Rebuilds the spinner: placeholder, active types, and "+ New type". */
    private void reloadTypes() {
        types.clear();
        types.addAll(garmentTypes.listActive());
        if (editing != null) {
            boolean listed = false;
            for (GarmentType t : types) {
                listed |= t.id == editing.garmentTypeId;
            }
            if (!listed) {
                GarmentType own = garmentTypes.get(editing.garmentTypeId);
                if (own != null) {
                    types.add(own);
                }
            }
        }
        final List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.garment_placeholder));
        for (GarmentType t : types) {
            labels.add(t.name);
        }
        labels.add(getString(R.string.garment_new));
        final int last = labels.size() - 1;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_spinner, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return tint((TextView) super.getView(position, convertView, parent), position);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return tint((TextView) super.getDropDownView(position, convertView, parent), position);
            }

            private View tint(TextView tv, int position) {
                int color = position == 0 ? R.color.text_hint
                        : position == last ? R.color.accent : R.color.text_primary;
                tv.setTextColor(getColor(color));
                return tv;
            }
        };
        fGarment.setAdapter(adapter);
    }

    private void selectType(long id) {
        typeId = id;
        int position = 0;
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).id == id) {
                position = i + 1;
            }
        }
        if (position == 0) {
            typeId = 0;
        }
        fGarment.setSelection(position, false);
    }

    private void showNewTypeDialog(final long previousTypeId) {
        final boolean[] added = {false};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        box.setPadding(pad, dp(8), pad, 0);
        final EditText input = new EditText(this);
        input.setHint(R.string.garment_new_hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView error = new TextView(this, null, 0, R.style.FormError);
        box.addView(error);
        input.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                error.setVisibility(View.GONE);
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.garment_new_title)
                .setView(box)
                .setPositiveButton(R.string.action_ok, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.setOnDismissListener(d -> {
            openDialog = null;
            if (!added[0]) {
                selectType(previousTypeId);
            }
        });
        openDialog = dialog;
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
        // Replacing the click listener after show() keeps the dialog open on an error.
        input.setOnEditorActionListener((tv, action, event) -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            return true;
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                error.setText(R.string.error_type_blank);
                error.setVisibility(View.VISIBLE);
                return;
            }
            try {
                long id = garmentTypes.add(name);
                added[0] = true;
                reloadTypes();
                selectType(id);
                hideError(fGarment, R.id.e_garment);
                dialog.dismiss();
            } catch (DuplicateGarmentTypeException e) {
                error.setText(R.string.error_type_duplicate);
                error.setVisibility(View.VISIBLE);
            }
        });
    }

    // ---- deadline ----

    private void updateDeadlineText() {
        if (deadline == null) {
            fDeadline.setText(R.string.hint_deadline);
            fDeadline.setTextColor(getColor(R.color.text_hint));
        } else {
            fDeadline.setText(Format.dateFull(deadline));
            fDeadline.setTextColor(getColor(R.color.text_primary));
        }
    }

    private void showDatePicker() {
        LocalDate start = deadline != null ? deadline : LocalDate.now();
        DatePickerDialog d = new DatePickerDialog(this, (picker, y, m, day) -> {
            deadline = LocalDate.of(y, m + 1, day);
            updateDeadlineText();
            hideError(fDeadline, R.id.e_deadline);
        }, start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth());
        d.getDatePicker().setMinDate(MIN_DEADLINE.atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli());
        showDialog(d);
    }

    // ---- reminders ----

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

    private void updateRemindersText() {
        switch (remMode) {
            case CUSTOM:
                fReminders.setText(getString(R.string.reminders_custom, daysText(remDays)));
                break;
            case OFF:
                fReminders.setText(R.string.reminders_off);
                break;
            default:
                fReminders.setText(getString(R.string.reminders_default, daysText(Prefs.reminderDays(this))));
        }
    }

    private void showRemindersDialog() {
        final ReminderMode[] mode = {remMode};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        box.setPadding(pad, dp(8), pad, 0);

        final RadioGroup group = new RadioGroup(this);
        final RadioButton optDefault = new RadioButton(this);
        optDefault.setId(View.generateViewId());
        optDefault.setText(getString(R.string.reminders_opt_default, daysText(Prefs.reminderDays(this))));
        final RadioButton optCustom = new RadioButton(this);
        optCustom.setId(View.generateViewId());
        optCustom.setText(R.string.reminders_opt_custom);
        final RadioButton optOff = new RadioButton(this);
        optOff.setId(View.generateViewId());
        optOff.setText(R.string.reminders_opt_off);
        for (RadioButton rb : new RadioButton[]{optDefault, optCustom, optOff}) {
            rb.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(R.dimen.form_text_size));
            rb.setMinHeight(dp(48));
            group.addView(rb);
        }
        box.addView(group);

        final ReminderDaysEditor customBox = new ReminderDaysEditor(this, remDays);
        box.addView(customBox);

        final Runnable showMode = () -> customBox.setVisibility(mode[0] == ReminderMode.CUSTOM
                ? View.VISIBLE : View.GONE);
        group.check(mode[0] == ReminderMode.CUSTOM ? optCustom.getId()
                : mode[0] == ReminderMode.OFF ? optOff.getId() : optDefault.getId());
        showMode.run();
        group.setOnCheckedChangeListener((g, checked) -> {
            mode[0] = checked == optCustom.getId() ? ReminderMode.CUSTOM
                    : checked == optOff.getId() ? ReminderMode.OFF : ReminderMode.DEFAULT;
            customBox.clearError();
            showMode.run();
        });

        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.addView(box);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.reminders_dialog_title)
                .setView(dialogScroll)
                .setPositiveButton(R.string.action_ok, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        showDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (mode[0] == ReminderMode.CUSTOM && !customBox.commitInput()) {
                return;
            }
            List<Integer> days = customBox.days();
            if (mode[0] == ReminderMode.CUSTOM && days.isEmpty()) {
                customBox.showError(R.string.reminders_error_none);
                return;
            }
            remMode = mode[0];
            remDays = remMode == ReminderMode.CUSTOM ? new ArrayList<>(days) : new ArrayList<Integer>();
            updateRemindersText();
            dialog.dismiss();
        });
    }

    // ---- errors ----

    private void showError(View field, int errorId) {
        field.setActivated(true);
        findViewById(errorId).setVisibility(View.VISIBLE);
    }

    private void hideError(View field, int errorId) {
        field.setActivated(false);
        findViewById(errorId).setVisibility(View.GONE);
    }

    private void clearOnChange(final EditText field, final int errorId) {
        field.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                hideError(field, errorId);
            }
        });
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---- save ----

    private void save() {
        View firstBlock = null;
        View firstFocus = null;

        String name = fCustomer.getText().toString().trim();
        if (name.isEmpty()) {
            showError(fCustomer, R.id.e_customer);
            firstBlock = findViewById(R.id.block_customer);
            firstFocus = fCustomer;
        }

        if (typeId == 0) {
            showError(fGarment, R.id.e_garment);
            if (firstBlock == null) {
                firstBlock = findViewById(R.id.block_garment);
                firstFocus = fGarment;
            }
        }

        long price = 0;
        try {
            price = Money.parseMinor(fPrice.getText().toString());
        } catch (IllegalArgumentException e) {
            showError(fPrice, R.id.e_price);
            if (firstBlock == null) {
                firstBlock = findViewById(R.id.block_price);
                firstFocus = fPrice;
            }
        }

        if (deadline == null) {
            showError(fDeadline, R.id.e_deadline);
            if (firstBlock == null) {
                firstBlock = findViewById(R.id.block_deadline);
                firstFocus = fDeadline;
            }
        }

        long deposit = 0;
        String depositText = fDeposit.getText().toString().trim();
        if (editing == null && !depositText.isEmpty()) {
            try {
                deposit = Money.parseMinor(depositText);
                if (deposit <= 0) {
                    throw new IllegalArgumentException("Deposit is zero");
                }
            } catch (IllegalArgumentException e) {
                showError(fDeposit, R.id.e_deposit);
                if (firstBlock == null) {
                    firstBlock = findViewById(R.id.block_deposit);
                    firstFocus = fDeposit;
                }
            }
        }

        if (firstBlock != null) {
            final View block = firstBlock;
            scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, block.getTop() - dp(12))));
            firstFocus.requestFocus();
            if (firstFocus instanceof EditText) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(firstFocus, InputMethodManager.SHOW_IMPLICIT);
            }
            return;
        }

        String phone = fPhone.getText().toString().trim();
        String notes = fNotes.getText().toString().trim();
        try {
            long id;
            Stage stage;
            if (editing == null) {
                stage = newStage;
                Order o = new Order(0, name, phone.isEmpty() ? null : phone, kind, typeId, price,
                        deadline, notes.isEmpty() ? null : notes, stage, 0, 0, ReminderMode.DEFAULT);
                id = orders.insert(o, deposit, System.currentTimeMillis());
            } else {
                stage = editing.stage;
                id = editing.id;
                orders.update(new Order(id, name, phone.isEmpty() ? null : phone, kind, typeId, price,
                        deadline, notes.isEmpty() ? null : notes, stage, editing.createdAt,
                        editing.stageChangedAt, editing.reminderMode));
            }
            if (remMode == ReminderMode.CUSTOM) {
                orders.setCustomReminders(id, remDays);
            } else {
                orders.setReminderMode(id, remMode);
            }
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_STAGE, stage.name()));
            finish();
        } catch (RuntimeException e) {
            // Validation passed above, so this is a database failure. Tell the user, keep the form.
            Log.e(TAG, "order save failed", e);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---- contacts picker ----

    private static final int REQUEST_CONTACT = 1;

    private void pickContact() {
        try {
            startActivityForResult(Intents.pickPhoneContact(), REQUEST_CONTACT);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "form: no contacts app");
            Toast.makeText(this, R.string.no_contacts_app, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CONTACT || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        String[] cols = {ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
        try (Cursor c = getContentResolver().query(uri, cols, null, null, null)) {
            if (c == null || !c.moveToFirst()) {
                Log.w(TAG, "form: picked contact returned no row");
                return;
            }
            String number = c.getString(0);
            String name = c.getString(1);
            if (number != null) {
                fPhone.setText(number.trim());
            }
            if (name != null && fCustomer.getText().toString().trim().isEmpty()) {
                fCustomer.setText(name);
            }
        } catch (SecurityException | IllegalArgumentException e) {
            // Safe: a failed read leaves the form as it was; the user can type the number.
            Log.w(TAG, "form: contact read failed", e);
            Toast.makeText(this, R.string.no_contacts_app, Toast.LENGTH_LONG).show();
        }
    }
}
