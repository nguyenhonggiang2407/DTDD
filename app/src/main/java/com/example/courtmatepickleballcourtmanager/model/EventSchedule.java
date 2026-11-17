package com.example.courtmatepickleballcourtmanager.model;

public class EventSchedule {

    public int id;
    public int eventId;
    public String date;
    public String startTime;
    public String endTime;
    public int courtId;
    public String content;
    public String courtName;

    public String label() {
        return date + " • " + startTime + "–" + endTime
                + (courtName == null ? "" : " • " + courtName);
    }
}
