package com.example.courtmatepickleballcourtmanager;

import com.example.courtmatepickleballcourtmanager.security.RoleAccessPolicy;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoleAccessPolicyTest {
    @Test public void staffCanBook() {
        assertTrue(RoleAccessPolicy.canOpen("STAFF", "BookingFormActivity"));
    }
    @Test public void staffCanPay() {
        assertTrue(RoleAccessPolicy.canOpen("STAFF", "PaymentActivity"));
    }
    @Test public void staffCannotSeeLedger() {
        assertFalse(RoleAccessPolicy.canOpen("STAFF", "PaymentLedgerActivity"));
    }
    @Test public void staffCannotManageUsers() {
        assertFalse(RoleAccessPolicy.canOpen("STAFF", "UsersActivity"));
    }
    @Test public void staffCannotUseCloudAdminToken() {
        assertFalse(RoleAccessPolicy.canOpen("STAFF", "CloudBookingsActivity"));
    }
    @Test public void missingRoleDenied() {
        assertFalse(RoleAccessPolicy.canOpen(null, "DashboardActivity"));
        assertFalse(RoleAccessPolicy.canOpen("ANONYMOUS", "DashboardActivity"));
    }
    @Test public void newUnknownActivityDeniedToStaff() {
        assertFalse(RoleAccessPolicy.canOpen("STAFF", "SensitiveFutureActivity"));
    }
    @Test public void adminCanManageUsers() {
        assertTrue(RoleAccessPolicy.canOpen("ADMIN", "UsersActivity"));
    }
}
