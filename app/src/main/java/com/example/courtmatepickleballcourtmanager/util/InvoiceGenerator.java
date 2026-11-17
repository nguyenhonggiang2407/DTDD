package com.example.courtmatepickleballcourtmanager.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.example.courtmatepickleballcourtmanager.R;
import com.example.courtmatepickleballcourtmanager.model.CourtSession;
import com.example.courtmatepickleballcourtmanager.model.Rental;
import com.example.courtmatepickleballcourtmanager.model.ServiceOrder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class InvoiceGenerator {

    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN = 42;

    public static File createPdf(Context context, CourtSession session,
                                 List<ServiceOrder> orders, List<Rental> rentals)
            throws IOException {
        File dir = new File(context.getFilesDir(), "invoices");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Không tạo được thư mục hoá đơn");
        }
        String invoiceNo = session.invoiceNo == null || session.invoiceNo.isEmpty()
                ? "HD-" + session.id : session.invoiceNo;
        File file = new File(dir, invoiceNo.replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf");

        PdfDocument document = new PdfDocument();
        PdfDocument.Page page = startPage(document);
        Canvas canvas = page.getCanvas();
        float y = MARGIN;

        y = draw(canvas, context.getString(R.string.app_name), MARGIN, y, 16, true, Color.rgb(14, 124, 74));
        y = draw(canvas, context.getString(R.string.invoice_pdf_title), MARGIN, y + 6, 22, true, Color.BLACK);
        y = draw(canvas, context.getString(R.string.invoice_number_label, invoiceNo),
                MARGIN, y + 10, 11, false, Color.DKGRAY);
        String paidAt = session.paidAt == null ? "" : session.paidAt;
        y = draw(canvas, context.getString(R.string.invoice_created_at,
                DateTimeUtils.displayDateTime(paidAt.isEmpty()
                        ? DateTimeUtils.nowDbDateTime() : paidAt)), MARGIN, y + 4, 10, false, Color.DKGRAY);

        y += 10;
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint());
        y += 18;

        String customer = session.customerName == null || session.customerName.isEmpty()
                ? context.getString(R.string.session_walk_in_customer) : session.customerName;
        y = draw(canvas, context.getString(R.string.invoice_customer, customer),
                MARGIN, y, 11, true, Color.BLACK);
        if (session.customerPhone != null && !session.customerPhone.isEmpty()) {
            y = draw(canvas, context.getString(R.string.invoice_phone, session.customerPhone),
                    MARGIN, y + 4, 10, false, Color.DKGRAY);
        }
        y = draw(canvas, context.getString(R.string.invoice_court,
                session.courtName == null ? "" : session.courtName),
                MARGIN, y + 4, 10, false, Color.DKGRAY);
        y = draw(canvas, context.getString(R.string.invoice_play_time,
                DateTimeUtils.displayDateTime(session.startTime),
                session.endTime == null ? "" : DateTimeUtils.displayDateTime(session.endTime)),
                MARGIN, y + 4, 10, false, Color.DKGRAY);

        y += 16;
        y = draw(canvas, context.getString(R.string.invoice_items), MARGIN, y, 12, true, Color.BLACK);
        y += 4;
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint());
        y += 16;

        y = draw(canvas, context.getString(R.string.invoice_court_fee,
                MoneyUtils.format(session.courtFee)), MARGIN, y, 10, false, Color.BLACK);

        for (ServiceOrder order : orders) {
            if (y > PAGE_HEIGHT - 90) {
                document.finishPage(page);
                page = startPage(document);
                canvas = page.getCanvas();
                y = MARGIN + 10;
            }
            y = draw(canvas, "• " + order.serviceName + " × " + order.quantity
                    + " = " + MoneyUtils.format(order.amount), MARGIN + 10, y + 4,
                    10, false, Color.DKGRAY);
        }
        for (Rental rental : rentals) {
            if (y > PAGE_HEIGHT - 90) {
                document.finishPage(page);
                page = startPage(document);
                canvas = page.getCanvas();
                y = MARGIN + 10;
            }
            y = draw(canvas, "• " + context.getString(R.string.invoice_rental_line,
                    MoneyUtils.format(rental.rentFee), MoneyUtils.format(rental.deposit)),
                    MARGIN + 10, y + 4, 10, false, Color.DKGRAY);
        }

        // Reserve enough height for subtotal, discount, payment and footer.
        if (y > PAGE_HEIGHT - 235) {
            document.finishPage(page);
            page = startPage(document);
            canvas = page.getCanvas();
            y = MARGIN + 10;
        }
        y += 12;
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint());
        y += 18;
        int discount = session.discountCourt + session.discountService;
        y = draw(canvas, context.getString(R.string.invoice_subtotal,
                MoneyUtils.format(session.courtFee + session.serviceFee + session.rentalFee)),
                MARGIN, y, 10, false, Color.DKGRAY);
        if (discount > 0) {
            y = draw(canvas, context.getString(R.string.invoice_discount,
                    MoneyUtils.format(discount)), MARGIN, y + 4, 10, false, Color.DKGRAY);
        }
        y = draw(canvas, context.getString(R.string.invoice_total,
                MoneyUtils.format(session.total)), MARGIN, y + 8, 16, true, Color.rgb(14, 124, 74));

        y += 18;
        String method = CourtSession.METHOD_CASH.equals(session.paymentMethod)
                ? context.getString(R.string.payment_cash)
                : context.getString(R.string.payment_qr);
        y = draw(canvas, context.getString(R.string.invoice_payment_method, method),
                MARGIN, y, 10, false, Color.DKGRAY);
        y = draw(canvas, context.getString(R.string.invoice_payment_status,
                CourtSession.PAYMENT_PAID.equals(session.paymentStatus)
                        ? context.getString(R.string.payment_paid)
                        : context.getString(R.string.payment_unpaid)),
                MARGIN, y + 4, 10, false, Color.DKGRAY);
        if (session.paymentTransactionId != null && !session.paymentTransactionId.isEmpty()) {
            y = draw(canvas, context.getString(R.string.invoice_transaction,
                    session.paymentTransactionId), MARGIN, y + 4, 9, false, Color.GRAY);
        }
        draw(canvas, context.getString(R.string.invoice_footer), MARGIN, PAGE_HEIGHT - 50,
                9, false, Color.GRAY);

        document.finishPage(page);
        try (FileOutputStream output = new FileOutputStream(file)) {
            document.writeTo(output);
        } finally {
            document.close();
        }
        return file;
    }

    private static PdfDocument.Page startPage(PdfDocument document) {
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                PAGE_WIDTH, PAGE_HEIGHT, document.getPages().size() + 1).create();
        return document.startPage(info);
    }

    private static float draw(Canvas canvas, String text, float x, float y,
                              float size, boolean bold, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        canvas.drawText(text == null ? "" : text, x, y, paint);
        return y + size + 6;
    }

    private static Paint linePaint() {
        Paint paint = new Paint();
        paint.setColor(Color.LTGRAY);
        paint.setStrokeWidth(1);
        return paint;
    }

    public static Uri getUri(Context context, File file) {
        return FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", file);
    }

    public static void share(Context context, File file) {
        Uri uri = getUri(context, file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent,
                context.getString(R.string.invoice_share_title)));
    }

    public static void open(Context context, File file) {
        Uri uri = getUri(context, file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            share(context, file);
        }
    }
}
