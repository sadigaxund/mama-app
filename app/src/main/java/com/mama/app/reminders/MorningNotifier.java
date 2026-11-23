package com.mama.app.reminders;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.service.notification.StatusBarNotification;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.util.Log;

import com.mama.app.OrderFormActivity;
import com.mama.app.R;
import com.mama.app.data.AppointmentRepository;
import com.mama.app.data.DbHelper;
import com.mama.app.data.GarmentTypeRepository;
import com.mama.app.data.OrderRepository;
import com.mama.app.model.Appointment;
import com.mama.app.model.GarmentType;
import com.mama.app.model.Order;
import com.mama.app.model.OrderReminderInput;
import com.mama.app.model.ReminderItem;
import com.mama.app.model.ReminderMode;
import com.mama.app.model.ReminderPlanner;
import com.mama.app.model.Stage;
import com.mama.app.ui.Format;
import com.mama.app.ui.Prefs;
import com.mama.app.MainActivity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds and posts today's morning group: one summary and one notification per due order. */
public final class MorningNotifier {
    private static final String TAG = "MamaApp";

    static final String ACTION_DONE = "com.mama.app.action.ORDER_DONE";
    static final String ACTION_STOP = "com.mama.app.action.ORDER_STOP_REMINDING";
    static final String EXTRA_ORDER_ID = "order_id";

    private MorningNotifier() {}

    private static final Object LOCK = new Object();

    /** Posts unless today's morning was already posted. The alarm and the catch-up both come here, so a day posts once. */
    public static void postOnce(Context appContext) {
        synchronized (LOCK) {
            if (LocalDate.now().equals(Prefs.morningLastPosted(appContext))) {
                Log.d(TAG, "morning: already posted today");
                return;
            }
            post(appContext);
        }
    }

    /** Reads the database and posts. Runs on a background thread. Posts nothing when the day is empty. */
    public static void post(Context appContext) {
        Prefs.setMorningLastPosted(appContext, LocalDate.now());
        Context ctx = Notifications.localized(appContext);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        clearYesterday(nm);

        DbHelper db = new DbHelper(ctx);
        try {
            OrderRepository orders = new OrderRepository(db);
            GarmentTypeRepository types = new GarmentTypeRepository(db);
            AppointmentRepository appts = new AppointmentRepository(db);
            LocalDate today = LocalDate.now();

            Map<Long, Order> byId = new HashMap<>();
            List<OrderReminderInput> inputs = new ArrayList<>();
            for (Stage s : Stage.values()) {
                if (s == Stage.DONE) {
                    continue;
                }
                for (Order o : orders.listByStage(s)) {
                    byId.put(o.id, o);
                    List<Integer> custom = o.reminderMode == ReminderMode.CUSTOM
                            ? orders.getCustomDays(o.id) : new ArrayList<Integer>();
                    inputs.add(new OrderReminderInput(o.id, o.deadline, false, o.reminderMode, custom));
                }
            }
            List<ReminderItem> items = ReminderPlanner.plan(today, inputs, Prefs.reminderDays(ctx));
            List<Appointment> visits = appts.listBetween(Format.toMillis(today.atStartOfDay()),
                    Format.toMillis(today.plusDays(1).atStartOfDay()));

            List<String> visitNames = new ArrayList<>();
            for (Appointment a : visits) {
                Order o = byId.containsKey(a.orderId) ? byId.get(a.orderId) : orders.get(a.orderId);
                if (o != null) {
                    visitNames.add(Notifications.shortName(o.customerName) + " " + Format.time(Format.toLocal(a.startsAt).toLocalTime()));
                }
            }
            List<String> todayNames = new ArrayList<>();
            List<String> overdueNames = new ArrayList<>();
            List<String> soonNames = new ArrayList<>();
            for (ReminderItem item : items) {
                String name = Notifications.shortName(byId.get(item.orderId).customerName);
                switch (item.type) {
                    case OVERDUE: overdueNames.add(name); break;
                    case DUE_TODAY: todayNames.add(name); break;
                    default: soonNames.add(name); break;
                }
            }
            if (items.isEmpty() && visitNames.isEmpty()) {
                Log.d(TAG, "morning: nothing to post");
                return;
            }

            // The shade lists the newest first, so the most urgent item is posted last.
            for (int k = items.size() - 1; k >= 0; k--) {
                ReminderItem item = items.get(k);
                Order o = byId.get(item.orderId);
                GarmentType type = types.get(o.garmentTypeId);
                nm.notify(Notifications.orderId(o.id), orderNotification(ctx, o,
                        type == null ? "" : type.name, item, today));
            }
            nm.notify(Notifications.SUMMARY_ID, summary(ctx, visitNames, todayNames, overdueNames, soonNames));
            Log.d(TAG, "morning: posted " + items.size() + " orders, " + visitNames.size() + " visits");
        } finally {
            db.close();
        }
    }

