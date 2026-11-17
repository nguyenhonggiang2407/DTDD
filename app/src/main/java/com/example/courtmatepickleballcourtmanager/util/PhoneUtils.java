package com.example.courtmatepickleballcourtmanager.util;

public final class PhoneUtils {

    private PhoneUtils() {
    }

    public static String normalize(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }

    public static boolean isValid(String phone) {
        int length = normalize(phone).length();
        return length >= 9 && length <= 11;
    }
}
