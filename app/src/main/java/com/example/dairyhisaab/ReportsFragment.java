package com.example.dairyhisaab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ReportsFragment extends Fragment {

    DairyDataManager dm;
    private String activeDairyName = "My Dairy"; // Default fallback name

    private List<ReportRow> currentReportData = new ArrayList<>();
    private String currentFrom = "";
    private String currentTo   = "";

    private List<StatementRow> currentStatementData = new ArrayList<>();
    private String statCustomerName = "";
    private String statCustomerCode = "";
    private double statPfPerLiter   = 0;

    private List<PfStatementRow> calculatedPfData = new ArrayList<>();
    private String selectedPfMonthName = "";

    // Modes: "report" (Bhugtan Summary), "statement" (Individual List), "pf_statement" (Monthwise PF)
    private String currentMode = "report";

    // ── Models ────────────────────────────────────────────────────────────────

    static class ReportRow {
        String memberCode, name;
        double totalLitres, totalBill, pfAmount, payableAmount, totalPaid, baaki;
        int entryCount;
    }

    static class StatementRow {
        String date, shift;
        double qty, fat, rate, amount;
    }

    static class PfStatementRow {
        String uid, memberCode, name;
        double totalMilk, avgFat, grossAmount, pfAmount, netPaid;
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reports, container, false);
        dm = DairyDataManager.getInstance(getContext());

        // Settings se set kiya gaya dairy ka naam (sab jagah ek hi jagah se)
        activeDairyName = dm.getDairyName();

        LinearLayout layoutFilters = view.findViewById(R.id.layoutFilters);
        LinearLayout layoutPFMonthSelector = view.findViewById(R.id.layoutPFMonthSelector);
        Spinner spinnerPfMonths = view.findViewById(R.id.spinnerPfMonths);
        TextView tvModeTitle = view.findViewById(R.id.tvModeTitle);

        Button btnTabBhugtan = view.findViewById(R.id.btnTabBhugtan);
        Button btnTabStatement = view.findViewById(R.id.btnTabStatement);
        Button btnTabPFStatement = view.findViewById(R.id.btnTabPFStatement);
        Button btnGeneratePF = view.findViewById(R.id.btnGeneratePF);

        // Current Year Months list injection
        String[] pfMonths = {"2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11", "2026-12"};
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, pfMonths);
        if (spinnerPfMonths != null) {
            spinnerPfMonths.setAdapter(monthAdapter);
        }

        String today = DairyDataManager.today();
        String monthStart = today.substring(0, 7) + "-01";
        ((EditText) view.findViewById(R.id.etFrom)).setText(monthStart);
        ((EditText) view.findViewById(R.id.etTo)).setText(today);

        // --- CLEAN TAB SWITCHING CONTROLLER ---
        btnTabBhugtan.setOnClickListener(v -> {
            currentMode = "report";
            tvModeTitle.setText("💰 Bhugtan Report (Date to Date)");
            layoutFilters.setVisibility(View.VISIBLE);
            layoutPFMonthSelector.setVisibility(View.GONE);
            updateTabColors(btnTabBhugtan, btnTabStatement, btnTabPFStatement);
            generateReport(view);
        });

        btnTabStatement.setOnClickListener(v -> {
            currentMode = "statement";
            tvModeTitle.setText("📋 Customer Statement");
            layoutFilters.setVisibility(View.VISIBLE);
            layoutPFMonthSelector.setVisibility(View.GONE);
            updateTabColors(btnTabStatement, btnTabBhugtan, btnTabPFStatement);
            generateStatement(view);
        });

        btnTabPFStatement.setOnClickListener(v -> {
            currentMode = "pf_statement";
            tvModeTitle.setText("🏦 Monthly PF Statement");
            layoutFilters.setVisibility(View.GONE);
            layoutPFMonthSelector.setVisibility(View.VISIBLE);
            updateTabColors(btnTabPFStatement, btnTabBhugtan, btnTabStatement);
            generatePfStatement(view);
        });

        // Click Actions
        view.findViewById(R.id.btnGenerate).setOnClickListener(v -> {
            if (currentMode.equals("report")) generateReport(view);
            else if (currentMode.equals("statement")) generateStatement(view);
        });
        
        if (btnGeneratePF != null) {
            btnGeneratePF.setOnClickListener(v -> generatePfStatement(view));
        }

        view.findViewById(R.id.btnExportExcel).setOnClickListener(v -> exportExcel());
        view.findViewById(R.id.btnExportPdf).setOnClickListener(v -> exportPdf());
        view.findViewById(R.id.btnPrint).setOnClickListener(v -> printReport());
        view.findViewById(R.id.btnWhatsApp).setOnClickListener(v -> shareStatementOnWhatsApp());

        generateReport(view);
        return view;
    }

    private void updateTabColors(Button active, Button in1, Button in2) {
        active.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0D47A1")));
        active.setTextColor(Color.WHITE);
        in1.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#ECEFF1")));
        in1.setTextColor(Color.parseColor("#37474F"));
        in2.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#ECEFF1")));
        in2.setTextColor(Color.parseColor("#37474F"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  1. SUMMARY REPORT (BHUGTAN)
    // ─────────────────────────────────────────────────────────────────────────

    void generateReport(View view) {
        currentMode = "report";
        String from = ((EditText) view.findViewById(R.id.etFrom)).getText().toString().trim();
        String to   = ((EditText) view.findViewById(R.id.etTo)).getText().toString().trim();
        String customerFilter = ((EditText) view.findViewById(R.id.etCustomerFilter)).getText().toString().trim().toLowerCase(Locale.getDefault());

        currentFrom = from; currentTo = to;
        currentReportData.clear();

        LinearLayout reportList = view.findViewById(R.id.reportList);
        if (reportList == null) return;
        reportList.removeAllViews();

        List<Customer>  customers = dm.getCustomers();
        List<MilkEntry> entries   = dm.getEntries();
        List<Payment>   payments  = dm.getPayments();

        RateEntry activeRate = dm.getActiveRate(to);
        double pfPerLiter = (activeRate != null) ? activeRate.pfPerLiter : 0.0;

        if (customers.isEmpty()) {
            showEmptyMessage(reportList, "Koi member nahi.");
            view.findViewById(R.id.layoutExportButtons).setVisibility(View.GONE);
            return;
        }

        int shown = 0;
        for (Customer c : customers) {
            if (c == null) continue;
            if (!customerFilter.isEmpty()) {
                boolean matchName = c.name != null && c.name.toLowerCase(Locale.getDefault()).contains(customerFilter);
                boolean matchCode = c.memberCode != null && c.memberCode.toLowerCase(Locale.getDefault()).contains(customerFilter);
                if (!matchName && !matchCode) continue;
            }

            List<MilkEntry> cEntries = new ArrayList<>();
            if (entries != null) {
                for (MilkEntry e : entries)
                    if (e != null && e.cid != null && e.cid.equals(c.id) && e.date != null && e.date.compareTo(from) >= 0 && e.date.compareTo(to) <= 0)
                        cEntries.add(e);
            }

            List<Payment> cPays = new ArrayList<>();
            if (payments != null) {
                for (Payment p : payments)
                    if (p != null && p.cid != null && p.cid.equals(c.id) && p.date != null && p.date.compareTo(from) >= 0 && p.date.compareTo(to) <= 0)
                        cPays.add(p);
            }

            double totalLitres = 0, bill = 0;
            for (MilkEntry e : cEntries) { totalLitres += e.qty; bill += e.qty * e.rate; }

            double pfAmount      = totalLitres * pfPerLiter;
            double payableAmount = bill - pfAmount;
            double paid = 0;
            for (Payment p : cPays) paid += p.amount;
            double baaki = payableAmount - paid;

            ReportRow row = new ReportRow();
            row.memberCode = c.memberCode; row.name = c.name;
            row.totalLitres = totalLitres; row.totalBill = bill;
            row.pfAmount = pfAmount; row.payableAmount = payableAmount;
            row.totalPaid = paid; row.baaki = baaki; row.entryCount = cEntries.size();
            currentReportData.add(row);

            LinearLayout card = makeCard(12);
            TextView tvName = new TextView(getContext());
            tvName.setText("[" + c.memberCode + "]  " + c.name);
            tvName.setTextColor(0xFF212121); tvName.setTextSize(14);
            tvName.setTypeface(null, Typeface.BOLD);
            card.addView(tvName);

            addRow(card, "Entries",       String.valueOf(cEntries.size()));
            addRow(card, "Kul Doodh",     String.format(Locale.getDefault(), "%.2f L", totalLitres));
            addRow(card, "Total Bill",    String.format(Locale.getDefault(), "₹%.2f", bill));

            LinearLayout pfRow = buildRow("PF Katoti (@₹" + String.format(Locale.getDefault(), "%.2f", pfPerLiter) + "/L)", String.format(Locale.getDefault(), "₹%.2f", pfAmount));
            ((TextView) pfRow.getChildAt(1)).setTextColor(Color.parseColor("#1565C0"));
            card.addView(pfRow);

            LinearLayout payRow = buildRow("Payable Amount", String.format(Locale.getDefault(), "₹%.2f", payableAmount));
            ((TextView) payRow.getChildAt(1)).setTextColor(Color.parseColor("#0D47A1"));
            ((TextView) payRow.getChildAt(1)).setTypeface(null, Typeface.BOLD);
            card.addView(payRow);

            addRow(card, "Paid", String.format(Locale.getDefault(), "₹%.2f", paid));

            LinearLayout baakiRow = buildRow("Baaki", String.format(Locale.getDefault(), "₹%.2f", baaki));
            ((TextView) baakiRow.getChildAt(1)).setTextColor(baaki > 0 ? Color.RED : Color.parseColor("#2E7D32"));
            card.addView(baakiRow);

            reportList.addView(card);
            shown++;
        }

        if (shown == 0) showEmptyMessage(reportList, "Koi data nahi mila.");
        view.findViewById(R.id.layoutExportButtons).setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.btnWhatsApp).setVisibility(View.GONE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  2. CUSTOMER STATEMENT (INDIVIDUAL)
    // ─────────────────────────────────────────────────────────────────────────

    void generateStatement(View view) {
        currentMode = "statement";
        String from   = ((EditText) view.findViewById(R.id.etFrom)).getText().toString().trim();
        String to     = ((EditText) view.findViewById(R.id.etTo)).getText().toString().trim();
        String filter = ((EditText) view.findViewById(R.id.etCustomerFilter)).getText().toString().trim().toLowerCase(Locale.getDefault());

        if (filter.isEmpty()) {
            Toast.makeText(getContext(), "Statement ke liye customer naam ya code likho!", Toast.LENGTH_SHORT).show();
            return;
        }

        currentFrom = from; currentTo = to;
        currentStatementData.clear();

        LinearLayout reportList = view.findViewById(R.id.reportList);
        if (reportList == null) return;
        reportList.removeAllViews();

        List<Customer>  customers = dm.getCustomers();
        List<MilkEntry> entries   = dm.getEntries();
        List<Payment>   payments  = dm.getPayments();

        RateEntry activeRate = dm.getActiveRate(to);
        statPfPerLiter = (activeRate != null) ? activeRate.pfPerLiter : 0.0;

        Customer found = null;
        if (customers != null) {
            for (Customer c : customers) {
                if (c == null) continue;
                boolean matchName = c.name != null && c.name.toLowerCase(Locale.getDefault()).contains(filter);
                boolean matchCode = c.memberCode != null && c.memberCode.toLowerCase(Locale.getDefault()).contains(filter);
                if (matchName || matchCode) { found = c; break; }
            }
        }

        if (found == null) {
            showEmptyMessage(reportList, "Customer nahi mila: " + filter);
            view.findViewById(R.id.layoutExportButtons).setVisibility(View.GONE);
            return;
        }

        statCustomerName = found.name != null ? found.name : "";
        statCustomerCode = found.memberCode != null ? found.memberCode : "";

        List<MilkEntry> cEntries = new ArrayList<>();
        if (entries != null) {
            for (MilkEntry e : entries)
                if (e != null && e.cid != null && e.cid.equals(found.id) && e.date != null && e.date.compareTo(from) >= 0 && e.date.compareTo(to) <= 0)
                    cEntries.add(e);
        }

        Collections.sort(cEntries, (a, b) -> {
            if (a == null || b == null || a.date == null || b.date == null) return 0;
            int d = a.date.compareTo(b.date);
            if (d != 0) return d;
            return (b.shift != null ? b.shift : "").compareTo(a.shift != null ? a.shift : "");
        });

        List<Payment> cPays = new ArrayList<>();
        if (payments != null) {
            for (Payment p : payments)
                if (p != null && p.cid != null && p.cid.equals(found.id) && p.date != null && p.date.compareTo(from) >= 0 && p.date.compareTo(to) <= 0)
                    cPays.add(p);
        }

        LinearLayout headerCard = makeCard(8);
        headerCard.setBackgroundColor(0xFF0D47A1);

        TextView tvTitle = new TextView(getContext());
        tvTitle.setText("📋 Customer Statement"); tvTitle.setTextColor(Color.WHITE); tvTitle.setTextSize(15); tvTitle.setTypeface(null, Typeface.BOLD);
        headerCard.addView(tvTitle);

        TextView tvCust = new TextView(getContext());
        tvCust.setText("[" + statCustomerCode + "]  " + statCustomerName); tvCust.setTextColor(0xFFFFF176); tvCust.setTextSize(13); tvCust.setTypeface(null, Typeface.BOLD);
        headerCard.addView(tvCust);
        reportList.addView(headerCard);

        if (cEntries.isEmpty()) {
            showEmptyMessage(reportList, "Is period mein koi entry nahi.");
            return;
        }

        reportList.addView(buildStatementColHeader());

        double totalLitres = 0, totalBill = 0; int sNo = 1;
        for (MilkEntry e : cEntries) {
            if (e == null) continue;
            double amount = e.qty * e.rate;
            totalLitres += e.qty; totalBill += amount;

            StatementRow sr = new StatementRow();
            sr.date = e.date; sr.shift = e.shift; sr.qty = e.qty; sr.fat = e.fat; sr.rate = e.rate; sr.amount = amount;
            currentStatementData.add(sr);

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(8, 10, 8, 10);
            row.setBackgroundColor(sNo % 2 == 0 ? 0xFFF0F7FF : 0xFFFFFFFF);

            String shiftLabel = (e.shift != null && (e.shift.equalsIgnoreCase("Subah") || e.shift.equalsIgnoreCase("M"))) ? "☀️" : "🌙";
            
            addStatCol(row, String.valueOf(sNo), 0.5f, 0xFF212121, false);
            addStatCol(row, e.date != null && e.date.length() >= 5 ? e.date.substring(5) : "", 1.5f, 0xFF212121, false);
            addStatCol(row, shiftLabel, 0.5f, 0xFF212121, false);
            addStatCol(row, fmt2(e.qty), 1f, 0xFF1565C0, true);
            addStatCol(row, fmt1(e.fat), 0.8f, 0xFF212121, false);
            addStatCol(row, fmt2(e.rate), 0.8f, 0xFF212121, false);
            addStatCol(row, fmt2(amount), 1.2f, 0xFF2E7D32, true);
            reportList.addView(row);
            sNo++;
        }

        double pfAmount = totalLitres * statPfPerLiter;
        double payableAmount = totalBill - pfAmount;
        double paid = 0; for (Payment p : cPays) if (p != null) paid += p.amount;
        double baaki = payableAmount - paid;

        LinearLayout summaryCard = makeCard(4);
        summaryCard.setBackgroundColor(0xFFE8F5E9);
        addRow(summaryCard, "Kul Doodh", fmt2(totalLitres) + " L");
        addRow(summaryCard, "Total Bill", "₹" + fmt2(totalBill));
        addRow(summaryCard, "PF Katoti", "₹" + fmt2(pfAmount));
        addRow(summaryCard, "Payable Amount", "₹" + fmt2(payableAmount));
        addRow(summaryCard, "Paid", "₹" + fmt2(paid));
        addRow(summaryCard, "Baaki", "₹" + fmt2(baaki));
        reportList.addView(summaryCard);

        view.findViewById(R.id.layoutExportButtons).setVisibility(View.VISIBLE);
        view.findViewById(R.id.btnWhatsApp).setVisibility(View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  3. MONTHLY PF SUMMARY
    // ─────────────────────────────────────────────────────────────────────────

    void generatePfStatement(View view) {
        currentMode = "pf_statement";
        Spinner spinnerPfMonths = view.findViewById(R.id.spinnerPfMonths);
        if (spinnerPfMonths == null || spinnerPfMonths.getSelectedItem() == null) return;

        selectedPfMonthName = spinnerPfMonths.getSelectedItem().toString();
        calculatedPfData.clear();

        LinearLayout reportList = view.findViewById(R.id.reportList);
        if (reportList == null) return;
        reportList.removeAllViews();

        List<Customer> customers  = dm.getCustomers();
        List<MilkEntry> entries   = dm.getEntries();

        String targetRateDate = selectedPfMonthName + "-28"; 
        RateEntry activeRate = dm.getActiveRate(targetRateDate);
        double pfPerLiter = (activeRate != null) ? activeRate.pfPerLiter : 0.0;

        if (customers == null || customers.isEmpty()) {
            showEmptyMessage(reportList, "Database mein koi customer nahi mila.");
            view.findViewById(R.id.layoutExportButtons).setVisibility(View.GONE);
            return;
        }

        LinearLayout headerCard = makeCard(8);
        headerCard.setBackgroundColor(0xFF1B365D); 
        TextView tvTitle = new TextView(getContext());
        tvTitle.setText("🏦 Monthly PF Summary Ledger"); tvTitle.setTextColor(Color.WHITE); tvTitle.setTypeface(null, Typeface.BOLD); headerCard.addView(tvTitle);
        TextView tvMonth = new TextView(getContext());
        tvMonth.setText("Target Month: " + selectedPfMonthName); tvMonth.setTextColor(0xFFFFF176); headerCard.addView(tvMonth);
        reportList.addView(headerCard);

        int count = 0;
        for (Customer c : customers) {
            if (c == null || c.id == null) continue;
            double milkQty = 0; double fatProduct = 0; double grossAmt = 0;

            if (entries != null) {
                for (MilkEntry e : entries) {
                    if (e != null && e.cid != null && e.cid.equals(c.id) && e.date != null && e.date.startsWith(selectedPfMonthName)) {
                        milkQty += e.qty;
                        fatProduct += (e.qty * e.fat);
                        grossAmt += (e.qty * e.rate);
                    }
                }
            }

            if (milkQty == 0) continue;

            // Simple weighted average fat setup
            double avgFat = (milkQty > 0) ? (fatProduct / milkQty) : 0.0;
            double pfAmount = milkQty * pfPerLiter;
            double netPaid = grossAmt - pfAmount;
            
            String cleanId = c.id.replaceAll("\\D+", "");
            int parsedId = 0;
            try { if (!cleanId.isEmpty()) parsedId = Integer.parseInt(cleanId); } catch (Exception e) { parsedId = 0; }
            String uniqueId = "UID-" + (2000 + parsedId);

            PfStatementRow row = new PfStatementRow();
            row.uid = uniqueId; row.memberCode = c.memberCode != null ? c.memberCode : ""; row.name = c.name != null ? c.name : "Unknown";
            row.totalMilk = milkQty; row.avgFat = avgFat; row.grossAmount = grossAmt; row.pfAmount = pfAmount; row.netPaid = netPaid;
            calculatedPfData.add(row);

            LinearLayout card = makeCard(10);
            TextView tvCust = new TextView(getContext());
            tvCust.setText(uniqueId + " | [" + row.memberCode + "] " + row.name);
            tvCust.setTypeface(null, Typeface.BOLD); tvCust.setTextColor(0xFF1B365D);
            card.addView(tvCust);

            addRow(card, "Total Milk Quantity", fmt2(milkQty) + " Ltr");
            addRow(card, "Weighted Avg Fat", fmt1(avgFat));
            addRow(card, "Gross Invoice Amt", "₹" + fmt2(grossAmt));
            addRow(card, "PF Cut amount", "₹" + fmt2(pfAmount));
            addRow(card, "Net Payable Amt", "₹" + fmt2(netPaid));
            reportList.addView(card);
            count++;
        }

        if (count == 0) showEmptyMessage(reportList, "Is mahine ke liye koi doodh entry nahi mili.");
        view.findViewById(R.id.layoutExportButtons).setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.btnWhatsApp).setVisibility(View.GONE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EXCEL EXPORT SYSTEM
    // ─────────────────────────────────────────────────────────────────────────
    private void exportExcel() {
        if (currentMode.equals("pf_statement")) exportPfStatementExcel();
        else if (currentMode.equals("statement")) exportStatementExcel();
        else exportReportExcel();
    }

    private void exportPfStatementExcel() {
        if (calculatedPfData.isEmpty()) return;
        StringBuilder csv = new StringBuilder();
        csv.append(escape(activeDairyName)).append(" - Monthly PF Report (").append(selectedPfMonthName).append(")\n\n");
        csv.append("Unique ID,Member No.,Customer Name,Total Milk (Ltr),Avg Fat,Gross Amount (Rs),PF Deduction (Rs),Net Paid Amount (Rs)\n");
        double m = 0, g = 0, p = 0, n = 0;
        for (PfStatementRow r : calculatedPfData) {
            csv.append(escape(r.uid)).append(",")
               .append(escape(r.memberCode)).append(",")
               .append(escape(r.name)).append(",")
               .append(fmt2(r.totalMilk)).append(",")
               .append(fmt1(r.avgFat)).append(",")
               .append(fmt2(r.grossAmount)).append(",")
               .append(fmt2(r.pfAmount)).append(",")
               .append(fmt2(r.netPaid)).append("\n");
            m += r.totalMilk; g += r.grossAmount; p += r.pfAmount; n += r.netPaid;
        }
        csv.append("Grand Total Summary,-,-,").append(fmt2(m)).append(",-,").append(fmt2(g)).append(",").append(fmt2(p)).append(",").append(fmt2(n)).append("\n");
        File file = saveToDownloads("PF_Report_" + selectedPfMonthName + ".csv", csv.toString().getBytes());
        if (file != null) shareFile(file, "text/csv");
    }

    private void exportReportExcel() {
        if (currentReportData.isEmpty()) return;
        StringBuilder csv = new StringBuilder();
        csv.append(escape(activeDairyName)).append(" - Bhugtan Summary\n");
        csv.append("Member Code,Naam,Entries,Kul Doodh (L),Total Bill (Rs),PF Katoti (Rs),Payable Amount (Rs),Paid (Rs),Baaki (Rs)\n");
        double gL=0, gB=0, gPf=0, gPay=0, gP=0, gBk=0;
        for (ReportRow r : currentReportData) {
            csv.append(escape(r.memberCode)).append(",")
               .append(escape(r.name)).append(",")
               .append(r.entryCount).append(",")
               .append(fmt2(r.totalLitres)).append(",")
               .append(fmt2(r.totalBill)).append(",")
               .append(fmt2(r.pfAmount)).append(",")
               .append(fmt2(r.payableAmount)).append(",")
               .append(fmt2(r.totalPaid)).append(",")
               .append(fmt2(r.baaki)).append("\n");
            gL+=r.totalLitres; gB+=r.totalBill; gPf+=r.pfAmount; gPay+=r.payableAmount; gP+=r.totalPaid; gBk+=r.baaki;
        }
        csv.append("Grand Total Summary,-,-,").append(fmt2(gL)).append(",-,").append(fmt2(gB)).append(",").append(fmt2(gPf)).append(",").append(fmt2(gPay)).append(",").append(fmt2(gP)).append(",").append(fmt2(gBk)).append("\n");
        File file = saveToDownloads("DairyReport_" + currentFrom + "_to_" + currentTo + ".csv", csv.toString().getBytes());
        if (file != null) shareFile(file, "text/csv");
    }

    private void exportStatementExcel() {
        if (currentStatementData.isEmpty()) return;
        StringBuilder csv = new StringBuilder();
        csv.append(escape(activeDairyName)).append("\n");
        csv.append("Customer: ").append(statCustomerCode).append(" - ").append(statCustomerName).append("\n");
        csv.append("S.No,Date,Shift,Doodh (L),FAT%,Rate,Amount (Rs)\n");
        int sno = 1;
        for (StatementRow r : currentStatementData) {
            String shift = (r.shift != null && (r.shift.equalsIgnoreCase("Subah") || r.shift.equalsIgnoreCase("M"))) ? "Subah" : "Shaam";
            csv.append(sno++).append(",").append(r.date).append(",").append(shift).append(",").append(fmt2(r.qty)).append(",").append(fmt1(r.fat)).append(",").append(fmt2(r.rate)).append(",").append(fmt2(r.amount)).append("\n");
        }
        File file = saveToDownloads("Statement_" + statCustomerCode + ".csv", csv.toString().getBytes());
        if (file != null) shareFile(file, "text/csv");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HTML PRINT PREVIEW LOGIC
    // ─────────────────────────────────────────────────────────────────────────
    private void exportPdf() {
        if (currentMode.equals("pf_statement")) printPfHtml();
        else if (currentMode.equals("statement")) printStatement();
        else printSummaryReport();
    }

    private void printReport() {
        if (currentMode.equals("pf_statement")) printPfHtml();
        else if (currentMode.equals("statement")) printStatement();
        else printSummaryReport();
    }

    private void printPfHtml() {
        if (calculatedPfData.isEmpty()) return;
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
            .append("body { font-family: sans-serif; margin: 15px; color: #2D3748; }")
            .append(".header { background-color: #1B365D; color: white; padding: 15px; text-align: center; border-radius: 4px; }")
            .append("table { width: 100%; border-collapse: collapse; margin-top: 15px; }")
            .append("th { background-color: #1B365D; color: white; padding: 10px; font-size: 12px; }")
            .append("td { padding: 10px; border-bottom: 1px solid #E2E8F0; font-size: 11px; }")
            .append("tr:nth-child(even) { background-color: #F8FAFC; }")
            .append(".right { text-align: right; } .center { text-align: center; }")
            .append(".total-row td { font-weight: bold; background-color: #EDF2F7; color: #1B365D; border-top: 2px solid #1B365D; }")
            .append("</style></head><body>")
            // 🟢 FIX: 'safe()' function ka error remove karke seedhe 'safe2()' framework laga diya
            .append("<div class='header'><h2>").append(safe2(activeDairyName)).append("</h2>")
            .append("<p>Monthly PF Summary - ").append(selectedPfMonthName).append("</p></div>")
            .append("<table><thead><tr>")
            .append("<th class='center'>Unique ID</th><th class='center'>Member No.</th><th>Customer Name</th><th class='right'>Milk Qty (Ltr)</th><th class='right'>Avg Fat</th><th class='right'>Gross Amt (₹)</th><th class='right'>PF Cut (₹)</th><th class='right'>Net Paid (₹)</th>")
            .append("</tr></thead><tbody>");

        double m = 0, g = 0, p = 0, n = 0;
        for (PfStatementRow r : calculatedPfData) {
            m += r.totalMilk; g += r.grossAmount; p += r.pfAmount; n += r.netPaid;
            html.append("<tr>")
                .append("<td class='center'><b>").append(r.uid).append("</b></td>")
                .append("<td class='center'>").append(r.memberCode).append("</td>")
                .append("<td>").append(r.name).append("</td>")
                .append("<td class='right'>").append(fmt2(r.totalMilk)).append("</td>")
                .append("<td class='right'>").append(fmt1(r.avgFat)).append("</td>")
                .append("<td class='right'>").append(fmt2(r.grossAmount)).append("</td>")
                .append("<td class='right'>").append(fmt2(r.pfAmount)).append("</td>")
                .append("<td class='right'><b>").append(fmt2(r.netPaid)).append("</b></td>")
                .append("</tr>");
        }
        html.append("<tr class='total-row'>")
            .append("<td class='center'>Total</td><td class='center'>-</td><td>Summary</td>")
            .append("<td class='right'>").append(fmt1(m)).append("</td><td>-</td>")
            .append("<td class='right'>").append(fmt2(g)).append("</td>")
            .append("<td class='right'>").append(fmt2(p)).append("</td>")
            .append("<td class='right'>").append(fmt2(n)).append("</td>")
            .append("</tr></tbody></table></body></html>");

        doPrint(html.toString(), "PF_Report_" + selectedPfMonthName);
    }

    private void printSummaryReport() {
        if (currentReportData.isEmpty()) return;
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
            .append("body{font-family:sans-serif;padding:15px;}")
            .append(".header { background-color: #0D47A1; color: white; padding: 15px; text-align: center; border-radius: 4px; }")
            .append("table{width:100%;border-collapse:collapse;margin-top:15px;}")
            .append("th{background:#0D47A1;color:#fff;padding:8px;text-align:left;}")
            .append("td{padding:8px;border-bottom:1px solid #ddd;}")
            .append("</style></head><body>")
            .append("<div class='header'><h2>").append(safe2(activeDairyName)).append("</h2>")
            .append("<p>Period: ").append(currentFrom).append(" to ").append(currentTo).append("</p></div>")
            .append("<table><thead><tr><th>Code</th><th>Name</th><th>Milk(L)</th><th>Bill</th><th>PF Cut</th><th>Payable</th><th>Paid</th><th>Baaki</th></tr></thead><tbody>");

        double gL=0, gB=0, gPf=0, gPay=0, gP=0, gBk=0;
        for (ReportRow r : currentReportData) {
            html.append("<tr>")
                .append("<td>").append(safe2(r.memberCode)).append("</td>")
                .append("<td>").append(safe2(r.name)).append("</td>")
                .append("<td>").append(fmt2(r.totalLitres)).append("</td>")
                .append("<td>").append(fmt2(r.totalBill)).append("</td>")
                .append("<td>").append(fmt2(r.pfAmount)).append("</td>")
                .append("<td>").append(fmt2(r.payableAmount)).append("</td>")
                .append("<td>").append(fmt2(r.totalPaid)).append("</td>")
                .append("<td>").append(fmt2(r.baaki)).append("</td>")
                .append("</tr>");
            gL+=r.totalLitres; gB+=r.totalBill; gPf+=r.pfAmount; gPay+=r.payableAmount; gP+=r.totalPaid; gBk+=r.baaki;
        }
        html.append("<tr style='font-weight:bold; background:#eee;'><td colspan='2'>Total</td><td>").append(fmt2(gL)).append("</td><td>").append(fmt2(gB)).append("</td><td>").append(fmt2(gPf)).append("</td><td>").append(fmt2(gPay)).append("</td><td>").append(fmt2(gP)).append("</td><td>").append(fmt2(gBk)).append("</td></tr>");
        html.append("</tbody></table></body></html>");
        doPrint(html.toString(), "DairyReport_" + currentFrom);
    }

    private void printStatement() {
        if (currentStatementData.isEmpty()) return;
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
            .append("body{font-family:sans-serif;padding:15px;}")
            .append(".header { background-color: #0D47A1; color: white; padding: 15px; text-align: center; border-radius: 4px; }")
            .append("table{width:100%;border-collapse:collapse;margin-top:15px;}")
            .append("th{background:#0D47A1;color:#fff;padding:8px;}")
            .append("td{padding:8px;border-bottom:1px solid #ddd;text-align:center;}")
            .append("</style></head><body>")
            .append("<div class='header'><h2>").append(safe2(activeDairyName)).append("</h2>")
            .append("<p>Customer Statement - [").append(statCustomerCode).append("] ").append(statCustomerName).append("</p></div>")
            .append("<table><thead><tr><th>Date</th><th>Shift</th><th>Doodh (L)</th><th>FAT%</th><th>Rate</th><th>Amount</th></tr></thead><tbody>");

        double tQty = 0, tAmt = 0;
        for (StatementRow r : currentStatementData) {
            String shift = (r.shift != null && (r.shift.equalsIgnoreCase("Subah") || r.shift.equalsIgnoreCase("M"))) ? "Subah" : "Shaam";
            html.append("<tr>")
                .append("<td>").append(r.date).append("</td>")
                .append("<td>").append(shift).append("</td>")
                .append("<td>").append(fmt2(r.qty)).append("</td>")
                .append("<td>").append(fmt1(r.fat)).append("</td>")
                .append("<td>").append(fmt2(r.rate)).append("</td>")
                .append("<td>").append(fmt2(r.amount)).append("</td>")
                .append("</tr>");
            tQty += r.qty; tAmt += r.amount;
        }
        double pfAmt = tQty * statPfPerLiter;
        double payable = tAmt - pfAmt;
        html.append("<tr style='font-weight:bold; background:#eee;'><td colspan='2'>Total</td><td>").append(fmt2(tQty)).append("</td><td colspan='3'>Gross Bill: ₹").append(fmt2(tAmt)).append("</td></tr>");
        html.append("</tbody></table>");
        html.append("<br><div style='background:#f9f9f9; padding:10px;'><b>PF Deducted:</b> ₹").append(fmt2(pfAmt)).append("<br><b>Final Payable Amount:</b> ₹").append(fmt2(payable)).append("</div>");
        html.append("</body></html>");
        doPrint(html.toString(), "Statement_" + statCustomerCode);
    }

    private void exportReportPdf() { printSummaryReport(); }
    private void exportStatementPdf() { printStatement(); }

    private void doPrint(String html, String jobName) {
        if (getContext() == null) return;
        WebView webView = new WebView(requireContext()); webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (getActivity() == null) return;
                PrintManager pm = (PrintManager) requireContext().getSystemService(Context.PRINT_SERVICE);
                if (pm != null) pm.print(jobName, view.createPrintDocumentAdapter(jobName), new PrintAttributes.Builder().build());
            }
        });
    }

    private void shareStatementOnWhatsApp() {
        if (currentStatementData.isEmpty()) return;
        StringBuilder msg = new StringBuilder();
        msg.append("🐄 *").append(activeDairyName).append("*\n")
           .append("👤 *[").append(statCustomerCode).append("] ").append(statCustomerName).append("*\n")
           .append("📅 Period: ").append(currentFrom).append(" -> ").append(currentTo).append("\n\n");
        
        double totalL = 0, totalAmt = 0;
        for (StatementRow r : currentStatementData) {
            String shift = (r.shift != null && (r.shift.equalsIgnoreCase("Subah") || r.shift.equalsIgnoreCase("M"))) ? "Subah" : "Shaam";
            msg.append(r.date.substring(5)).append(" (").append(shift.substring(0,1)).append(") ").append(fmt2(r.qty)).append("L ₹").append(fmt2(r.amount)).append("\n");
            totalL += r.qty; totalAmt += r.amount;
        }
        msg.append("\n*Total Milk:* ").append(fmt2(totalL)).append(" L\n*Total Bill:* ₹").append(fmt2(totalAmt));
        
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain"); intent.setPackage("com.whatsapp"); intent.putExtra(android.content.Intent.EXTRA_TEXT, msg.toString());
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "WhatsApp nahi mila", Toast.LENGTH_SHORT).show();
        }
    }

    private void writePdf(PdfDocument pdf, String fileName) {
        File file = new File(requireContext().getExternalFilesDir(null), fileName);
        try { FileOutputStream fos = new FileOutputStream(file); pdf.writeTo(fos); fos.close(); pdf.close(); shareFile(file, "application/pdf"); } catch (IOException ex) { pdf.close(); }
    }

    // ── UTILS & CORE HELPERS ─────────────────────────────────────────────────
    private void showEmptyMessage(LinearLayout parent, String text) {
        if (getContext() == null) return;
        TextView empty = new TextView(getContext()); empty.setText(text); empty.setTextColor(0xFF78909C); empty.setPadding(0, 20, 0, 0); parent.addView(empty);
    }

    private LinearLayout makeCard(int bottomMargin) {
        LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.VERTICAL); card.setBackgroundColor(0xFFFFFFFF); card.setPadding(14, 14, 14, 14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, bottomMargin); card.setLayoutParams(lp); return card;
    }

    void addRow(LinearLayout parent, String label, String value) { parent.addView(buildRow(label, value)); }

    LinearLayout buildRow(String label, String value) {
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0, 4, 0, 4);
        TextView tvLabel = new TextView(getContext()); tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)); tvLabel.setText(label); tvLabel.setTextColor(0xFF78909C); tvLabel.setTextSize(13);
        TextView tvVal = new TextView(getContext()); tvVal.setText(value); tvVal.setTextColor(0xFF212121); tvVal.setTextSize(13); tvVal.setTypeface(null, Typeface.BOLD);
        row.addView(tvLabel); row.addView(tvVal); return row;
    }

    private LinearLayout buildStatementColHeader() {
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setBackgroundColor(0xFF0D47A1); row.setPadding(8, 10, 8, 10);
        addStatColHeader(row, "#", 0.5f); addStatColHeader(row, "Date", 1.5f); addStatColHeader(row, "Sh", 0.5f); addStatColHeader(row, "Doodh", 1f); addStatColHeader(row, "FAT%", 0.8f); addStatColHeader(row, "Rate", 0.8f); addStatColHeader(row, "Amount", 1.2f); return row;
    }

    private void addStatColHeader(LinearLayout parent, String text, float weight) {
        TextView tv = new TextView(getContext()); tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)); tv.setText(text); tv.setTextColor(Color.WHITE); tv.setTextSize(11); tv.setTypeface(null, Typeface.BOLD); parent.addView(tv);
    }

    private void addStatCol(LinearLayout parent, String text, float weight, int color, boolean bold) {
        TextView tv = new TextView(getContext()); tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)); tv.setText(text); tv.setTextColor(color); tv.setTextSize(11); if (bold) tv.setTypeface(null, Typeface.BOLD); parent.addView(tv);
    }

    private Paint makePaint(int color, float size, boolean bold) { Paint p = new Paint(); p.setColor(color); p.setTextSize(size); p.setAntiAlias(true); if (bold) p.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)); return p; }

    private File saveToDownloads(String fileName, byte[] data) {
        try { File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); if (!dir.exists()) dir.mkdirs(); File file = new File(dir, fileName); FileOutputStream fos = new FileOutputStream(file); fos.write(data); fos.close(); return file; } catch (IOException e) { return null; }
    }

    private void shareFile(File file, String mimeType) {
        if (getContext() == null) return;
        try { android.net.Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", file); android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND); intent.setType(mimeType); intent.putExtra(android.content.Intent.EXTRA_STREAM, uri); intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(android.content.Intent.createChooser(intent, "Share")); } catch (Exception e) { }
    }

    private String fmt2(double v) { return String.format(Locale.getDefault(), "%.2f", v); }
    private String fmt1(double v) { return String.format(Locale.getDefault(), "%.1f", v); }
    private String fmt0(double v) { return String.format(Locale.getDefault(), "%.0f", v); }
    private String escape(String s) { if (s == null) return ""; return (s.contains(",") || s.contains("\"") || s.contains("\n")) ? "\"" + s.replace("\"", "\"\"") + "\"" : s; }
    private String safe2(String s) { return s == null ? "" : s; }
    private String truncate(String s, int max) { if (s == null) return ""; return s.length() > max ? s.substring(0, max - 1) + "…" : s; }
}
