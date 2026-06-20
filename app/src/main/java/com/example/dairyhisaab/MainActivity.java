package com.example.dairyhisaab;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

public class MainActivity extends AppCompatActivity {

    // ==================== TABS ====================
    Button tab_dashboard, tab_members, tab_daily, tab_payments, tab_rate, tab_reports, tab_backup;
    Button[] allTabs;
    ViewPager2 viewPager;
    ImageButton btnSettings;

    // ==================== LICENSE / ACTIVATION ====================
    private String deviceId = "";
    private SharedPreferences activationPrefs;
    private static final String PREFS_NAME   = "DairyLicensePrefs";
    private static final String KEY_ACTIVATED = "IsAppFullyActivated";
    private static final String KEY_DEMO_START = "DemoStartTime";
    private static final long   DEMO_DURATION_MS = 24L * 60 * 60 * 1000; // 24 ghante
    private boolean isAppActivated = false;
    private boolean isDemoMode     = false;
    private boolean demoUsedOnDevice = false; // 🔒 device_locks se aaya result (cached for this session)
    private CountDownTimer demoCountDownTimer = null;
    private TextView tvDemoTimer = null;

    // ==================== LIFECYCLE ====================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Device ID lao ---
        try {
            deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (deviceId == null || deviceId.isEmpty()) deviceId = "DAIRYHISAAB404";
        } catch (Exception e) {
            deviceId = "DAIRYHISAAB999";
        }

        // --- Activation check karo ---
        activationPrefs  = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isAppActivated   = activationPrefs.getBoolean(KEY_ACTIVATED, false);
    if (isAppActivated) {
    FirebaseManager.getInstance().checkLicenseRevoked(deviceId, new FirebaseManager.BooleanCallback() {
        @Override
        public void onSuccess(boolean revoked) {
            if (revoked) {
                activationPrefs.edit().putBoolean(KEY_ACTIVATED, false).apply();
                isAppActivated = false;
                Toast.makeText(MainActivity.this, "❌ License revoke ho gayi hai. Dobara activate karein.", Toast.LENGTH_LONG).show();
                recreate();
            }
        }
        @Override
        public void onFailure(String error) { }
    });
                    }
        // --- Firebase: device ID account se link karo + demo used check karo ---
        if (FirebaseManager.getInstance().isLoggedIn()) {
            // Pehli baar login pe device ID Firebase mein save karo (agar already set nahi)
            FirebaseManager.getInstance().saveDeviceIdIfNew(deviceId);

            // Account pe demo pehle use ho chuka hai kya — Firebase se confirm karo
            FirebaseManager.getInstance().checkDemoUsed(new FirebaseManager.BooleanCallback() {
                @Override
                public void onSuccess(boolean used) {
                    if (used) {
                        // Local mein bhi mark kar do taki dobara reinstall pe demo na mile
                        activationPrefs.edit().putBoolean("DemoUsedOnAccount", true).apply();
                    }
                }
                @Override
                public void onFailure(String error) { }
            });
        }

        // --- Views init karo ---
        initViews();
        setupTabs();

        // tvDemoTimer reference lo (activity_main.xml me add kiya hai)
        tvDemoTimer = findViewById(R.id.tvDemoTimer);

        // --- Demo / Activation flow ---
        // 🔒 FIX: Pehle ye dialog turant (synchronously) khul jaata tha jabki Firebase
        // ka demo-used check abhi async chal rha hota tha — isliye check complete hone
        // se pehle hi Demo button dikh jaata tha (race condition). Ab dialog device-ID
        // lock check complete hone ke BAAD hi khulta hai.
        long demoStart = activationPrefs.getLong(KEY_DEMO_START, 0);
        if (!isAppActivated && demoStart > 0) {
            long elapsed = System.currentTimeMillis() - demoStart;
            if (elapsed < DEMO_DURATION_MS) {
                // Demo isi install pe pehle se chal rha hai — dobara check ki zaroorat nahi
                isDemoMode     = true;
                isAppActivated = true;
                startDemoTimer(DEMO_DURATION_MS - elapsed);
            } else {
                activationPrefs.edit().remove(KEY_DEMO_START).apply();
                verifyDeviceDemoLockThenShowDialog();
            }
        } else if (!isAppActivated) {
            verifyDeviceDemoLockThenShowDialog();
        }

