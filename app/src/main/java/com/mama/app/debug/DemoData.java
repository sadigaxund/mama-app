package com.mama.app.debug;

import com.mama.app.data.AppointmentRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Demo orders for visual checks. Only MainActivity calls it, and only in a debuggable build. */
public final class DemoData {
    private DemoData() {}

    /** Inserts about ten orders when the orders table is empty. Returns true when it inserted. */
    public static boolean seedIfEmpty(DbHelper helper, long nowMillis) {
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
                    prices[i], today.plusDays(dayOffsets[i]), null, stages[i],
                    nowMillis, nowMillis, ReminderMode.DEFAULT);
            long id = orders.insert(o, deposits[i], nowMillis + i);
            if (i == 3) {
                ledger.add(id, LedgerType.EXPENSE, 7500, "Ткань", nowMillis);
            }
            if (i == 6) {
                ledger.add(id, LedgerType.EXPENSE, 1200, "Пуговицы", nowMillis);
            }
            if (i == 4) {
                // An upcoming visit: tomorrow at 15:00.
                long at = LocalDateTime.of(today.plusDays(1), java.time.LocalTime.of(15, 0))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                appts.add(id, at, "Примерка");
            }
            if (i == 6) {
                long at = LocalDateTime.of(today.plusDays(2), java.time.LocalTime.of(11, 30))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                appts.add(id, at, null);
            }
        }
        return true;
    }
}
