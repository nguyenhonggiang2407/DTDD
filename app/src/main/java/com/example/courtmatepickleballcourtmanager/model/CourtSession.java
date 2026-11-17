package com.example.courtmatepickleballcourtmanager.model;

public class CourtSession {

    public static final String TYPE_BOOKING = "DAT_TRUOC";
    public static final String TYPE_WALK_IN = "VANG_LAI";
    public static final String STATUS_PLAYING = "DANG_CHOI";
    public static final String STATUS_FINISHED = "DA_KET_THUC";

    public static final String PAYMENT_UNPAID = "CHUA_THANH_TOAN";
    public static final String PAYMENT_PAID = "DA_THANH_TOAN";
    public static final String PAYMENT_FAILED = "THAT_BAI";

    public static final String METHOD_CASH = "TIEN_MAT";
    public static final String METHOD_QR = "QR_NOI_BO";
    public static final String METHOD_MOMO = "MOMO";

    public int id;
    public int courtId;
    public int bookingId;
    public int customerId;
    public int slotId;
    public String type;
    public String startTime;
    public String endTime;
    public int walkInPrice;
    public int courtFee;
    public int serviceFee;
    public int rentalFee;
    public int discount;
    public int discountCourt;
    public int discountService;
    public int promotionId;
    public String promotionCode;
    public int total;
    public String status;
    public String note;
    public String invoiceNo;
    public String paymentStatus;
    public String paymentMethod;
    public String paymentTransactionId;
    public String paidAt;

    public String courtName;
    public String customerName;
    public String customerPhone;
}
