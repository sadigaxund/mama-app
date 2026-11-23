package com.mama.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.mama.app.R;
import com.mama.app.model.Kind;
import com.mama.app.model.Order;
import com.mama.app.model.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws order cards. Rows arrive fully loaded, so getView never touches the database. */
public class OrderCardAdapter extends BaseAdapter {
    /** One card's data. */
    public static final class Row {
        public final Order order;
        public final String garmentName;
        public final long balanceMinor;
        public final Bitmap photo;
        public final LocalDateTime nextAppointment;

        public Row(Order order, String garmentName, long balanceMinor, Bitmap photo,
                   LocalDateTime nextAppointment) {
            this.order = order;
            this.garmentName = garmentName;
            this.balanceMinor = balanceMinor;
            this.photo = photo;
            this.nextAppointment = nextAppointment;
        }
    }

    /** Called when a card's arrow button is tapped. */
    public interface ArrowListener {
        void onArrow(Order order);
    }

    /** Called when a card is long-pressed. */
    public interface LongPressListener {
        void onLongPress(Order order);
    }

    /** Called when a card is tapped. */
    public interface ClickListener {
        void onClick(Order order);
    }

    private static final int PLACEHOLDER_ALPHA = 0x33;

    private final Context context;
    private final LayoutInflater inflater;
    private final ArrowListener arrowListener;
    private final LongPressListener longPressListener;
    private ClickListener clickListener;
    private List<Row> rows = new ArrayList<>();
    private LocalDate today = LocalDate.now();

    public OrderCardAdapter(Context context, ArrowListener arrowListener,
                            LongPressListener longPressListener) {
        this.context = context;
        this.longPressListener = longPressListener;
        this.inflater = LayoutInflater.from(context);
        this.arrowListener = arrowListener;
    }

    public void setClickListener(ClickListener listener) {
        this.clickListener = listener;
    }

    public void setRows(List<Row> rows, LocalDate today) {
        this.rows = rows;
        this.today = today;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return rows.size(); }
    @Override public Row getItem(int position) { return rows.get(position); }
    @Override public long getItemId(int position) { return rows.get(position).order.id; }
    @Override public boolean isEnabled(int position) { return false; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView != null ? convertView : inflater.inflate(R.layout.item_order_card, parent, false);
        Row row = rows.get(position);
        Order o = row.order;
        Locale locale = Format.locale(context);

        int stripe = context.getColor(o.kind == Kind.TAILORING
                ? R.color.kind_tailoring_stripe : R.color.kind_repair_stripe);
        int bg = context.getColor(o.kind == Kind.TAILORING
                ? R.color.kind_tailoring_bg : R.color.kind_repair_bg);
        v.findViewById(R.id.card).setBackground(cardBackground(context, bg, stripe));

        FrameSlot.bind(v, row, stripe, context);

        ((TextView) v.findViewById(R.id.number)).setText(context.getString(R.string.order_number, o.id));
        ((TextView) v.findViewById(R.id.name)).setText(o.customerName);

        TextView line2 = v.findViewById(R.id.line2);
        View chip = v.findViewById(R.id.overdue_chip);
        boolean overdue = o.stage != Stage.DONE && o.deadline.isBefore(today);
        if (overdue) {
            line2.setText(context.getString(R.string.card_line2_overdue, row.garmentName));
            chip.setBackground(chipBackground(context));
            chip.setVisibility(View.VISIBLE);
        } else {
            line2.setText(context.getString(R.string.card_line2_due, row.garmentName,
                    Format.date(o.deadline, today)));
            chip.setVisibility(View.GONE);
        }

        ((TextView) v.findViewById(R.id.balance)).setText(
                context.getString(R.string.card_balance, Format.money(context, row.balanceMinor)));

        TextView appt = v.findViewById(R.id.appointment);
        if (row.nextAppointment != null) {
            appt.setText(context.getString(R.string.card_appointment,
                    Format.weekday(row.nextAppointment.getDayOfWeek(), locale),
                    Format.time(row.nextAppointment.toLocalTime())));
            appt.setVisibility(View.VISIBLE);
        } else {
            // Invisible, not gone: every card keeps the same height.
            appt.setText(" ");
            appt.setVisibility(View.INVISIBLE);
        }

        // On the card, not the list: isEnabled is false, so the list never reports item long
        // presses. The arrow button is clickable and not long-clickable, so it keeps its touches.
        v.findViewById(R.id.card).setOnClickListener(c -> {
            if (clickListener != null) {
                clickListener.onClick(o);
            }
        });
        v.findViewById(R.id.card).setOnLongClickListener(c -> {
            longPressListener.onLongPress(o);
            return true;
        });

        ImageButton arrow = v.findViewById(R.id.arrow);
        if (o.stage.next() != null) {
            arrow.setVisibility(View.VISIBLE);
            arrow.setOnClickListener(b -> arrowListener.onArrow(o));
        } else {
            arrow.setVisibility(View.GONE);
            arrow.setOnClickListener(null);
        }
        return v;
    }

    /** Tinted rounded card with a stripe on the left edge. */
    public static LayerDrawable cardBackground(Context context, int bg, int stripe) {
        float r = context.getResources().getDimension(R.dimen.card_radius);
        GradientDrawable stripeShape = new GradientDrawable();
        stripeShape.setColor(stripe);
        stripeShape.setCornerRadius(r);
        GradientDrawable body = new GradientDrawable();
        body.setColor(bg);
        body.setCornerRadii(new float[]{0, 0, r, r, r, r, 0, 0});
        LayerDrawable layers = new LayerDrawable(new GradientDrawable[]{stripeShape, body});
        layers.setLayerInset(1, context.getResources().getDimensionPixelSize(R.dimen.card_stripe_width), 0, 0, 0);
        return layers;
    }

    public static GradientDrawable chipBackground(Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(context.getColor(R.color.overdue_bg));
        d.setCornerRadius(context.getResources().getDimension(R.dimen.chip_radius));
        return d;
    }

    /** The fixed photo slot. It is always present so text starts at the same x on every card. */
    private static final class FrameSlot {
        static void bind(View v, Row row, int stripe, Context context) {
            View slot = v.findViewById(R.id.photo_slot);
            ImageView photo = v.findViewById(R.id.photo);
            TextView letter = v.findViewById(R.id.placeholder_letter);
            float radius = context.getResources().getDimension(R.dimen.photo_slot_radius);
            GradientDrawable bgShape = new GradientDrawable();
            bgShape.setCornerRadius(radius);
            slot.setClipToOutline(true);
            slot.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            if (row.photo != null) {
                bgShape.setColor(Color.TRANSPARENT);
                slot.setBackground(bgShape);
                photo.setImageBitmap(row.photo);
                photo.setVisibility(View.VISIBLE);
                letter.setVisibility(View.GONE);
            } else {
                bgShape.setColor(Color.argb(PLACEHOLDER_ALPHA, Color.red(stripe), Color.green(stripe), Color.blue(stripe)));
                slot.setBackground(bgShape);
                photo.setImageDrawable(null);
                photo.setVisibility(View.GONE);
                letter.setVisibility(View.VISIBLE);
                letter.setTextColor(stripe);
                String name = row.garmentName;
                letter.setText(name == null || name.isEmpty()
                        ? context.getString(R.string.placeholder_letter)
                        : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Format.locale(context)));
            }
        }
    }
}
