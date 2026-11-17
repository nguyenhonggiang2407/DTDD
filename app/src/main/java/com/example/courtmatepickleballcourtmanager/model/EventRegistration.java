package com.example.courtmatepickleballcourtmanager.model;

public class EventRegistration {

    public static final String STATUS_ACTIVE = "DANG_KY";
    public static final String STATUS_CANCELLED = "DA_HUY";

    public int id;
    public int eventId;
    public int customerId;
    public String date;
    public int fee;
    public int discount;
    public int promotionId;
    public int paid;
    public String status;

    public String customerName;
    public String customerPhone;
    public String eventName;
}
