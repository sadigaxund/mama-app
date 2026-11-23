package com.atelier.app.share;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.util.Log;

import com.atelier.app.MainActivity;
import com.atelier.app.R;
import com.atelier.app.data.AppointmentRepository;
import com.atelier.app.data.DbHelper;
import com.atelier.app.data.GarmentTypeRepository;
import com.atelier.app.data.LedgerRepository;
import com.atelier.app.data.OrderRepository;
import com.atelier.app.model.Appointment;
import com.atelier.app.model.GarmentType;
import com.atelier.app.model.Kind;
import com.atelier.app.model.LedgerEntry;
import com.atelier.app.model.Order;
import com.atelier.app.photo.AppFileProvider;
import com.atelier.app.ui.Format;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** Builds the summary of an order and turns it into text, a PNG or a PDF for the share sheet. */
public final class OrderSharer {
    private static final String TAG = "Atelier";
    /** A4 in PDF points. */
    static final int PAGE_W = 595;
    static final int PAGE_H = 842;
    private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;
    /** A debuggable build also copies each file here, since run-as is not always available. */
    private static final String DEBUG_DIR = "share-debug";

    private final Context context;

    /** {@code context} must carry the app language, as an Activity does. */
    public OrderSharer(Context context) {
        this.context = context;
    }

    /** The localized words and formatters for a summary. */
    public static OrderSummary.Labels labels(final Context c) {
        OrderSummary.Labels l = new OrderSummary.Labels();
        l.header = c.getString(R.string.share_header);
        l.customer = c.getString(R.string.share_customer);
        l.phone = c.getString(R.string.share_phone);
        l.stage = c.getString(R.string.share_stage);
        l.deadline = c.getString(R.string.detail_deadline);
        l.nextVisit = c.getString(R.string.share_next_visit);
        l.kindTailoring = c.getString(R.string.kind_tailoring);
        l.kindRepair = c.getString(R.string.kind_repair);
        l.price = c.getString(R.string.label_price);
        l.expenseDefault = c.getString(R.string.ledger_expense_default);
        l.paymentDefault = c.getString(R.string.ledger_payment_default);
        l.expenseAmount = c.getString(R.string.ledger_expense_amount);
        l.paymentAmount = c.getString(R.string.ledger_payment_amount);
        l.balance = c.getString(R.string.money_balance);
        l.overpaid = c.getString(R.string.share_overpaid);
        l.notes = c.getString(R.string.label_notes);
        l.date = c.getString(R.string.share_date);
        l.money = minor -> Format.money(c, minor);
        l.dateText = Format::dateFull;
        l.visitText = at -> Format.appointment(c, at, LocalDate.now());
        return l;
    }

    /** Reads the order and its ledger and builds the summary. Null when the order is gone. */
    public OrderSummary load(DbHelper db, long orderId, long nowMillis) {
        Order o = new OrderRepository(db).get(orderId);
        if (o == null) {
            return null;
        }
        GarmentType type = new GarmentTypeRepository(db).get(o.garmentTypeId);
        List<LedgerEntry> entries = new LedgerRepository(db).listForOrder(orderId);
        Appointment next = new AppointmentRepository(db).nextUpcoming(orderId, nowMillis);
        return OrderSummary.build(labels(context), o, type == null ? null : type.name,
                context.getString(MainActivity.stageLabel(o.stage)), entries, next, LocalDate.now());
    }

    private OrderSummaryRenderer renderer(OrderSummary s) {
        int stripe = context.getColor(s.kind == Kind.TAILORING
                ? R.color.kind_tailoring_stripe : R.color.kind_repair_stripe);
        return new OrderSummaryRenderer(s, stripe);
    }

    /** Draws the bill into a new bitmap {@link OrderSummaryRenderer#DESIGN_WIDTH} px wide. */
    public Bitmap renderBitmap(OrderSummary s) {
        OrderSummaryRenderer r = renderer(s);
        int w = OrderSummaryRenderer.DESIGN_WIDTH;
        Bitmap bmp = Bitmap.createBitmap(w, r.height(w), Bitmap.Config.ARGB_8888);
        r.draw(new Canvas(bmp), w);
        return bmp;
    }

    /** Writes cache/shared/order-N.png. */
    public File writePng(OrderSummary s) throws IOException {
        File out = sharedFile("order-" + s.orderId + ".png");
        Bitmap bmp = renderBitmap(s);
        try (FileOutputStream os = new FileOutputStream(out)) {
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                throw new IOException("PNG encode failed");
            }
        } finally {
            bmp.recycle();
        }
        debugCopy(out);
        return out;
    }

    /** Writes cache/shared/order-N.pdf: A4 pages, the bill scaled to the page width. */
    public File writePdf(OrderSummary s) throws IOException {
        File out = sharedFile("order-" + s.orderId + ".pdf");
        OrderSummaryRenderer r = renderer(s);
        int w = OrderSummaryRenderer.DESIGN_WIDTH;
        float scale = PAGE_W / (float) w;
        int pageHeight = Math.round(PAGE_H / scale);
        List<OrderSummaryRenderer.Page> pages = r.paginate(w, pageHeight);
        PdfDocument doc = new PdfDocument();
        try {
            for (int i = 0; i < pages.size(); i++) {
                PdfDocument.Page page = doc.startPage(
                        new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, i + 1).create());
                Canvas c = page.getCanvas();
                c.scale(scale, scale);
                r.drawPage(c, w, pages.get(i));
                doc.finishPage(page);
            }
            try (FileOutputStream os = new FileOutputStream(out)) {
                doc.writeTo(os);
            }
        } finally {
            doc.close();
        }
        debugCopy(out);
        return out;
    }

    /** The send intent for the text form, wrapped in a chooser. */
    public Intent textIntent(OrderSummary s) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, s.subject);
        send.putExtra(Intent.EXTRA_TEXT, s.toText());
        return Intent.createChooser(send, context.getString(R.string.share_chooser_title));
    }

    /** The send intent for a file in cache/shared, wrapped in a chooser. */
    public Intent fileIntent(File file, String mime, OrderSummary s) {
        Uri uri = AppFileProvider.uriFor(context, AppFileProvider.DIR_SHARED, file.getName());
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_SUBJECT, s.subject);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newUri(context.getContentResolver(), file.getName(), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, context.getString(R.string.share_chooser_title));
        chooser.setClipData(send.getClipData());
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return chooser;
    }

    /** Deletes files in cache/shared last modified more than a day before {@code nowMillis}. */
    public void cleanOld(long nowMillis) {
        File[] files = new File(context.getCacheDir(), AppFileProvider.DIR_SHARED).listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isFile() && nowMillis - f.lastModified() > MAX_AGE_MS && !f.delete()) {
                Log.w(TAG, "share: could not delete " + f.getName());
            }
        }
    }

    private File sharedFile(String name) throws IOException {
        File dir = new File(context.getCacheDir(), AppFileProvider.DIR_SHARED);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        return new File(dir, name);
    }

    private void debugCopy(File file) {
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return;
        }
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            return;
        }
        File dir = new File(base, DEBUG_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "share: cannot create " + dir);
            return;
        }
        try (java.io.InputStream in = new java.io.FileInputStream(file);
             FileOutputStream out = new FileOutputStream(new File(dir, file.getName()))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            Log.w(TAG, "share: debug copy failed: " + e.getMessage());
        }
    }
}
