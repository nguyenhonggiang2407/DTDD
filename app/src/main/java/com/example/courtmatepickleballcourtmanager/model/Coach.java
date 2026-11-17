package com.example.courtmatepickleballcourtmanager.model;

public class Coach {

    public static final String STATUS_ACTIVE = "HOAT_DONG";
    public static final String STATUS_PAUSED = "NGHI";

    public int id;
    public String name;
    public String phone;
    public String specialty;
    public String experience;
    public int hourlyRate;
    public String status;

    public Coach() {
        this.status = STATUS_ACTIVE;
    }

    @Override
    public String toString() {
        return name;
    }
}
