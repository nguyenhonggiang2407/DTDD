package com.example.courtmatepickleballcourtmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.courtmatepickleballcourtmanager.util.PhoneUtils;

import org.junit.Test;

public class PhoneUtilsTest {

    @Test
    public void normalize_keepsDigitsOnly() {
        assertEquals("0901234567", PhoneUtils.normalize("090 123-4567"));
        assertEquals("84912345678", PhoneUtils.normalize("+84 912 345 678"));
        assertEquals("", PhoneUtils.normalize(null));
        assertEquals("", PhoneUtils.normalize("abc"));
    }

    @Test
    public void isValid_acceptsNineToElevenDigits() {
        assertTrue(PhoneUtils.isValid("090123456"));
        assertTrue(PhoneUtils.isValid("0901234567"));
        assertTrue(PhoneUtils.isValid("09012345678"));
    }

    @Test
    public void isValid_rejectsTooShortOrTooLong() {
        assertFalse(PhoneUtils.isValid("09012345"));
        assertFalse(PhoneUtils.isValid("090123456789"));
        assertFalse(PhoneUtils.isValid(""));
        assertFalse(PhoneUtils.isValid(null));
    }
}
