package com.example.courtmatepickleballcourtmanager.repository;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Remote bookings are centralized server records, NOT synchronized SQLite local bookings. */
public final class CloudBookingRepository {
    private final String baseUrl;
    private final String bearer;
    public CloudBookingRepository(String url, String token) {
        String normalized = url == null ? "" : url.trim().replaceAll("/+$", "");
        try {
            URI parsed = URI.create(normalized);
            if (!"https".equals(parsed.getScheme()) || parsed.getHost() == null
                    || parsed.getRawUserInfo() != null || parsed.getRawQuery() != null
                    || parsed.getRawFragment() != null) {
                throw new IllegalArgumentException("Máy chủ phải dùng HTTPS hợp lệ");
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Máy chủ phải dùng HTTPS hợp lệ", e);
        }
        if (token == null || token.trim().length() < 32) {
            throw new IllegalArgumentException("Token quản trị không hợp lệ");
        }
        this.baseUrl = normalized;
        this.bearer = token.trim();
    }
    private String request(String method, String path, JSONObject body) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setInstanceFollowRedirects(false); // Do not leak Authorization to redirects.
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + bearer);
            if (body != null) {
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            try (InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                String content = "";
                if (in != null) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] block = new byte[4096];
                    int bytes;
                    while ((bytes = in.read(block)) != -1) {
                        if (buffer.size() + bytes > 1_000_000)
                            throw new IllegalStateException("Phản hồi máy chủ vượt giới hạn");
                        buffer.write(block, 0, bytes);
                    }
                    content = buffer.toString("UTF-8");
                }
                if (code < 200 || code >= 300) {
                    String reason = "Yêu cầu thất bại";
                    try { reason = new JSONObject(content).optString("detail", reason); }
                    catch (Exception ignored) { /* Do not crash on non-JSON server errors */ }
                    throw new IllegalStateException("HTTP " + code + ": " + reason);
                }
                return content;
            }
        } finally {
            conn.disconnect();
        }
    }
    public JSONArray courts() throws Exception {
        return new JSONArray(request("GET", "/v1/admin/courts", null));
    }
    public JSONObject createCourt(String name, int priceHour) throws Exception {
        return new JSONObject(request("POST", "/v1/admin/courts",
                new JSONObject().put("name", name).put("price_hour", priceHour)));
    }
    public JSONObject summary() throws Exception {
        return new JSONObject(request("GET", "/v1/admin/summary", null));
    }
    public JSONObject updateCourtActive(int id, boolean active) throws Exception {
        return new JSONObject(request("POST", "/v1/admin/courts/" + id + "/update",
                new JSONObject().put("active", active)));
    }
    public JSONArray bookings() throws Exception {
        return new JSONArray(request("GET", "/v1/admin/bookings", null));
    }
    public JSONObject create(JSONObject values) throws Exception {
        return new JSONObject(request("POST", "/v1/admin/bookings", values));
    }
    public JSONObject updateStatus(int id, String status) throws Exception {
        // PATCH support added to backend; do not use GET to change booking state.
        return new JSONObject(request("POST", "/v1/admin/bookings/" + id + "/status",
                new JSONObject().put("status", status)));
    }
    public JSONObject bankIntent(int id) throws Exception {
        return new JSONObject(request("POST", "/v1/admin/bookings/" + id + "/bank-intent", null));
    }
}
