package com.example.dairyhisaab;

import android.content.Context;
import android.util.Log;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DriveHelper {

    private static final String TAG = "DriveHelper";
    private static final String FOLDER_NAME = "DairyHisaab_Backups";
    private static final String BACKUP_FILE_NAME = "DairyHisaab_Latest_Backup.json";

    // Token lao
    private static String getAccessToken(Context context) {
        try {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
            if (account == null) return null;
            return GoogleAuthHelper.getToken(context, account.getAccount());
        } catch (Exception e) {
            Log.e(TAG, "Token error: " + e.getMessage());
            return null;
        }
    }

    // Folder ID dhundo ya banao
    public static String getOrCreateFolder(String token) throws Exception {
        // Pehle dhundo
        String query = "mimeType='application/vnd.google-apps.folder' and name='" + FOLDER_NAME + "' and trashed=false";
        URL url = new URL("https://www.googleapis.com/drive/v3/files?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&fields=files(id)");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");

        if (conn.getResponseCode() == 200) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject response = new JSONObject(sb.toString());
            JSONArray files = response.getJSONArray("files");
            if (files.length() > 0) {
                return files.getJSONObject(0).getString("id");
            }
        }
        conn.disconnect();

        // Nahi mila, banao
        URL createUrl = new URL("https://www.googleapis.com/drive/v3/files");
        HttpURLConnection createConn = (HttpURLConnection) createUrl.openConnection();
        createConn.setRequestMethod("POST");
        createConn.setRequestProperty("Authorization", "Bearer " + token);
        createConn.setRequestProperty("Content-Type", "application/json");
        createConn.setDoOutput(true);

        JSONObject meta = new JSONObject();
        meta.put("name", FOLDER_NAME);
        meta.put("mimeType", "application/vnd.google-apps.folder");

        OutputStream os = createConn.getOutputStream();
        os.write(meta.toString().getBytes(StandardCharsets.UTF_8));
        os.close();

        BufferedReader br = new BufferedReader(new InputStreamReader(createConn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        createConn.disconnect();

        return new JSONObject(sb.toString()).getString("id");
    }

    // Purani file ka ID dhundo
    private static String findExistingFileId(String token, String folderId) throws Exception {
        String query = "name='" + BACKUP_FILE_NAME + "' and '" + folderId + "' in parents and trashed=false";
        URL url = new URL("https://www.googleapis.com/drive/v3/files?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&fields=files(id)");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + token);

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();

        JSONObject response = new JSONObject(sb.toString());
        JSONArray files = response.getJSONArray("files");
        if (files.length() > 0) return files.getJSONObject(0).getString("id");
        return null;
    }

    // Upload ya update karo (sirf 1 file rahegi)
    public static boolean uploadBackup(Context context, String jsonData) {
        try {
            String token = getAccessToken(context);
            if (token == null) return false;

            String folderId = getOrCreateFolder(token);
            String existingId = findExistingFileId(token, folderId);

            String boundary = "-------DairyBackup";
            String metaJson;

            if (existingId != null) {
                // Update karo
                metaJson = "{\"name\":\"" + BACKUP_FILE_NAME + "\"}";
                URL url = new URL("https://www.googleapis.com/upload/drive/v3/files/" + existingId + "?uploadType=multipart");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
                conn.setDoOutput(true);
                writeMultipart(conn, boundary, metaJson, jsonData, folderId, false);
                int code = conn.getResponseCode();
                conn.disconnect();
                return code == 200;
            } else {
                // Naya banao
                metaJson = "{\"name\":\"" + BACKUP_FILE_NAME + "\",\"parents\":[\"" + folderId + "\"]}";
                URL url = new URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
                conn.setDoOutput(true);
                writeMultipart(conn, boundary, metaJson, jsonData, folderId, true);
                int code = conn.getResponseCode();
                conn.disconnect();
                return code == 200;
            }

        } catch (Exception e) {
            Log.e(TAG, "Upload error: " + e.getMessage());
            return false;
        }
    }

    private static void writeMultipart(HttpURLConnection conn, String boundary,
                                        String meta, String data,
                                        String folderId, boolean isNew) throws Exception {
        OutputStream os = conn.getOutputStream();
        String CRLF = "\r\n";
        os.write(("--" + boundary + CRLF).getBytes());
        os.write(("Content-Type: application/json; charset=UTF-8" + CRLF + CRLF).getBytes());
        os.write(meta.getBytes(StandardCharsets.UTF_8));
        os.write((CRLF + "--" + boundary + CRLF).getBytes());
        os.write(("Content-Type: application/json" + CRLF + CRLF).getBytes());
        os.write(data.getBytes(StandardCharsets.UTF_8));
        os.write((CRLF + "--" + boundary + "--" + CRLF).getBytes());
        os.flush();
        os.close();
    }

    // Download karo restore ke liye
    public static String downloadBackup(Context context) {
        try {
            String token = getAccessToken(context);
            if (token == null) return null;

            String folderId = getOrCreateFolder(token);
            String fileId = findExistingFileId(token, folderId);
            if (fileId == null) return null;

            URL url = new URL("https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + token);

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            conn.disconnect();
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Download error: " + e.getMessage());
            return null;
        }
    }
}