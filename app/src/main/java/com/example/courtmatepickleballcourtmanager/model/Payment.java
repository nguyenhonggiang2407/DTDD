package com.example.courtmatepickleballcourtmanager.model;

public class Payment {

    public static final String TARGET_BOOKING = "DAT_LICH";
    public static final String TARGET_SESSION = "PHIEN";
    public static final String TARGET_RENTAL = "THUE";
    public static final String TARGET_CLASS = "LOP";
    public static final String TARGET_EVENT = "SU_KIEN";
    public static final String TARGET_PACKAGE = "GOI";

    public static final String TYPE_DEPOSIT = "COC";
    public static final String TYPE_PAYMENT = "THANH_TOAN";
    public static final String TYPE_REFUND = "HOAN_COC";
    public static final String TYPE_ADJUST = "DIEU_CHINH";

    public int id;
    public String targetType;
    public int targetId;
    public String transactionType;
    public int amount;
    public String method;
    public String transactionId;
    public String time;
    public String note;

    public boolean isRefund() {
        return TYPE_REFUND.equals(transactionType);
    }
}
