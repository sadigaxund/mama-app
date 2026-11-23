package com.atelier.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.atelier.app.data.DbHelper;
import com.atelier.app.data.PhotoRepository;
import com.atelier.app.model.OrderPhoto;
import com.atelier.app.photo.PhotoStore;
import com.atelier.app.ui.BaseActivity;

import java.util.ArrayList;
import java.util.List;

/** One order's photos full screen, newest first. A horizontal swipe moves between them. */
public class PhotoViewerActivity extends BaseActivity {
    private static final String EXTRA_ORDER_ID = "order_id";
    private static final String EXTRA_INDEX = "index";
    private static final String STATE_INDEX = "index";

    public static Intent intent(Context context, long orderId, int index) {
        return new Intent(context, PhotoViewerActivity.class)
                .putExtra(EXTRA_ORDER_ID, orderId).putExtra(EXTRA_INDEX, index);
    }

    private DbHelper db;
    private PhotoRepository photos;
    private PhotoStore store;
    private long orderId;
    private List<OrderPhoto> list = new ArrayList<>();
    private int index;
    private int loadSerial;
    private int screenEdge;
    private ImageView image;
    private TextView counter;
    private GestureDetector swipes;
    private AlertDialog openDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_viewer);
        db = new DbHelper(this);
        photos = new PhotoRepository(db);
        store = new PhotoStore(this);
        orderId = getIntent().getLongExtra(EXTRA_ORDER_ID, 0);
        index = savedInstanceState != null ? savedInstanceState.getInt(STATE_INDEX)
                : getIntent().getIntExtra(EXTRA_INDEX, 0);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenEdge = Math.max(dm.widthPixels, dm.heightPixels);

        image = findViewById(R.id.image);
        counter = findViewById(R.id.counter);
        ImageButton back = findViewById(R.id.btn_back);
        ImageButton del = findViewById(R.id.btn_delete);
        // The shared icons are dark for light screens. White reads on the black bar.
        back.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        del.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        back.setOnClickListener(v -> finish());
        del.setOnClickListener(v -> confirmDelete());

        final int minDistance = getResources().getDimensionPixelSize(R.dimen.swipe_min_distance);
        swipes = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) {
                    return false;
                }
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) < minDistance || Math.abs(dx) < Math.abs(dy) * 1.5f) {
                    return false;
                }
                show(dx < 0 ? index + 1 : index - 1);
                return true;
            }
        });
        reload();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_INDEX, index);
    }

    @Override
    protected void onDestroy() {
        if (openDialog != null) {
            openDialog.dismiss();
        }
        db.close();
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        swipes.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void reload() {
        list = photos.listForOrder(orderId);
        if (list.isEmpty()) {
            finish();
            return;
        }
        show(index);
    }

    private void show(int target) {
        if (target < 0 || target >= list.size()) {
            if (target != index) {
                return;
            }
            target = Math.max(0, Math.min(target, list.size() - 1));
        }
        index = target;
        counter.setText(getString(R.string.photo_viewer_desc, index + 1, list.size()));
        final int serial = ++loadSerial;
        store.loadFitAsync(list.get(index).fileName, screenEdge, bmp -> {
            // A newer swipe or a closed screen makes this result stale.
            if (serial == loadSerial && !isDestroyed()) {
                image.setImageBitmap(bmp);
            }
        });
    }

    private void confirmDelete() {
        openDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.photo_delete_confirm)
                .setPositiveButton(R.string.action_delete, (d, w) -> deleteCurrent())
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        openDialog.setOnDismissListener(x -> openDialog = null);
        openDialog.show();
    }

    private void deleteCurrent() {
        OrderPhoto p = list.get(index);
        photos.delete(p.id);
        store.delete(p.fileName);
        reload();
    }
}
