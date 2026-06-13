package com.example.dairyhisaab;

import android.accounts.Account;
import android.content.Context;
import com.google.android.gms.auth.GoogleAuthUtil;

public class GoogleAuthHelper {
    private static final String SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file";
    
    public static String getToken(Context context, Account account) throws Exception {
        return GoogleAuthUtil.getToken(context, account, SCOPE);
    }
    
    public static void invalidateToken(Context context, String token) {
        try {
            GoogleAuthUtil.clearToken(context, token);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}