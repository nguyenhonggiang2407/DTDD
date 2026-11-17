package com.example.courtmatepickleballcourtmanager.model;

public class Promotion {

    public static final String TYPE_PERCENT = "PHAN_TRAM";
    public static final String TYPE_AMOUNT = "SO_TIEN";

    public static final String SCOPE_COURT = "SAN";
    public static final String SCOPE_SERVICE = "DICH_VU";
    public static final String SCOPE_CLASS = "LOP_HOC";
    public static final String SCOPE_EVENT = "SU_KIEN";
    public static final String SCOPE_ALL = "TAT_CA";

    public static final String STATUS_ACTIVE = "HOAT_DONG";
    public static final String STATUS_PAUSED = "TAM_NGUNG";

    public int id;
    public String name;
    public String discountType;
    public int value;
    public String scope;
    public int minOrder;
    public int maxDiscount;
    public String fromDate;
    public String toDate;
    public int annual;
    public String code;
    public String status;
    public String note;

    public Promotion() {
        this.discountType = TYPE_AMOUNT;
        this.scope = SCOPE_ALL;
        this.status = STATUS_ACTIVE;
    }

    public boolean isPercent() {
        return TYPE_PERCENT.equals(discountType);
    }

    @Override
    public String toString() {
        return name;
    }
}
