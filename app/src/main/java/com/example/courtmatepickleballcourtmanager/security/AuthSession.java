package com.example.courtmatepickleballcourtmanager.security;

/** In-memory authentication: restart/expiry requires login. Never persist a logged-in flag. */
public final class AuthSession {
    public static final String ADMIN = "ADMIN";
    public static final String STAFF = "STAFF";
    private static final long MAX_SESSION_MS = 6L * 60 * 60 * 1000;
    private static int id;
    private static String user;
    private static String role;
    private static long expiresAt;
    private AuthSession() {}
    public static synchronized void login(int userId, String username, String userRole) {
        id = userId; user = username; role = userRole;
        expiresAt = System.currentTimeMillis() + MAX_SESSION_MS;
    }
    public static synchronized boolean isLoggedIn() {
        if (id == 0 || System.currentTimeMillis() >= expiresAt) {
            logout(); return false;
        }
        return true;
    }
    public static synchronized boolean isAdmin() {
        return isLoggedIn() && ADMIN.equals(role);
    }
    public static synchronized int userId() { return isLoggedIn() ? id : 0; }
    public static synchronized String username() { return isLoggedIn() ? user : ""; }
    public static synchronized String role() { return isLoggedIn() ? role : ""; }
    public static synchronized String currentUsername() {
        return isLoggedIn() ? user : "He thong";
    }
    public static synchronized void logout() {
        id = 0; user = null; role = null; expiresAt = 0;
    }
}
