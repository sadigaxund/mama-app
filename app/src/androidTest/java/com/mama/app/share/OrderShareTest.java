package com.mama.app.share;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.test.AndroidTestCase;

import com.mama.app.data.DbHelper;
import com.mama.app.data.GarmentTypeRepository;
import com.mama.app.data.LedgerRepository;
import com.mama.app.data.OrderRepository;
import com.mama.app.model.Kind;
import com.mama.app.model.LedgerType;
import com.mama.app.model.Order;
import com.mama.app.model.ReminderMode;
import com.mama.app.model.Stage;
import com.mama.app.ui.LocaleSupport;

import java.io.File;
import java.time.LocalDate;

/** Renders a real order to a bitmap, a PNG and a PDF and checks the output. */
public class OrderShareTest extends AndroidTestCase {
    private DbHelper db;
    private OrderRepository orders;
    private LedgerRepository ledger;
    private long typeId;
    private OrderSharer sharer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        db = new DbHelper(getContext(), null);
        orders = new OrderRepository(db);
        ledger = new LedgerRepository(db);
        typeId = new GarmentTypeRepository(db).add("Dress");
        Context localized = LocaleSupport.wrap(getContext());
        sharer = new OrderSharer(localized);
    }

    @Override
    protected void tearDown() throws Exception {
        db.close();
        File dir = new File(getContext().getCacheDir(), "shared");
        new File(dir, "order-1.png").delete();
        new File(dir, "order-1.pdf").delete();
        super.tearDown();
    }

    private long order(int ledgerRows, String notes) {
        Order o = new Order(0, "Hasanova Leyla", "+994501112233", Kind.TAILORING, typeId, 25000,
                LocalDate.now().plusDays(5), notes, Stage.FITTING_1, 0, 0, ReminderMode.DEFAULT);
        long id = orders.insert(o, 0, 1000L);
        for (int i = 0; i < ledgerRows; i++) {
            ledger.add(id, i % 2 == 0 ? LedgerType.PAYMENT : LedgerType.EXPENSE, 1000 + i,
                    "Entry " + i, 2000L + i);
        }
        return id;
    }

    private int pdfPages(File f) throws Exception {
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)) {
            PdfRenderer r = new PdfRenderer(fd);
            int n = r.getPageCount();
            r.close();
            return n;
        }
    }

    public void testBitmapIsNotBlank() {
        long id = order(3, "Hem 2 cm");
        OrderSummary s = sharer.load(db, id, 0);
        Bitmap b = sharer.renderBitmap(s);
        assertEquals(1080, b.getWidth());
        assertTrue(b.getHeight() > 300);
        int ink = 0;
        for (int y = 0; y < b.getHeight(); y += 4) {
            for (int x = 0; x < b.getWidth(); x += 4) {
                if (b.getPixel(x, y) != Color.WHITE) {
                    ink++;
                }
            }
        }
        assertTrue("ink samples " + ink, ink > 200);
    }

    public void testPngAndOnePagePdf() throws Exception {
        long id = order(3, "Hem 2 cm");
        assertEquals(1, id);
        OrderSummary s = sharer.load(db, id, 0);
        File png = sharer.writePng(s);
        File pdf = sharer.writePdf(s);
        assertTrue(png.length() > 1000);
        assertTrue(pdf.length() > 500);
        assertEquals("order-1.png", png.getName());
        assertEquals(1, pdfPages(pdf));
    }

    public void testFortyLedgerRowsMakeTwoPages() throws Exception {
        long id = order(40, "Hem 2 cm");
        File pdf = sharer.writePdf(sharer.load(db, id, 0));
        assertEquals(2, pdfPages(pdf));
    }
}
