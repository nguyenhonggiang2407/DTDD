package com.example.courtmatepickleballcourtmanager.model;

import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;

public class Court {

    public static final String STATUS_ACTIVE = "HOAT_DONG";
    public static final String STATUS_MAINTENANCE = "BAO_TRI";

    private int id;
    private String name;
    private int pricePerHour;
    private String status;

    public Court() {
        this.status = STATUS_ACTIVE;
    }

    public Court(int id, String name, int pricePerHour, String status) {
        this.id = id;
        this.name = name;
        this.pricePerHour = pricePerHour;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPricePerHour() {
        return pricePerHour;
    }

    public void setPricePerHour(int pricePerHour) {
        this.pricePerHour = pricePerHour;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public String getStatusLabel() {
        return isActive() ? "Hoạt động" : "Bảo trì";
    }

    @Override
    public String toString() {
        return name + " – " + MoneyUtils.format(pricePerHour) + "/giờ"
                + (isActive() ? "" : " (bảo trì)");
    }
}
