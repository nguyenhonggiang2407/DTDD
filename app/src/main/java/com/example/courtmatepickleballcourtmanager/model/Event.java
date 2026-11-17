package com.example.courtmatepickleballcourtmanager.model;

public class Event {

    public static final String TYPE_TOURNAMENT = "GIAI_DAU";
    public static final String TYPE_EVENT = "SU_KIEN";

    public static final String STATUS_UPCOMING = "SAP_DIEN_RA";
    public static final String STATUS_REGISTERING = "DANG_DANG_KY";
    public static final String STATUS_RUNNING = "DANG_DIEN_RA";
    public static final String STATUS_FINISHED = "KET_THUC";
    public static final String STATUS_CANCELLED = "HUY";

    public int id;
    public String name;
    public String type;
    public String description;
    public String startDate;
    public String endDate;
    public String deadline;
    public int fee;
    public int capacity;
    public int annual;
    public String status;

    public int registered;

    public Event() {
        this.type = TYPE_EVENT;
        this.status = STATUS_UPCOMING;
    }

    @Override
    public String toString() {
        return name;
    }
}
