package com.mama.app;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.EditText;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.mama.app.data.AppointmentRepository;
import com.mama.app.data.DbHelper;
import com.mama.app.data.GarmentTypeRepository;
import com.mama.app.data.LedgerRepository;
import com.mama.app.data.OrderRepository;
import com.mama.app.data.PhotoRepository;
import com.mama.app.debug.DemoData;
import com.mama.app.model.Appointment;
import com.mama.app.model.GarmentType;
import com.mama.app.model.Kind;
import com.mama.app.model.Order;
import com.mama.app.model.OrderFilter;
import com.mama.app.model.OrderPhoto;
import com.mama.app.model.Stage;
import com.mama.app.photo.PhotoStore;
import com.mama.app.ui.AppointmentFlow;
import com.mama.app.ui.BaseActivity;
import com.mama.app.ui.Format;
import com.mama.app.ui.OrderCardAdapter;
import com.mama.app.ui.Prefs;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The board: one tab per stage and the selected stage's orders. */
public class MainActivity extends BaseActivity {
    private static final String TAG = "MamaApp";
    private static final String STATE_STAGE = "stage";
    private static final int REQUEST_FORM = 1;
    private static final String EXTRA_SEED_DEMO = "seed_demo";

    private DbHelper db;
    private OrderRepository orders;
    private GarmentTypeRepository garmentTypes;
    private LedgerRepository ledger;
    private AppointmentRepository appointments;
    private PhotoRepository photos;
    private PhotoStore photoStore;

    private final Stage[] stages = Stage.values();
    private final List<View> tabViews = new ArrayList<>();
    private HorizontalScrollView tabScroll;
    private ListView list;
    private View emptyText;
    private OrderCardAdapter adapter;
    private GestureDetector swipeDetector;
    private boolean trackingSwipe;
    private int selected = 0;

    private static final long SEARCH_DEBOUNCE_MILLIS = 250;
    private EditText searchField;
    private View searchClear;
    private View tabRule;
    private String searchQuery = "";
    private OrderFilter filter = OrderFilter.NONE;
    private final Runnable applySearch = this::applySearchText;
    private View chipAll;
    private View chipTailoring;
    private View chipRepair;
    private View chipType;
    private View chipReset;
    private List<GarmentType> typeList = new ArrayList<>();

