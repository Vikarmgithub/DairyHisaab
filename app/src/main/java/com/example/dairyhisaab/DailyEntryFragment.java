package com.example.dairyhisaab;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DailyEntryFragment extends Fragment {

    // Header views
    private EditText etDate;
    private TextView btnMorning, btnEvening, btnPrevDay, btnNextDay;
    private TextView tvRateBanner;

    // Quick entry views
    private EditText etQuickCode, etQuickQty, etQuickFat;
    private TextView tvQuickName, tvQuickAmount, tvAnimalToggle;
    private Button btnQuickSave;

    // List + bottom
    private LinearLayout entryList;
    private TextView tvTotalSummary;
    private Button btnPrintSlip;

    private String selectedShift = "Subah";
    private boolean isBuffalo = true; // true=भैंस, false=गाय

    private double buffFatRate = 875.00;
    private double buffCommission = 7.00;
    private double cowFatRate = 875.00;
    private double cowCommission = 7.00;

    private DairyDataManager dm;
    private List<Customer> customerList = new ArrayList<>();

    // In-memory list for current session display
    private List<MilkEntry> sessionEntries = new ArrayList<>();

    public DailyEntryFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily, container, false);

        dm = DairyDataManager.getInstance(getContext());

        // Bind views
        etDate          = view.findViewById(R.id.etDate);
        btnMorning      = view.findViewById(R.id.btnMorning);
        btnEvening      = view.findViewById(R.id.btnEvening);
        btnPrevDay      = view.findViewById(R.id.btnPrevDay);
        btnNextDay      = view.findViewById(R.id.btnNextDay);
        tvRateBanner    = view.findViewById(R.id.tvRateBanner);

        etQuickCode     = view.findViewById(R.id.etQuickCode);
        etQuickQty      = view.findViewById(R.id.etQuickQty);
        etQuickFat      = view.findViewById(R.id.etQuickFat);
        tvQuickName     = view.findViewById(R.id.tvQuickName);
        tvQuickAmount   = view.findViewById(R.id.tvQuickAmount);
        tvAnimalToggle  = view.findViewById(R.id.tvAnimalToggle);
        btnQuickSave    = view.findViewById(R.id.btnQuickSave);

        entryList       = view.findViewById(R.id.entryList);
        tvTotalSummary  = view.findViewById(R.id.tvTotalSummary);
        btnPrintSlip    = view.findViewById(R.id.btnPrintSlip);

        // Set today's date
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        etDate.setText(today);

        updateRateBanner();

        // Date controls
        etDate.setOnClickListener(v -> openDatePicker());
        btnPrevDay.setOnClickListener(v -> changeDate(-1));
        btnNextDay.setOnClickListener(v -> changeDate(1));

        // Shift toggle
        btnMorning.setOnClickListener(v -> selectShift("Subah"));
        btnEvening.setOnClickListener(v -> selectShift("Shaam"));

        // Animal toggle
        tvAnimalToggle.setOnClickListener(v -> {
            isBuffalo = !isBuffalo;
            tvAnimalToggle.setText(isBuffalo ? "भैंस" : "गाय");
            tvAnimalToggle.setBackgroundColor(
                    Color.parseColor(isBuffalo ? "#1B5E20" : "#E65100"));
            recalcQuickAmount();
        });

        // Code lookup
        etQuickCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                lookupMember(s.toString().trim());
            }
        });

        // Auto calc on qty/fat change
        TextWatcher calcWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                recalcQuickAmount();
            }
        };
        etQuickQty.addTextChangedListener(calcWatcher);
        etQuickFat.addTextChangedListener(calcWatcher);

        // Save button
        btnQuickSave.setOnClickListener(v -> saveQuickEntry());

        // Print slip
        btnPrintSlip.setOnClickListener(v -> printSlipPdf());

        // Load saved entries for today
        loadEntriesForCurrentDateShift();

        return view;
    }

    private void updateRateBanner() {
        tvRateBanner.setText("भैंस: ₹" + buffFatRate + "(+" + buffCommission +
                ") | गाय: ₹" + cowFatRate + "(+" + cowCommission + ")");
    }

    // ── Member lookup by code ──
    private Customer foundCustomer = null;

    private void lookupMember(String code) {
        foundCustomer = null;
        tvQuickName.setText("— Member naam —");
        tvQuickName.setTextColor(Color.parseColor("#9E9E9E"));
        if (code.isEmpty()) return;

        customerList = dm.getCustomers();
        for (Customer c : customerList) {
            if (code.equalsIgnoreCase(c.memberCode)) {
                foundCustomer = c;
                tvQuickName.setText(c.name);
                tvQuickName.setTextColor(Color.parseColor("#2E7D32"));
                return;
            }
        }
        tvQuickName.setText("❌ Not found");
        tvQuickName.setTextColor(Color.parseColor("#C62828"));
    }

    // ── Amount calculation ──
    private void recalcQuickAmount() {
        String qStr = etQuickQty.getText().toString().trim();
        String fStr = etQuickFat.getText().toString().trim();
        if (qStr.isEmpty() || fStr.isEmpty()) {
            tvQuickAmount.setText("₹0.00");
            return;
        }
        try {
            double qty = Double.parseDouble(qStr);
            double fat = Double.parseDouble(fStr);
            double rate = (isBuffalo ? buffFatRate : cowFatRate) * fat / 100.0
                        + (isBuffalo ? buffCommission : cowCommission);
            tvQuickAmount.setText(String.format(Locale.getDefault(), "₹%.2f", qty * rate));
        } catch (Exception e) {
            tvQuickAmount.setText("₹0.00");
        }
    }

    // ── Save quick entry ──
    private void saveQuickEntry() {
        if (foundCustomer == null) {
            Toast.makeText(getContext(), "Pehle sahi Code No. daalo!", Toast.LENGTH_SHORT).show();
            return;
        }
        String qStr = etQuickQty.getText().toString().trim();
        String fStr = etQuickFat.getText().toString().trim();
        if (qStr.isEmpty()) {
            Toast.makeText(getContext(), "Qty daalo!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double qty   = Double.parseDouble(qStr);
            double fat   = fStr.isEmpty() ? 0 : Double.parseDouble(fStr);
            double rate  = (isBuffalo ? buffFatRate : cowFatRate) * fat / 100.0
                         + (isBuffalo ? buffCommission : cowCommission);
            double total = qty * rate;

            MilkEntry entry = new MilkEntry();
            entry.id    = System.currentTimeMillis() + "_" + foundCustomer.id;
            entry.cid   = foundCustomer.id;
            entry.date  = etDate.getText().toString().trim();
            entry.shift = selectedShift;
            entry.qty   = qty;
            entry.fat   = fat;
            entry.rate  = rate;

            // Save to DB
            List<MilkEntry> all = dm.getEntries();
            all.add(entry);
            dm.saveEntries(all);

            // Add to session list and refresh UI
            sessionEntries.add(entry);
            addEntryRowToList(entry, foundCustomer, total);
            updateTotalSummary();

            // Clear inputs
            etQuickCode.setText("");
            etQuickQty.setText("");
            etQuickFat.setText("");
            tvQuickName.setText("— Member naam —");
            tvQuickName.setTextColor(Color.parseColor("#9E9E9E"));
            tvQuickAmount.setText("₹0.00");
            foundCustomer = null;
            etQuickCode.requestFocus();

        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ── Add one row to the saved entries list ──
    private void addEntryRowToList(MilkEntry entry, Customer c, double total) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(12, 10, 12, 10);
        row.setTag(entry.id);

        // Alternate row color
        int rowColor = (entryList.getChildCount() % 2 == 0)
                ? Color.WHITE : Color.parseColor("#F1F8E9");
        row.setBackgroundColor(rowColor);

        // Code
        TextView tvCode = makeCell(c.memberCode, 40, false, "#1B5E20");
        row.addView(tvCode);

        // Name
        TextView tvName = makeCell(c.name, 0, true, "#212121");
        ((LinearLayout.LayoutParams) tvName.getLayoutParams()).weight = 2f;
        row.addView(tvName);

        // Qty
        TextView tvQty = makeCell(String.format(Locale.getDefault(), "%.1f", entry.qty),
                45, false, "#212121");
        tvQty.setGravity(android.view.Gravity.CENTER);
        row.addView(tvQty);

        // Fat
        TextView tvFat = makeCell(String.format(Locale.getDefault(), "%.1f", entry.fat),
                40, false, "#212121");
        tvFat.setGravity(android.view.Gravity.CENTER);
        row.addView(tvFat);

        // Amount
        TextView tvAmt = makeCell(String.format(Locale.getDefault(), "₹%.2f", total),
                60, true, "#2E7D32");
        tvAmt.setGravity(android.view.Gravity.END);
        row.addView(tvAmt);

        // Delete button
        TextView tvDel = new TextView(getContext());
        LinearLayout.LayoutParams delP = new LinearLayout.LayoutParams(50,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tvDel.setLayoutParams(delP);
        tvDel.setText("🗑");
        tvDel.setTextSize(16);
        tvDel.setGravity(android.view.Gravity.CENTER);
        tvDel.setClickable(true);
        tvDel.setFocusable(true);
        tvDel.setOnClickListener(v -> {
            // Remove from DB
            List<MilkEntry> all = dm.getEntries();
            all.removeIf(e -> e.id.equals(entry.id));
            dm.saveEntries(all);
            // Remove from session
            sessionEntries.removeIf(e -> e.id.equals(entry.id));
            entryList.removeView(row);
            updateTotalSummary();
        });
        row.addView(tvDel);

        entryList.addView(row);
    }

    private TextView makeCell(String text, int widthDp, boolean bold, String color) {
        TextView tv = new TextView(getContext());
        LinearLayout.LayoutParams lp;
        if (widthDp == 0) {
            lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            int px = (int) (widthDp * getResources().getDisplayMetrics().density);
            lp = new LinearLayout.LayoutParams(px, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    // ── Load existing entries for current date+shift ──
    private void loadEntriesForCurrentDateShift() {
        entryList.removeAllViews();
        sessionEntries.clear();
        customerList = dm.getCustomers();

        String date = etDate.getText().toString().trim();
        List<MilkEntry> all = dm.getEntries();

        for (MilkEntry e : all) {
            if (e.date.equals(date) && e.shift.equalsIgnoreCase(selectedShift)) {
                sessionEntries.add(e);
                Customer c = findCustomer(e.cid);
                if (c != null) {
                    double total = e.qty * e.rate;
                    addEntryRowToList(e, c, total);
                }
            }
        }
        updateTotalSummary();
    }

    private Customer findCustomer(String cid) {
        for (Customer c : customerList) {
            if (c.id.equals(cid)) return c;
        }
        return null;
    }

    // ── Update bottom total bar ──
    private void updateTotalSummary() {
        double total = 0;
        for (MilkEntry e : sessionEntries) total += e.qty * e.rate;
        tvTotalSummary.setText(String.format(Locale.getDefault(),
                "Total: %d entries | ₹%.2f", sessionEntries.size(), total));
    }

    // ── Print Slip as PDF ──
    private void printSlipPdf() {
        if (sessionEntries.isEmpty()) {
            Toast.makeText(getContext(), "Koi entry nahi hai!", Toast.LENGTH_SHORT).show();
            return;
        }

        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = doc.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        int y = 40;

        // Title
        paint.setTextSize(20);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setColor(Color.parseColor("#1B5E20"));
        canvas.drawText("Dairy Hisaab - Collection Slip", 40, y, paint);
        y += 28;

        // Date + Shift
        paint.setTextSize(13);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(Color.DKGRAY);
        canvas.drawText("Date: " + etDate.getText().toString() +
                "   Shift: " + selectedShift, 40, y, paint);
        y += 20;

        // Divider
        paint.setColor(Color.parseColor("#1B5E20"));
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(40, y, 555, y, paint);
        y += 16;

        // Table header
        paint.setTextSize(12);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setColor(Color.BLACK);
        canvas.drawText("Code", 40, y, paint);
        canvas.drawText("Member", 100, y, paint);
        canvas.drawText("Qty", 310, y, paint);
        canvas.drawText("Fat", 370, y, paint);
        canvas.drawText("Amount", 460, y, paint);
        y += 6;
        paint.setStrokeWidth(0.8f);
        canvas.drawLine(40, y, 555, y, paint);
        y += 14;

        // Rows
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(11);
        double grandTotal = 0;

        for (MilkEntry e : sessionEntries) {
            Customer c = findCustomer(e.cid);
            String name = c != null ? c.name : e.cid;
            String code = c != null ? c.memberCode : "-";
            double amt  = e.qty * e.rate;
            grandTotal += amt;

            paint.setColor(Color.BLACK);
            canvas.drawText(code, 40, y, paint);
            canvas.drawText(name, 100, y, paint);
            canvas.drawText(String.format(Locale.getDefault(), "%.1f L", e.qty), 310, y, paint);
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", e.fat), 370, y, paint);
            paint.setColor(Color.parseColor("#2E7D32"));
            canvas.drawText(String.format(Locale.getDefault(), "Rs %.2f", amt), 460, y, paint);
            y += 18;
        }

        // Grand total
        y += 4;
        paint.setColor(Color.parseColor("#1B5E20"));
        paint.setStrokeWidth(1f);
        canvas.drawLine(40, y, 555, y, paint);
        y += 16;
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(13);
        paint.setColor(Color.BLACK);
        canvas.drawText("Grand Total:", 310, y, paint);
        paint.setColor(Color.parseColor("#1B5E20"));
        canvas.drawText(String.format(Locale.getDefault(), "Rs %.2f", grandTotal), 460, y, paint);

        doc.finishPage(page);

        // Save PDF
        try {
            File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir != null && !dir.exists()) dir.mkdirs();
            String fileName = "slip_" + etDate.getText().toString() + "_" + selectedShift + ".pdf";
            File file = new File(dir, fileName);
            doc.writeTo(new FileOutputStream(file));
            doc.close();

            // Open PDF
            Uri uri = FileProvider.getUriForFile(getContext(),
                    getContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(getContext(), "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ── Date helpers ──
    private void openDatePicker() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date current = sdf.parse(etDate.getText().toString().trim());
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(current);
            new android.app.DatePickerDialog(getContext(),
                (picker, y, m, d) -> {
                    java.util.Calendar nc = java.util.Calendar.getInstance();
                    nc.set(y, m, d);
                    etDate.setText(sdf.format(nc.getTime()));
                    loadEntriesForCurrentDateShift();
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Date error!", Toast.LENGTH_SHORT).show();
        }
    }

    private void changeDate(int days) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date d = sdf.parse(etDate.getText().toString().trim());
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(d);
            cal.add(java.util.Calendar.DAY_OF_MONTH, days);
            etDate.setText(sdf.format(cal.getTime()));
            loadEntriesForCurrentDateShift();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Date error!", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectShift(String shift) {
        selectedShift = shift;
        if (shift.equals("Subah")) {
            btnMorning.setBackgroundColor(Color.WHITE);
            btnMorning.setTextColor(Color.parseColor("#1B5E20"));
            btnEvening.setBackgroundColor(Color.TRANSPARENT);
            btnEvening.setTextColor(Color.WHITE);
        } else {
            btnEvening.setBackgroundColor(Color.WHITE);
            btnEvening.setTextColor(Color.parseColor("#1B5E20"));
            btnMorning.setBackgroundColor(Color.TRANSPARENT);
            btnMorning.setTextColor(Color.WHITE);
        }
        loadEntriesForCurrentDateShift();
    }
                }
