package com.example.dairyhisaab;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class MembersFragment extends Fragment {

    private EditText etCode, etName, etFatherHusband, etPhone, etAadhar, etJanAadhar, etUniqueId, etBankName, etBankAcc, etIfsc;
    private Button btnAdd, btnToggleForm, btnPdf, btnExcel, btnPrint;
    private LinearLayout membersContainer, layoutAddMemberForm;
    private DairyDataManager dm;
    private boolean isFormVisible = false;

    public MembersFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_members, container, false);

        dm = DairyDataManager.getInstance(getContext());

        // फॉर्म कंपोनेंट्स
        layoutAddMemberForm = view.findViewById(R.id.layoutAddMemberForm);
        btnToggleForm = view.findViewById(R.id.btnToggleForm);
        
        etCode = view.findViewById(R.id.etMemberCode);
        etName = view.findViewById(R.id.etMemberName);
        etFatherHusband = view.findViewById(R.id.etFatherHusband);
        etPhone = view.findViewById(R.id.etMemberPhone);
        etAadhar = view.findViewById(R.id.etMemberAadhar);
        etJanAadhar = view.findViewById(R.id.etJanAadhar);
        etUniqueId = view.findViewById(R.id.etMemberUniqueId); 
        etBankName = view.findViewById(R.id.etBankName);
        etBankAcc = view.findViewById(R.id.etBankAcc);
        etIfsc = view.findViewById(R.id.etIfsc);
        btnAdd = view.findViewById(R.id.btnAddMember);
        
        // एक्सपोर्ट बटन्स
        btnPdf = view.findViewById(R.id.btnExportPdf);
        btnExcel = view.findViewById(R.id.btnExportExcel);
        btnPrint = view.findViewById(R.id.btnPrintList);

        membersContainer = view.findViewById(R.id.membersContainer);

        // 💡 फॉर्म शो/हाइड करने का लॉजिक (जैसा आपकी असली फाइल में था)
        btnToggleForm.setOnClickListener(v -> {
            if (isFormVisible) {
                layoutAddMemberForm.setVisibility(View.GONE);
                btnToggleForm.setText("➕ JODNE KE LIYE MENU KHOLO");
                isFormVisible = false;
            } else {
                layoutAddMemberForm.setVisibility(View.VISIBLE);
                btnToggleForm.setText("➖ MENU BAND KARO");
                isFormVisible = true;
            }
        });

        btnAdd.setOnClickListener(v -> saveCustomerToDb());
        
        // एक्सपोर्ट क्लिक्स (जैसा आपकी असली फाइल में था)
        btnPdf.setOnClickListener(v -> exportToPdf());
        btnExcel.setOnClickListener(v -> exportToExcel());
        btnPrint.setOnClickListener(v -> printMemberList());

        loadAndDisplayMembers();

        return view;
    }

    private void saveCustomerToDb() {
        String code = etCode.getText().toString().trim();
        String name = etName.getText().toString().trim();
        String father = etFatherHusband.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String aadharNo = etAadhar.getText().toString().trim();
        String janAadharNo = etJanAadhar.getText().toString().trim();
        String uniqueIdNo = etUniqueId.getText().toString().trim();
        String bank = etBankName.getText().toString().trim();
        String acc = etBankAcc.getText().toString().trim();
        String ifscCode = etIfsc.getText().toString().trim();

        if (code.isEmpty() || name.isEmpty() || uniqueIdNo.isEmpty()) {
            Toast.makeText(getContext(), "Code, Naam aur Unique ID daalna zaroori hai!", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Customer> currentList = dm.getCustomers();
        for (Customer existing : currentList) {
            if (existing.memberCode != null && existing.memberCode.equalsIgnoreCase(code)) {
                Toast.makeText(getContext(), "⚠️ Yeh Member Code pehle se bana hai!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String generatedId = "cust_" + System.currentTimeMillis();
        
        Customer newCustomer = new Customer();
        newCustomer.id = generatedId;
        newCustomer.memberCode = code;
        newCustomer.name = name;
        newCustomer.fatherHusband = father;
        newCustomer.phone = phone;
        newCustomer.aadhar = aadharNo;
        newCustomer.janAadhar = janAadharNo;
        newCustomer.uniqueId = uniqueIdNo; 
        newCustomer.address = ""; 
        newCustomer.bankName = bank;
        newCustomer.bankAcc = acc;
        newCustomer.ifsc = ifscCode;

        currentList.add(newCustomer);
        dm.saveCustomers(currentList);

        Toast.makeText(getContext(), name + " successfully judd gaya!", Toast.LENGTH_SHORT).show();

        layoutAddMemberForm.setVisibility(View.GONE);
        btnToggleForm.setText("➕ JODNE KE LIYE MENU KHOLO");
        isFormVisible = false;

        etCode.setText(""); etName.setText(""); etFatherHusband.setText("");
        etPhone.setText(""); etAadhar.setText(""); etJanAadhar.setText("");
        etUniqueId.setText(""); etBankName.setText(""); etBankAcc.setText(""); etIfsc.setText("");

        loadAndDisplayMembers();
    }

    private void loadAndDisplayMembers() {
        membersContainer.removeAllViews();
        List<Customer> list = dm.getCustomers();

        if (list == null || list.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText("Abhi koi member nahi joda gaya hai.");
            tvEmpty.setTextColor(Color.parseColor("#78909C"));
            tvEmpty.setTextSize(14);
            tvEmpty.setPadding(0, 20, 0, 0);
            membersContainer.addView(tvEmpty);
            return;
        }

        int count = 1;
        for (Customer c : list) {
            LinearLayout rowCard = new LinearLayout(getContext());
            rowCard.setOrientation(LinearLayout.VERTICAL);
            rowCard.setPadding(16, 16, 16, 16);
            rowCard.setBackgroundColor(count % 2 == 0 ? Color.parseColor("#F0F7FF") : Color.WHITE);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 12);
            rowCard.setLayoutParams(lp);

            rowCard.setOnClickListener(v -> showMemberDetailsPopup(c));

            TextView tvNameAndCode = new TextView(getContext());
            String displayName = count + ". [" + (c.memberCode != null ? c.memberCode : "N/A") + "] " + (c.name != null ? c.name : "N/A");
            if (c.fatherHusband != null && !c.fatherHusband.isEmpty()) {
                displayName += " (S/o, W/o: " + c.fatherHusband + ")";
            }
            tvNameAndCode.setText(displayName);
            tvNameAndCode.setTextSize(15);
            tvNameAndCode.setTextColor(Color.parseColor("#0D47A1")); 
            tvNameAndCode.setTypeface(null, Typeface.BOLD);
            rowCard.addView(tvNameAndCode);

            LinearLayout detailsLayout = new LinearLayout(getContext());
            detailsLayout.setOrientation(LinearLayout.VERTICAL);
            detailsLayout.setPadding(12, 6, 0, 0);

            TextView tvMob = new TextView(getContext());
            tvMob.setText("📞 Mobile: " + (c.phone != null && !c.phone.isEmpty() ? c.phone : "N/A"));
            tvMob.setTextSize(13);
            tvMob.setTextColor(Color.parseColor("#212121"));
            detailsLayout.addView(tvMob);

            TextView tvUid = new TextView(getContext());
            String uniqueIdText = (c.uniqueId != null && !c.uniqueId.isEmpty()) ? c.uniqueId : "N/A";
            tvUid.setText("🆔 Unique ID: " + uniqueIdText);
            tvUid.setTextSize(13);
            tvUid.setTextColor(Color.parseColor("#2E7D32")); 
            tvUid.setTypeface(null, Typeface.BOLD);
            detailsLayout.addView(tvUid);

            rowCard.addView(detailsLayout);
            membersContainer.addView(rowCard);
            count++;
        }
    }

    // 💡 यहाँ आपका असली पॉपअप है, जिसमें बस एक "✏️ EDIT" बटन नीचे जोड़ दिया गया है
    private void showMemberDetailsPopup(Customer c) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("👤 Member Full Details");
        StringBuilder details = new StringBuilder();
        details.append("🔹 Code: ").append(c.memberCode).append("\n\n")
               .append("🔹 Name: ").append(c.name).append("\n\n")
               .append("🔹 Father/Husband: ").append(c.fatherHusband).append("\n\n")
               .append("🔹 Mobile: ").append(c.phone).append("\n\n")
               .append("🆔 Unique ID: ").append(c.uniqueId).append("\n\n")
               .append("🔹 Aadhaar: ").append(c.aadhar).append("\n\n")
               .append("🔹 Jan Aadhaar: ").append(c.janAadhar).append("\n\n")
               .append("🏦 Bank Name: ").append(c.bankName).append("\n")
               .append("🏦 Account No: ").append(c.bankAcc).append("\n")
               .append("🏦 IFSC Code: ").append(c.ifsc);
        builder.setMessage(details.toString());
        
        // ✏️ यह रहा एडिट बटन जो आपके असली पॉपअप के साथ जुड़ गया है
        builder.setPositiveButton("✏️ EDIT", (dialog, which) -> showEditMemberDialog(c));
        builder.setNegativeButton("CLOSE", null);
        builder.create().show();
    }
    // 💡 इस सही मेथड को अपनी Java फाइल में पुराने वाले से बदल दें भाई
    private void showEditMemberDialog(Customer c) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_member, null);
        builder.setView(dialogView);

        EditText etEditCode = dialogView.findViewById(R.id.etEditCode);
        EditText etEditName = dialogView.findViewById(R.id.etEditName);
        EditText etEditFather = dialogView.findViewById(R.id.etEditFather);
        EditText etEditPhone = dialogView.findViewById(R.id.etEditPhone);
        EditText etEditUniqueId = dialogView.findViewById(R.id.etEditUniqueId);
        EditText etEditAadhar = dialogView.findViewById(R.id.etEditAadhar);
        EditText etEditJanAadhar = dialogView.findViewById(R.id.etEditJanAadhar);
        EditText etEditBankName = dialogView.findViewById(R.id.etEditBankName);
        EditText etEditBankAcc = dialogView.findViewById(R.id.etEditBankAcc);
        EditText etEditIfsc = dialogView.findViewById(R.id.etEditIfsc);
        Button btnSaveEdit = dialogView.findViewById(R.id.btnSaveEdit);

        // पुरानी वैल्यू सेट करना
        etEditCode.setText(c.memberCode);
        etEditName.setText(c.name);
        etEditFather.setText(c.fatherHusband);
        etEditPhone.setText(c.phone);
        etEditUniqueId.setText(c.uniqueId);
        etEditAadhar.setText(c.aadhar);
        etEditJanAadhar.setText(c.janAadhar);
        etEditBankName.setText(c.bankName);
        etEditBankAcc.setText(c.bankAcc);
        etEditIfsc.setText(c.ifsc);

        AlertDialog dialog = builder.create();
        
        btnSaveEdit.setOnClickListener(v -> {
            // 💡 1. मुख्य डेटाबेस से कस्टमर्स की असली लिस्ट लोड करें
            List<Customer> allCustomers = dm.getCustomers();
            
            // 💡 2. लिस्ट में इस कस्टमर को उसकी यूनिक आईडी (id) से ढूँढें
            int targetIndex = -1;
            for (int i = 0; i < allCustomers.size(); i++) {
                if (allCustomers.get(i).id != null && allCustomers.get(i).id.equals(c.id)) {
                    targetIndex = i;
                    break;
                }
            }

            // 💡 3. अगर कस्टमर मिल जाता है, तो उसकी सारी वैल्यूज को अपडेट करें
            if (targetIndex != -1) {
                Customer updatedCustomer = allCustomers.get(targetIndex);
                updatedCustomer.memberCode = etEditCode.getText().toString().trim();
                updatedCustomer.name = etEditName.getText().toString().trim();
                updatedCustomer.fatherHusband = etEditFather.getText().toString().trim();
                updatedCustomer.phone = etEditPhone.getText().toString().trim();
                updatedCustomer.uniqueId = etEditUniqueId.getText().toString().trim();
                updatedCustomer.aadhar = etEditAadhar.getText().toString().trim();
                updatedCustomer.janAadhar = etEditJanAadhar.getText().toString().trim();
                updatedCustomer.bankName = etEditBankName.getText().toString().trim();
                updatedCustomer.bankAcc = etEditBankAcc.getText().toString().trim();
                updatedCustomer.ifsc = etEditIfsc.getText().toString().trim();

                // 💡 4. अब इस अपडेटेड पूरी लिस्ट को डेटाबेस में पक्का सेव करें
                dm.saveCustomers(allCustomers);
                
                Toast.makeText(getContext(), "✅ Details Updated Successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "⚠️ Error: Member nahi mila!", Toast.LENGTH_SHORT).show();
            }
            
            // लिस्ट रिफ्रेश करें और पॉपअप बंद करें
            loadAndDisplayMembers();
            dialog.dismiss();
        });
        
        dialog.show();
    }



       // 💡 इस 100% सही मेथड को अपनी Java फाइल में पुराने exportToPdf() से बदल दें भाई
    private void exportToPdf() {
        List<Customer> list = dm.getCustomers();
        if (list.isEmpty()) { 
            Toast.makeText(getContext(), "List khali hai!", Toast.LENGTH_SHORT).show(); 
            return; 
        }

        PdfDocument pdfDocument = new PdfDocument();
        
        android.graphics.Paint titlePaint = new android.graphics.Paint();
        android.graphics.Paint headerPaint = new android.graphics.Paint();
        android.graphics.Paint textPaint = new android.graphics.Paint();
        android.graphics.Paint borderPaint = new android.graphics.Paint();

        // टाइटल्स और फ़ॉन्ट्स की सेटिंग्स
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
        borderPaint.setStyle(android.graphics.Paint.Style.STROKE);

        // Landscape के हिसाब से सभी 11 कॉलम्स की चौड़ाई (X-Coordinates)
        int startX = 15;
        int colSNo = 15;       
        int colCode = 45;      
        int colName = 85;      
        int colFather = 185;   
        int colPhone = 285;    
        int colUniqueId = 365; 
        int colAadhar = 445;   
        int colJan = 525;      
        int colBank = 605;     
        int colAcc = 695;      
        int endX = 825;        

        int currentMemberIndex = 0;
        int pageNumber = 1;

        while (currentMemberIndex < list.size()) {
            // A4 Landscape साइज पन्ना (842 चौड़ाई, 595 ऊँचाई)
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(842, 595, pageNumber).create(); 
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            android.graphics.Canvas canvas = page.getCanvas();

            // मुख्य हेडर टाइटल
            canvas.drawText("Dairy Hisaab Kitab - Complete Members Master Ledger (PDF Report)", startX, 35, titlePaint);

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
                
                String aadharStr = "-";
                if (c.aadhar != null && !c.aadhar.isEmpty()) {
                    aadharStr = (c.aadhar.length() >= 4) ? "XXXX-XXXX-" + c.aadhar.substring(c.aadhar.length() - 4) : c.aadhar;
                }
                canvas.drawText(aadharStr, colAadhar + 3, y + 15, textPaint);
                
                canvas.drawText(c.janAadhar != null && !c.janAadhar.isEmpty() ? c.janAadhar : "-", colJan + 3, y + 15, textPaint);
                
                String bankStr = c.bankName != null && !c.bankName.isEmpty() ? c.bankName : "-";
                if (bankStr.length() > 16) bankStr = bankStr.substring(0, 14) + "..";
                canvas.drawText(bankStr, colBank + 3, y + 15, textPaint);

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

            // --- खड़े खंभे (Vertical Grid Lines) खींचना ---
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

            canvas.drawText("Page " + pageNumber, endX - 50, 570, textPaint);

            pdfDocument.finishPage(page);
            pageNumber++;
        }

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(downloadsDir, "Members_Master_Report_" + System.currentTimeMillis() + ".pdf");

        try {
            pdfDocument.writeTo(new FileOutputStream(file));
            Toast.makeText(getContext(), "PDF Saved in Downloads Folder!", Toast.LENGTH_LONG).show();
            shareFile(file, "application/pdf");
        } catch (IOException e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            pdfDocument.close();
        }
    }


    // 💡 2. EXCEL export logic (जैसा आपकी असली फाइल में था)
    private void exportToExcel() {
        List<Customer> list = dm.getCustomers();
        if (list.isEmpty()) { Toast.makeText(getContext(), "List khali hai!", Toast.LENGTH_SHORT).show(); return; }

        StringBuilder csvData = new StringBuilder();
        csvData.append("S.No,Member Code,Name,Father Name,Mobile,Unique ID,Aadhaar,Jan Aadhaar,Bank Name,Account No,IFSC\n");

        int count = 1;
        for (Customer c : list) {
            csvData.append(count).append(",")
                   .append(c.memberCode).append(",")
                   .append(c.name).append(",")
                   .append(c.fatherHusband).append(",")
                   .append(c.phone).append(",")
                   .append(c.uniqueId).append(",")
                   .append(c.aadhar).append(",")
                   .append(c.janAadhar).append(",")
                   .append(c.bankName).append(",")
                   .append(c.bankAcc).append(",")
                   .append(c.ifsc).append("\n");
            count++;
        }

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(downloadsDir, "Members_List_" + System.currentTimeMillis() + ".csv");

        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(csvData.toString().getBytes());
            fos.close();
            Toast.makeText(getContext(), "Excel (CSV) Saved in Downloads!", Toast.LENGTH_LONG).show();
            shareFile(file, "text/csv");
        } catch (IOException e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 💡 3. PRINT logic (जैसा आपकी असली फाइल में था)
    private void printMemberList() {
        PrintManager printManager = (PrintManager) requireActivity().getSystemService(Context.PRINT_SERVICE);
        String jobName = getString(R.string.app_name) + " Document";
        printManager.print(jobName, new MyPrintDocumentAdapter(getContext(), dm.getCustomers()), null);
    }

    private void shareFile(File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share Report"));
    }
}
