package com.example.courtmatepickleballcourtmanager;

import com.example.courtmatepickleballcourtmanager.util.PaymentAmountMath;
import org.junit.Test;
import static org.junit.Assert.*;

public class PaymentAmountMathTest {
    @Test public void computesNetTotal() {
        assertEquals(230000, PaymentAmountMath.netTotal(200000,50000,10000,10000,20000));
    }
    @Test public void discountCannotProduceNegativeBill() {
        assertEquals(0, PaymentAmountMath.netTotal(1000,0,0,10000,0));
    }
    @Test public void overflowIsRejected() {
        try {
            PaymentAmountMath.netTotal(Integer.MAX_VALUE,1,0,0,0);
            throw new AssertionError("Expected ArithmeticException");
        } catch (ArithmeticException expected) { /* correct */ }
    }
    @Test public void negativeInputRejected() {
        try {
            PaymentAmountMath.netTotal(-1,0,0,0,0);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { /* correct */ }
    }
}
