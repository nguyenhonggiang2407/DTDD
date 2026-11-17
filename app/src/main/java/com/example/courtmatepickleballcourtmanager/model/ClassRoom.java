package com.example.courtmatepickleballcourtmanager.model;

public class ClassRoom {

    public static final String STATUS_OPEN = "MO_DANG_KY";
    public static final String STATUS_LEARNING = "DANG_HOC";
    public static final String STATUS_FINISHED = "KET_THUC";
    public static final String STATUS_CANCELLED = "HUY";

    public int id;
    public String name;
    public int coachId;
    public int courtId;
    public String level;
    public String startDate;
    public String endDate;
    public int capacity;
    public int fee;
    public String status;
    public String note;

    public String coachName;
    public String courtName;
    public int enrolled;

    public ClassRoom() {
        this.status = STATUS_OPEN;
    }

    @Override
    public String toString() {
        return name;
    }
}
