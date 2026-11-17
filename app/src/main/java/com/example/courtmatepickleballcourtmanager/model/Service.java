package com.example.courtmatepickleballcourtmanager.model;

import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;

public class Service {

    public static final String TYPE_RENTAL = "THUE";
    public static final String TYPE_SALE = "BAN";
    public static final String STATUS_ACTIVE = "DANG_BAN";
    public static final String STATUS_PAUSED = "NGUNG";

    public int id;
    public String name;
    public String type;
    public String unit;
    public int price;
    public int deposit;
    public int lateFee;
    public int stock;
    public String status;

    public Service() {
        this.type = TYPE_SALE;
        this.unit = "phần";
        this.status = STATUS_ACTIVE;
    }

    public boolean isRental() {
        return TYPE_RENTAL.equals(type);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    @Override
    public String toString() {
        return name + " – " + MoneyUtils.format(price) + "/" + unit;
    }
}
