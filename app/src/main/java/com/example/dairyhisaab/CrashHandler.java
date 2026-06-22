package com.example.dairyhisaab;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.PrintWriter;
import java.io.StringWriter;

// 🩺 Global crash catcher — logcat ke bina bhi exact crash reason dekhne ke liye.
// App kahin bhi crash ho (kisi bhi Activity/Fragment/background thread mein),
// ye poora stack trace SharedPreferences mein save kar leta hai. Agli baar app
// open hote hi LoginActivity ye trace dikha dega ek dialog mein, phir clear kar dega.
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String PREFS = "crash_prefs";
    private static final String KEY_TRACE = "last_crash_trace";

    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final Context appContext;

    public CrashHandler(Context context, Thread.UncaughtExceptionHandler defaultHandler) {
        this.appContext = context.getApplicationContext();
        this.defaultHandler = defaultHandler;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_TRACE, sw.toString()).apply();
        } catch (Exception ignored) {
            // Crash handler khud crash na kare
        }
        // Default handler ko bhi chalne do (normal crash/close behavior bana rahega)
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }

    // Pichla crash trace lao (null agar koi nahi hai)
    public static String getLastCrash(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TRACE, null);
    }

    // Dikhane ke baad clear kar do taaki dobara dobara na dikhe
    public static void clearLastCrash(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_TRACE).apply();
    }
}
