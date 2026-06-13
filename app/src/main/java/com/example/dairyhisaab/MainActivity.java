package com.example.dairyhisaab;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    Button tab_dashboard, tab_members, tab_daily, tab_payments, tab_rate, tab_reports, tab_backup;
    Button[] allTabs;
    ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tab_dashboard = findViewById(R.id.tab_dashboard);
        tab_members   = findViewById(R.id.tab_members);
        tab_daily     = findViewById(R.id.tab_daily);
        tab_payments  = findViewById(R.id.tab_payments);
        tab_rate      = findViewById(R.id.tab_rate);
        tab_reports   = findViewById(R.id.tab_reports);
        tab_backup    = findViewById(R.id.tab_backup);
        viewPager     = findViewById(R.id.viewPager);

        allTabs = new Button[]{tab_dashboard, tab_members, tab_daily, tab_payments, tab_rate, tab_reports, tab_backup};

        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateTabStyles(allTabs[position]);
            }
        });

        tab_dashboard.setOnClickListener(v -> viewPager.setCurrentItem(0, true));
        tab_members.setOnClickListener(v -> viewPager.setCurrentItem(1, true));
        tab_daily.setOnClickListener(v -> viewPager.setCurrentItem(2, true));
        tab_payments.setOnClickListener(v -> viewPager.setCurrentItem(3, true));
        tab_rate.setOnClickListener(v -> viewPager.setCurrentItem(4, true));
        tab_reports.setOnClickListener(v -> viewPager.setCurrentItem(5, true));
        tab_backup.setOnClickListener(v -> viewPager.setCurrentItem(6, true));

        updateTabStyles(tab_dashboard);
        scheduleAutoBackup();
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

    private void scheduleAutoBackup() {
        if (!FirebaseManager.getInstance().isLoggedIn()) return;

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest backupRequest = new PeriodicWorkRequest.Builder(
                AutoBackupWorker.class,
                12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "DairyAutoBackup",
                ExistingPeriodicWorkPolicy.KEEP,
                backupRequest);
    }

    public DairyDataManager getDataManager() {
        return DairyDataManager.getInstance(this);
    }
}