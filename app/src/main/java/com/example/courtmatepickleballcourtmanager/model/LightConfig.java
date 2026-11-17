package com.example.courtmatepickleballcourtmanager.model;

public class LightConfig {

    public static final String TYPE_ESP32 = "ESP32";
    public static final String TYPE_HOME_ASSISTANT = "HOME_ASSISTANT";
    public static final String STATUS_ON = "DANG_BAT";
    public static final String STATUS_OFF = "DANG_TAT";
    public static final String STATUS_UNKNOWN = "KHONG_RO";
    public static final String STATUS_ERROR = "LOI";

    public int courtId;
    public String courtName;
    public String type;
    public String onUrl;
    public String offUrl;
    public String entityId;
    public String token;
    public String method;
    public int timeoutMs;
    public String status;
    public String lastError;
    public boolean autoOff;
    public int graceMinutes;

    public LightConfig() {
        this.type = TYPE_ESP32;
        this.method = "GET";
        this.timeoutMs = 5000;
        this.status = STATUS_UNKNOWN;
    }

    public boolean isConfigured() {
        if (TYPE_HOME_ASSISTANT.equals(type)) {
            return onUrl != null && !onUrl.isEmpty() && entityId != null && !entityId.isEmpty();
        }
        return onUrl != null && !onUrl.isEmpty() && offUrl != null && !offUrl.isEmpty();
    }
}
