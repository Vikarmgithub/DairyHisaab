package com.example.dairyhisaab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class MyPrintDocumentAdapter extends PrintDocumentAdapter {

    private Context context;
    private List<Customer> list;
    private PdfDocument pdfDocument;

    public MyPrintDocumentAdapter(Context context, List<Customer> list) {
        this.context = context;
        this.list = list;
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes, CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        
        // लैंडस्केप मोड में एक पेज पर आराम से 18 मेंबर्स का पूरा डेटा आ जाएगा
        int totalPages = (int) Math.ceil((double) list.size() / 18);
        if (totalPages == 0) totalPages = 1;

        PrintDocumentInfo pdi = new PrintDocumentInfo.Builder("Complete_Members_Report.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(totalPages).build();
        callback.onLayoutFinished(pdi, true);
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination, CancellationSignal cancellationSignal, WriteResultCallback callback) {
        pdfDocument = new PdfDocument();
        
        Paint titlePaint = new Paint();
        Paint headerPaint = new Paint();
        Paint textPaint = new Paint();
        Paint borderPaint = new Paint();

        // टाइटल्स और फ़ॉन्ट्स की सेटिंग
        titlePaint.setColor(Color.parseColor("#0D47A1")); 
        titlePaint.setTextSize(16);
        titlePaint.setFakeBoldText(true);

        headerPaint.setColor(Color.BLACK);
        headerPaint.setTextSize(9);
        headerPaint.setFakeBoldText(true);

        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(8.5f);

        borderPaint.setColor(Color.DKGRAY);
        borderPaint.setStrokeWidth(0.8f);
        borderPaint.setStyle(Paint.Style.STROKE);

        // 💡 Landscape (आड़े पन्ने) के हिसाब से सभी 11 कॉलम्स की सटीक चौड़ाई (X-Coordinates)
        int startX = 15;
        int colSNo = 15;       // S.No
        int colCode = 45;      // Code
        int colName = 85;      // Name
        int colFather = 185;   // Father/Husband
        int colPhone = 285;    // Mobile
        int colUniqueId = 365; // Unique ID
        int colAadhar = 445;   // Aadhaar
        int colJan = 525;      // Jan Aadhaar
        int colBank = 605;     // Bank Name
        int colAcc = 695;      // Account No & IFSC
        int endX = 825;        // A4 Landscape कुल चौड़ाई (842 के अंदर सुरक्षित)

        int currentMemberIndex = 0;
        int pageNumber = 1;

        while (currentMemberIndex < list.size()) {
            // 💡 यहाँ पन्ने को Landscape (842 चौड़ाई, 595 ऊँचाई) में जनरेट कर रहे हैं
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(842, 595, pageNumber).create(); 
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // मुख्य हेडर टाइटल
            canvas.drawText("Dairy Hisaab Kitab - Complete Members Master Ledger (Excel Format)", startX, 35, titlePaint);

            // --- टेबल हेडर की शुरुआत ---
            int y = 55;
            int rowHeight = 24;

            canvas.drawLine(startX, y, endX, y, borderPaint);
            canvas.drawText("S.No", colSNo + 3, y + 15, headerPaint);
            canvas.drawText("Code", colCode + 3, y + 15, headerPaint);
            canvas.drawText("Member Name", colName + 3, y + 15, headerPaint);
            canvas.drawText("Father/Husband", colFather + 3, y + 15, headerPaint);
            canvas.drawText("Mobile No", colPhone + 3, y + 15, headerPaint);
            canvas.drawText("Unique ID", colUniqueId + 3, y + 15, headerPaint);
            canvas.drawText("Aadhaar No", colAadhar + 3, y + 15, headerPaint);
            canvas.drawText("Jan Aadhaar", colJan + 3, y + 15, headerPaint);
            canvas.drawText("Bank Name", colBank + 3, y + 15, headerPaint);
            canvas.drawText("Account No [IFSC]", colAcc + 3, y + 15, headerPaint);
            
            y += rowHeight;
            canvas.drawLine(startX, y, endX, y, borderPaint); 

            int membersOnThisPage = 0;

            // --- टेबल के अंदर की कतारें (Rows) ---
            while (currentMemberIndex < list.size() && membersOnThisPage < 18) {
                Customer c = list.get(currentMemberIndex);

                // डेटा को खानों में लिखना (लंबा होने पर कटने से बचाने की सेटिंग के साथ)
                canvas.drawText(String.valueOf(currentMemberIndex + 1), colSNo + 3, y + 15, textPaint);
                canvas.drawText(c.memberCode != null ? c.memberCode : "-", colCode + 3, y + 15, textPaint);
                
                String nameStr = c.name != null ? c.name : "-";
                if (nameStr.length() > 18) nameStr = nameStr.substring(0, 16) + "..";
                canvas.drawText(nameStr, colName + 3, y + 15, textPaint);

                String fatherStr = c.fatherHusband != null && !c.fatherHusband.isEmpty() ? c.fatherHusband : "-";
                if (fatherStr.length() > 18) fatherStr = fatherStr.substring(0, 16) + "..";
                canvas.drawText(fatherStr, colFather + 3, y + 15, textPaint);

                canvas.drawText(c.phone != null && !c.phone.isEmpty() ? c.phone : "-", colPhone + 3, y + 15, textPaint);
                canvas.drawText(c.uniqueId != null && !c.uniqueId.isEmpty() ? c.uniqueId : "-", colUniqueId + 3, y + 15, textPaint);
                
                // आधार प्राइवेसी मास्क के साथ पूरा दिखाना
                String aadharStr = "-";
                if (c.aadhar != null && !c.aadhar.isEmpty()) {
                    aadharStr = (c.aadhar.length() >= 4) ? "XXXX-XXXX-" + c.aadhar.substring(c.aadhar.length() - 4) : c.aadhar;
                }
                canvas.drawText(aadharStr, colAadhar + 3, y + 15, textPaint);
                
                canvas.drawText(c.janAadhar != null && !c.janAadhar.isEmpty() ? c.janAadhar : "-", colJan + 3, y + 15, textPaint);
                
                String bankStr = c.bankName != null && !c.bankName.isEmpty() ? c.bankName : "-";
                if (bankStr.length() > 16) bankStr = bankStr.substring(0, 14) + "..";
                canvas.drawText(bankStr, colBank + 3, y + 15, textPaint);

                // बैंक खाता संख्या और ब्रैकेट में IFSC कोड एक साथ दिखाना
                String accStr = "-";
                if (c.bankAcc != null && !c.bankAcc.isEmpty()) {
                    accStr = c.bankAcc;
                    if (c.ifsc != null && !c.ifsc.isEmpty()) {
                        accStr += " [" + c.ifsc + "]";
                    }
                }
                if (accStr.length() > 24) accStr = accStr.substring(0, 22) + "..";
                canvas.drawText(accStr, colAcc + 3, y + 15, textPaint);

                y += rowHeight;
                canvas.drawLine(startX, y, endX, y, borderPaint); 

                currentMemberIndex++;
                membersOnThisPage++;
            }

            // --- टेबल के सारे खड़े खंभे (Vertical Lines) खींचना ---
            int tBottom = y;
            int tTop = 55;
            canvas.drawLine(colSNo, tTop, colSNo, tBottom, borderPaint);
            canvas.drawLine(colCode, tTop, colCode, tBottom, borderPaint);
            canvas.drawLine(colName, tTop, colName, tBottom, borderPaint);
            canvas.drawLine(colFather, tTop, colFather, tBottom, borderPaint);
            canvas.drawLine(colPhone, tTop, colPhone, tBottom, borderPaint);
            canvas.drawLine(colUniqueId, tTop, colUniqueId, tBottom, borderPaint);
            canvas.drawLine(colAadhar, tTop, colAadhar, tBottom, borderPaint);
            canvas.drawLine(colJan, tTop, colJan, tBottom, borderPaint);
            canvas.drawLine(colBank, tTop, colBank, tBottom, borderPaint);
            canvas.drawLine(colAcc, tTop, colAcc, tBottom, borderPaint);
            canvas.drawLine(endX, tTop, endX, tBottom, borderPaint);

            // पेज नंबर फूटर
            canvas.drawText("Page " + pageNumber, endX - 50, 570, textPaint);

            pdfDocument.finishPage(page);
            pageNumber++;
        }

        try {
            pdfDocument.writeTo(new FileOutputStream(destination.getFileDescriptor()));
        } catch (IOException e) {
            callback.onWriteFailed(e.toString());
            return;
        } finally {
            pdfDocument.close();
        }
        callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
    }
}
