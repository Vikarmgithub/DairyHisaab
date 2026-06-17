package com.example.dairyhisaab;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * 👑 Admin-only: Device ID se License Key generate karo
 * Sirf ADMIN_EMAIL wala user access kar sakta hai (Firebase Auth se verify hota hai)
 */
public class KeyGeneratorActivity extends Activity {

    // ⚠️ Apni admin Gmail ID yahan set karo
    private static final String ADMIN_EMAIL = "vikarmsrkian6514@gmail.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔒 Sirf admin access kar sakta hai
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.getEmail().equalsIgnoreCase(ADMIN_EMAIL)) {
            Toast.makeText(this,
                "🔴 Unauthorized! Sirf Admin access kar sakta hai.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // ---- UI ----
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 72, 56, 56);
        layout.setBackgroundColor(0xFFF0F4F8);
        scroll.addView(layout);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("🥛 Dairy Hisaab Kitab\n👑 Admin — Key Generator");
        tvTitle.setTextSize(21);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(0xFF1565C0);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 8);
        layout.addView(tvTitle);

        // Admin info
        TextView tvAdmin = new TextView(this);
        tvAdmin.setText("🟢 Verified Admin: " + user.getEmail());
        tvAdmin.setTextSize(12);
        tvAdmin.setTextColor(Color.parseColor("#388E3C"));
        tvAdmin.setGravity(Gravity.CENTER);
        tvAdmin.setPadding(0, 0, 0, 32);
        layout.addView(tvAdmin);

        // Device ID input label
        TextView tvLabel = new TextView(this);
        tvLabel.setText("📱 Customer की Device ID yahan paste karein:");
        tvLabel.setTextSize(14);
        tvLabel.setTextColor(Color.BLACK);
        layout.addView(tvLabel);

        EditText etDeviceId = new EditText(this);
        etDeviceId.setHint("e.g.  ea3df55f0a5378ae");
        etDeviceId.setBackgroundColor(Color.WHITE);
        etDeviceId.setPadding(16, 12, 16, 12);
        layout.addView(etDeviceId);

        // Spacer
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 20));
        layout.addView(spacer);

        // Generate button
        Button btnGenerate = new Button(this);
        btnGenerate.setText("⚡ License Key बनाएं");
        btnGenerate.setBackgroundColor(Color.parseColor("#1565C0"));
        btnGenerate.setTextColor(Color.WHITE);
        btnGenerate.setTextSize(15);
        layout.addView(btnGenerate);

        // Result TextView
        TextView tvResult = new TextView(this);
        tvResult.setTextSize(15);
        tvResult.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvResult.setTextColor(Color.parseColor("#2E7D32"));
        tvResult.setGravity(Gravity.CENTER);
        tvResult.setPadding(0, 28, 0, 28);
        layout.addView(tvResult);

        // Copy button (hidden initially)
        Button btnCopy = new Button(this);
        btnCopy.setText("📋 Key Copy करें");
        btnCopy.setBackgroundColor(Color.parseColor("#00897B"));
        btnCopy.setTextColor(Color.WHITE);
        btnCopy.setVisibility(View.GONE);
        layout.addView(btnCopy);

        setContentView(scroll);

        // ---- Logic ----
        btnGenerate.setOnClickListener(v -> {
            String dId = etDeviceId.getText().toString().trim();
            if (dId.isEmpty()) {
                Toast.makeText(this, "Pehle Device ID daalo!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Algorithm (MainActivity ke validateLicenseKey se match karta hai)
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dId.length(); i++) {
                sb.append((char) (dId.charAt(i) + 3));
            }
            String shifted = sb.toString().toUpperCase();
            if (shifted.length() > 2) {
                char first = shifted.charAt(0);
                char last  = shifted.charAt(shifted.length() - 1);
                shifted = last + shifted.substring(1, shifted.length() - 1) + first;
            }

            final String generatedKey = "DAIRY-" + shifted + "-" + (dId.length() * 7) + "-893";

            tvResult.setText("🔑 License Key:\n\n" + generatedKey);
            btnCopy.setVisibility(View.VISIBLE);

            btnCopy.setOnClickListener(v1 -> {
                ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("DairyKey", generatedKey);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "✅ Key Copy हो गई!", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
