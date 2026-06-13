package com.example.dairyhisaab;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import java.util.List;

public class DashboardFragment extends Fragment {

    DairyDataManager dm;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        dm = DairyDataManager.getInstance(getContext());
        loadData(view);
        return view;
    }

    void loadData(View view) {
        List<Customer> customers = dm.getCustomers();
        List<MilkEntry> entries = dm.getEntries();
        List<RateEntry> rateHistory = dm.getRateHistory();

        String today = DairyDataManager.today();
        String thisMonth = today.substring(0, 7);

        // Aaj ka doodh
        double todayMilk = 0;
        for (MilkEntry e : entries) {
            if (e.date.equals(today)) todayMilk += e.qty;
        }

        // Is mahine ka doodh aur revenue
        double monthMilk = 0, monthRev = 0;
        for (MilkEntry e : entries) {
            if (e.date.startsWith(thisMonth)) {
                monthMilk += e.qty;
                monthRev += e.qty * e.rate;
            }
        }

        // Total baaki
        double totalBaaki = 0;
        for (Customer c : customers) {
            totalBaaki += dm.outstanding(c.id);
        }

        // Stat cards set karo (Perfectly Matched with New IDs)
        ((TextView) view.findViewById(R.id.tvMembers)).setText(String.valueOf(customers.size()));
        ((TextView) view.findViewById(R.id.tvToday)).setText(String.format("%.1fL", todayMilk));
        ((TextView) view.findViewById(R.id.tvMonth)).setText(String.format("%.1fL", monthMilk));
        ((TextView) view.findViewById(R.id.tvBaaki)).setText(String.format("₹%.0f", totalBaaki));

        // Mahine ka hisaab (Bottom Section Card)
        ((TextView) view.findViewById(R.id.tvMonthMilk)).setText(String.format("%.2f L", monthMilk));
        ((TextView) view.findViewById(R.id.tvMonthRev)).setText(String.format("₹ %.2f", monthRev));
        ((TextView) view.findViewById(R.id.tvTotalBaaki)).setText(String.format("₹ %.2f", totalBaaki));

        // Premium Blue Rate Banner System
        RateEntry activeRate = dm.getActiveRate(today);
        TextView tvBanner = view.findViewById(R.id.tvBanner);
        if (activeRate != null) {
            tvBanner.setText("Aaj ka Rate: " + activeRate.rate + " + Base Rs." + activeRate.base + " | June 2026");
            tvBanner.setTextColor(0xFFFFFFFF); // Premium White on Gradient Text
        } else {
            tvBanner.setText("⚠️ Rate set nahi! Rate tab mein set karo.");
            tvBanner.setTextColor(0xFFFFCDD2); // Light red for alert
        }

        // Baaki wale list container
        LinearLayout baakiList = view.findViewById(R.id.baakiList);
        baakiList.removeAllViews();
        boolean koi = false;
        for (Customer c : customers) {
            double baaki = dm.outstanding(c.id);
            if (baaki > 0) {
                koi = true;
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 12, 0, 12); // Slightly more padding for modern UI

                TextView name = new TextView(getContext());
                name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                name.setText("[" + c.memberCode + "] " + c.name);
                name.setTextColor(0xFF212121); // Modern text dark gray
                name.setTextSize(14);

                TextView amt = new TextView(getContext());
                amt.setText("₹" + String.format("%.0f", baaki));
                amt.setTextColor(0xFFC62828); // Accent red for outstandings
                amt.setTypeface(null, android.graphics.Typeface.BOLD);
                amt.setTextSize(14);

                row.addView(name);
                row.addView(amt);
                baakiList.addView(row);
            }
        }

        if (!koi) {
            TextView empty = new TextView(getContext());
            empty.setText("🎉 Sabka khata clear hai!");
            empty.setTextColor(0xFF2E7D32); // Success green
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 16, 0, 16);
            baakiList.addView(empty);
        }
    }
}
