package com.example.courtmatepickleballcourtmanager.model;

public class WalkInSlot {

    public static final String STATUS_ACTIVE = "DANG_AP_DUNG";
    public static final String STATUS_PAUSED = "TAM_NGUNG";

    public int id;
    public int courtId;
    public int weekday;
    public String startTime;
    public String endTime;
    public int price;
    public String status;
    public String courtName;

    public WalkInSlot() {
        this.status = STATUS_ACTIVE;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public String weekdayLabel() {
        return weekdayLabel(weekday);
    }

    public static String weekdayLabel(int weekday) {
        switch (weekday) {
            case 1:
                return "Thứ 2";
            case 2:
                return "Thứ 3";
            case 3:
                return "Thứ 4";
            case 4:
                return "Thứ 5";
            case 5:
                return "Thứ 6";
            case 6:
                return "Thứ 7";
            default:
                return "Chủ nhật";
        }
    }
}
