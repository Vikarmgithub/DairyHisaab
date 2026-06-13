package com.example.dairyhisaab;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class MainPagerAdapter extends FragmentStateAdapter {
    public MainPagerAdapter(@NonNull FragmentActivity fa) { super(fa); }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new DashboardFragment();
            case 1: return new MembersFragment();
            case 2: return new DailyEntryFragment();
            case 3: return new PaymentsFragment();
            case 4: return new RateFragment();
            case 5: return new ReportsFragment();
            case 6: return new BackupFragment();
            default: return new DashboardFragment();
        }
    }

    @Override
    public int getItemCount() { return 7; }
}