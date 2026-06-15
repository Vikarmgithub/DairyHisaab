package com.example.dairyhisaab;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.google.firebase.auth.FirebaseAuth;
import java.io.ByteArrayOutputStream;
import java.text.ParseException;
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
    String paidBySign = "";
    String receivedBySign = "";

    // Filter date state
    String filterDateFrom = "";
    String filterDateTo   = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_payments, container, false);
        dm = DairyDataManager.getInstance(getContext());
        customers = dm.getCustomers();



        setupTabButtons(view);
        setupNewPaymentSection(view);
        setupPrevPaymentsSection(view);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Naya member ya rate add hone ke baad fresh data laao,
        // taaki member code search turant kaam kare
        if (dm != null) {
            customers = dm.getCustomers();
        }
    }

    // ══════════════════════════════════════════════
    //  TAB SWITCHING
    // ══════════════════════════════════════════════

    void setupTabButtons(View view) {
        Button btnNew  = view.findViewById(R.id.btnNewPaymentTab);
        Button btnPrev = view.findViewById(R.id.btnPrevPaymentsTab);
        View secNew  = view.findViewById(R.id.sectionNewPayment);
        View secPrev = view.findViewById(R.id.sectionPrevPayments);

        btnNew.setOnClickListener(v -> {
            secNew.setVisibility(View.VISIBLE);
            secPrev.setVisibility(View.GONE);
            btnNew.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1a3c5e));
            btnPrev.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF6B7280));
        });

        btnPrev.setOnClickListener(v -> {
            secNew.setVisibility(View.GONE);
            secPrev.setVisibility(View.VISIBLE);
            btnPrev.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1a3c5e));
            btnNew.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF6B7280));
        });
    }

    // ══════════════════════════════════════════════
    //  NAYA PAYMENT SECTION
    // ══════════════════════════════════════════════

    void setupNewPaymentSection(View view) {
        // Date picker
        EditText etPayDate = view.findViewById(R.id.etPayDate);
        etPayDate.setText(DairyDataManager.today());
        etPayDate.setOnClickListener(v -> showDatePicker(etPayDate, null));

        // Auto time - current mobile time
        EditText etPayTime = view.findViewById(R.id.etPayTime);
        String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        etPayTime.setText(currentTime);

        setupMemberCodeInput(view);
        setupSignatureButtons(view);

        view.findViewById(R.id.btnSavePayment).setOnClickListener(v -> savePayment(view));
    }

    // ── Member code input ──
    void setupMemberCodeInput(View view) {
        EditText etCode = view.findViewById(R.id.etMemberCode);
        TextView tvInfo = view.findViewById(R.id.tvMemberInfo);

        etCode.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                String code = s.toString().trim().toUpperCase();
                // "1" → try "M001" bhi
                String codeAlt = "";
                try {
                    int num = Integer.parseInt(code);
                    codeAlt = String.format(Locale.getDefault(), "M%03d", num);
                } catch (NumberFormatException ignored) {}

                Customer found = null;
                for (Customer cu : customers) {
                    if (cu.memberCode != null) {
                        String stored = cu.memberCode.toUpperCase();
                        if (stored.equals(code) || (!codeAlt.isEmpty() && stored.equals(codeAlt))) {
                            found = cu;
                            break;
                        }
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

    // ── Save payment ──
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
        String time = ((EditText) view.findViewById(R.id.etPayTime)).getText().toString().trim();
        String note = ((EditText) view.findViewById(R.id.etNote)).getText().toString().trim();

        Payment p = new Payment();
        p.id             = String.valueOf(System.currentTimeMillis());
        p.cid            = selectedCid;
        p.amount         = Double.parseDouble(amtStr);
        p.date           = date;
        p.time           = time;
        p.note           = note;
        p.paidBySign     = paidBySign;
        p.receivedBySign = receivedBySign;

        List<Payment> all = dm.getPayments();
        all.add(p);
        dm.savePayments(all);

        // Reset form
        ((EditText) view.findViewById(R.id.etMemberCode)).setText("");
        ((EditText) view.findViewById(R.id.etAmount)).setText("");
        ((EditText) view.findViewById(R.id.etNote)).setText("");
        ((EditText) view.findViewById(R.id.etPayTime)).setText(
            new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        ((TextView) view.findViewById(R.id.tvMemberInfo)).setVisibility(View.GONE);
        ((TextView) view.findViewById(R.id.tvPaidSignStatus)).setVisibility(View.GONE);
        ((TextView) view.findViewById(R.id.tvReceivedSignStatus)).setVisibility(View.GONE);
        selectedCid      = "";
        paidBySign       = "";
        receivedBySign   = "";
        customers        = dm.getCustomers();

        Toast.makeText(getContext(), "✅ Payment save ho gayi!", Toast.LENGTH_SHORT).show();
    }

    // ══════════════════════════════════════════════
    //  PURANE PAYMENTS SECTION
    // ══════════════════════════════════════════════

    void setupPrevPaymentsSection(View view) {
        EditText etFrom   = view.findViewById(R.id.etFilterDateFrom);
        EditText etTo     = view.findViewById(R.id.etFilterDateTo);
        EditText etMember = view.findViewById(R.id.etFilterMember);
        Button   btnSearch = view.findViewById(R.id.btnSearchPayments);

        // Default: aaj se aaj tak
        String today = DairyDataManager.today();
        filterDateFrom = today;
        filterDateTo   = today;
        etFrom.setText(formatDisplayDate(today));
        etTo.setText(formatDisplayDate(today));

        etFrom.setOnClickListener(v -> showDatePicker(etFrom, date -> filterDateFrom = date));
        etTo.setOnClickListener(v ->   showDatePicker(etTo,   date -> filterDateTo   = date));

        btnSearch.setOnClickListener(v -> {
            String memberInput = etMember.getText().toString().trim().toUpperCase();
            String filterCid   = "";

            if (!memberInput.isEmpty()) {
                // Same M001 / 1 matching logic
                String memberAlt = "";
                try {
                    int num = Integer.parseInt(memberInput);
                    memberAlt = String.format(Locale.getDefault(), "M%03d", num);
                } catch (NumberFormatException ignored) {}

                for (Customer cu : customers) {
                    if (cu.memberCode != null) {
                        String stored = cu.memberCode.toUpperCase();
                        if (stored.equals(memberInput) || (!memberAlt.isEmpty() && stored.equals(memberAlt))) {
                            filterCid = cu.id;
                            break;
                        }
                    }
                }
                if (filterCid.isEmpty()) {
                    Toast.makeText(getContext(), "Member code nahi mila: " + memberInput, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (filterDateFrom.isEmpty() || filterDateTo.isEmpty()) {
                Toast.makeText(getContext(), "Date range choose karo!", Toast.LENGTH_SHORT).show();
                return;
            }

            showFilteredPayments(view, filterCid, filterDateFrom, filterDateTo);
        });
    }

    void showFilteredPayments(View view, String filterCid, String fromDate, String toDate) {
        LinearLayout resultsCard = view.findViewById(R.id.prevResultsCard);
        LinearLayout listLayout  = view.findViewById(R.id.prevPaymentsList);
        TextView     tvTitle     = view.findViewById(R.id.tvPrevResultTitle);
        TextView     tvTotal     = view.findViewById(R.id.tvPrevTotal);

        listLayout.removeAllViews();
        resultsCard.setVisibility(View.VISIBLE);

        List<Payment> allPayments = dm.getPayments();
        List<Payment> filtered    = new ArrayList<>();

        for (Payment p : allPayments) {
            if (p.date == null) continue;
            boolean inRange = p.date.compareTo(fromDate) >= 0 && p.date.compareTo(toDate) <= 0;
            boolean memberMatch = filterCid.isEmpty() || p.cid.equals(filterCid);
            if (inRange && memberMatch) filtered.add(p);
        }

        filtered.sort((a, b) -> {
            int dateCompare = b.date.compareTo(a.date);
            if (dateCompare != 0) return dateCompare;
            // Same date: sort by time descending
            String tA = a.time != null ? a.time : "00:00";
            String tB = b.time != null ? b.time : "00:00";
            return tB.compareTo(tA);
        });

        // Title
        String memberLabel = filterCid.isEmpty() ? "Sab Members" : getMemberLabel(filterCid);
        tvTitle.setText("📊 " + memberLabel + " | " + formatDisplayDate(fromDate) + " → " + formatDisplayDate(toDate));

        if (filtered.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("Is period mein koi transaction nahi mili.");
            empty.setTextColor(0xFF9e7b5a);
            empty.setTextSize(13);
            listLayout.addView(empty);
            tvTotal.setVisibility(View.GONE);
            return;
        }

        double grandTotal = 0;

        for (Payment p : filtered) {
            Customer c = null;
            for (Customer cu : customers) {
                if (cu.id.equals(p.cid)) { c = cu; break; }
            }
            final Customer fc = c;
            grandTotal += p.amount;

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8, 10, 8, 10);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, 2);
            row.setLayoutParams(rowLp);
            row.setBackgroundColor(0xFFFAF0E6);

            // Info
            LinearLayout info = new LinearLayout(getContext());
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvName = new TextView(getContext());
            tvName.setText(c != null ? "[" + c.memberCode + "] " + c.name : "?");
            tvName.setTextColor(0xFF3d2610);
            tvName.setTextSize(13);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvDateTime = new TextView(getContext());
            String timeStr = (p.time != null && !p.time.isEmpty()) ? " " + p.time : "";
            String noteStr = (p.note != null && !p.note.isEmpty()) ? " · " + p.note : "";
            tvDateTime.setText(p.date + timeStr + noteStr);
            tvDateTime.setTextColor(0xFF9e7b5a);
            tvDateTime.setTextSize(11);

            info.addView(tvName);
            info.addView(tvDateTime);

            // Amount
            TextView tvAmt = new TextView(getContext());
            tvAmt.setText("Rs." + String.format("%.0f", p.amount));
            tvAmt.setTextColor(0xFF059669);
            tvAmt.setTextSize(14);
            tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);
            tvAmt.setPadding(8, 0, 4, 0);

            // View button
            Button btnView = new Button(getContext());
            btnView.setText("👁");
            btnView.setTextSize(12);
            btnView.setBackgroundColor(0xFF1a3c5e);
            btnView.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            vLp.setMargins(4, 0, 4, 0);
            btnView.setLayoutParams(vLp);
            final Payment fp = p;
            btnView.setOnClickListener(v -> showPaymentDetail(fp, fc));

            // Delete button
            Button btnDel = new Button(getContext());
            btnDel.setText("🗑");
            btnDel.setTextSize(12);
            btnDel.setBackgroundColor(0xFFbe123c);
            btnDel.setTextColor(Color.WHITE);
            btnDel.setOnClickListener(v ->
                BiometricHelper.authenticate(this, "🗑️ Delete Confirm Karo", () -> {
                    List<Payment> all = dm.getPayments();
                    all.removeIf(x -> x.id.equals(fp.id));
                    dm.savePayments(all);
                    showFilteredPayments(view, filterCid, fromDate, toDate);
                })
            );

            row.addView(info);
            row.addView(tvAmt);
            row.addView(btnView);
            row.addView(btnDel);
            listLayout.addView(row);
        }

        // Total
        tvTotal.setText("💰 Kul Amount: Rs." + String.format("%.0f", grandTotal)
            + "  (" + filtered.size() + " transactions)");
        tvTotal.setVisibility(View.VISIBLE);
    }

    String getMemberLabel(String cid) {
        for (Customer cu : customers) {
            if (cu.id.equals(cid)) return "[" + cu.memberCode + "] " + cu.name;
        }
        return "?";
    }

    // ══════════════════════════════════════════════
    //  PIN DIALOGS
    // ══════════════════════════════════════════════

    void showSetPinDialog() {
        Dialog d = new Dialog(getContext());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(false);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 56, 56, 56);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(getContext());
        title.setText("🔐 DELETE PIN SET KARO");
        title.setTextSize(16);
        title.setTextColor(0xFF1a3c5e);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 8);

        TextView sub = new TextView(getContext());
        sub.setText("Yeh PIN har delete ke liye maanga jaayega.\nBackup tab se baad mein change bhi kar sakte ho.");
        sub.setTextSize(12);
        sub.setTextColor(0xFF9e7b5a);
        sub.setPadding(0, 0, 0, 24);

        EditText etPin = new EditText(getContext());
        etPin.setHint("4-6 digit PIN daalo");
        etPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin.setPadding(20, 14, 20, 14);
        etPin.setBackgroundColor(0xFFF4F7FC);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp1.setMargins(0, 0, 0, 14);
        etPin.setLayoutParams(lp1);

        EditText etPin2 = new EditText(getContext());
        etPin2.setHint("PIN dobara daalo");
        etPin2.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin2.setPadding(20, 14, 20, 14);
        etPin2.setBackgroundColor(0xFFF4F7FC);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, 0, 0, 24);
        etPin2.setLayoutParams(lp2);

        Button btnSet = new Button(getContext());
        btnSet.setText("✅ PIN SET KARO");
        btnSet.setBackgroundColor(0xFF1a3c5e);
        btnSet.setTextColor(Color.WHITE);

        btnSet.setOnClickListener(v -> {
            String p1 = etPin.getText().toString().trim();
            String p2 = etPin2.getText().toString().trim();
            if (p1.length() < 4) {
                Toast.makeText(getContext(), "Kam se kam 4 digit ka PIN chahiye!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!p1.equals(p2)) {
                Toast.makeText(getContext(), "PIN match nahi kiya!", Toast.LENGTH_SHORT).show();
                etPin2.setText("");
                return;
            }
            dm.savePin(p1);
            Toast.makeText(getContext(), "✅ PIN set ho gaya!", Toast.LENGTH_SHORT).show();
            d.dismiss();
        });

        layout.addView(title);
        layout.addView(sub);
        layout.addView(etPin);
        layout.addView(etPin2);
        layout.addView(btnSet);
        d.setContentView(layout);
        d.show();
    }

    void askPinThenDelete(Runnable onSuccess) {
        Dialog d = new Dialog(getContext());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 56, 56, 56);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(getContext());
        title.setText("🔐 DELETE PIN DAALO");
        title.setTextSize(16);
        title.setTextColor(0xFFbe123c);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 20);

        EditText etPin = new EditText(getContext());
        etPin.setHint("PIN daalo");
        etPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin.setPadding(20, 14, 20, 14);
        etPin.setBackgroundColor(0xFFF4F7FC);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 20);
        etPin.setLayoutParams(lp);

        Button btnOk = new Button(getContext());
        btnOk.setText("🗑 DELETE KARO");
        btnOk.setBackgroundColor(0xFFbe123c);
        btnOk.setTextColor(Color.WHITE);

        // Forgot PIN link
        TextView tvForgotPin = new TextView(getContext());
        tvForgotPin.setText("🔑 PIN bhool gaye? Login password se reset karo");
        tvForgotPin.setTextColor(0xFF1d6ec9);
        tvForgotPin.setPadding(0, 16, 0, 0);
        tvForgotPin.setTextSize(13);
        tvForgotPin.setClickable(true);
        tvForgotPin.setPaintFlags(tvForgotPin.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        tvForgotPin.setOnClickListener(v -> {
            d.dismiss();
            showPinResetDialog();
        });

        btnOk.setOnClickListener(v -> {
            if (dm.verifyPin(etPin.getText().toString().trim())) {
                d.dismiss();
                onSuccess.run();
            } else {
                Toast.makeText(getContext(), "❌ Galat PIN!", Toast.LENGTH_SHORT).show();
                etPin.setText("");
            }
        });

        layout.addView(title);
        layout.addView(etPin);
        layout.addView(btnOk);
        layout.addView(tvForgotPin);
        d.setContentView(layout);
        d.show();
    }

    void showPinResetDialog() {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(getContext(), "❌ Koi login account nahi mila.", Toast.LENGTH_SHORT).show();
            return;
        }
        String userEmail = user.getEmail();

        Dialog d2 = new Dialog(getContext());
        d2.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 56, 56, 56);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(getContext());
        title.setText("🔑 PIN RESET KARO");
        title.setTextSize(16);
        title.setTextColor(0xFF0369a1);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 6);

        TextView subtitle = new TextView(getContext());
        subtitle.setText("Account: " + userEmail);
        subtitle.setTextSize(12);
        subtitle.setTextColor(0xFF64748b);
        subtitle.setPadding(0, 0, 0, 20);

        EditText etPassword = new EditText(getContext());
        etPassword.setHint("Login password daalo");
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setPadding(20, 14, 20, 14);
        etPassword.setBackgroundColor(0xFFF4F7FC);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp1.setMargins(0, 0, 0, 16);
        etPassword.setLayoutParams(lp1);

        EditText etNewPin = new EditText(getContext());
        etNewPin.setHint("Naya PIN daalo (4-6 digit)");
        etNewPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etNewPin.setPadding(20, 14, 20, 14);
        etNewPin.setBackgroundColor(0xFFF4F7FC);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, 0, 0, 16);
        etNewPin.setLayoutParams(lp2);

        EditText etConfirmPin = new EditText(getContext());
        etConfirmPin.setHint("PIN dobara daalo");
        etConfirmPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etConfirmPin.setPadding(20, 14, 20, 14);
        etConfirmPin.setBackgroundColor(0xFFF4F7FC);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp3.setMargins(0, 0, 0, 20);
        etConfirmPin.setLayoutParams(lp3);

        Button btnReset = new Button(getContext());
        btnReset.setText("✅ PIN RESET KARO");
        btnReset.setBackgroundColor(0xFF0369a1);
        btnReset.setTextColor(Color.WHITE);

        btnReset.setOnClickListener(v -> {
            String password   = etPassword.getText().toString().trim();
            String newPin     = etNewPin.getText().toString().trim();
            String confirmPin = etConfirmPin.getText().toString().trim();

            if (password.isEmpty()) {
                Toast.makeText(getContext(), "Login password daalo!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPin.length() < 4) {
                Toast.makeText(getContext(), "PIN kam se kam 4 digit ka hona chahiye!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPin.equals(confirmPin)) {
                Toast.makeText(getContext(), "PIN match nahi kiya!", Toast.LENGTH_SHORT).show();
                etConfirmPin.setText("");
                return;
            }

            com.google.firebase.auth.AuthCredential credential =
                com.google.firebase.auth.EmailAuthProvider.getCredential(userEmail, password);

            user.reauthenticate(credential)
                .addOnSuccessListener(task -> {
                    dm.savePin(newPin);
                    d2.dismiss();
                    Toast.makeText(getContext(), "✅ Naya PIN set ho gaya!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(),
                        "❌ Galat password! Login password sahi daalo.", Toast.LENGTH_LONG).show();
                    etPassword.setText("");
                });
        });

        layout.addView(title);
        layout.addView(subtitle);
        layout.addView(etPassword);
        layout.addView(etNewPin);
        layout.addView(etConfirmPin);
        layout.addView(btnReset);
        d2.setContentView(layout);
        d2.show();
    }

    // ══════════════════════════════════════════════
    //  SIGNATURE BUTTONS
    // ══════════════════════════════════════════════

    void setupSignatureButtons(View view) {
        Button btnPaidBy      = view.findViewById(R.id.btnPaidBy);
        Button btnReceivedBy  = view.findViewById(R.id.btnReceivedBy);
        TextView tvPaidStatus = view.findViewById(R.id.tvPaidSignStatus);
        TextView tvReceivedStatus = view.findViewById(R.id.tvReceivedSignStatus);

        btnPaidBy.setOnClickListener(v ->
            showSignatureDialog("Paid By (Dene Wala)", bitmap -> {
                paidBySign = bitmapToBase64(bitmap);
                tvPaidStatus.setText("✅ Signed");
                tvPaidStatus.setTextColor(0xFF059669);
                tvPaidStatus.setVisibility(View.VISIBLE);
            })
        );

        btnReceivedBy.setOnClickListener(v ->
            showSignatureDialog("Received By (Lene Wala)", bitmap -> {
                receivedBySign = bitmapToBase64(bitmap);
                tvReceivedStatus.setText("✅ Signed");
                tvReceivedStatus.setTextColor(0xFF059669);
                tvReceivedStatus.setVisibility(View.VISIBLE);
            })
        );
    }

    void showSignatureDialog(String title, SignatureCallback callback) {
        Dialog d = new Dialog(getContext());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setBackgroundColor(Color.WHITE);

        TextView tv = new TextView(getContext());
        tv.setText("✍️ " + title);
        tv.setTextSize(15);
        tv.setTextColor(0xFF1a3c5e);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, 0, 0, 16);

        SignatureView signView = new SignatureView(getContext());
        LinearLayout.LayoutParams signLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 400);
        signLp.setMargins(0, 0, 0, 8);
        signView.setLayoutParams(signLp);

        TextView hint = new TextView(getContext());
        hint.setText("Upar white box mein sign karo");
        hint.setTextSize(11);
        hint.setTextColor(0xFF9e7b5a);
        hint.setPadding(0, 0, 0, 16);

        LinearLayout btnRow = new LinearLayout(getContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setWeightSum(2);

        Button btnClear = new Button(getContext());
        btnClear.setText("🗑 Clear");
        btnClear.setBackgroundColor(0xFFe8d5c0);
        btnClear.setTextColor(0xFF3d2610);
        LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        bp1.setMargins(0, 0, 8, 0);
        btnClear.setLayoutParams(bp1);
        btnClear.setOnClickListener(v -> signView.clear());

        Button btnSave = new Button(getContext());
        btnSave.setText("✅ Save Sign");
        btnSave.setBackgroundColor(0xFF1a3c5e);
        btnSave.setTextColor(Color.WHITE);
        btnSave.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        btnSave.setOnClickListener(v -> {
            if (signView.isEmpty()) {
                Toast.makeText(getContext(), "Pehle sign karo!", Toast.LENGTH_SHORT).show();
                return;
            }
            callback.onSigned(signView.getBitmap());
            d.dismiss();
        });

        btnRow.addView(btnClear);
        btnRow.addView(btnSave);

        layout.addView(tv);
        layout.addView(signView);
        layout.addView(hint);
        layout.addView(btnRow);
        d.setContentView(layout);
        d.show();
    }

    interface SignatureCallback {
        void onSigned(Bitmap bitmap);
    }

    // ══════════════════════════════════════════════
    //  PAYMENT DETAIL POPUP
    // ══════════════════════════════════════════════

    void showPaymentDetail(Payment p, Customer c) {
        Dialog d = new Dialog(getContext());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(getContext());
        title.setText("💰 Payment Detail");
        title.setTextSize(17);
        title.setTextColor(0xFF1a3c5e);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        addDetailRow(layout, "👤 Member", c != null ? "[" + c.memberCode + "] " + c.name : "?");
        addDetailRow(layout, "📅 Date", p.date);
        addDetailRow(layout, "🕐 Time", (p.time != null && !p.time.isEmpty()) ? p.time : "—");
        addDetailRow(layout, "💵 Amount", "Rs." + String.format("%.2f", p.amount));
        if (p.note != null && !p.note.isEmpty())
            addDetailRow(layout, "📝 Note", p.note);

        if (p.paidBySign != null && !p.paidBySign.isEmpty()) {
            TextView lbl1 = new TextView(getContext());
            lbl1.setText("✍️ Paid By (Dene Wala):");
            lbl1.setTextSize(12);
            lbl1.setTextColor(0xFF9e7b5a);
            lbl1.setTypeface(null, android.graphics.Typeface.BOLD);
            lbl1.setPadding(0, 16, 0, 6);
            layout.addView(lbl1);

            ImageView imgPaid = new ImageView(getContext());
            byte[] paidBytes = Base64.decode(p.paidBySign, Base64.DEFAULT);
            Bitmap paidBmp   = BitmapFactory.decodeByteArray(paidBytes, 0, paidBytes.length);
            imgPaid.setImageBitmap(paidBmp);
            imgPaid.setBackgroundColor(0xFFF4F7FC);
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300);
            imgLp.setMargins(0, 0, 0, 12);
            imgPaid.setLayoutParams(imgLp);
            imgPaid.setScaleType(ImageView.ScaleType.FIT_CENTER);
            layout.addView(imgPaid);
        }

        if (p.receivedBySign != null && !p.receivedBySign.isEmpty()) {
            TextView lbl2 = new TextView(getContext());
            lbl2.setText("✍️ Received By (Lene Wala):");
            lbl2.setTextSize(12);
            lbl2.setTextColor(0xFF9e7b5a);
            lbl2.setTypeface(null, android.graphics.Typeface.BOLD);
            lbl2.setPadding(0, 8, 0, 6);
            layout.addView(lbl2);

            ImageView imgRecv = new ImageView(getContext());
            byte[] recvBytes  = Base64.decode(p.receivedBySign, Base64.DEFAULT);
            Bitmap recvBmp    = BitmapFactory.decodeByteArray(recvBytes, 0, recvBytes.length);
            imgRecv.setImageBitmap(recvBmp);
            imgRecv.setBackgroundColor(0xFFF4F7FC);
            LinearLayout.LayoutParams imgLp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300);
            imgLp2.setMargins(0, 0, 0, 12);
            imgRecv.setLayoutParams(imgLp2);
            imgRecv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            layout.addView(imgRecv);
        }

        if ((p.paidBySign == null || p.paidBySign.isEmpty()) &&
            (p.receivedBySign == null || p.receivedBySign.isEmpty())) {
            TextView noSign = new TextView(getContext());
            noSign.setText("📋 Koi signature save nahi tha.");
            noSign.setTextSize(12);
            noSign.setTextColor(0xFF9e7b5a);
            noSign.setPadding(0, 12, 0, 0);
            layout.addView(noSign);
        }

        Button btnClose = new Button(getContext());
        btnClose.setText("✖ BAND KARO");
        btnClose.setBackgroundColor(0xFF1a3c5e);
        btnClose.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        closeLp.setMargins(0, 20, 0, 0);
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(v -> d.dismiss());
        layout.addView(btnClose);

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.addView(layout);
        d.setContentView(scrollView);
        d.show();
    }

    // ══════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════

    /** DatePicker jo EditText mein yyyy-MM-dd set kare aur display DD/MM/YY dikhaaye */
    interface DateCallback { void onDate(String isoDate); }

    void showDatePicker(EditText target, DateCallback callback) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        // Agar already kuch hai to parse karo
        try {
            String existing = target.getTag() != null ? (String) target.getTag() : "";
            if (!existing.isEmpty()) {
                Date d = sdf.parse(existing);
                if (d != null) cal.setTime(d);
            }
        } catch (ParseException ignored) {}

        new DatePickerDialog(getContext(), (picker, y, m, dd) -> {
            Calendar nc = Calendar.getInstance();
            nc.set(y, m, dd);
            String iso = sdf.format(nc.getTime());
            target.setTag(iso);                          // raw ISO stored in tag
            target.setText(formatDisplayDate(iso));      // display DD/MM/YY
            if (callback != null) callback.onDate(iso);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** yyyy-MM-dd → DD/MM/YY */
    String formatDisplayDate(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(iso);
            return new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(d);
        } catch (ParseException e) { return iso; }
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

    String bitmapToBase64(Bitmap bmp) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 80, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    void addDetailRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 10);
        row.setLayoutParams(lp);

        TextView tvLabel = new TextView(getContext());
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(0xFF9e7b5a);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvValue = new TextView(getContext());
        tvValue.setText(value);
        tvValue.setTextSize(13);
        tvValue.setTextColor(0xFF1a3c5e);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));

        row.addView(tvLabel);
        row.addView(tvValue);
        parent.addView(row);
    }
}
