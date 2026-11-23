package com.mama.app;

import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mama.app.data.AppointmentRepository;
import com.mama.app.data.DbHelper;
import com.mama.app.data.GarmentTypeRepository;
import com.mama.app.data.LedgerRepository;
import com.mama.app.data.OrderRepository;
import com.mama.app.data.PhotoRepository;
import com.mama.app.debug.DemoData;
import com.mama.app.model.Appointment;
import com.mama.app.model.GarmentType;
import com.mama.app.model.Order;
import com.mama.app.model.OrderPhoto;
import com.mama.app.model.Stage;
import com.mama.app.ui.BaseActivity;
import com.mama.app.ui.OrderCardAdapter;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The board: one tab per stage and the selected stage's orders. Read-only in this step. */
public class MainActivity extends BaseActivity {
    private static final String TAG = "MamaApp";
    private static final String STATE_STAGE = "stage";
    private static final String EXTRA_SEED_DEMO = "seed_demo";

    private DbHelper db;
    private OrderRepository orders;
    private GarmentTypeRepository garmentTypes;
    private LedgerRepository ledger;
    private AppointmentRepository appointments;
    private PhotoRepository photos;

    private final Stage[] stages = Stage.values();
    private final List<View> tabViews = new ArrayList<>();
    private HorizontalScrollView tabScroll;
    private ListView list;
    private View emptyText;
    private OrderCardAdapter adapter;
    private GestureDetector swipeDetector;
    private boolean trackingSwipe;
    private int selected = 0;

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

        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debuggable && getIntent().getBooleanExtra(EXTRA_SEED_DEMO, false)) {
            if (DemoData.seedIfEmpty(db, System.currentTimeMillis())) {
                Log.d(TAG, "demo data seeded");
            }
        }

        if (savedInstanceState != null) {
            selected = savedInstanceState.getInt(STATE_STAGE, 0);
        }

        tabScroll = findViewById(R.id.tab_scroll);
        list = findViewById(R.id.order_list);
        emptyText = findViewById(R.id.empty_text);
        adapter = new OrderCardAdapter(this, order -> comingSoon());
        list.setAdapter(adapter);

        buildTabs();
        findViewById(R.id.fab).setOnClickListener(v -> comingSoon());

        final int minDistance = getResources().getDimensionPixelSize(R.dimen.swipe_min_distance);
        swipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) {
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STAGE, selected);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
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

    private void comingSoon() {
        Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
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
        refresh();
    }

    private static int stageLabel(Stage s) {
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
    private void refresh() {
        Map<Stage, Integer> counts = orders.countsByStage();
        for (int i = 0; i < stages.length; i++) {
            View tab = tabViews.get(i);
            boolean on = i == selected;
            TextView text = tab.findViewById(R.id.tab_text);
            String count = Integer.toString(counts.get(stages[i]));
            String label = getString(R.string.tab_label, getString(stageLabel(stages[i])), counts.get(stages[i]));
            SpannableString styled = new SpannableString(label);
            int countStart = label.length() - count.length();
            // The count is muted so "Примерка 1 2" reads as stage "Примерка 1" with 2 orders.
            styled.setSpan(new ForegroundColorSpan(getColor(R.color.text_muted)), countStart,
                    label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setText(styled);
            text.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tab.findViewById(R.id.tab_underline).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            tab.setSelected(on);
        }
        final View selectedTab = tabViews.get(selected);
        tabScroll.post(() -> {
            int target = selectedTab.getLeft() - (tabScroll.getWidth() - selectedTab.getWidth()) / 2;
            tabScroll.smoothScrollTo(Math.max(0, target), 0);
        });

        List<OrderCardAdapter.Row> rows = loadRows(stages[selected]);
        adapter.setRows(rows, LocalDate.now());
        list.setSelection(0);
        emptyText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private List<OrderCardAdapter.Row> loadRows(Stage stage) {
        Map<Long, String> typeNames = new HashMap<>();
        for (GarmentType t : garmentTypes.listAll()) {
            typeNames.put(t.id, t.name);
        }
        long now = System.currentTimeMillis();
        List<OrderCardAdapter.Row> rows = new ArrayList<>();
        for (Order o : orders.listByStage(stage)) {
            String type = typeNames.get(o.garmentTypeId);
            Appointment next = appointments.nextUpcoming(o.id, now);
            LocalDateTime when = next == null ? null
                    : LocalDateTime.ofInstant(Instant.ofEpochMilli(next.startsAt), ZoneId.systemDefault());
            rows.add(new OrderCardAdapter.Row(o, type == null ? "" : type, ledger.balanceDue(o.id),
                    loadThumb(o.id), when));
        }
        return rows;
    }

    /** The newest photo of the order, downsampled to the slot size, or null. */
    private Bitmap loadThumb(long orderId) {
        OrderPhoto p = photos.newest(orderId);
        if (p == null) {
            return null;
        }
        File f = new File(new File(getFilesDir(), "photos"), p.fileName);
        int size = getResources().getDimensionPixelSize(R.dimen.photo_slot_size);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "photo unreadable: " + f.getName());
            return null;
        }
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), opts);
    }
}
