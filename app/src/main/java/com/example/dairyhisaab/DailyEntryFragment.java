package com.example.dairyhisaab;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DailyEntryFragment extends Fragment {

    private EditText etDate, etMemberSearch;
    private TextView btnMorning, btnEvening;
    private TextView btnPrevDay, btnNextDay;
    private Button btnSaveAll;
    private TextView tvRateBanner;
    private LinearLayout entryList;
    private String selectedShift = "Subah";

    private double buffFatRate = 875.00;
    private double buffCommission = 7.00;
    private double cowFatRate = 875.00;
    private double cowCommission = 7.00;

    private DairyDataManager dm;
    private List<Customer> customerList = new ArrayList<>();
    private List<Customer> filteredList = new ArrayList<>(); // search ke liye
    private Map<String, Integer> customerRowCount = new HashMap<>();

    public DailyEntryFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily, container, false);

        dm = DairyDataManager.getInstance(getContext());

        etDate         = view.findViewById(R.id.etDate);
        btnMorning     = view.findViewById(R.id.btnMorning);
        btnEvening     = view.findViewById(R.id.btnEvening);
        tvRateBanner   = view.findViewById(R.id.tvRateBanner);
        entryList      = view.findViewById(R.id.entryList);
        btnSaveAll     = view.findViewById(R.id.btnSaveAll);
        btnPrevDay     = view.findViewById(R.id.btnPrevDay);
        btnNextDay     = view.findViewById(R.id.btnNextDay);
        etMemberSearch = view.findViewById(R.id.etMemberSearch);

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        etDate.setText(currentDate);

        tvRateBanner.setText("भैंस फैट रेट: ₹" + buffFatRate + " (+" + buffCommission + 
                             ") | गाय फैट रेट: ₹" + cowFatRate + " (+" + cowCommission + ")");

        btnMorning.setOnClickListener(v -> selectShift("Subah"));
        btnEvening.setOnClickListener(v -> selectShift("Shaam"));
        btnPrevDay.setOnClickListener(v -> changeDate(-1));
        btnNextDay.setOnClickListener(v -> changeDate(1));

        etDate.setOnClickListener(v -> openDatePicker());

        // 🔍 Search filter — type karo, list filter ho
        etMemberSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAndReload(s.toString().trim());
            }
        });

        loadRealMembers();
        populateEntryList(inflater);

        btnSaveAll.setOnClickListener(v -> saveAllEntriesToDatabase());

        return view;
    }

    // 🔍 Search filter logic
    private void filterAndReload(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(customerList);
        } else {
            String lower = query.toLowerCase();
            for (Customer c : customerList) {
                if ((c.name != null && c.name.toLowerCase().contains(lower)) ||
                    (c.memberCode != null && c.memberCode.toLowerCase().contains(lower))) {
                    filteredList.add(c);
                }
            }
        }
        if (getActivity() != null) {
            populateEntryList(LayoutInflater.from(getContext()));
        }
    }

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
                    if (getActivity() != null)
                        populateEntryList(LayoutInflater.from(getContext()));
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Date error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            if (getActivity() != null)
                populateEntryList(LayoutInflater.from(getContext()));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Date error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void selectShift(String shift) {
        if (shift.equalsIgnoreCase("Subah")) {
            selectedShift = "Subah";
            btnMorning.setBackgroundColor(Color.WHITE);
            btnMorning.setTextColor(Color.parseColor("#1B5E20"));
            btnEvening.setBackgroundColor(Color.TRANSPARENT);
            btnEvening.setTextColor(Color.WHITE);
        } else {
            selectedShift = "Shaam";
            btnEvening.setBackgroundColor(Color.WHITE);
            btnEvening.setTextColor(Color.parseColor("#1B5E20"));
            btnMorning.setBackgroundColor(Color.TRANSPARENT);
            btnMorning.setTextColor(Color.WHITE);
        }
        if (getActivity() != null) {
            loadRealMembers();
            populateEntryList(LayoutInflater.from(getContext()));
        }
    }

    private void loadRealMembers() {
        customerList = dm != null ? dm.getCustomers() : new ArrayList<>();
        if (customerList == null) customerList = new ArrayList<>();

        // search query preserve karo
        String query = etMemberSearch != null ?
                etMemberSearch.getText().toString().trim() : "";
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(customerList);
        } else {
            String lower = query.toLowerCase();
            for (Customer c : customerList) {
                if ((c.name != null && c.name.toLowerCase().contains(lower)) ||
                    (c.memberCode != null && c.memberCode.toLowerCase().contains(lower))) {
                    filteredList.add(c);
                }
            }
        }
    }

    private void populateEntryList(LayoutInflater inflater) {
        entryList.removeAllViews();
        customerRowCount.clear();

        if (filteredList.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText(customerList.isEmpty()
                    ? "कोई मेंबर नहीं मिला। पहले 'MEMBERS' टैब में जाकर जोड़ें!"
                    : "कोई मेंबर इस नाम से नहीं मिला।");
            empty.setTextColor(Color.parseColor("#78909C"));
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 40, 0, 0);
            entryList.addView(empty);
            return;
        }

        List<MilkEntry> existingEntries = dm.getEntries();
        String currentDate = etDate.getText().toString().trim();

        Map<String, List<MilkEntry>> entryMap = new HashMap<>();
        for (MilkEntry e : existingEntries) {
            if (e.date.equals(currentDate) && e.shift.equalsIgnoreCase(selectedShift)) {
                if (!entryMap.containsKey(e.cid)) entryMap.put(e.cid, new ArrayList<>());
                entryMap.get(e.cid).add(e);
            }
        }

        int sn = 1;
        for (Customer c : filteredList) {
            if (entryMap.containsKey(c.id) && !entryMap.get(c.id).isEmpty()) {
                List<MilkEntry> cEntries = entryMap.get(c.id);
                customerRowCount.put(c.id, cEntries.size());
                for (int r = 0; r < cEntries.size(); r++) {
                    addEntryRow(c, sn, r + 1, cEntries.get(r));
                }
            } else {
                customerRowCount.put(c.id, 1);
                addEntryRow(c, sn, 1, null);
            }
            sn++;
        }
    }

    private void addEntryRow(Customer c, int sn, int rowNumber, MilkEntry existingEntry) {

        if (rowNumber > 1) {
            TextView tvWarning = new TextView(getContext());
            tvWarning.setText("⚠️ " + c.name + " ki " + rowNumber + " entry (Multiple bartan)");
            tvWarning.setTextColor(Color.parseColor("#C62828"));
            tvWarning.setBackgroundColor(Color.parseColor("#FFEBEE"));
            tvWarning.setTextSize(11);
            tvWarning.setPadding(12, 4, 12, 4);
            tvWarning.setTag("warning_" + c.id + "_" + rowNumber);
            entryList.addView(tvWarning);
        }

        LinearLayout rowView = new LinearLayout(getContext());
        rowView.setOrientation(LinearLayout.HORIZONTAL);
        rowView.setWeightSum(11);
        rowView.setPadding(0, 12, 0, 12);
        if (rowNumber > 1) rowView.setBackgroundColor(Color.parseColor("#F0F7FF"));

        TextView tvSn = new TextView(getContext());
        tvSn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvSn.setText(rowNumber > 1 ? "  ↳" : String.valueOf(sn));
        tvSn.setTextColor(Color.parseColor("#212121"));
        rowView.addView(tvSn);

        TextView tvName = new TextView(getContext());
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.5f));
        tvName.setText("[" + c.memberCode + "] " + c.name);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setTextColor(rowNumber > 1 ? Color.parseColor("#C62828") : Color.parseColor("#212121"));
        rowView.addView(tvName);

        Spinner spAnimal = new Spinner(getContext());
        spAnimal.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, new String[]{"भैंस", "गाय"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAnimal.setAdapter(adapter);
        rowView.addView(spAnimal);

        EditText etQty = new EditText(getContext());
        etQty.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));
        etQty.setHint("0.0");
        etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etQty.setTextColor(Color.parseColor("#212121"));
        if (existingEntry != null) etQty.setText(String.valueOf(existingEntry.qty));
        rowView.addView(etQty);

        EditText etFat = new EditText(getContext());
        etFat.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        etFat.setHint("0.0");
        etFat.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etFat.setTextColor(Color.parseColor("#212121"));
        if (existingEntry != null) etFat.setText(String.valueOf(existingEntry.fat));
        rowView.addView(etFat);

        EditText etClr = new EditText(getContext());
        etClr.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        etClr.setHint("0");
        etClr.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etClr.setTextColor(Color.parseColor("#212121"));
        rowView.addView(etClr);

        TextView tvTotalRs = new TextView(getContext());
        tvTotalRs.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        tvTotalRs.setText("₹0.00");
        tvTotalRs.setGravity(android.view.Gravity.CENTER);
        tvTotalRs.setTypeface(null, Typeface.BOLD);
        tvTotalRs.setTextColor(Color.parseColor("#2E7D32"));
        rowView.addView(tvTotalRs);

        Button btnAdd = new Button(getContext());
        btnAdd.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnAdd.setText(rowNumber == 1 ? "+" : "✕");
        btnAdd.setTextSize(14);
        btnAdd.setPadding(0, 0, 0, 0);

        if (rowNumber == 1) {
            btnAdd.setBackgroundColor(Color.parseColor("#2E7D32")); // green
            btnAdd.setTextColor(Color.WHITE);
            btnAdd.setOnClickListener(v -> {
                int cc = customerRowCount.getOrDefault(c.id, 1);
                customerRowCount.put(c.id, cc + 1);
                addEntryRow(c, sn, cc + 1, null);
            });
        } else {
            btnAdd.setBackgroundColor(Color.parseColor("#C62828"));
            btnAdd.setTextColor(Color.WHITE);
            btnAdd.setOnClickListener(v -> {
                int idx = getViewIndexInParent(rowView);
                if (idx > 0) {
                    View prev = entryList.getChildAt(idx - 1);
                    if (prev != null && prev.getTag() != null &&
                            prev.getTag().toString().startsWith("warning_" + c.id)) {
                        entryList.removeView(prev);
                    }
                }
                entryList.removeView(rowView);
                int cc = customerRowCount.getOrDefault(c.id, 1);
                if (cc > 1) customerRowCount.put(c.id, cc - 1);
            });
        }
        rowView.addView(btnAdd);
        rowView.setTag(c.id);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                calculateRowAmount(spAnimal, etQty, etFat, tvTotalRs);
            }
        };
        etQty.addTextChangedListener(watcher);
        etFat.addTextChangedListener(watcher);
        spAnimal.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                calculateRowAmount(spAnimal, etQty, etFat, tvTotalRs);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        if (existingEntry != null) calculateRowAmount(spAnimal, etQty, etFat, tvTotalRs);

        entryList.addView(rowView);
    }

    private int getViewIndexInParent(View view) {
        for (int i = 0; i < entryList.getChildCount(); i++) {
            if (entryList.getChildAt(i) == view) return i;
        }
        return -1;
    }

    private void calculateRowAmount(Spinner spAnimal, EditText etQty, EditText etFat, TextView tvTotalRs) {
        String qtyStr = etQty.getText().toString().trim();
        String fatStr = etFat.getText().toString().trim();
        if (qtyStr.isEmpty() || fatStr.isEmpty()) { tvTotalRs.setText("₹0.00"); return; }
        try {
            double qty = Double.parseDouble(qtyStr);
            double fat = Double.parseDouble(fatStr);
            boolean isCow = spAnimal.getSelectedItem().toString().equals("गाय");
            double rate = ((isCow ? cowFatRate : buffFatRate) * fat / 100.0)
                        + (isCow ? cowCommission : buffCommission);
            tvTotalRs.setText(String.format(Locale.getDefault(), "₹%.2f", qty * rate));
        } catch (NumberFormatException e) {
            tvTotalRs.setText("₹0.00");
        }
    }

    private void saveAllEntriesToDatabase() {
        String date = etDate.getText().toString().trim();
        if (date.isEmpty()) {
            Toast.makeText(getContext(), "Tarikh daalo!", Toast.LENGTH_SHORT).show();
            return;
        }

        List<MilkEntry> allEntries = dm.getEntries();
        for (int i = allEntries.size() - 1; i >= 0; i--) {
            MilkEntry e = allEntries.get(i);
            if (e.date.equals(date) && e.shift.equalsIgnoreCase(selectedShift))
                allEntries.remove(i);
        }

        int saved = 0;
        long base = System.currentTimeMillis();

        for (int i = 0; i < entryList.getChildCount(); i++) {
            View child = entryList.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) child;
            if (row.getTag() == null || row.getChildCount() < 7) continue;

            String cid     = row.getTag().toString();
            String qtyStr  = ((EditText)  row.getChildAt(3)).getText().toString().trim();
            String fatStr  = ((EditText)  row.getChildAt(4)).getText().toString().trim();
            String clrStr  = ((EditText)  row.getChildAt(5)).getText().toString().trim();
            String totStr  = ((TextView)  row.getChildAt(6)).getText().toString()
                               .replace("₹", "").trim();

            if (qtyStr.isEmpty() || qtyStr.equals("0") || qtyStr.equals("0.0")) continue;

            try {
                double qty  = Double.parseDouble(qtyStr);
                double fat  = fatStr.isEmpty() ? 0 : Double.parseDouble(fatStr);
                double tot  = totStr.isEmpty()  ? 0 : Double.parseDouble(totStr);
                double rate = qty > 0 ? tot / qty : 0;

                MilkEntry entry = new MilkEntry();
                entry.id    = (base + i) + "_" + cid;
                entry.cid   = cid;
                entry.date  = date;
                entry.shift = selectedShift;
                entry.qty   = qty;
                entry.fat   = fat;
                entry.rate  = rate;
                allEntries.add(entry);
                saved++;
            } catch (NumberFormatException ignored) {}
        }

        dm.saveEntries(allEntries);
        Toast.makeText(getContext(),
                saved + " entries " + selectedShift + " shift mein save ho gayi!",
                Toast.LENGTH_SHORT).show();
    }
}
