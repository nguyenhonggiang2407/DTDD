package com.example.courtmatepickleballcourtmanager.model;

public class ClassSchedule {

    public int id;
    public int classId;
    public int weekday;
    public String startTime;
    public String endTime;

    public String label() {
        return WalkInSlot.weekdayLabel(weekday) + " • " + startTime + "–" + endTime;
    }
}
