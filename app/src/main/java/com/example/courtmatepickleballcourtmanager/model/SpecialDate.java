package com.example.courtmatepickleballcourtmanager.model;

public class SpecialDate {

    public static final String TYPE_HOLIDAY = "NGAY_LE";
    public static final String TYPE_ANNIVERSARY = "KY_NIEM_SAN";
    public static final String STATUS_ACTIVE = "HOAT_DONG";
    public static final String STATUS_PAUSED = "TAM_NGUNG";

    public int id;
    public String name;
    public String type;
    public int month;
    public int day;
    public int year;
    public int courtId;
    public int annual;
    public String status;
    public String courtName;

    public SpecialDate() {
        this.type = TYPE_HOLIDAY;
        this.annual = 1;
        this.status = STATUS_ACTIVE;
    }

    @Override
    public String toString() {
        return name + " (" + day + "/" + month + ")";
    }
}
