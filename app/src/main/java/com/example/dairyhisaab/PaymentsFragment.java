package com.example.dairyhisaab;

import android.app.DatePickerDialog;
import android.os.Bundle;
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
import androidx.fragment.app.Fragment;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PaymentsFragment extends Fragment {

    DairyDataManager dm;
    List<Customer> customers = new ArrayList<>();
    String selectedCid = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_payments, container, false);
        dm = DairyDataManager.getInstance(getContext());
        customers = dm.getCustomers();

        EditText etPayDate = view.findViewById(R.id.etPayDate);
        etPayDate.setText(DairyDataManager.today());
        etPayDate.setFocusable(false);
        etPayDate.setClickable(true);
        etPayDate.setOnClickListener(v -> {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date current = sdf.parse(etPayDate.getText().toString().trim());
                Calendar cal = Calendar.getInstance();
                if (current != null) cal.setTime(current);
                new DatePickerDialog(getContext(),
                    (picker, y, m, d) -> {
                        Calendar nc = Calendar.getInstance();
                        nc.set(y, m, d);
                        etPayDate.setText(sdf.format(nc.getTime()));
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)).show();
            } catch (Exception e) {
                Toast.makeText(getContext(), "Date error!", Toast.LENGTH_SHORT).show();
            }
        });

        setupMemberCodeInput(view);
        loadPayments(view);

        view.findViewById(R.id.btnSavePayment).setOnClickListener(v -> savePayment(view));
        return view;
    }

    double netBill(String customerId) {
        double total = 0;
        for (MilkEntry e : dm.getEntries()) {
            if (e.cid.equals(customerId)) {
                RateEntry rate = dm.getActiveRate(e.date);
                double pf = rate != null ? e.qty * rate.pfPerLiter : 0;
                total += (e.qty * e.rate) - pf;
            }
        }
        return total;
    }

    double netOutstanding(String customerId) {
        return netBill(customerId) - dm.totalPaid(customerId);
    }

    void setupMemberCodeInput(View view) {
        EditText etCode = view.findViewById(R.id.etMemberCode);
        TextView tvInfo = view.findViewById(R.id.tvMemberInfo);

        etCode.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                String code = s.toString().trim().toUpperCase();
                Customer found = null;
                for (Customer cu : customers) {
                    if (cu.memberCode != null && cu.memberCode.toUpperCase().equals(code)) {
                        found = cu;
                        break;
                    }
                }
                if (found != null) {
                    selectedCid = found.id;
                    double bill  = netBill(found.id);
                    double paid  = dm.totalPaid(found.id);
                    double baaki = netOutstanding(found.id);
                    tvInfo.setText(found.name
                        + " | Bill: Rs." + String.format("%.0f", bill)
                        + " | Paid: Rs." + String.format("%.0f", paid)
                        + " | Baaki: Rs." + String.format("%.0f", baaki));
                    tvInfo.setTextColor(baaki > 0 ? 0xFFe05a2b : 0xFF059669);
                    tvInfo.setVisibility(View.VISIBLE);
                } else {
                    selectedCid = "";
                    if (!code.isEmpty()) {
                        tvInfo.setText("Code nahi mila: " + code);
                        tvInfo.setTextColor(0xFFe05a2b);
                        tvInfo.setVisibility(View.VISIBLE);
                    } else {
                        tvInfo.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    void savePayment(View view) {
        if (selectedCid.isEmpty()) {
            Toast.makeText(getContext(), "Sahi member code daalo!", Toast.LENGTH_SHORT).show();
            return;
        }
        String amtStr = ((EditText) view.findViewById(R.id.etAmount)).getText().toString().trim();
        if (amtStr.isEmpty() || Double.parseDouble(amtStr) <= 0) {
            Toast.makeText(getContext(), "Amount daalo!", Toast.LENGTH_SHORT).show();
            return;
        }
        String date = ((EditText) view.findViewById(R.id.etPayDate)).getText().toString().trim();
        String note = ((EditText) view.findViewById(R.id.etNote)).getText().toString().trim();

        Payment p = new Payment();
        p.id = String.valueOf(System.currentTimeMillis());
        p.cid = selectedCid;
        p.amount = Double.parseDouble(amtStr);
        p.date = date;
        p.note = note;

        List<Payment> all = dm.getPayments();
        all.add(p);
        dm.savePayments(all);

        ((EditText) view.findViewById(R.id.etMemberCode)).setText("");
        ((EditText) view.findViewById(R.id.etAmount)).setText("");
        ((EditText) view.findViewById(R.id.etNote)).setText("");
        selectedCid = "";

        Toast.makeText(getContext(), "✅ Payment save ho gayi!", Toast.LENGTH_SHORT).show();
        loadPayments(view);
    }

    void loadPayments(View view) {
        LinearLayout list = view.findViewById(R.id.paymentsList);
        list.removeAllViews();

        List<Payment> payments = dm.getPayments();
        if (payments.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("Koi payment nahi.");
            empty.setTextColor(0xFF9e7b5a);
            empty.setTextSize(13);
            list.addView(empty);
            return;
        }

        payments.sort((a, b) -> b.date.compareTo(a.date));
        int count = Math.min(payments.size(), 15);

        for (int i = 0; i < count; i++) {
            Payment p = payments.get(i);
            Customer c = null;
            for (Customer cu : customers) {
                if (cu.id.equals(p.cid)) { c = cu; break; }
            }

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 10, 0, 10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 1);
            row.setLayoutParams(lp);
            row.setBackgroundColor(0xFFFAF0E6);

            LinearLayout info = new LinearLayout(getContext());
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvName = new TextView(getContext());
            tvName.setText(c != null ? "[" + c.memberCode + "] " + c.name : "?");
            tvName.setTextColor(0xFF3d2610);
            tvName.setTextSize(13);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvDate = new TextView(getContext());
            tvDate.setText(p.date + (p.note != null && !p.note.isEmpty() ? " · " + p.note : ""));
            tvDate.setTextColor(0xFF9e7b5a);
            tvDate.setTextSize(11);

            info.addView(tvName);
            info.addView(tvDate);

            TextView tvAmt = new TextView(getContext());
            tvAmt.setText("Rs." + String.format("%.0f", p.amount));
            tvAmt.setTextColor(0xFF059669);
            tvAmt.setTextSize(14);
            tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);
            tvAmt.setPadding(8, 0, 8, 0);

            final Payment fp = p;
            Button btnDel = new Button(getContext());
            btnDel.setText("Del");
            btnDel.setTextSize(11);
            btnDel.setBackgroundColor(0xFFbe123c);
            btnDel.setTextColor(0xFFFFFFFF);
            btnDel.setOnClickListener(v -> {
                List<Payment> all = dm.getPayments();
                all.removeIf(x -> x.id.equals(fp.id));
                dm.savePayments(all);
                loadPayments(view);
            });

            row.addView(info);
            row.addView(tvAmt);
            row.addView(btnDel);
            list.addView(row);
        }
    }
}
