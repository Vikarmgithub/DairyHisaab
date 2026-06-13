package com.example.dairyhisaab;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DairyDataManager {

    private static DairyDataManager instance;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    private static final String KEY_CUSTOMERS    = "dairy_customers";
    private static final String KEY_ENTRIES      = "dairy_entries";
    private static final String KEY_PAYMENTS     = "dairy_payments";
    private static final String KEY_RATE_HISTORY = "dairy_rateHistory";

    private DairyDataManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences("dairy_hisaab", Context.MODE_PRIVATE);
    }

    public static synchronized DairyDataManager getInstance(Context context) {
        if (instance == null) instance = new DairyDataManager(context);
        return instance;
    }

    // ── Save / Load helpers ──
    private <T> void save(String key, List<T> list) {
        prefs.edit().putString(key, gson.toJson(list)).apply();
    }

    private <T> List<T> load(String key, Type type) {
        String json = prefs.getString(key, null);
        if (json == null) return new ArrayList<>();
        List<T> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    // ── Customers ──
    public List<Customer> getCustomers() {
        return load(KEY_CUSTOMERS, new TypeToken<List<Customer>>(){}.getType());
    }
    public void saveCustomers(List<Customer> list) { save(KEY_CUSTOMERS, list); }

    // ── Milk Entries ──
    public List<MilkEntry> getEntries() {
        return load(KEY_ENTRIES, new TypeToken<List<MilkEntry>>(){}.getType());
    }
    public void saveEntries(List<MilkEntry> list) { save(KEY_ENTRIES, list); }

    // ── Payments ──
    public List<Payment> getPayments() {
        return load(KEY_PAYMENTS, new TypeToken<List<Payment>>(){}.getType());
    }
    public void savePayments(List<Payment> list) { save(KEY_PAYMENTS, list); }

    // ── Rate History ──
    public List<RateEntry> getRateHistory() {
        return load(KEY_RATE_HISTORY, new TypeToken<List<RateEntry>>(){}.getType());
    }
    public void saveRateHistory(List<RateEntry> list) { save(KEY_RATE_HISTORY, list); }

    // ── Active rate for given date ──
    public RateEntry getActiveRate(String date) {
        List<RateEntry> history = getRateHistory();
        if (history == null || history.isEmpty()) return null;
        RateEntry best = null;
        for (RateEntry r : history) {
            if (r.date.compareTo(date) <= 0) {
                if (best == null || r.date.compareTo(best.date) > 0) {
                    best = r;
                }
            }
        }
        return best;
    }

    // ── Bill helpers ──
    public double totalBill(String customerId) {
        double total = 0;
        for (MilkEntry e : getEntries()) {
            if (e.cid.equals(customerId)) total += e.qty * e.rate;
        }
        return total;
    }

    public double totalPaid(String customerId) {
        double total = 0;
        for (Payment p : getPayments()) {
            if (p.cid.equals(customerId)) total += p.amount;
        }
        return total;
    }

    public double outstanding(String customerId) {
        return totalBill(customerId) - totalPaid(customerId);
    }

    // ── fat * rate / 100 + base ──
    public static double calcPPL(double fat, double rate, double base) {
        return Math.round((fat * rate / 100.0 + base) * 100.0) / 100.0;
    }

    // ── Auto next member code: M001, M002... ──
    public String nextMemberCode() {
        List<Customer> customers = getCustomers();
        int max = 0;
        for (Customer c : customers) {
            if (c.memberCode != null) {
                try {
                    int n = Integer.parseInt(c.memberCode.replaceAll("\\D", ""));
                    if (n > max) max = n;
                } catch (NumberFormatException ignored) {}
            }
        }
        return String.format(Locale.getDefault(), "M%03d", max + 1);
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    public static String formatDate(String d) {
        if (d == null) return "—";
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(d);
            return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date);
        } catch (Exception e) { return d; }
    }
}
