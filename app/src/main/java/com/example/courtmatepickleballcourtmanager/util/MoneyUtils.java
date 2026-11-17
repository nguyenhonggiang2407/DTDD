package com.example.courtmatepickleballcourtmanager.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyUtils {

    private static final Locale VI = Locale.forLanguageTag("vi-VN");
    private static final NumberFormat FORMAT = NumberFormat.getNumberInstance(VI);
    private static final DecimalFormat SHORT_FORMAT =
            new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(VI));

    static {
        FORMAT.setMaximumFractionDigits(0);
        FORMAT.setGroupingUsed(true);
    }

    private MoneyUtils() {
    }

    public static String format(long amount) {
        return FORMAT.format(amount) + "đ";
    }

    public static String formatShort(long amount) {
        if (amount >= 1_000_000L) {
            return SHORT_FORMAT.format(amount / 1_000_000.0) + "tr";
        }
        if (amount >= 1_000L) {
            return SHORT_FORMAT.format(amount / 1_000.0) + "k";
        }
        return String.valueOf(amount);
    }

    public static int parseOrDefault(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
