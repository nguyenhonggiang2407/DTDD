package com.example.courtmatepickleballcourtmanager.model;

public class Rental {

    public static final String STATUS_RENTING = "DANG_THUE";
    public static final String STATUS_OVERDUE = "QUA_HAN";
    public static final String STATUS_RETURNED = "DA_TRA";

    public int id;
    public int customerId;
    public int sessionId;
    public String rentTime;
    public String dueTime;
    public String returnTime;
    public int deposit;
    public int rentFee;
    public int lateFee;
    public int compensation;
    public int refund;
    public String status;
    public String note;

    public String customerName;
    public String customerPhone;
    public String courtName;
}
