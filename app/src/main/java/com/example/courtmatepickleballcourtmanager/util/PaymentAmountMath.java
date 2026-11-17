package com.example.courtmatepickleballcourtmanager.util;

/** Shared checkout arithmetic with explicit overflow rejection. Currency units are VND integer. */
public final class PaymentAmountMath {
    private PaymentAmountMath() { }
    public static int netTotal(int court, int services, int rental, int courtDiscount,
                               int serviceDiscount) {
        if (court < 0 || services < 0 || rental < 0
                || courtDiscount < 0 || serviceDiscount < 0) {
            throw new IllegalArgumentException("Giá trị tiền không thể âm");
        }
        long net = (long)court + services + rental - courtDiscount - serviceDiscount;
        if (net > Integer.MAX_VALUE) {
            throw new ArithmeticException("Tổng thanh toán vượt phạm vi INTEGER của ứng dụng");
        }
        return (int)Math.max(0L, net);
    }
}
