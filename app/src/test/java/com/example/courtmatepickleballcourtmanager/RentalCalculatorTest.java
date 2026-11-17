package com.example.courtmatepickleballcourtmanager;

import static org.junit.Assert.assertEquals;

import com.example.courtmatepickleballcourtmanager.model.RentalItem;
import com.example.courtmatepickleballcourtmanager.util.RentalCalculator;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class RentalCalculatorTest {

    private RentalItem item(int quantity, int unitPrice, int deposit) {
        RentalItem item = new RentalItem();
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.deposit = deposit;
        return item;
    }

    @Test
    public void calculateDeposit_sumsAllItems() {
        List<RentalItem> items = Arrays.asList(
                item(2, 50000, 20000),
                item(1, 30000, 10000));
        assertEquals(50000, RentalCalculator.calculateDeposit(items));
    }

    @Test
    public void calculateRentFee_sumsAllItems() {
        List<RentalItem> items = Arrays.asList(
                item(2, 50000, 20000),
                item(1, 30000, 10000));
        assertEquals(130000, RentalCalculator.calculateRentFee(items));
    }

    @Test
    public void lateMinutes_returnsZeroWhenReturnedOnTime() {
        assertEquals(0, RentalCalculator.lateMinutes(
                "2026-11-03 10:00", "2026-11-03 10:00"));
        assertEquals(0, RentalCalculator.lateMinutes(
                "2026-11-03 10:00", "2026-11-03 09:00"));
    }

    @Test
    public void lateMinutes_returnsMinutesWhenOverdue() {
        assertEquals(90, RentalCalculator.lateMinutes(
                "2026-11-03 10:00", "2026-11-03 11:30"));
    }

    @Test
    public void lateFee_isRoundedToThousand() {
        assertEquals(0, RentalCalculator.lateFee(20000, 0));
        assertEquals(20000, RentalCalculator.lateFee(20000, 60));
        assertEquals(10000, RentalCalculator.lateFee(20000, 30));
    }

    @Test
    public void refund_neverNegative() {
        assertEquals(50000, RentalCalculator.refund(100000, 20000, 30000));
        assertEquals(0, RentalCalculator.refund(100000, 120000, 0));
        assertEquals(100000, RentalCalculator.refund(100000, -5000, -5000));
    }

    @Test
    public void lateFeeForOutstandingItems_usesPerItemRateAndRemainingQuantity() {
        RentalItem paddles = item(3, 50000, 100000);
        paddles.returnedQuantity = 1;
        paddles.lateFee = 20000;
        RentalItem balls = item(2, 20000, 0);
        balls.returnedQuantity = 2;
        balls.lateFee = 5000;
        assertEquals(60000, RentalCalculator.lateFeeForOutstandingItems(
                Arrays.asList(paddles, balls), 90));
    }

    @Test
    public void lateFeeForOutstandingItems_handlesNullEmptyAndNotOverdue() {
        assertEquals(0, RentalCalculator.lateFeeForOutstandingItems(null, 90));
        assertEquals(0, RentalCalculator.lateFeeForOutstandingItems(
                Arrays.asList(item(1, 50000, 30000)), 0));
        assertEquals(0, RentalCalculator.lateFeeForOutstandingItems(
                Arrays.asList(item(1, 50000, 30000)), 60));
    }

    @Test
    public void lateFee_guardsNegativeRatesAndOverflow() {
        assertEquals(0, RentalCalculator.lateFee(-100, 60));
        assertEquals(Integer.MAX_VALUE,
                RentalCalculator.lateFee(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
