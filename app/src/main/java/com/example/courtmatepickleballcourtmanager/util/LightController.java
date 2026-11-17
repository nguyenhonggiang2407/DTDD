package com.example.courtmatepickleballcourtmanager.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.model.LightConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LightController {

    private static final String TAG = "LightController";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LightController() {
    }

    public interface Callback {
        void onResult(boolean success, String message);
    }

    public static void turnOn(Context context, int courtId, Callback callback) {
        setLight(context, courtId, true, callback);
    }

    public static void turnOff(Context context, int courtId, Callback callback) {
        setLight(context, courtId, false, callback);
    }

    public static void test(Context context, LightConfig config, boolean on, Callback callback) {
        EXECUTOR.execute(() -> send(context, config, on, callback));
    }

    private static void setLight(Context context, int courtId, boolean on, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            LightConfig config = DatabaseHelper.getInstance(app).getLightConfig(courtId);
            if (config == null || !config.isConfigured()) {
                post(callback, false, "Sân chưa cấu hình đèn");
                return;
            }
            send(app, config, on, callback);
        });
    }

    private static void send(Context context, LightConfig config, boolean on, Callback callback) {
        HttpURLConnection connection = null;
        try {
            String url = buildUrl(config, on);
            String method = LightConfig.TYPE_HOME_ASSISTANT.equals(config.type)
                    ? "POST" : (config.method == null ? "GET" : config.method);
            String body = LightConfig.TYPE_HOME_ASSISTANT.equals(config.type)
                    ? "{\"entity_id\":\"" + config.entityId + "\"}" : null;

            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(config.timeoutMs > 0 ? config.timeoutMs : 5000);
            connection.setReadTimeout(config.timeoutMs > 0 ? config.timeoutMs : 5000);
            connection.setRequestProperty("Accept", "application/json");
            if (config.token != null && !config.token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + config.token);
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            boolean success = code >= 200 && code < 300;
            String message = success ? "Đã " + (on ? "bật" : "tắt") + " đèn"
                    : "Lỗi HTTP " + code;
            DatabaseHelper.getInstance(context).updateLightStatus(config.courtId,
                    success ? (on ? LightConfig.STATUS_ON : LightConfig.STATUS_OFF)
                            : LightConfig.STATUS_ERROR,
                    success ? null : message);
            post(callback, success, message);
        } catch (Exception e) {
            Log.w(TAG, "Cannot control light", e);
            DatabaseHelper.getInstance(context).updateLightStatus(config.courtId,
                    LightConfig.STATUS_ERROR, e.getMessage());
            post(callback, false, "Không điều khiển được đèn: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String buildUrl(LightConfig config, boolean on) {
        if (LightConfig.TYPE_HOME_ASSISTANT.equals(config.type)) {
            String base = config.onUrl.endsWith("/")
                    ? config.onUrl.substring(0, config.onUrl.length() - 1) : config.onUrl;
            return base + "/api/services/light/" + (on ? "turn_on" : "turn_off");
        }
        return on ? config.onUrl : config.offUrl;
    }

    private static void post(Callback callback, boolean success, String message) {
        if (callback == null) {
            return;
        }
        MAIN.post(() -> callback.onResult(success, message));
    }
}