    private static final long UNDO_MILLIS = 4000;
    private static final long UNDO_VISIT_MILLIS = 6000;
    private AppointmentFlow appointmentFlow;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideUndo = this::hideUndoBar;
    private View undoBar;
    private View fab;
    private long undoOrderId;
    private Stage undoStage;
    private long undoChangedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DbHelper(this);
        orders = new OrderRepository(db);
        garmentTypes = new GarmentTypeRepository(db);
        ledger = new LedgerRepository(db);
        appointments = new AppointmentRepository(db);
        photos = new PhotoRepository(db);
        photoStore = new PhotoStore(this);

        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debuggable && getIntent().getBooleanExtra(EXTRA_SEED_DEMO, false)) {
            if (DemoData.seedIfEmpty(this, db, System.currentTimeMillis())) {
                Log.d(TAG, "demo data seeded");
            }
        }

        if (savedInstanceState != null) {
            selected = savedInstanceState.getInt(STATE_STAGE, 0);
        }

        filter = Prefs.filter(this);
        tabScroll = findViewById(R.id.tab_scroll);
        tabRule = findViewById(R.id.tab_rule);
        list = findViewById(R.id.order_list);
        emptyText = findViewById(R.id.empty_text);
        adapter = new OrderCardAdapter(this, order -> moveForward(order), this::showMoveMenu);
        adapter.setClickListener(order ->
                startActivity(OrderDetailActivity.intent(this, order.id)));
        list.setAdapter(adapter);

        findViewById(R.id.btn_menu).setOnClickListener(this::showMenu);
        buildTabs();
        buildFilterChips();
        buildSearch();
        fab = findViewById(R.id.fab);
        undoBar = findViewById(R.id.undo_bar);
        findViewById(R.id.undo_action).setOnClickListener(v -> undoMove());
        appointmentFlow = new AppointmentFlow(this, appointments, () -> refresh(false));
        findViewById(R.id.visit_action).setOnClickListener(v -> {
            long id = undoOrderId;
            hideUndoBar();
            appointmentFlow.add(id);
        });
        fab.setOnClickListener(v ->
                startActivityForResult(OrderFormActivity.newOrderIntent(this, stages[selected]), REQUEST_FORM));

        final int minDistance = getResources().getDimensionPixelSize(R.dimen.swipe_min_distance);
        swipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null || isSearching()) {
                    return false;
                }
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(vx) < 2 * Math.abs(vy) || Math.abs(dx) < Math.max(minDistance, 2 * Math.abs(dy))) {
                    return false;
                }
                // A swipe to the left shows the next stage.
                selectStage(selected + (dx < 0 ? 1 : -1));
                return true;
            }
        });
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, R.string.menu_settings, 0, R.string.menu_settings);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.string.menu_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
        menu.show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STAGE, selected);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FORM && resultCode == RESULT_OK && data != null) {
            String name = data.getStringExtra(OrderFormActivity.EXTRA_STAGE);
            for (int i = 0; i < stages.length; i++) {
                if (stages[i].name().equals(name)) {
                    // onResume runs next and refreshes the board on this tab.
                    selected = i;
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(true);
    }

    @Override
    public void onBackPressed() {
        if (isSearching()) {
            searchField.setText("");
            applySearchText();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hideUndo);
        handler.removeCallbacks(applySearch);
        db.close();
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int[] loc = new int[2];
            findViewById(R.id.content).getLocationOnScreen(loc);
            trackingSwipe = ev.getRawY() >= loc[1];
        }
        if (trackingSwipe) {
            swipeDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isSearching() {
        return !searchQuery.isEmpty();
    }

    private void buildSearch() {
        searchField = findViewById(R.id.search_field);
        searchClear = findViewById(R.id.search_clear);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                searchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                handler.removeCallbacks(applySearch);
                // Clearing the field leaves search mode at once. Typing waits for the debounce.
                if (s.toString().trim().isEmpty()) {
                    applySearchText();
                } else {
                    handler.postDelayed(applySearch, SEARCH_DEBOUNCE_MILLIS);
                }
            }
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(applySearch);
                applySearchText();
                hideKeyboard();
                return true;
            }
            return false;
        });
        searchClear.setOnClickListener(v -> searchField.setText(""));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
                if (state == SCROLL_STATE_TOUCH_SCROLL) {
                    hideKeyboard();
                }
            }

            @Override
            public void onScroll(AbsListView v, int first, int visible, int total) {}
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
    }

    private void applySearchText() {
        String q = searchField.getText().toString().trim();
        if (q.equals(searchQuery)) {
            return;
        }
        searchQuery = q;
        refresh(true);
    }

    private void buildFilterChips() {
        LinearLayout strip = findViewById(R.id.filter_strip);
        chipAll = addChip(strip, getString(R.string.filter_all), v -> setFilter(filter.withKind(null)));
        chipTailoring = addChip(strip, getString(R.string.kind_tailoring),
                v -> setFilter(filter.withKind(Kind.TAILORING)));
        chipRepair = addChip(strip, getString(R.string.kind_repair),
                v -> setFilter(filter.withKind(Kind.REPAIR)));
        chipType = addChip(strip, getString(R.string.filter_type), v -> showTypeDialog());
        chipReset = addChip(strip, getString(R.string.filter_reset), v -> setFilter(OrderFilter.NONE));
    }

    private View addChip(LinearLayout strip, String label, View.OnClickListener click) {
        View chip = LayoutInflater.from(this).inflate(R.layout.item_filter_chip, strip, false);
        ((TextView) chip.findViewById(R.id.chip_text)).setText(label);
        chip.setOnClickListener(click);
        strip.addView(chip);
        return chip;
    }

    private void setFilter(OrderFilter f) {
        filter = f;
        Prefs.saveFilter(this, f);
        refresh(true);
    }

    private void showTypeDialog() {
        typeList = garmentTypes.listAll();
        String[] labels = new String[typeList.size() + 1];
        labels[0] = getString(R.string.filter_type_all);
        int checked = 0;
        for (int i = 0; i < typeList.size(); i++) {
            labels[i + 1] = typeList.get(i).name;
            if (typeList.get(i).id == filter.garmentTypeId) {
                checked = i + 1;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.filter_type_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    setFilter(filter.withGarmentType(which == 0 ? 0 : typeList.get(which - 1).id));
                    d.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Styles one chip: selected chips take the given colors, others a white outline. */
    private void styleChip(View chip, boolean on, int fill, int stroke, int text) {
        TextView t = chip.findViewById(R.id.chip_text);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(getResources().getDimension(R.dimen.filter_chip_radius));
        d.setColor(on ? fill : getColor(R.color.field_bg));
        d.setStroke(getResources().getDimensionPixelSize(R.dimen.form_stroke),
                on ? stroke : getColor(R.color.field_stroke));
        t.setBackground(d);
        t.setTextColor(on ? text : getColor(R.color.text_primary));
        t.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        chip.setSelected(on);
    }

    private void updateFilterChips() {
        int accent = getColor(R.color.accent);
        int accentFill = getColor(R.color.chip_selected_bg);
        styleChip(chipAll, filter.kind == null, accentFill, accent, accent);
        styleChip(chipTailoring, filter.kind == Kind.TAILORING, getColor(R.color.kind_tailoring_bg),
                getColor(R.color.kind_tailoring_stripe), getColor(R.color.kind_tailoring_stripe));
        styleChip(chipRepair, filter.kind == Kind.REPAIR, getColor(R.color.kind_repair_bg),
                getColor(R.color.kind_repair_stripe), getColor(R.color.kind_repair_stripe));
        TextView type = chipType.findViewById(R.id.chip_text);
        String name = null;
        if (filter.garmentTypeId != 0) {
            for (GarmentType t : garmentTypes.listAll()) {
                if (t.id == filter.garmentTypeId) {
                    name = t.name;
                }
            }
        }
        type.setText(name == null ? getString(R.string.filter_type) : name + " ▾");
        styleChip(chipType, filter.garmentTypeId != 0, accentFill, accent, accent);
        chipReset.setVisibility(filter.isActive() ? View.VISIBLE : View.GONE);
        ((TextView) chipReset.findViewById(R.id.chip_text)).setTextColor(accent);
        ((TextView) chipReset.findViewById(R.id.chip_text)).setBackground(null);
    }

    private void moveForward(Order order) {
        Stage next = order.stage.next();
        if (next != null) {
            move(order, next);
        }
    }

    /** Long-press menu: forward moves first, the back move last (Stage.allowedMoves order). */
    private void showMoveMenu(Order order) {
        final List<Stage> moves = order.stage.allowedMoves();
        if (moves.isEmpty()) {
            return;
        }
        final Stage back = order.stage.previous();
        String[] labels = new String[moves.size()];
        for (int i = 0; i < labels.length; i++) {
            Stage s = moves.get(i);
            labels[i] = getString(s == back ? R.string.move_back : R.string.move_forward,
                    getString(stageLabel(s)));
        }
        new AlertDialog.Builder(this)
                .setTitle(order.customerName)
                .setItems(labels, (d, which) -> move(order, moves.get(which)))
                .show();
    }

    private void move(Order order, Stage target) {
        try {
            orders.moveTo(order.id, target, System.currentTimeMillis());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // The card was stale (moved or deleted elsewhere). Reload and show nothing to undo.
            Log.w(TAG, "move failed for #" + order.id + ": " + e.getMessage());
            refresh(false);
            return;
        }
        Log.d(TAG, "moved #" + order.id + " " + order.stage + " -> " + target);
        undoOrderId = order.id;
        undoStage = order.stage;
        undoChangedAt = order.stageChangedAt;
        showUndoBar(getString(R.string.undo_moved, order.id, getString(stageLabel(target))),
                target.offersVisit());
        refresh(false);
    }

    private void undoMove() {
        handler.removeCallbacks(hideUndo);
        if (undoStage != null) {
            try {
                orders.restoreStage(undoOrderId, undoStage, undoChangedAt);
                Log.d(TAG, "undo #" + undoOrderId + " -> " + undoStage);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "undo failed: " + e.getMessage());
            }
            undoStage = null;
        }
        hideUndoBar();
        refresh(false);
    }

    private void showUndoBar(String text, boolean offerVisit) {
        ((TextView) findViewById(R.id.undo_text)).setText(text);
        findViewById(R.id.visit_action).setVisibility(offerVisit ? View.VISIBLE : View.GONE);
        undoBar.setVisibility(View.VISIBLE);
        // Lift the FAB clear of the bar: bar height plus its margin twice.
        float margin = getResources().getDimension(R.dimen.undo_bar_margin);
        fab.setTranslationY(-(getResources().getDimension(R.dimen.undo_bar_height) + margin));
        handler.removeCallbacks(hideUndo);
        handler.postDelayed(hideUndo, offerVisit ? UNDO_VISIT_MILLIS : UNDO_MILLIS);
    }

    private void hideUndoBar() {
        handler.removeCallbacks(hideUndo);
        undoBar.setVisibility(View.GONE);
        fab.setTranslationY(0);
        undoStage = null;
    }

    private void buildTabs() {
        LinearLayout strip = findViewById(R.id.tab_strip);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < stages.length; i++) {
            final int index = i;
            View tab = inflater.inflate(R.layout.item_stage_tab, strip, false);
            tab.setOnClickListener(v -> selectStage(index));
            strip.addView(tab);
            tabViews.add(tab);
        }
    }

    private void selectStage(int index) {
        if (index < 0 || index >= stages.length || index == selected) {
            return;
        }
        selected = index;
        refresh(true);
    }

    static int stageLabel(Stage s) {
        switch (s) {
            case ACCEPTED: return R.string.stage_accepted;
            case CUTTING: return R.string.stage_cutting;
            case FITTING_1: return R.string.stage_fitting_1;
            case FITTING_2: return R.string.stage_fitting_2;
            case ALTERATIONS: return R.string.stage_alterations;
            default: return R.string.stage_done;
        }
    }

    /** Reloads counts and the selected stage's cards. */
    private void refresh(boolean resetScroll) {
        updateFilterChips();
        boolean searching = isSearching();
        tabScroll.setVisibility(searching ? View.GONE : View.VISIBLE);
        tabRule.setVisibility(searching ? View.GONE : View.VISIBLE);
        Map<Stage, Integer> counts = orders.countsByStage(filter);
        for (int i = 0; i < stages.length; i++) {
            View tab = tabViews.get(i);
            boolean on = i == selected;
            TextView text = tab.findViewById(R.id.tab_text);
            int count = counts.get(stages[i]);
            text.setText(stageLabel(stages[i]));
            TextView badge = tab.findViewById(R.id.tab_badge);
            badge.setText(Integer.toString(count));
            badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            badge.getBackground().mutate().setTint(getColor(on ? R.color.accent : R.color.tab_badge_idle_bg));
            badge.setTextColor(getColor(on ? R.color.on_accent : R.color.accent));
            text.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tab.findViewById(R.id.tab_underline).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            tab.setSelected(on);
        }
        final View selectedTab = tabViews.get(selected);
        tabScroll.post(() -> {
            int target = selectedTab.getLeft() - (tabScroll.getWidth() - selectedTab.getWidth()) / 2;
            tabScroll.smoothScrollTo(Math.max(0, target), 0);
        });

        List<OrderCardAdapter.Row> rows = searching
                ? loadRows(orders.search(searchQuery, filter), true)
                : loadRows(orders.listByStage(stages[selected], filter), false);
        adapter.setRows(rows, LocalDate.now());
        if (resetScroll && !rows.isEmpty()) {
            list.setSelection(0);
        }
        ((TextView) emptyText).setText(searching ? R.string.search_empty : R.string.board_empty);
        emptyText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private List<OrderCardAdapter.Row> loadRows(List<Order> found, boolean showStage) {
        Map<Long, String> typeNames = new HashMap<>();
        for (GarmentType t : garmentTypes.listAll()) {
            typeNames.put(t.id, t.name);
        }
        long now = System.currentTimeMillis();
        List<OrderCardAdapter.Row> rows = new ArrayList<>();
        for (Order o : found) {
            String type = typeNames.get(o.garmentTypeId);
            Appointment next = appointments.nextUpcoming(o.id, now);
            LocalDateTime when = next == null ? null : Format.toLocal(next.startsAt);
            rows.add(new OrderCardAdapter.Row(o, type == null ? "" : type, ledger.balanceDue(o.id),
                    loadThumb(o.id), when, showStage ? getString(stageLabel(o.stage)) : null));
        }
        return rows;
    }

    /** The newest photo of the order at the slot size, from the shared thumbnail cache, or null. */
    private Bitmap loadThumb(long orderId) {
        OrderPhoto p = photos.newest(orderId);
        if (p == null) {
            return null;
        }
        return photoStore.loadThumbnail(p.fileName, getResources().getDimensionPixelSize(R.dimen.photo_slot_size));
    }
}