    /** Morning notifications from earlier days go, so a finished order does not linger. */
    private static void clearYesterday(NotificationManager nm) {
        for (StatusBarNotification sbn : nm.getActiveNotifications()) {
            if (sbn.getId() == Notifications.SUMMARY_ID || Notifications.isOrderNotificationId(sbn.getId())) {
                nm.cancel(sbn.getId());
            }
        }
    }

    private static Notification summary(Context ctx, List<String> visits, List<String> today,
                                        List<String> overdue, List<String> soon) {
        List<String> lines = new ArrayList<>();
        addLine(ctx, lines, R.plurals.morning_visits, visits);
        addLine(ctx, lines, R.plurals.morning_today, today);
        addLine(ctx, lines, R.plurals.morning_overdue, overdue);
        addLine(ctx, lines, R.plurals.morning_soon, soon);
        Notification.InboxStyle style = new Notification.InboxStyle();
        for (String l : lines) {
            style.addLine(l);
        }
        return base(ctx)
                .setContentTitle(ctx.getString(R.string.notif_today_title))
                .setContentText(lines.get(0))
                .setStyle(style)
                .setGroupSummary(true)
                .setContentIntent(Notifications.openBoard(ctx))
                .build();
    }

    private static void addLine(Context ctx, List<String> lines, int plural, List<String> names) {
        if (!names.isEmpty()) {
            lines.add(ctx.getResources().getQuantityString(plural, names.size(), names.size(),
                    String.join(", ", names)));
        }
    }

    private static Notification.Builder base(Context ctx) {
        return new Notification.Builder(ctx, Notifications.CHANNEL_MORNING)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ctx.getColor(R.color.accent))
                .setGroup(Notifications.GROUP_MORNING)
                .setShowWhen(false)
                .setAutoCancel(true);
    }

    private static Notification orderNotification(Context ctx, Order o, String garment,
                                                  ReminderItem item, LocalDate today) {
        String title;
        switch (item.type) {
            case OVERDUE:
                title = ctx.getString(R.string.notif_overdue, Notifications.shortName(o.customerName), garment,
                        ctx.getString(R.string.stage_days, item.days));
                break;
            case DUE_TODAY:
                title = ctx.getString(R.string.notif_due_today, Notifications.shortName(o.customerName), garment);
                break;
            default:
                title = ctx.getString(R.string.notif_due_in, Notifications.shortName(o.customerName), garment,
                        ctx.getString(R.string.stage_days, item.days));
                break;
        }
        String text = ctx.getString(R.string.notif_order_text,
                ctx.getString(MainActivity.stageLabel(o.stage)), Format.date(o.deadline, today));
        return base(ctx)
                .setContentTitle(title)
                .setContentText(text)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                .setContentIntent(Notifications.openOrder(ctx, o.id))
                .addAction(action(ctx, R.string.stage_done, receiverIntent(ctx, ACTION_DONE, o.id, 1)))
                .addAction(action(ctx, R.string.notif_move_deadline, Notifications.stack(ctx, o.id, 2,
                        OrderFormActivity.editIntent(ctx, o.id).putExtra(OrderFormActivity.EXTRA_PICK_DEADLINE, true))))
                .addAction(action(ctx, R.string.notif_stop, receiverIntent(ctx, ACTION_STOP, o.id, 3)))
                .build();
    }

    private static Notification.Action action(Context ctx, int label, PendingIntent pi) {
        return new Notification.Action.Builder(
                Icon.createWithResource(ctx, R.drawable.ic_notification), ctx.getString(label), pi).build();
    }

    /** Broadcast to ReminderActionReceiver. The data URI and request code keep each action's PendingIntent distinct. */
    private static PendingIntent receiverIntent(Context ctx, String action, long orderId, int slot) {
        Intent i = new Intent(ctx, ReminderActionReceiver.class).setAction(action)
                .setData(Uri.parse("mama://order/" + orderId + "/" + slot))
                .putExtra(EXTRA_ORDER_ID, orderId);
        return PendingIntent.getBroadcast(ctx, (int) (4 * orderId + slot), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
