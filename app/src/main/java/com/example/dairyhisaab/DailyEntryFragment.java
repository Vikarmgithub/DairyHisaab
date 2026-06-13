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
import com.google.firebase.auth.FirebaseAuth;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DailyEntryFragment extends Fragment {

    private EditText etDate;
    private TextView btnMorning, btnEvening, btnPrevDay, btnNextDay;
    private TextView tvRateBanner;

    private EditText etQuickCode, etQuickQty, etQuickFat;
    private TextView tvQuickName, tvQuickAmount, tvAnimalToggle;
    private Button btnQuickSave;

    private LinearLayout entryList;
    private TextView tvTotalSummary;
    private Button btnPrintSlip;

    private String selectedShift = "Subah";
    private boolean isBuffalo = true;

    private double buffFatRate = 0, buffCommission = 0;
    private double cowFatRate = 0, cowCommission = 0;
    private double pfPerLiter = 0;

    private DairyDataManager dm;
    private List<Customer> customerList = new ArrayList<>();
    private List<MilkEntry> sessionEntries = new ArrayList<>();
    private Customer foundCustomer = null;

    public DailyEntryFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily, container, false);

        dm = DairyDataManager.getInstance(getContext());

        etDate         = view.findViewById(R.id.etDate);
        btnMorning     = view.findViewById(R.id.btnMorning);
        btnEvening     = view.findViewById(R.id.btnEvening);
        btnPrevDay     = view.findViewById(R.id.btnPrevDay);
        btnNextDay     = view.findViewById(R.id.btnNextDay);
        tvRateBanner   = view.findViewById(R.id.tvRateBanner);

        etQuickCode    = view.findViewById(R.id.etQuickCode);
        etQuickQty     = view.findViewById(R.id.etQuickQty);
        etQuickFat     = view.findViewById(R.id.etQuickFat);
        tvQuickName    = view.findViewById(R.id.tvQuickName);
        tvQuickAmount  = view.findViewById(R.id.tvQuickAmount);
        tvAnimalToggle = view.findViewById(R.id.tvAnimalToggle);
        btnQuickSave   = view.findViewById(R.id.btnQuickSave);

        entryList      = view.findViewById(R.id.entryList);
        tvTotalSummary = view.findViewById(R.id.tvTotalSummary);
        btnPrintSlip   = view.findViewById(R.id.btnPrintSlip);

        // Today's date
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        etDate.setText(today);

        // Load active rates
        loadActiveRates();

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
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                lookupMember(s.toString().trim());
            }
        });

        // Qty/Fat auto calc
        TextWatcher calcWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                recalcQuickAmount();
            }
        };
        etQuickQty.addTextChangedListener(calcWatcher);
        etQuickFat.addTextChangedListener(calcWatcher);

        btnQuickSave.setOnClickListener(v -> saveQuickEntry());
        btnPrintSlip.setOnClickListener(v -> printSlipPdf());

        loadEntriesForCurrentDateShift();

        return view;
    }

    // ── Active rate load karo ──
    private void loadActiveRates() {
        String date = etDate != null ? etDate.getText().toString().trim()
                : new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        RateEntry active = dm.getActiveRate(date);
        if (active != null) {
            buffFatRate    = active.rate;
            buffCommission = active.base;
            cowFatRate     = active.rate;
            cowCommission  = active.base;
            pfPerLiter     = active.pfPerLiter;
            tvRateBanner.setText("भैंस: ₹" + buffFatRate + "(+" + buffCommission +
                    ") | PF: ₹" + pfPerLiter + "/L");
        } else {
            tvRateBanner.setText("⚠️ Rate set nahi hai — Rate tab mein jaayen");
        }
    }

    // ── Member lookup ──
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
            double qty  = Double.parseDouble(qStr);
            double fat  = Double.parseDouble(fStr);
            double rate = (isBuffalo ? buffFatRate : cowFatRate) * fat / 100.0
                        + (isBuffalo ? buffCommission : cowCommission);
            double gross = qty * rate;
            double pf    = qty * pfPerLiter;
            double net   = gross - pf;
            tvQuickAmount.setText(String.format(Locale.getDefault(), "₹%.2f", net));
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

            MilkEntry entry = new MilkEntry();
            entry.id    = System.currentTimeMillis() + "_" + foundCustomer.id;
            entry.cid   = foundCustomer.id;
            entry.date  = etDate.getText().toString().trim();
            entry.shift = selectedShift;
            entry.qty   = qty;
            entry.fat   = fat;
            entry.rate  = rate;

            List<MilkEntry> all = dm.getEntries();
            all.add(entry);
            dm.saveEntries(all);

            sessionEntries.add(entry);
            addEntryRowToList(entry, foundCustomer);
            updateTotalSummary();

            // Clear
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

    // ── Add row to list ──
    private void addEntryRowToList(MilkEntry entry, Customer c) {
        double gross = entry.qty * entry.rate;
        double pf    = entry.qty * pfPerLiter;
        double net   = gross - pf;

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(12, 10, 12, 10);
        row.setTag(entry.id);
        int rowColor = (entryList.getChildCount() % 2 == 0)
                ? Color.WHITE : Color.parseColor("#F1F8E9");
        row.setBackgroundColor(rowColor);

        // Code
        row.addView(makeCell(c.memberCode, 42, false, "#1B5E20"));
        // Name
        TextView tvName = makeCell(c.name, 0, true, "#212121");
        ((LinearLayout.LayoutParams) tvName.getLayoutParams()).weight = 2f;
        row.addView(tvName);
        // Milk
        row.addView(makeCellCenter(
                String.format(Locale.getDefault(), "%.1f", entry.qty), 40, false, "#212121"));
        // Fat
        row.addView(makeCellCenter(
                String.format(Locale.getDefault(), "%.1f", entry.fat), 36, false, "#212121"));
        // Rate
        row.addView(makeCellCenter(
                String.format(Locale.getDefault(), "%.1f", entry.rate), 42, false, "#212121"));
        // PF Deduction
        row.addView(makeCellCenter(
                String.format(Locale.getDefault(), "₹%.1f", pf), 44, false, "#C62828"));
        // Rest Amount
        row.addView(makeCellEnd(
                String.format(Locale.getDefault(), "₹%.2f", net), 56, true, "#2E7D32"));

        // Delete
        TextView tvDel = new TextView(getContext());
        tvDel.setLayoutParams(new LinearLayout.LayoutParams(44,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        tvDel.setText("🗑");
        tvDel.setTextSize(14);
        tvDel.setGravity(android.view.Gravity.CENTER);
        tvDel.setClickable(true);
        tvDel.setFocusable(true);
        tvDel.setOnClickListener(v -> {
            List<MilkEntry> all = dm.getEntries();
            all.removeIf(e -> e.id.equals(entry.id));
            dm.saveEntries(all);
            sessionEntries.removeIf(e -> e.id.equals(entry.id));
            entryList.removeView(row);
            updateTotalSummary();
        });
        row.addView(tvDel);

        entryList.addView(row);
    }

    private TextView makeCell(String text, int widthDp, boolean bold, String color) {
        TextView tv = new TextView(getContext());
        LinearLayout.LayoutParams lp = widthDp == 0
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private TextView makeCellCenter(String text, int widthDp, boolean bold, String color) {
        TextView tv = makeCell(text, widthDp, bold, color);
        tv.setGravity(android.view.Gravity.CENTER);
        return tv;
    }

    private TextView makeCellEnd(String text, int widthDp, boolean bold, String color) {
        TextView tv = makeCell(text, widthDp, bold, color);
        tv.setGravity(android.view.Gravity.END);
        return tv;
    }

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }

    // ── Load saved entries ──
    private void loadEntriesForCurrentDateShift() {
        entryList.removeAllViews();
        sessionEntries.clear();
        loadActiveRates();
        customerList = dm.getCustomers();
        String date = etDate.getText().toString().trim();

        for (MilkEntry e : dm.getEntries()) {
            if (e.date.equals(date) && e.shift.equalsIgnoreCase(selectedShift)) {
                sessionEntries.add(e);
                Customer c = findCustomer(e.cid);
                if (c != null) addEntryRowToList(e, c);
            }
        }
        updateTotalSummary();
    }

    private Customer findCustomer(String cid) {
        for (Customer c : customerList) if (c.id.equals(cid)) return c;
        return null;
    }

    private void updateTotalSummary() {
        double gross = 0, pf = 0;
        for (MilkEntry e : sessionEntries) {
            gross += e.qty * e.rate;
            pf    += e.qty * pfPerLiter;
        }
        double net = gross - pf;
        tvTotalSummary.setText(String.format(Locale.getDefault(),
                "%d entries | Gross:₹%.2f PF:₹%.2f Net:₹%.2f",
                sessionEntries.size(), gross, pf, net));
    }

    // ── Print Slip PDF ──
    private void printSlipPdf() {
        if (sessionEntries.isEmpty()) {
            Toast.makeText(getContext(), "Koi entry nahi hai!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Dairy naam Firebase se
        String dairyName = "Dairy Hisaab";
        try {
            com.google.firebase.auth.FirebaseUser user =
                    FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getDisplayName() != null
                    && !user.getDisplayName().isEmpty()) {
                dairyName = user.getDisplayName();
            }
        } catch (Exception ignored) {}

        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = doc.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint p = new Paint();
        p.setAntiAlias(true);

        int y = 45;
        int left = 40;

        // ── Dairy Name ──
        p.setTextSize(22);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(Color.parseColor("#1B5E20"));
        canvas.drawText(dairyName, left, y, p);
        y += 26;

        // ── Subtitle ──
        p.setTextSize(12);
        p.setTypeface(Typeface.DEFAULT);
        p.setColor(Color.DKGRAY);
        canvas.drawText("Collection Slip  |  " + selectedShift + " Shift  |  " +
                etDate.getText().toString(), left, y, p);
        y += 8;

        // ── Divider ──
        p.setColor(Color.parseColor("#1B5E20"));
        p.setStrokeWidth(1.5f);
        canvas.drawLine(left, y, 555, y, p);
        y += 18;

        // ── Member summary (single entry slip style) ──
        // Agar sirf ek member ki entry ho to uski detail upar dikhao
        if (sessionEntries.size() == 1) {
            MilkEntry e  = sessionEntries.get(0);
            Customer  c  = findCustomer(e.cid);
            if (c != null) {
                p.setTextSize(13);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setColor(Color.BLACK);
                canvas.drawText("Member: " + c.name, left, y, p);
                y += 18;
                p.setTypeface(Typeface.DEFAULT);
                p.setTextSize(12);
                p.setColor(Color.DKGRAY);
                String fh = (c.fatherHusband != null && !c.fatherHusband.isEmpty())
                        ? "S/o W/o: " + c.fatherHusband : "";
                if (!fh.isEmpty()) { canvas.drawText(fh, left, y, p); y += 16; }
                canvas.drawText("Code No.: " + c.memberCode +
                        (c.uniqueId != null && !c.uniqueId.isEmpty()
                                ? "   Un.No.: " + c.uniqueId : ""), left, y, p);
                y += 20;
                p.setStrokeWidth(0.5f);
                p.setColor(Color.LTGRAY);
                canvas.drawLine(left, y, 555, y, p);
                y += 16;
            }
        }

        // ── Table Header ──
        p.setTextSize(11);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(Color.parseColor("#1B5E20"));
        int[] cols = {left, left+60, left+180, left+240, left+295, left+365, left+440};
        canvas.drawText("Code",    cols[0], y, p);
        canvas.drawText("Member",  cols[1], y, p);
        canvas.drawText("Milk L",  cols[2], y, p);
        canvas.drawText("Fat",     cols[3], y, p);
        canvas.drawText("Rate",    cols[4], y, p);
        canvas.drawText("PF Ded.", cols[5], y, p);
        canvas.drawText("Net Amt", cols[6], y, p);
        y += 6;
        p.setStrokeWidth(1f);
        p.setColor(Color.parseColor("#1B5E20"));
        canvas.drawLine(left, y, 555, y, p);
        y += 14;

        // ── Rows ──
        double grandGross = 0, grandPf = 0;
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(11);

        for (MilkEntry e : sessionEntries) {
            Customer c    = findCustomer(e.cid);
            String name   = c != null ? c.name : e.cid;
            String code   = c != null ? c.memberCode : "-";
            double gross  = e.qty * e.rate;
            double pf     = e.qty * pfPerLiter;
            double net    = gross - pf;
            grandGross   += gross;
            grandPf      += pf;

            p.setColor(Color.BLACK);
            canvas.drawText(code,  cols[0], y, p);
            canvas.drawText(name,  cols[1], y, p);
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", e.qty),  cols[2], y, p);
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", e.fat),  cols[3], y, p);
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", e.rate), cols[4], y, p);
            p.setColor(Color.parseColor("#C62828"));
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", pf),     cols[5], y, p);
            p.setColor(Color.parseColor("#2E7D32"));
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", net),    cols[6], y, p);
            y += 18;
        }

        // ── Grand Total ──
        y += 4;
        p.setColor(Color.DKGRAY);
        p.setStrokeWidth(0.8f);
        canvas.drawLine(left, y, 555, y, p);
        y += 16;
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(12);
        p.setColor(Color.BLACK);
        canvas.drawText("TOTAL:", cols[4], y, p);
        p.setColor(Color.parseColor("#C62828"));
        canvas.drawText(String.format(Locale.getDefault(), "%.2f", grandPf), cols[5], y, p);
        p.setColor(Color.parseColor("#2E7D32"));
        canvas.drawText(String.format(Locale.getDefault(), "%.2f", grandGross - grandPf),
                cols[6], y, p);
        y += 30;

        // ── Footer ──
        p.setTextSize(10);
        p.setTypeface(Typeface.DEFAULT);
        p.setColor(Color.GRAY);
        canvas.drawText("Generated by Dairy Hisaab App", left, y, p);

        doc.finishPage(page);

        // ── Save & Open ──
        try {
            File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir != null && !dir.exists()) dir.mkdirs();
            String fileName = "slip_" + etDate.getText().toString()
                    + "_" + selectedShift + ".pdf";
            File file = new File(dir, fileName);
            doc.writeTo(new FileOutputStream(file));
            doc.close();

            Uri uri = FileProvider.getUriForFile(getContext(),
                    getContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(getContext(), "PDF error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
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
