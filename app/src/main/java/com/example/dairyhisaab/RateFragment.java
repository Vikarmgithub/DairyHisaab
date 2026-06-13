package com.example.dairyhisaab;

import android.os.Bundle;
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
import androidx.fragment.app.Fragment;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class RateFragment extends Fragment {

    private EditText etRateDate, etRateNote;
    private EditText etBuffFatRate, etBuffCom, etBuffClr, etBuffSnf;
    private EditText etCowFatRate, etCowCom, etCowClr, etCowSnf;
    private EditText etPfPerLiter;
    private Button btnSaveRate;
    private LinearLayout rateList;
    private DairyDataManager dm;

    public RateFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rate, container, false);

        dm = DairyDataManager.getInstance(getContext());

        etRateDate    = view.findViewById(R.id.etRateDate);
        etRateNote    = view.findViewById(R.id.etRateNote);
        etPfPerLiter  = view.findViewById(R.id.etPfPerLiter);
        btnSaveRate   = view.findViewById(R.id.btnSaveRate);
        rateList      = view.findViewById(R.id.rateList);

        etBuffFatRate = view.findViewById(R.id.etBuffFatRate);
        etBuffCom     = view.findViewById(R.id.etBuffCom);
        etBuffClr     = view.findViewById(R.id.etBuffClr);
        etBuffSnf     = view.findViewById(R.id.etBuffSnf);

        etCowFatRate  = view.findViewById(R.id.etCowFatRate);
        etCowCom      = view.findViewById(R.id.etCowCom);
        etCowClr      = view.findViewById(R.id.etCowClr);
        etCowSnf      = view.findViewById(R.id.etCowSnf);

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        etRateDate.setText(currentDate);

        btnSaveRate.setOnClickListener(v -> saveRateData());
        loadRateHistory();

        return view;
    }

    private void saveRateData() {
        String dateStr        = etRateDate.getText().toString().trim();
        String noteStr        = etRateNote.getText().toString().trim();
        String buffFatRateStr = etBuffFatRate.getText().toString().trim();
        String buffComStr     = etBuffCom.getText().toString().trim();
        String cowFatRateStr  = etCowFatRate.getText().toString().trim();
        String cowComStr      = etCowCom.getText().toString().trim();
        String pfStr          = etPfPerLiter.getText().toString().trim();

        if (dateStr.isEmpty() || buffFatRateStr.isEmpty() || buffComStr.isEmpty()
                || cowFatRateStr.isEmpty() || cowComStr.isEmpty()) {
            Toast.makeText(getContext(), "कृपया सभी ज़रूरी रेट और कमीशन भरें!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double buffFatRate = Double.parseDouble(buffFatRateStr);
            double buffCom     = Double.parseDouble(buffComStr);
            double cowFatRate  = Double.parseDouble(cowFatRateStr);
            double cowCom      = Double.parseDouble(cowComStr);
            double pfPerLiter  = pfStr.isEmpty() ? 0.0 : Double.parseDouble(pfStr);

            RateEntry entry = new RateEntry();
            entry.id         = UUID.randomUUID().toString();
            entry.date       = dateStr;
            entry.rate       = buffFatRate;
            entry.base       = buffCom;
            entry.pfPerLiter = pfPerLiter;
            entry.note       = noteStr;

            List<RateEntry> history = dm.getRateHistory();
            history.add(0, entry);
            dm.saveRateHistory(history);

            Toast.makeText(getContext(), "✅ रेट सफ़लतापूर्वक सेव हो गए!", Toast.LENGTH_SHORT).show();
            addRateToHistoryList(dateStr, buffFatRate, buffCom, cowFatRate, cowCom, pfPerLiter);

        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "कृपया सही नंबर टाइप करें!", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadRateHistory() {
        rateList.removeAllViews();
        List<RateEntry> history = dm.getRateHistory();
        for (RateEntry r : history) {
            addRateCardFromEntry(r);
        }
    }

    private void addRateCardFromEntry(RateEntry r) {
        TextView tv = new TextView(getContext());
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        String text = "📅 दिनांक: " + r.date
                + "\n🐃 Fat Rate: " + r.rate + " | Com: ₹" + r.base + "/L"
                + "\n💰 PF: ₹" + r.pfPerLiter + "/L"
                + (r.note != null && !r.note.isEmpty() ? "\n📝 " + r.note : "")
                + "\n─────────────────────────";
        tv.setText(text);
        tv.setPadding(10, 10, 10, 10);
        tv.setTextSize(12);
        tv.setTextColor(0xFF3d2610);
        rateList.addView(tv);
    }

    private void addRateToHistoryList(String date, double bFat, double bCom,
                                      double cFat, double cCom, double pf) {
        TextView tv = new TextView(getContext());
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        String text = "📅 दिनांक: " + date
                + "\n🐃 Buff Fat: " + bFat + " | Com: ₹" + bCom + "/L"
                + "\n🐄 Cow Fat: " + cFat + " | Com: ₹" + cCom + "/L"
                + "\n💰 PF: ₹" + pf + "/L"
                + "\n─────────────────────────";
        tv.setText(text);
        tv.setPadding(10, 10, 10, 10);
        tv.setTextSize(12);
        tv.setTextColor(0xFF3d2610);
        rateList.addView(tv, 0);
    }
}