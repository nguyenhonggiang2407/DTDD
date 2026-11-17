package com.example.courtmatepickleballcourtmanager.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class BackupManager {

    private static final String DB_NAME = "courtmate.db";
    private static final String PREFS = "backup_meta";
    private static final String KEY_LAST_BACKUP = "last_backup_at";
    private static final int MIN_SCHEMA_VERSION = 1;

    private BackupManager() {
    }

    public static void backup(Context context, Uri destination) throws IOException {
        if (destination == null) {
            throw new IOException("Đích sao lưu không hợp lệ");
        }
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        File databaseFile = context.getDatabasePath(DB_NAME);
        if (!databaseFile.exists()) {
            helper.getWritableDatabase();
        }
        File temp = new File(context.getCacheDir(), "backup_candidate.db");
        try {
            if (!helper.backupDatabase(temp)) {
                throw new IOException("Không thể sao lưu cơ sở dữ liệu");
            }
            try (InputStream in = new FileInputStream(temp);
                 OutputStream out = context.getContentResolver().openOutputStream(destination)) {
                if (out == null) {
                    throw new IOException("Không mở được tệp đích");
                }
                copy(in, out);
            }
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }
        prefs(context).edit()
                .putString(KEY_LAST_BACKUP, DateTimeUtils.nowDbDateTime())
                .apply();
    }

    public static String lastBackupAt(Context context) {
        return prefs(context).getString(KEY_LAST_BACKUP, null);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean validate(Context context, Uri source) {
        if (source == null) {
            return false;
        }
        File temp = new File(context.getCacheDir(), "restore_candidate.db");
        try {
            try (InputStream in = context.getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(temp)) {
                if (in == null) {
                    return false;
                }
                copy(in, out);
            }
            return isValidDatabase(temp);
        } catch (IOException | SecurityException e) {
            return false;
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }
    }

    public static boolean restore(Context context, Uri source) throws IOException {
        if (source == null) {
            throw new IOException("Nguồn khôi phục không hợp lệ");
        }
        File candidate = new File(context.getCacheDir(), "restore_candidate.db");
        File safety = new File(context.getCacheDir(), "restore_safety.db");
        File databaseFile = context.getDatabasePath(DB_NAME);
        try {
            try (InputStream in = context.getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(candidate)) {
                if (in == null) {
                    throw new IOException("Không đọc được tệp khôi phục");
                }
                copy(in, out);
            }
            if (!isValidDatabase(candidate)) {
                throw new IOException("Tệp không phải bản sao CourtMate hợp lệ");
            }

            DatabaseHelper helper = DatabaseHelper.getInstance(context);
            if (databaseFile.exists()) {
                if (!helper.backupDatabase(safety)) {
                    throw new IOException("Không thể sao lưu dữ liệu hiện tại");
                }
            } else {
                helper.close();
            }

            copyFile(candidate, databaseFile);
            deleteSideFiles(databaseFile);
            if (!isValidDatabase(databaseFile)) {
                if (safety.exists()) {
                    copyFile(safety, databaseFile);
                    deleteSideFiles(databaseFile);
                }
                helper.getWritableDatabase();
                throw new IOException("Tệp khôi phục bị hỏng");
            }
            helper.getWritableDatabase();
            return true;
        } finally {
            if (candidate.exists()) {
                candidate.delete();
            }
            if (safety.exists()) {
                safety.delete();
            }
        }
    }

    private static boolean isValidDatabase(File file) {
        if (file == null || !file.exists() || file.length() == 0) {
            return false;
        }
        int version = DatabaseHelper.readDatabaseVersion(file);
        if (version < MIN_SCHEMA_VERSION
                || version > DatabaseHelper.getSchemaVersion()) {
            return false;
        }
        if (!DatabaseHelper.hasRequiredTables(file)) {
            return false;
        }
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            try (Cursor cursor = database.rawQuery("PRAGMA quick_check", null)) {
                return cursor.moveToFirst() && "ok".equalsIgnoreCase(cursor.getString(0));
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }

    private static void deleteSideFiles(File databaseFile) {
        File wal = new File(databaseFile.getPath() + "-wal");
        File shm = new File(databaseFile.getPath() + "-shm");
        File journal = new File(databaseFile.getPath() + "-journal");
        if (wal.exists()) {
            wal.delete();
        }
        if (shm.exists()) {
            shm.delete();
        }
        if (journal.exists()) {
            journal.delete();
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            copy(in, out);
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }
}
