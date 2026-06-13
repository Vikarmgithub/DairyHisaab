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

    private EditText etDate;
    private TextView btnMorning, btnEvening; 
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
    private Map<String, Integer> customerRowCount = new HashMap<>();

    public DailyEntryFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily, container, false);

        dm = DairyDataManager.getInstance(getContext());

        etDate = view.findViewById(R.id.etDate);
        btnMorning = view.findViewById(R.id.btnMorning);
        btnEvening = view.findViewById(R.id.btnEvening);
        tvRateBanner = view.findViewById(R.id.tvRateBanner);
        entryList = view.findViewById(R.id.entryList);
        btnSaveAll = view.findViewById(R.id.btnSaveAll);

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        etDate.setText(currentDate);

        tvRateBanner.setText("भैंस फैट रेट: ₹" + buffFatRate + " (+" + buffCommission + ") | गाय फैट रेट: ₹" + cowFatRate + " (+" + cowCommission + ")");

        btnMorning.setOnClickListener(v -> selectShift("Subah"));
        btnEvening.setOnClickListener(v -> selectShift("Shaam"));
        etDate.addTextChangedListener(new TextWatcher() {
    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    @Override public void afterTextChanged(Editable s) {
        String txt = s.toString().trim();
        if (txt.length() == 10 && getActivity() != null) {
            populateEntryList(LayoutInflater.from(getContext()));
        }
    }
});

        loadRealMembers();
        populateEntryList(inflater);

        btnSaveAll.setOnClickListener(v -> saveAllEntriesToDatabase());

        return view;
    }

    private void selectShift(String shift) {
        // 💡 यहाँ शिफ्ट को बिल्कुल पक्का लॉक कर रहे हैं
        if (shift.equalsIgnoreCase("Subah")) {
            selectedShift = "Subah";
            btnMorning.setBackgroundColor(Color.WHITE);
            btnMorning.setTextColor(Color.parseColor("#0D47A1")); 
            btnEvening.setBackgroundColor(Color.TRANSPARENT);
            btnEvening.setTextColor(Color.WHITE);
        } else {
            selectedShift = "Shaam";
            btnEvening.setBackgroundColor(Color.WHITE);
            btnEvening.setTextColor(Color.parseColor("#0D47A1")); 
            btnMorning.setBackgroundColor(Color.TRANSPARENT);
            btnMorning.setTextColor(Color.WHITE);
        }

        // 💡 शिफ्ट बदलते ही पुरानी लिस्ट साफ़ करके फ्रेश रीलोड करना ज़रूरी है
        if (getActivity() != null) {
            loadRealMembers();
            populateEntryList(LayoutInflater.from(getContext()));
        }
    }

    private void loadRealMembers() {
        if (dm != null) {
            customerList = dm.getCustomers();
        }
        if (customerList == null) {
            customerList = new ArrayList<>();
        }
    }

    private void populateEntryList(LayoutInflater inflater) {
        entryList.removeAllViews();
        customerRowCount.clear();

        if (customerList.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("कोई मेंबर नहीं मिला। पहले 'MEMBERS' टैब में जाकर जोड़ें!");
            empty.setTextColor(Color.parseColor("#78909C"));
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 40, 0, 0);
            entryList.addView(empty);
            return;
        }

        // 💡 डेटाबेस से आज की डेट और सिलेक्टेड शिफ्ट का पहले से सेव डेटा ढूंढते हैं
        List<MilkEntry> existingEntries = dm.getEntries();
        String currentDate = etDate.getText().toString().trim();
        
        Map<String, List<MilkEntry>> entryMap = new HashMap<>();
        for (MilkEntry e : existingEntries) {
            if (e.date.equals(currentDate) && e.shift.equalsIgnoreCase(selectedShift)) {
                if (!entryMap.containsKey(e.cid)) {
                    entryMap.put(e.cid, new ArrayList<>());
                }
                entryMap.get(e.cid).add(e);
            }
        }

        int sn = 1;
        for (Customer c : customerList) {
            if (entryMap.containsKey(c.id) && !entryMap.get(c.id).isEmpty()) {
                // अगर पहले से सुबह या शाम का डेटा सेव है, तो वही वैल्यू डब्बों में भरो
                List<MilkEntry> cEntries = entryMap.get(c.id);
                customerRowCount.put(c.id, cEntries.size());
                for (int r = 0; r < cEntries.size(); r++) {
                    addEntryRow(c, sn, r + 1, cEntries.get(r));
                }
            } else {
                // नहीं तो खाली डिफ़ॉल्ट रो दिखाओ
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

        if (rowNumber > 1) {
            rowView.setBackgroundColor(Color.parseColor("#F0F7FF")); 
        }

        // 1. S.No
        TextView tvSn = new TextView(getContext());
        tvSn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvSn.setText(rowNumber > 1 ? "  ↳" : String.valueOf(sn));
        tvSn.setTextColor(Color.parseColor("#212121"));
        rowView.addView(tvSn);

        // 2. Name
        TextView tvName = new TextView(getContext());
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.5f));
        tvName.setText("[" + c.memberCode + "] " + c.name);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setTextColor(Color.parseColor("#212121"));
        if (rowNumber > 1) tvName.setTextColor(Color.parseColor("#C62828"));
        rowView.addView(tvName);

        // 3. Animal Spinner
        Spinner spAnimal = new Spinner(getContext());
        spAnimal.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, new String[]{"भैंस", "गाय"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAnimal.setAdapter(adapter);
        rowView.addView(spAnimal);

        // 4. Qty
        EditText etQty = new EditText(getContext());
        etQty.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));
        etQty.setHint("0.0");
        etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etQty.setTextColor(Color.parseColor("#212121"));
        if (existingEntry != null) etQty.setText(String.valueOf(existingEntry.qty));
        rowView.addView(etQty);

        // 5. Fat
        EditText etFat = new EditText(getContext());
        etFat.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        etFat.setHint("0.0");
        etFat.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etFat.setTextColor(Color.parseColor("#212121"));
        if (existingEntry != null) etFat.setText(String.valueOf(existingEntry.fat));
        rowView.addView(etFat);

        // 6. CLR
        EditText etClr = new EditText(getContext());
        etClr.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        etClr.setHint("0");
        etClr.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etClr.setTextColor(Color.parseColor("#212121"));
        rowView.addView(etClr);

        // 7. Total Rs
        TextView tvTotalRs = new TextView(getContext());
        tvTotalRs.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        tvTotalRs.setText("₹0.00");
        tvTotalRs.setGravity(android.view.Gravity.CENTER);
        tvTotalRs.setTypeface(null, Typeface.BOLD);
        tvTotalRs.setTextColor(Color.parseColor("#2E7D32")); 
        rowView.addView(tvTotalRs);

        // 8. "+" / "✕" Button
        Button btnAdd = new Button(getContext());
        btnAdd.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnAdd.setText(rowNumber == 1 ? "+" : "✕");
        btnAdd.setTextSize(14);
        btnAdd.setPadding(0, 0, 0, 0);

        if (rowNumber == 1) {
            btnAdd.setBackgroundColor(Color.parseColor("#0D47A1")); 
            btnAdd.setTextColor(Color.WHITE);
            btnAdd.setOnClickListener(v -> {
                int currentCount = customerRowCount.getOrDefault(c.id, 1);
                customerRowCount.put(c.id, currentCount + 1);
                addEntryRow(c, sn, currentCount + 1, null);
            });
        } else {
            btnAdd.setBackgroundColor(Color.parseColor("#C62828")); 
            btnAdd.setTextColor(Color.WHITE);
            btnAdd.setOnClickListener(v -> {
                int rowIndex = getViewIndexInParent(rowView);
                if (rowIndex > 0) {
                    View prevView = entryList.getChildAt(rowIndex - 1);
                    if (prevView != null && prevView.getTag() != null &&
                        prevView.getTag().toString().startsWith("warning_" + c.id)) {
                        entryList.removeView(prevView);
                    }
                }
                entryList.removeView(rowView);
                int currentCount = customerRowCount.getOrDefault(c.id, 1);
                if (currentCount > 1) customerRowCount.put(c.id, currentCount - 1);
            });
        }
        rowView.addView(btnAdd);

        rowView.setTag(c.id);

        TextWatcher amountWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                calculateRowAmount(spAnimal, etQty, etFat, tvTotalRs);
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        etQty.addTextChangedListener(amountWatcher);
        etFat.addTextChangedListener(amountWatcher);

        spAnimal.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                calculateRowAmount(spAnimal, etQty, etFat, tvTotalRs);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 💡 अगर पुराना डेटा मौजूद था तो तुरंत कैलकुलेशन दिखाओ
        if (existingEntry != null) {
            calculateRowAmount(spAnimal, etQty, etFat, tvTotalRs);
        }

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

        if (qtyStr.isEmpty() || fatStr.isEmpty()) {
            tvTotalRs.setText("₹0.00");
            return;
        }

        try {
            double qty = Double.parseDouble(qtyStr);
            double fat = Double.parseDouble(fatStr);

            double currentFatRate = buffFatRate;
            double currentCommission = buffCommission;

            if (spAnimal.getSelectedItem().toString().equals("गाय")) {
                currentFatRate = cowFatRate;
                currentCommission = cowCommission;
            }

            double ratePerKg = ((currentFatRate * fat) / 100.0) + currentCommission;
            double totalAmount = qty * ratePerKg;

            tvTotalRs.setText(String.format(Locale.getDefault(), "₹%.2f", totalAmount));

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

        // 💡 डेटाबेस से पुरानी सिर्फ आज की सिलेक्टेड शिफ्ट की एंट्री हटाओ, दूसरी शिफ्ट सुरक्षित रहेगी
        for (int i = allEntries.size() - 1; i >= 0; i--) {
            MilkEntry e = allEntries.get(i);
            if (e.date.equals(date) && e.shift.equalsIgnoreCase(selectedShift)) {
                allEntries.remove(i);
            }
        }

        int saved = 0;
        long baseTime = System.currentTimeMillis();

        for (int i = 0; i < entryList.getChildCount(); i++) {
            View child = entryList.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue; 

            LinearLayout row = (LinearLayout) child;
            if (row.getTag() == null) continue;

            String customerId = row.getTag().toString();
            if (row.getChildCount() < 7) continue;

            // 💡 पक्के इंडेक्स से सही वैल्यू उठाना
            String qtyStr = ((EditText) row.getChildAt(3)).getText().toString().trim();
            String fatStr = ((EditText) row.getChildAt(4)).getText().toString().trim();
            String clrStr = ((EditText) row.getChildAt(5)).getText().toString().trim();
            String totalStr = ((TextView) row.getChildAt(6)).getText().toString()
                    .replace("₹", "").trim();

            if (qtyStr.isEmpty() || qtyStr.equals("0") || qtyStr.equals("0.0")) continue;

            try {
                double qty = Double.parseDouble(qtyStr);
                double fat = fatStr.isEmpty() ? 0 : Double.parseDouble(fatStr);
                double clr = clrStr.isEmpty() ? 0 : Double.parseDouble(clrStr);
                double rate = (totalStr.isEmpty() || Double.parseDouble(totalStr) == 0) ? 0
                        : Double.parseDouble(totalStr) / qty;

                MilkEntry entry = new MilkEntry();
                entry.id = (baseTime + i) + "_" + customerId;
                entry.cid = customerId;
                entry.date = date;
                entry.shift = selectedShift; // 💡 "Subah" या "Shaam" एकदम परफेक्ट लॉक होकर जाएगा
                entry.qty = qty;
                entry.fat = fat;
                entry.rate = rate;
                
                allEntries.add(entry);
                saved++;
            } catch (NumberFormatException e) {
                // skip invalid rows
            }
        }

        dm.saveEntries(allEntries);

        String msg = saved + " entries " + selectedShift + " shift me save ho gayi!";
        if (saved > customerList.size()) {
            msg += " (Kuch customers ki multiple entries hain)";
        }
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
