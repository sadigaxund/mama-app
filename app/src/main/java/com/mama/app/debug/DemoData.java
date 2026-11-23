package com.mama.app.debug;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Log;

import com.mama.app.data.AppointmentRepository;
import com.mama.app.data.PhotoRepository;
import com.mama.app.photo.PhotoStore;
import com.mama.app.data.DbHelper;
import com.mama.app.data.GarmentTypeRepository;
import com.mama.app.data.LedgerRepository;
import com.mama.app.data.OrderRepository;
import com.mama.app.model.GarmentType;
import com.mama.app.model.Kind;
import com.mama.app.model.LedgerType;
import com.mama.app.model.Order;
import com.mama.app.model.ReminderMode;
import com.mama.app.model.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Demo orders for visual checks. Only MainActivity calls it, and only in a debuggable build. */
public final class DemoData {
    private static final String DEMO_NOTES = "Рост 172, грудь 96, талия 78, бёдра 102.\n"
            + "Длина рукава 61 см, плечо 12 см. Подкладка шёлковая, цвет графит. "
            + "Клиентка просила чуть приталить и убрать лишнюю ширину в боках.\n"
            + "Примерка в четверг после обеда.";

    private DemoData() {}

    /** Inserts about ten orders when the orders table is empty. Returns true when it inserted. */
    public static boolean seedIfEmpty(Context context, DbHelper helper, long nowMillis) {
        OrderRepository orders = new OrderRepository(helper);
        for (int count : orders.countsByStage().values()) {
            if (count > 0) {
                return false;
            }
        }
        GarmentTypeRepository types = new GarmentTypeRepository(helper);
        if (types.listAll().isEmpty()) {
            for (String n : new String[]{"Костюм", "Платье", "Брюки", "Юбка", "Пиджак", "Рубашка", "Пальто"}) {
                types.add(n);
            }
        }
        List<GarmentType> all = types.listAll();
        LedgerRepository ledger = new LedgerRepository(helper);
        AppointmentRepository appts = new AppointmentRepository(helper);
        PhotoRepository photos = new PhotoRepository(helper);
        LocalDate today = LocalDate.now();

        String[][] names = {
            {"Иванова Анна"}, {"Гасанов Эльчин"}, {"Петрова Мария"},
            {"Мамедова Лейла Бахтияровна-Оглы Александрова-Кузнецова"}, {"Алиев Фуад"},
            {"Ли Ю"}, {"Смирнова Ольга"}, {"Кязимова Земфира"}, {"Сидоров Игорь"}, {"Орлова Вера"},
        };
        Stage[] stages = {Stage.ACCEPTED, Stage.ACCEPTED, Stage.CUTTING, Stage.CUTTING, Stage.FITTING_1,
            Stage.FITTING_1, Stage.FITTING_2, Stage.ALTERATIONS, Stage.DONE, Stage.DONE};
        int[] dayOffsets = {10, 3, 6, 1, -4, 12, 2, 5, -2, -9};
        long[] prices = {25000, 8000, 40000, 120050, 15000, 6000, 32000, 9000, 18000, 50000};
        long[] deposits = {10000, 0, 20000, 50000, 15000, 0, 32000, 4000, 18000, 50000};

        for (int i = 0; i < names.length; i++) {
            Kind kind = i % 3 == 1 || i == 5 || i == 7 ? Kind.REPAIR : Kind.TAILORING;
            GarmentType type = all.get(i % all.size());
            Order o = new Order(0, names[i][0], i % 2 == 0 ? "+994501234567" : null, kind, type.id,
                    prices[i], today.plusDays(dayOffsets[i]), i == 3 ? DEMO_NOTES : null, stages[i],
                    nowMillis, nowMillis, ReminderMode.DEFAULT);
            long id = orders.insert(o, deposits[i], nowMillis + i);
            if (i == 0) {
                addDemoPhoto(context, photos, id, Color.rgb(198, 40, 40), nowMillis);
                addDemoPhoto(context, photos, id, Color.rgb(25, 118, 210), nowMillis + 1);
                addDemoPhoto(context, photos, id, Color.rgb(46, 125, 50), nowMillis + 2);
            }
            if (i == 2) {
                addDemoPhoto(context, photos, id, Color.rgb(106, 27, 154), nowMillis);
            }
            if (i == 3) {
                ledger.add(id, LedgerType.EXPENSE, 7500, "Ткань", nowMillis);
                // A long ledger and long notes, so the share PDF runs to a second page.
                for (int k = 0; k < 40; k++) {
                    ledger.add(id, k % 3 == 0 ? LedgerType.EXPENSE : LedgerType.PAYMENT, 500 + k * 10,
                            "Запись " + (k + 1), nowMillis + 10 + k);
                }
            }
            if (i == 6) {
                ledger.add(id, LedgerType.EXPENSE, 1200, "Пуговицы", nowMillis);
            }
            if (i == 0) {
                // A visit today at 15:00, for the morning notification.
                long at = LocalDateTime.of(today, java.time.LocalTime.of(15, 0))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                appts.add(id, at, "Подогнать по росту");
            }
            if (i == 4) {
                // An upcoming visit: tomorrow at 15:00.
                long at = LocalDateTime.of(today.plusDays(1), java.time.LocalTime.of(15, 0))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                appts.add(id, at, "Примерка");
                // Past visits, to show the collapsed "past" list (four, so one stays hidden).
                int[] ago = {-21, -14, -8, -3};
                for (int d : ago) {
                    long past = LocalDateTime.of(today.plusDays(d), java.time.LocalTime.of(10, 0))
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    appts.add(id, past, d == -3 ? "Снять мерки" : null);
                }
            }
            if (i == 6) {
                long at = LocalDateTime.of(today.plusDays(2), java.time.LocalTime.of(11, 30))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                appts.add(id, at, null);
            }
        }
        return true;
    }

    /** Draws a 1200x900 test picture, imports it like a real photo and adds the row. */
    private static void addDemoPhoto(Context context, PhotoRepository photos, long orderId, int color,
                                     long nowMillis) {
        Bitmap b = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawColor(color);
        p.setColor(Color.argb(90, 255, 255, 255));
        c.drawCircle(300, 300, 220, p);
        p.setColor(Color.argb(90, 0, 0, 0));
        c.drawRect(600, 400, 1100, 800, p);
        p.setColor(Color.WHITE);
        p.setTextSize(120);
        c.drawText("#" + orderId, 80, 840, p);
        File tmp = new File(context.getCacheDir(), "demo-" + orderId + "-" + nowMillis + ".jpg");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            b.compress(Bitmap.CompressFormat.JPEG, 90, os);
            String name = new PhotoStore(context).importPhoto(orderId, Uri.fromFile(tmp));
            photos.add(orderId, name, nowMillis);
        } catch (IOException e) {
            Log.w("MamaApp", "demo photo failed: " + e);
        } finally {
            b.recycle();
            tmp.delete();
        }
    }
}
