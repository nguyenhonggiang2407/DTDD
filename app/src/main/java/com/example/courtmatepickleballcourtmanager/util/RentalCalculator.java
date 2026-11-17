package com.example.courtmatepickleballcourtmanager.util;

import com.example.courtmatepickleballcourtmanager.model.Rental;
import com.example.courtmatepickleballcourtmanager.model.RentalItem;

import java.util.Date;
import java.util.List;

public final class RentalCalculator {

    private RentalCalculator() {
    }

    public static int calculateDeposit(List<RentalItem> items) {
        int total = 0;
        for (RentalItem item : items) {
            total += item.deposit * item.quantity;
        }
        return total;
    }

    public static int calculateRentFee(List<RentalItem> items) {
        int total = 0;
        for (RentalItem item : items) {
            total += item.unitPrice * item.quantity;
        }
        return total;
    }

    public static int lateMinutes(String dueTime, String returnTime) {
        Date due = DateTimeUtils.parseDbDateTime(dueTime);
        Date returned = DateTimeUtils.parseDbDateTime(returnTime);
        if (due == null || returned == null || !returned.after(due)) {
            return 0;
        }
        return (int) ((returned.getTime() - due.getTime()) / 60000L);
    }

    public static int lateFee(int feePerHour, int lateMinutes) {
        if (lateMinutes <= 0) {
            return 0;
        }
        if (feePerHour <= 0) {
            return 0;
        }
        double raw = Math.ceil(lateMinutes / 60.0 * (double) feePerHour);
        long rounded = Math.round(raw / 1000.0) * 1000L;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, rounded));
    }

    /** Estimate an additional late charge for all items not yet returned.
     * The return UI initially selects every remaining item; staff may adjust the charge
     * when changing the quantities they actually receive.
     */
    public static int lateFeeForOutstandingItems(List<RentalItem> items, int lateMinutes) {
        if (items == null || lateMinutes <= 0) {
            return 0;
        }
        long total = 0;
        for (RentalItem item : items) {
            int remaining = Math.max(0, item.quantity - item.returnedQuantity);
            if (remaining == 0 || item.lateFee <= 0) {
                continue;
            }
            total += lateFee(item.lateFee, lateMinutes) * (long) remaining;
            if (total > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    public static int refund(int deposit, int lateFee, int compensation) {
        return Math.max(0, deposit - Math.max(0, lateFee) - Math.max(0, compensation));
    }

    public static int totalReturn(Rental rental, String returnTime, int compensation) {
        // rental.lateFee is the amount already charged, not a contractual hourly rate.
        // A preview of future late fees requires per-item rates; see lateFeeForOutstandingItems.
        return refund(rental.deposit, rental.lateFee,
                rental.compensation + Math.max(0, compensation));
    }
}
