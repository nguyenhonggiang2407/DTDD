package com.example.courtmatepickleballcourtmanager.security;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Deny-by-default access policy, unit-testable without Android dependencies. */
public final class RoleAccessPolicy {
    private static final Set<String> STAFF_ALLOWED = new HashSet<>(Arrays.asList(
            "DashboardActivity", "MainActivity", "BookingFormActivity",
            "CustomerActivity", "CalendarViewActivity", "CourtTimelineActivity",
            "CourtSessionActivity", "SessionDetailActivity", "PaymentActivity",
            "InvoiceActivity", "RentalActivity", "ExtendedManagementActivity",
            "AboutActivity", "ChangePasswordActivity"));
    private RoleAccessPolicy() { }
    public static boolean canOpen(String role, String activitySimpleName) {
        if (role == null || activitySimpleName == null) return false;
        if ("ADMIN".equals(role)) return true;
        return "STAFF".equals(role) && STAFF_ALLOWED.contains(activitySimpleName);
    }
}