        // --- Settings button ---
        btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsDialog());
        }
    }
    @Override
    protected void onDestroy() {
    super.onDestroy();
    DairyDataManager dm = DairyDataManager.getInstance(this);

    // ✅ Khali ho to backup mat karo, aur Demo Mode mein auto-backup bhi mat karo
    if (!DemoLockHelper.isDemoActive(this)
            && (!dm.getCustomers().isEmpty() || !dm.getEntries().isEmpty())) {
        LocalBackupHelper.saveLocalBackup(this);
    }
}
    // ==================== VIEWS ====================
    private void initViews() {
        tab_dashboard = findViewById(R.id.tab_dashboard);
        tab_members   = findViewById(R.id.tab_members);
        tab_daily     = findViewById(R.id.tab_daily);
        tab_payments  = findViewById(R.id.tab_payments);
        tab_rate      = findViewById(R.id.tab_rate);
        tab_reports   = findViewById(R.id.tab_reports);
        tab_backup    = findViewById(R.id.tab_backup);
        viewPager     = findViewById(R.id.viewPager);

        allTabs = new Button[]{tab_dashboard, tab_members, tab_daily,
                               tab_payments, tab_rate, tab_reports, tab_backup};
    }

    private void setupTabs() {
        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateTabStyles(allTabs[position]);
            }
        });

        tab_dashboard.setOnClickListener(v -> viewPager.setCurrentItem(0, true));
        tab_members.setOnClickListener(v   -> viewPager.setCurrentItem(1, true));
        tab_daily.setOnClickListener(v     -> viewPager.setCurrentItem(2, true));
        tab_payments.setOnClickListener(v  -> viewPager.setCurrentItem(3, true));
        tab_rate.setOnClickListener(v      -> viewPager.setCurrentItem(4, true));
        tab_reports.setOnClickListener(v   -> viewPager.setCurrentItem(5, true));
        tab_backup.setOnClickListener(v    -> viewPager.setCurrentItem(6, true));

        updateTabStyles(tab_dashboard);
    }

    private void updateTabStyles(Button activeTab) {
        for (Button tab : allTabs) {
            if (tab == activeTab) {
                tab.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1565C0")));
                tab.setTextColor(Color.WHITE);
            } else {
                tab.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#102A54")));
                tab.setTextColor(Color.parseColor("#90CAF9"));
            }
        }
    }

    public DairyDataManager getDataManager() {
        return DairyDataManager.getInstance(this);
    }

    // ==================== ⚙️ SETTINGS DIALOG ====================
    private void showSettingsDialog() {
        String status = isAppActivated
                ? (isDemoMode ? "⏳ Demo Mode" : "✅ Activated")
                : "❌ Not Activated";

        String[] options = {
            "🔑 License / Activation  [" + status + "]",
            "👑 Admin Panel (Key Generator)", 
            "🔒 Password Badlein (Email se)"
        };

        new AlertDialog.Builder(this)
            .setTitle("⚙️ Settings")
            .setItems(options, (dialog, which) -> {
    if (which == 0) showActivationDialog();
    else if (which == 1) {
        Intent intent = new Intent(this, KeyGeneratorActivity.class);
        startActivity(intent);
    } else if (which == 2) {
        sendPasswordChangeEmail();
    }
})
            .setNegativeButton("बंद करें", null)
            .show();
    }
    private void sendPasswordChangeEmail() {
    com.google.firebase.auth.FirebaseUser user =
        com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

    if (user == null || user.getEmail() == null) {
        Toast.makeText(this, "Login nahi hai ya email nahi mila!", Toast.LENGTH_SHORT).show();
        return;
    }

    // Sirf email/password wale users ke liye (Google Sign-In users ka Firebase password nahi hota)
    boolean isEmailUser = user.getProviderData().stream()
            .anyMatch(info -> info.getProviderId().equals("password"));
    if (!isEmailUser) {
        Toast.makeText(this, "Google Sign-In account ka password Google se hi change hoga.", Toast.LENGTH_LONG).show();
        return;
    }

    com.google.firebase.auth.FirebaseAuth.getInstance()
        .sendPasswordResetEmail(user.getEmail())
        .addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this,
                    "📧 Password reset link bheja gaya " + user.getEmail() + " pe! Email check karo.",
                    Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this,
                    "❌ Failed: " + (task.getException() != null ? task.getException().getMessage() : "Error"),
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    // ==================== 🔒 DEVICE-ID DEMO LOCK CHECK ====================
    // Activation dialog dikhane se PEHLE Firebase se confirm karo ki is device ID
    // ne kabhi demo use kiya hai ya nahi — reinstall ya naya account banane se
    // bhi ye lock bypass nahi hota, kyunki ye device ID pe based hai, account pe nahi.
    private void verifyDeviceDemoLockThenShowDialog() {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("⏳ Activation status check ho raha hai...");
        pd.setCancelable(false);
        pd.show();

        FirebaseManager.getInstance().checkDeviceDemoUsed(deviceId, new FirebaseManager.BooleanCallback() {
            @Override
            public void onSuccess(boolean usedOnDevice) {
                demoUsedOnDevice = usedOnDevice;
                if (usedOnDevice) {
                    // Local cache bhi update kar do (fast path agle baar ke liye)
                    activationPrefs.edit().putBoolean("DemoUsedOnAccount", true).apply();
                }
                if (pd.isShowing()) pd.dismiss();
                showActivationDialog();
            }
            @Override
            public void onFailure(String error) {
                // Firebase fail (offline waghera) — local cache pe hi bharosa karo,
                // app ko block nahi karna kyunki internet na hone se demo dena galat hoga
                if (pd.isShowing()) pd.dismiss();
                showActivationDialog();
            }
        });
    }

    // ==================== 🔐 ACTIVATION DIALOG ====================
    public void showActivationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔐 App Activation — Dairy Hisaab");
        builder.setCancelable(isDemoMode); // Demo chal rha ho to back ja sako

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        // --- Device ID dikhao ---
        TextView tvDeviceId = new TextView(this);
        tvDeviceId.setText("📱 आपकी Device ID:\n" + deviceId);
        tvDeviceId.setTextSize(13);
        tvDeviceId.setTextColor(Color.parseColor("#1565C0"));
        tvDeviceId.setPadding(0, 0, 0, 12);
        layout.addView(tvDeviceId);

        // --- Copy Device ID button ---
        Button btnCopy = new Button(this);
        btnCopy.setText("📋 Device ID Copy करें");
        btnCopy.setTextColor(Color.WHITE);
        btnCopy.setBackgroundColor(Color.parseColor("#1565C0"));
        btnCopy.setOnClickListener(v -> {
            android.content.ClipboardManager cb =
                (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip =
                android.content.ClipData.newPlainText("DeviceID", deviceId);
            if (cb != null) {
                cb.setPrimaryClip(clip);
                Toast.makeText(this, "✅ Device ID Copy हो गई!", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(btnCopy);

        builder.setView(layout);

        // ✅ Activate button — ab Firestore licenses/{deviceId} se check hota hai
        builder.setPositiveButton("✅ Activation Check Karein", (dialog, which) -> {
            Toast.makeText(this, "⏳ Check kar rahe hain...", Toast.LENGTH_SHORT).show();
            FirebaseManager.getInstance().checkLicenseValid(deviceId, new FirebaseManager.BooleanCallback() {
                @Override
                public void onSuccess(boolean valid) {
                    if (valid) {
                        activationPrefs.edit()
                            .putBoolean(KEY_ACTIVATED, true)
                            .remove(KEY_DEMO_START)
                            .apply();
                        isAppActivated = true;
                        isDemoMode     = false;
                        if (demoCountDownTimer != null) {
                            demoCountDownTimer.cancel();
                            demoCountDownTimer = null;
                        }
                        if (tvDemoTimer != null) tvDemoTimer.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, "🎉 App Activate हो गई! धन्यवाद।", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "❌ Abhi activate nahi hua. Admin se contact karein.", Toast.LENGTH_LONG).show();
                        showActivationDialog();
                    }
                }
                @Override
                public void onFailure(String error) {
                    Toast.makeText(MainActivity.this, "⚠️ Internet check karo, dobara try karo.", Toast.LENGTH_LONG).show();
                }
            });
        });

        // 🕐 Demo button — sirf pehli baar (local, account, YA device pe pehle use na hua ho)
        boolean demoUsed = activationPrefs.getLong(KEY_DEMO_START, 0) > 0
                || activationPrefs.getBoolean("DemoUsedOnAccount", false)
                || demoUsedOnDevice; // 🔒 device-ID lock — reinstall/naya account se bhi bypass nahi hota
        if (!demoUsed) {
            builder.setNegativeButton("🕐 Demo (1 दिन)", (dialog, which) -> {
                activationPrefs.edit().putLong(KEY_DEMO_START, System.currentTimeMillis()).apply();
                activationPrefs.edit().putBoolean("DemoUsedOnAccount", true).apply();
                demoUsedOnDevice = true;
                // Firebase pe bhi mark karo — account AND device dono pe, taaki
                // reinstall ya naya account banane par bhi demo dobara na mile
                if (FirebaseManager.getInstance().isLoggedIn()) {
                    FirebaseManager.getInstance().saveDemoUsed();
                }
                FirebaseManager.getInstance().markDeviceDemoUsed(deviceId);
                isDemoMode     = true;
                isAppActivated = true;
                startDemoTimer(DEMO_DURATION_MS);
                Toast.makeText(this, "✅ 1 दिन का Demo शुरू हुआ!", Toast.LENGTH_LONG).show();
            });
        }

        // Demo chal rha ho to "बाद में" button
        if (isDemoMode) {
            builder.setNeutralButton("⬅️ बाद में", null);
        }

        builder.show();
    }

    // ==================== ⏱ DEMO TIMER ====================
    private void startDemoTimer(long remainingMs) {
        if (tvDemoTimer != null) tvDemoTimer.setVisibility(View.VISIBLE);
        if (demoCountDownTimer != null) demoCountDownTimer.cancel();

        demoCountDownTimer = new CountDownTimer(remainingMs, 1000) {
            @Override
            public void onTick(long ms) {
                long h = ms / 3600000;
                long m = (ms % 3600000) / 60000;
                long s = (ms % 60000) / 1000;
                String txt = String.format("⏱ Demo: %02d:%02d:%02d  बचा है", h, m, s);
                if (tvDemoTimer != null) tvDemoTimer.setText(txt);
            }
            @Override
            public void onFinish() {
                isDemoMode     = false;
                isAppActivated = false;
                activationPrefs.edit().remove(KEY_DEMO_START).apply();
                if (tvDemoTimer != null) tvDemoTimer.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this,
                    "⏰ Demo समाप्त! App activate करें।", Toast.LENGTH_LONG).show();
                showActivationDialog();
            }
        }.start();
    }

}
