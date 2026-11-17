package com.example.courtmatepickleballcourtmanager.security;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local identity store; not a cloud identity provider. */
public final class AuthRepository {
    private static final int ITERATIONS = 210_000;
    private final DatabaseHelper db;
    public AuthRepository(Context context) { db = DatabaseHelper.getInstance(context); }
    public static final class Account {
        public final int id;
        public final String username;
        public final String role;
        public final boolean active;
        public Account(int id, String username, String role, boolean active) {
            this.id=id; this.username=username; this.role=role; this.active=active;
        }
    }
    private static String cleanUser(String user) {
        if (user == null) throw new IllegalArgumentException("Cần tên đăng nhập");
        String u = user.trim().toLowerCase(java.util.Locale.ROOT);
        if (!u.matches("[a-z0-9_.-]{3,32}"))
            throw new IllegalArgumentException("Tên đăng nhập: 3–32 ký tự a-z, 0-9, . _ -");
        return u;
    }
    private static void checkPassword(char[] password) {
        if (password == null || password.length < 10 || password.length > 128)
            throw new IllegalArgumentException("Mật khẩu cần từ 10 đến 128 ký tự");
    }
    private static String hash(char[] password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, 256);
        try {
            // PBKDF2-HMAC-SHA1 is supported on Android 7.0+. Use 210k rounds and random salt.
            byte[] result = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                    .generateSecret(spec).getEncoded();
            return Base64.encodeToString(result, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo mật khẩu an toàn", e);
        } finally { spec.clearPassword(); }
    }
    public int countUsers() {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM nguoi_dung", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
    /** First-run admin creation is a transaction so only ONE initial admin can be created. */
    public long createFirstAdmin(String username, char[] password) {
        String name = cleanUser(username);
        checkPassword(password);
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        try {
            try (Cursor c = sql.rawQuery("SELECT COUNT(*) FROM nguoi_dung", null)) {
                c.moveToFirst();
                if (c.getInt(0) != 0) throw new IllegalStateException("Đã có tài khoản chủ sân");
            }
            long id = insert(sql, name, password, AuthSession.ADMIN);
            sql.setTransactionSuccessful();
            return id;
        } finally { sql.endTransaction(); }
    }
    private long insert(SQLiteDatabase sql, String user, char[] password, String role) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        ContentValues values = new ContentValues();
        values.put("ten_dang_nhap", user);
        values.put("muoi", Base64.encodeToString(salt, Base64.NO_WRAP));
        values.put("bam_mat_khau", hash(password, salt, ITERATIONS));
        values.put("so_vong", ITERATIONS);
        values.put("vai_tro", role);
        values.put("hoat_dong", 1);
        return sql.insertOrThrow("nguoi_dung", null, values);
    }
    public long createStaff(String username, char[] password) {
        if (!AuthSession.isAdmin()) throw new SecurityException("Chỉ chủ sân được tạo tài khoản");
        String name = cleanUser(username);
        checkPassword(password);
        return insert(db.getWritableDatabase(), name, password, AuthSession.STAFF);
    }
    public boolean signIn(String username, char[] password) {
        String name;
        try { name = cleanUser(username); } catch (IllegalArgumentException e) { return false; }
        // Perform a real PBKDF2 operation even for missing users to reduce enumeration signal.
        String salt = Base64.encodeToString(new byte[16], Base64.NO_WRAP);
        String expected = "";
        int rounds = ITERATIONS, id = 0;
        String role = "";
        boolean active = false;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT ma_nd,muoi,bam_mat_khau,so_vong,vai_tro,hoat_dong "
                        + "FROM nguoi_dung WHERE ten_dang_nhap=?", new String[]{name})) {
            if (c.moveToFirst()) {
                id = c.getInt(0); salt = c.getString(1); expected = c.getString(2);
                rounds = c.getInt(3); role = c.getString(4); active = c.getInt(5) == 1;
            }
        }
        String calculated = hash(password == null ? new char[0] : password,
                Base64.decode(salt, Base64.DEFAULT), rounds);
        boolean valid = MessageDigest.isEqual(calculated.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
        if (valid && active) {
            AuthSession.login(id, name, role);
            return true;
        }
        return false;
    }
    public void changeMyPassword(char[] oldPassword, char[] newPassword) {
        int userId = AuthSession.userId();
        String username = AuthSession.username();
        if (userId <= 0 || !signIn(username, oldPassword)) {
            throw new SecurityException("Mật khẩu cũ không chính xác");
        }
        checkPassword(newPassword);
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        ContentValues values = new ContentValues();
        values.put("muoi", Base64.encodeToString(salt, Base64.NO_WRAP));
        values.put("bam_mat_khau", hash(newPassword, salt, ITERATIONS));
        values.put("so_vong", ITERATIONS);
        if (db.getWritableDatabase().update("nguoi_dung",values,"ma_nd=?",
                new String[]{String.valueOf(userId)}) != 1) {
            throw new IllegalStateException("Không thể đổi mật khẩu");
        }
        AuthSession.logout();
    }

    public List<Account> accounts() {
        if (!AuthSession.isAdmin()) throw new SecurityException("Không có quyền");
        List<Account> all = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT ma_nd,ten_dang_nhap,vai_tro,hoat_dong FROM nguoi_dung ORDER BY ma_nd", null)) {
            while (c.moveToNext()) {
                all.add(new Account(c.getInt(0), c.getString(1), c.getString(2), c.getInt(3) == 1));
            }
        }
        return all;
    }
    public void setStaffActive(int userId, boolean active) {
        if (!AuthSession.isAdmin()) throw new SecurityException("Không có quyền");
        ContentValues v = new ContentValues(); v.put("hoat_dong", active ? 1 : 0);
        int changed = db.getWritableDatabase().update("nguoi_dung", v,
                "ma_nd = ? AND vai_tro = 'STAFF'", new String[]{String.valueOf(userId)});
        if (changed != 1) throw new IllegalArgumentException("Chỉ có thể khóa/mở tài khoản nhân viên");
    }
}
