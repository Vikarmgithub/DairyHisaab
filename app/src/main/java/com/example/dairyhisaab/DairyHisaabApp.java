package com.example.dairyhisaab;

import android.app.Application;

public class DairyHisaabApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this, defaultHandler));
    }
}
