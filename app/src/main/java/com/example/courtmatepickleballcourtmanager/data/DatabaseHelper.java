package com.example.courtmatepickleballcourtmanager.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.courtmatepickleballcourtmanager.model.ActivityLog;
import com.example.courtmatepickleballcourtmanager.model.Booking;
import com.example.courtmatepickleballcourtmanager.model.ClassEnrollment;
import com.example.courtmatepickleballcourtmanager.model.ClassRoom;
import com.example.courtmatepickleballcourtmanager.model.ClassSchedule;
import com.example.courtmatepickleballcourtmanager.model.Coach;
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.model.CourtSession;
import com.example.courtmatepickleballcourtmanager.model.Customer;
import com.example.courtmatepickleballcourtmanager.model.Event;
import com.example.courtmatepickleballcourtmanager.model.EventRegistration;
import com.example.courtmatepickleballcourtmanager.model.EventSchedule;
import com.example.courtmatepickleballcourtmanager.model.LightConfig;
import com.example.courtmatepickleballcourtmanager.model.Payment;
import com.example.courtmatepickleballcourtmanager.model.Promotion;
import com.example.courtmatepickleballcourtmanager.model.Rental;
import com.example.courtmatepickleballcourtmanager.model.RentalItem;
import com.example.courtmatepickleballcourtmanager.model.Service;
import com.example.courtmatepickleballcourtmanager.model.ServiceOrder;
import com.example.courtmatepickleballcourtmanager.model.SpecialDate;
import com.example.courtmatepickleballcourtmanager.model.WalkInSlot;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.example.courtmatepickleballcourtmanager.util.PhoneUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";

    public static final long ERROR = -1L;
    public static final long ERROR_OVERLAP = -2L;
    public static final long ERROR_COURT_UNAVAILABLE = -3L;
    public static final long ERROR_SESSION_OVERLAP = -4L;
    public static final long ERROR_OUTSIDE_WALKIN = -5L;
    public static final long ERROR_COURT_BUSY = -6L;
    public static final long ERROR_STOCK = -7L;
    public static final long ERROR_CAPACITY = -8L;
    public static final long ERROR_INVALID = -9L;
    public static final long ERROR_REFERENCED = -10L;

    private static final String DB_NAME = "courtmate.db";
    private static final int DB_VERSION = 7;

    private static final String T_SAN = "san";
    private static final String T_KH = "khach_hang";
    private static final String T_DAT = "dat_lich";
    private static final String T_GIO = "gio_vang_lai";
    private static final String T_PHIEN = "phien_choi";
    private static final String T_DV = "dich_vu";
    private static final String T_DON_DV = "don_dich_vu";
    private static final String T_PHIEU_THUE = "phieu_thue";
    private static final String T_CT_THUE = "chi_tiet_thue";
    private static final String T_HLV = "huan_luyen_vien";
    private static final String T_LOP = "lop_hoc";
    private static final String T_LICH_LOP = "lich_lop";
    private static final String T_DK_LOP = "dang_ky_lop";
    private static final String T_SK = "su_kien";
    private static final String T_SK_SAN = "su_kien_san";
    private static final String T_LICH_SK = "lich_su_kien";
    private static final String T_DK_SK = "dang_ky_su_kien";
    private static final String T_DIP = "dip_dac_biet";
    private static final String T_UU_DAI = "uu_dai";
    private static final String T_UU_DAI_DIP = "uu_dai_dip";
    private static final String T_DEN = "cau_hinh_den";
    private static final String T_THANH_TOAN = "thanh_toan";
    private static final String T_NHAT_KY = "nhat_ky_hoat_dong";

    private static DatabaseHelper instance;

    private final Context context;

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
    }

    public static synchronized DatabaseHelper getInstance(Context context) {
        Context app = context.getApplicationContext();
        if (instance == null) {
            prepareForUpgrade(app);
            instance = new DatabaseHelper(app);
        }
        return instance;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createBaseTables(db);
        createVersion2Tables(db);
        createVersion4Tables(db);
        createVersion5Tables(db);
        createVersion6Tables(db);
        createVersion7Tables(db);
        seedCourts(db);
        seedCustomersAndBookings(db);
        seedServices(db);
        seedSpecialDates(db);
        // Chỉ nạp toàn bộ demo trên CSDL vừa tạo; CSDL đang sử dụng không bị tự chèn dữ liệu.
        DemoDataSeeder.populate(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createVersion2Tables(db);
            seedCourts(db);
            seedServices(db);
            seedSpecialDates(db);
        }
        if (oldVersion < 3) {
            upgradePaymentColumns(db);
        }
        if (oldVersion < 4) {
            createVersion4Tables(db);
        }
        if (oldVersion < 5) {
            createVersion5Tables(db);
        }
        if (oldVersion < 6) {
            createVersion6Tables(db);
        }
        if (oldVersion < 7) {
            createVersion7Tables(db);
        }
        ensureCoreData(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        try {
            ensureCoreData(db);
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot repair core data", e);
        }
    }

    /** Chỉ được gọi khi chủ sân chủ động chọn nạp dữ liệu demo trong Cài đặt.
     *  Tất cả các bảng được cập nhật trong một transaction và dữ liệu theo khóa demo không tạo trùng. */
    public synchronized int addDemoData() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int inserted = DemoDataSeeder.populate(db);
            db.setTransactionSuccessful();
            return inserted;
        } finally {
            db.endTransaction();
        }
    }

    private void createVersion7Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS nguoi_dung ("
                + "ma_nd INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "ten_dang_nhap TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                + "muoi TEXT NOT NULL, bam_mat_khau TEXT NOT NULL, "
                + "so_vong INTEGER NOT NULL, "
                + "vai_tro TEXT NOT NULL CHECK(vai_tro IN ('ADMIN','STAFF')), "
                + "hoat_dong INTEGER NOT NULL DEFAULT 1 CHECK(hoat_dong IN (0,1)))");
    }

    private void createVersion6Tables(SQLiteDatabase db) {
        ensureColumn(db, T_DEN, "tu_dong_tat", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(db, T_DEN, "phut_tre", "INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureCoreData(SQLiteDatabase db) {
        if (countRows(db, T_SAN) == 0) {
            seedCourts(db);
        }
        if (countRows(db, T_DV) == 0) {
            seedServices(db);
        } else {
            ensureRentalServices(db);
        }
        if (countRows(db, T_DIP) == 0) {
            seedSpecialDates(db);
        }
    }

    private void createVersion5Tables(SQLiteDatabase db) {
        // Giữ nguyên cột phuong_thuc_tt cũ (ràng buộc CHECK chỉ cho TIEN_MAT/MOMO)
        // và bổ sung cột mới không ràng buộc để lưu QR nội bộ mà không phá dữ liệu cũ.
        ensureColumn(db, T_PHIEN, "phuong_thuc_tt_moi", "TEXT");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_phien_tt ON " + T_PHIEN
                + "(trang_thai_tt, bat_dau)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_phieu_thue_ngay ON " + T_PHIEU_THUE
                + "(ngay_thue)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_thanh_toan_loai ON " + T_THANH_TOAN
                + "(loai_giao_dich, thoi_gian)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dat_lich_trang_thai ON " + T_DAT
                + "(trang_thai, ngay)");
    }

    private void createVersion4Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_THANH_TOAN + " ("
                + "ma_tt INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "loai_doi_tuong TEXT NOT NULL"
                + " CHECK (loai_doi_tuong IN ('DAT_LICH','PHIEN','THUE','LOP','SU_KIEN','GOI')),"
                + "ma_doi_tuong INTEGER NOT NULL,"
                + "loai_giao_dich TEXT NOT NULL DEFAULT 'THANH_TOAN'"
                + " CHECK (loai_giao_dich IN ('COC','THANH_TOAN','HOAN_COC','DIEU_CHINH')),"
                + "so_tien INTEGER NOT NULL,"
                + " phuong_thuc TEXT NOT NULL DEFAULT 'TIEN_MAT',"
                + " ma_giao_dich TEXT,"
                + " thoi_gian TEXT NOT NULL,"
                + " ghi_chu TEXT"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_thanh_toan_doi_tuong ON " + T_THANH_TOAN
                + "(loai_doi_tuong, ma_doi_tuong)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_thanh_toan_thoi_gian ON " + T_THANH_TOAN
                + "(thoi_gian)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_NHAT_KY + " ("
                + "ma_nk INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "thoi_gian TEXT NOT NULL,"
                + "loai_thao_tac TEXT NOT NULL,"
                + "doi_tuong TEXT,"
                + "ma_doi_tuong INTEGER,"
                + "gia_tri_cu TEXT,"
                + "gia_tri_moi TEXT,"
                + "ket_qua TEXT,"
                + "nguoi_thuc_hien TEXT NOT NULL DEFAULT 'Nguoi van hanh'"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nhat_ky_thoi_gian ON " + T_NHAT_KY
                + "(thoi_gian)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nhat_ky_doi_tuong ON " + T_NHAT_KY
                + "(doi_tuong, ma_doi_tuong)");
        ensureColumn(db, T_KH, "trang_thai", "TEXT NOT NULL DEFAULT 'HOAT_DONG'");
    }

    private void ensureColumn(SQLiteDatabase db, String table, String column, String definition) {
        boolean exists = false;
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && column.equals(cursor.getString(nameIndex))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void upgradePaymentColumns(SQLiteDatabase db) {
        ensureColumn(db, T_PHIEN, "ma_hoa_don", "TEXT");
        ensureColumn(db, T_PHIEN, "trang_thai_tt",
                "TEXT NOT NULL DEFAULT 'CHUA_THANH_TOAN'");
        ensureColumn(db, T_PHIEN, "phuong_thuc_tt",
                "TEXT NOT NULL DEFAULT 'TIEN_MAT'");
        ensureColumn(db, T_PHIEN, "ma_giao_dich", "TEXT");
        ensureColumn(db, T_PHIEN, "thoi_gian_tt", "TEXT");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_phien_hoa_don ON "
                + T_PHIEN + "(ma_hoa_don) WHERE ma_hoa_don IS NOT NULL");
        db.execSQL("UPDATE " + T_PHIEN + " SET trang_thai_tt = ? WHERE trang_thai = ?",
                new Object[]{CourtSession.PAYMENT_PAID, CourtSession.STATUS_FINISHED});
    }

    private void createBaseTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SAN + " ("
                + "ma_san INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ten_san TEXT NOT NULL,"
                + "gia_gio INTEGER NOT NULL CHECK (gia_gio >= 0),"
                + "trang_thai TEXT NOT NULL DEFAULT 'HOAT_DONG'"
                + " CHECK (trang_thai IN ('HOAT_DONG','BAO_TRI'))"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_KH + " ("
                + "ma_kh INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ho_ten TEXT NOT NULL,"
                + "so_dien_thoai TEXT NOT NULL UNIQUE"
                + " CHECK (length(so_dien_thoai) BETWEEN 9 AND 11)"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_DAT + " ("
                + "ma_dat INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ma_san INTEGER NOT NULL REFERENCES " + T_SAN + "(ma_san) ON DELETE RESTRICT,"
                + "ma_kh INTEGER NOT NULL REFERENCES " + T_KH + "(ma_kh) ON DELETE RESTRICT,"
                + "ngay TEXT NOT NULL,"
                + "gio_bat_dau TEXT NOT NULL,"
                + "gio_ket_thuc TEXT NOT NULL,"
                + "tong_tien INTEGER NOT NULL DEFAULT 0 CHECK (tong_tien >= 0),"
                + "trang_thai TEXT NOT NULL DEFAULT 'DA_DAT'"
                + " CHECK (trang_thai IN ('DA_DAT','DA_HUY')),"
                + "ghi_chu TEXT,"
                + "CHECK (gio_ket_thuc > gio_bat_dau)"
                + ")");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dat_lich_san_ngay ON " + T_DAT + "(ma_san, ngay)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dat_lich_ngay ON " + T_DAT + "(ngay)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dat_lich_kh ON " + T_DAT + "(ma_kh)");
    }

    private void createVersion2Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_GIO + " ("
                + "ma_gio INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ma_san INTEGER NOT NULL REFERENCES " + T_SAN + "(ma_san) ON DELETE CASCADE,"
                + "thu INTEGER NOT NULL CHECK (thu BETWEEN 1 AND 7),"
                + "gio_bat_dau TEXT NOT NULL,"
                + "gio_ket_thuc TEXT NOT NULL,"
                + "gia INTEGER NOT NULL CHECK (gia >= 0),"
                + "trang_thai TEXT NOT NULL DEFAULT 'DANG_AP_DUNG'"
                + " CHECK (trang_thai IN ('DANG_AP_DUNG','TAM_NGUNG')),"
                + "CHECK (gio_ket_thuc > gio_bat_dau)"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_gio_san_thu ON " + T_GIO + "(ma_san, thu)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_DV + " ("
                + "ma_dv INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ten_dv TEXT NOT NULL,"
                + "loai TEXT NOT NULL DEFAULT 'BAN' CHECK (loai IN ('THUE','BAN')),"
                + "don_vi TEXT NOT NULL DEFAULT 'phần',"
                + "gia INTEGER NOT NULL CHECK (gia >= 0),"
                + "tien_coc INTEGER NOT NULL DEFAULT 0 CHECK (tien_coc >= 0),"
                + "phi_qua_han INTEGER NOT NULL DEFAULT 0 CHECK (phi_qua_han >= 0),"
                + "so_luong_ton INTEGER NOT NULL DEFAULT 0 CHECK (so_luong_ton >= 0),"
                + "trang_thai TEXT NOT NULL DEFAULT 'DANG_BAN'"
                + " CHECK (trang_thai IN ('DANG_BAN','NGUNG'))"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_DIP + " ("
                + "ma_dip INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ten_dip TEXT NOT NULL,"
                + "loai TEXT NOT NULL CHECK (loai IN ('NGAY_LE','KY_NIEM_SAN')),"
                + "thang INTEGER NOT NULL CHECK (thang BETWEEN 1 AND 12),"
                + "ngay INTEGER NOT NULL CHECK (ngay BETWEEN 1 AND 31),"
                + "nam INTEGER,"
                + "ma_san INTEGER NULL REFERENCES " + T_SAN + "(ma_san) ON DELETE CASCADE,"
                + "lap_lai_hang_nam INTEGER NOT NULL DEFAULT 1,"
                + "trang_thai TEXT NOT NULL DEFAULT 'HOAT_DONG'"
                + " CHECK (trang_thai IN ('HOAT_DONG','TAM_NGUNG'))"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_UU_DAI + " ("
                + "ma_ud INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ten_ud TEXT NOT NULL,"
                + "loai_giam TEXT NOT NULL CHECK (loai_giam IN ('PHAN_TRAM','SO_TIEN')),"
                + "gia_tri INTEGER NOT NULL CHECK (gia_tri > 0),"
                + "pham_vi TEXT NOT NULL CHECK (pham_vi IN ('SAN','DICH_VU','LOP_HOC','SU_KIEN','TAT_CA')),"
                + "gia_tri_don_toi_thieu INTEGER NOT NULL DEFAULT 0,"
                + "giam_toi_da INTEGER NOT NULL DEFAULT 0,"
                + "tu_ngay TEXT,"
                + "den_ngay TEXT,"
                + "lap_lai_hang_nam INTEGER NOT NULL DEFAULT 0,"
                + "ma_code TEXT UNIQUE,"
                + "trang_thai TEXT NOT NULL DEFAULT 'HOAT_DONG'"
                + " CHECK (trang_thai IN ('HOAT_DONG','TAM_NGUNG')),"
                + "ghi_chu TEXT"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_UU_DAI_DIP + " ("
                + "ma_ud INTEGER NOT NULL REFERENCES " + T_UU_DAI + "(ma_ud) ON DELETE CASCADE,"
                + "ma_dip INTEGER NOT NULL REFERENCES " + T_DIP + "(ma_dip) ON DELETE CASCADE,"
                + "PRIMARY KEY (ma_ud, ma_dip)"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_PHIEN + " ("
                + "ma_phien INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ma_san INTEGER NOT NULL REFERENCES " + T_SAN + "(ma_san) ON DELETE RESTRICT,"
                + "ma_dat INTEGER NULL REFERENCES " + T_DAT + "(ma_dat) ON DELETE SET NULL,"
                + "ma_kh INTEGER NULL REFERENCES " + T_KH + "(ma_kh) ON DELETE SET NULL,"
                + "ma_gio INTEGER NULL REFERENCES " + T_GIO + "(ma_gio) ON DELETE SET NULL,"
                + "loai_phien TEXT NOT NULL DEFAULT 'DAT_TRUOC'"
                + " CHECK (loai_phien IN ('DAT_TRUOC','VANG_LAI')),"
                + "bat_dau TEXT NOT NULL,"
                + "ket_thuc TEXT,"
                + "don_gia_vang_lai INTEGER NOT NULL DEFAULT 0 CHECK (don_gia_vang_lai >= 0),"
                + "tien_san INTEGER NOT NULL DEFAULT 0,"
                + "tien_dich_vu INTEGER NOT NULL DEFAULT 0,"
                + "tien_thue INTEGER NOT NULL DEFAULT 0,"
                + "giam_san INTEGER NOT NULL DEFAULT 0,"
                + "giam_dich_vu INTEGER NOT NULL DEFAULT 0,"
                + "tien_giam INTEGER NOT NULL DEFAULT 0,"
                + "ma_ud INTEGER NULL REFERENCES " + T_UU_DAI + "(ma_ud) ON DELETE SET NULL,"
                + "ma_code TEXT,"
                + "tong_thanh_toan INTEGER NOT NULL DEFAULT 0,"
                + "trang_thai TEXT NOT NULL DEFAULT 'DANG_CHOI'"
                + " CHECK (trang_thai IN ('DANG_CHOI','DA_KET_THUC')),"
                + "ma_hoa_don TEXT,"
                + "trang_thai_tt TEXT NOT NULL DEFAULT 'CHUA_THANH_TOAN'"
                + " CHECK (trang_thai_tt IN ('CHUA_THANH_TOAN','DA_THANH_TOAN','THAT_BAI')),"
                + "phuong_thuc_tt TEXT NOT NULL DEFAULT 'TIEN_MAT'"
                + " CHECK (phuong_thuc_tt IN ('TIEN_MAT','MOMO')),"
                + "ma_giao_dich TEXT,"
                + "thoi_gian_tt TEXT,"
                + "ghi_chu TEXT"
                + ")");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_phien_dang_choi_san ON " + T_PHIEN
                + "(ma_san) WHERE trang_thai = 'DANG_CHOI'");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_phien_hoa_don ON " + T_PHIEN
                + "(ma_hoa_don) WHERE ma_hoa_don IS NOT NULL");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_phien_san_bat_dau ON " + T_PHIEN + "(ma_san, bat_dau)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_phien_ngay ON " + T_PHIEN + "(bat_dau)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_DON_DV + " ("
                + "ma_don INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ma_phien INTEGER NOT NULL REFERENCES " + T_PHIEN + "(ma_phien) ON DELETE CASCADE,"
                + "ma_dv INTEGER NOT NULL REFERENCES " + T_DV + "(ma_dv) ON DELETE RESTRICT,"
                + "so_luong INTEGER NOT NULL CHECK (so_luong > 0),"
                + "don_gia INTEGER NOT NULL CHECK (don_gia >= 0),"
                + "thanh_tien INTEGER NOT NULL CHECK (thanh_tien >= 0),"
                + "thoi_gian TEXT NOT NULL"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_don_dv_phien ON " + T_DON_DV + "(ma_phien)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_PHIEU_THUE + " ("
                + "ma_phieu INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ma_kh INTEGER NOT NULL REFERENCES " + T_KH + "(ma_kh) ON DELETE RESTRICT,"
                + "ma_phien INTEGER NULL REFERENCES " + T_PHIEN + "(ma_phien) ON DELETE SET NULL,"
                + "ngay_thue TEXT NOT NULL,"
                + "hen_tra TEXT NOT NULL,"
                + "ngay_tra TEXT,"
                + "tien_coc INTEGER NOT NULL DEFAULT 0,"
                + "tien_thue INTEGER NOT NULL DEFAULT 0,"
                + "phi_qua_han INTEGER NOT NULL DEFAULT 0,"
                + "tien_boi_thuong INTEGER NOT NULL DEFAULT 0,"
                + "tien_hoan_coc INTEGER NOT NULL DEFAULT 0,"
                + "trang_thai TEXT NOT NULL DEFAULT 'DANG_THUE'"
                + " CHECK (trang_thai IN ('DANG_THUE','QUA_HAN','DA_TRA')),"
                + "ghi_chu TEXT"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_phieu_thue_kh ON " + T_PHIEU_THUE + "(ma_kh)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_phieu_thue_trang_thai ON " + T_PHIEU_THUE + "(trang_thai, hen_tra)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_CT_THUE + " ("
                + "ma_ct INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ma_phieu INTEGER NOT NULL REFERENCES " + T_PHIEU_THUE + "(ma_phieu) ON DELETE CASCADE,"
                + "ma_dv INTEGER NOT NULL REFERENCES " + T_DV + "(ma_dv) ON DELETE RESTRICT,"
                + "so_luong INTEGER NOT NULL CHECK (so_luong > 0),"
                + "don_gia INTEGER NOT NULL,"
                + "don_vi TEXT NOT NULL,"
                + "phi_qua_han INTEGER NOT NULL DEFAULT 0,"
                + "so_luong_da_tra INTEGER NOT NULL DEFAULT 0,"
                + "tinh_trang TEXT NOT NULL DEFAULT 'BINH_THUONG'"
                + " CHECK (tinh_trang IN ('BINH_THUONG','HONG','MAT')),"
                + "tien_boi_thuong INTEGER NOT NULL DEFAULT 0,"
                + "thanh_tien INTEGER NOT NULL DEFAULT 0"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ct_thue_phieu ON " + T_CT_THUE + "(ma_phieu)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_HLV + " ("
                + "ma_hlv INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ho_ten TEXT NOT NULL,"
                + "so_dien_thoai TEXT,"
                + "chuyen_mon TEXT,"
                + "kinh_nghiem TEXT,"
                + "phi_theo_gio INTEGER NOT NULL DEFAULT 0,"
                + "trang_thai TEXT NOT NULL DEFAULT 'HOAT_DONG'"
                + " CHECK (trang_thai IN ('HOAT_DONG','NGHI'))"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_LOP + " ("
                + "ma_lop INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ten_lop TEXT NOT NULL,"
                + "ma_hlv INTEGER NOT NULL REFERENCES " + T_HLV + "(ma_hlv) ON DELETE RESTRICT,"
                + "ma_san INTEGER NOT NULL REFERENCES " + T_SAN + "(ma_san) ON DELETE RESTRICT,"
                + "trinh_do TEXT,"
                + "ngay_bat_dau TEXT,"
                + "ngay_ket_thuc TEXT,"
                + "suc_chua INTEGER NOT NULL CHECK (suc_chua > 0),"
                + "hoc_phi INTEGER NOT NULL DEFAULT 0,"
                + "trang_thai TEXT NOT NULL DEFAULT 'MO_DANG_KY'"
                + " CHECK (trang_thai IN ('MO_DANG_KY','DANG_HOC','KET_THUC','HUY')),"
                + "ghi_chu TEXT"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_LICH_LOP + " ("
                + "ma_lich INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ma_lop INTEGER NOT NULL REFERENCES " + T_LOP + "(ma_lop) ON DELETE CASCADE,"
                + "thu INTEGER NOT NULL CHECK (thu BETWEEN 1 AND 7),"
                + "gio_bat_dau TEXT NOT NULL,"
                + "gio_ket_thuc TEXT NOT NULL,"
                + "CHECK (gio_ket_thuc > gio_bat_dau)"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_lich_lop_lop ON " + T_LICH_LOP + "(ma_lop)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_DK_LOP + " ("
                + "ma_dk INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ma_lop INTEGER NOT NULL REFERENCES " + T_LOP + "(ma_lop) ON DELETE CASCADE,"
                + "ma_kh INTEGER NOT NULL REFERENCES " + T_KH + "(ma_kh) ON DELETE RESTRICT,"
                + "ngay_dang_ky TEXT NOT NULL,"
                + "hoc_phi INTEGER NOT NULL DEFAULT 0,"
                + "tien_giam INTEGER NOT NULL DEFAULT 0,"
                + "ma_ud INTEGER NULL REFERENCES " + T_UU_DAI + "(ma_ud) ON DELETE SET NULL,"
                + "da_thanh_toan INTEGER NOT NULL DEFAULT 0,"
                + "trang_thai TEXT NOT NULL DEFAULT 'DANG_KY'"
                + " CHECK (trang_thai IN ('DANG_KY','DA_HUY')),"
                + "UNIQUE (ma_lop, ma_kh)"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dk_lop_lop ON " + T_DK_LOP + "(ma_lop, trang_thai)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SK + " ("
                + "ma_sk INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ten_sk TEXT NOT NULL,"
                + "loai TEXT NOT NULL DEFAULT 'SU_KIEN' CHECK (loai IN ('GIAI_DAU','SU_KIEN')),"
                + "mo_ta TEXT,"
                + "ngay_bat_dau TEXT,"
                + "ngay_ket_thuc TEXT,"
                + "han_dang_ky TEXT,"
                + "le_phi INTEGER NOT NULL DEFAULT 0,"
                + "suc_chua INTEGER NOT NULL DEFAULT 0,"
                + "lap_lai_hang_nam INTEGER NOT NULL DEFAULT 0,"
                + "trang_thai TEXT NOT NULL DEFAULT 'SAP_DIEN_RA'"
                + " CHECK (trang_thai IN ('SAP_DIEN_RA','DANG_DANG_KY','DANG_DIEN_RA','KET_THUC','HUY'))"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SK_SAN + " ("
                + "ma_sk INTEGER NOT NULL REFERENCES " + T_SK + "(ma_sk) ON DELETE CASCADE,"
                + "ma_san INTEGER NOT NULL REFERENCES " + T_SAN + "(ma_san) ON DELETE RESTRICT,"
                + "PRIMARY KEY (ma_sk, ma_san)"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_LICH_SK + " ("
                + "ma_lich INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ma_sk INTEGER NOT NULL REFERENCES " + T_SK + "(ma_sk) ON DELETE CASCADE,"
                + "ma_san INTEGER NOT NULL REFERENCES " + T_SAN + "(ma_san) ON DELETE RESTRICT,"
                + "ngay TEXT NOT NULL,"
                + "gio_bat_dau TEXT NOT NULL,"
                + "gio_ket_thuc TEXT NOT NULL,"
                + "noi_dung TEXT,"
                + "CHECK (gio_ket_thuc > gio_bat_dau)"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_lich_sk ON " + T_LICH_SK + "(ma_san, ngay)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_lich_sk_sk ON " + T_LICH_SK + "(ma_sk, ngay)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_DK_SK + " ("
                + "ma_dk INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ma_sk INTEGER NOT NULL REFERENCES " + T_SK + "(ma_sk) ON DELETE CASCADE,"
                + "ma_kh INTEGER NOT NULL REFERENCES " + T_KH + "(ma_kh) ON DELETE RESTRICT,"
                + "ngay_dang_ky TEXT NOT NULL,"
                + "le_phi INTEGER NOT NULL DEFAULT 0,"
                + "tien_giam INTEGER NOT NULL DEFAULT 0,"
                + "ma_ud INTEGER NULL REFERENCES " + T_UU_DAI + "(ma_ud) ON DELETE SET NULL,"
                + "da_thanh_toan INTEGER NOT NULL DEFAULT 0,"
                + "trang_thai TEXT NOT NULL DEFAULT 'DANG_KY'"
                + " CHECK (trang_thai IN ('DANG_KY','DA_HUY')),"
                + "UNIQUE (ma_sk, ma_kh)"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dk_sk_sk ON " + T_DK_SK + "(ma_sk, trang_thai)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_DEN + " ("
                + "ma_san INTEGER PRIMARY KEY REFERENCES " + T_SAN + "(ma_san) ON DELETE CASCADE,"
                + "loai TEXT NOT NULL DEFAULT 'ESP32' CHECK (loai IN ('ESP32','HOME_ASSISTANT')),"
                + "url_bat TEXT,"
                + "url_tat TEXT,"
                + "entity_id TEXT,"
                + "token TEXT,"
                + "phuong_thuc TEXT NOT NULL DEFAULT 'GET',"
                + "timeout_ms INTEGER NOT NULL DEFAULT 5000,"
                + "trang_thai TEXT NOT NULL DEFAULT 'KHONG_RO',"
                + "loi_cuoi TEXT"
                + ")");
    }

    private void seedCourts(SQLiteDatabase db) {
        insertCourtIfMissing(db, "Sân 1", 120000, Court.STATUS_ACTIVE);
        insertCourtIfMissing(db, "Sân 2", 150000, Court.STATUS_ACTIVE);
        insertCourtIfMissing(db, "Sân 3", 180000, Court.STATUS_ACTIVE);
        insertCourtIfMissing(db, "Sân 4", 160000, Court.STATUS_MAINTENANCE);
        insertCourtIfMissing(db, "Sân 5", 140000, Court.STATUS_ACTIVE);
        insertCourtIfMissing(db, "Sân 6", 170000, Court.STATUS_ACTIVE);
        insertCourtIfMissing(db, "Sân 7", 190000, Court.STATUS_ACTIVE);
        insertCourtIfMissing(db, "Sân 8", 200000, Court.STATUS_ACTIVE);
        insertCourtIfMissing(db, "Sân 9", 150000, Court.STATUS_ACTIVE);
        insertCourtIfMissing(db, "Sân 10", 160000, Court.STATUS_ACTIVE);
    }

    private void insertCourtIfMissing(SQLiteDatabase db, String name, int price, String status) {
        try (Cursor cursor = db.query(T_SAN, new String[]{"ma_san"}, "ten_san = ?",
                new String[]{name}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return;
            }
        }
        insertCourt(db, new Court(0, name, price, status));
    }

    private void seedCustomersAndBookings(SQLiteDatabase db) {
        long kh1 = insertCustomer(db, new Customer(0, "Nguyễn Văn An", "0901234567"));
        long kh2 = insertCustomer(db, new Customer(0, "Trần Thị Bình", "0912345678"));

        Calendar calendar = Calendar.getInstance();
        String today = DateTimeUtils.formatDbDate(calendar.getTime());
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        String yesterday = DateTimeUtils.formatDbDate(calendar.getTime());
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        String twoDaysAgo = DateTimeUtils.formatDbDate(calendar.getTime());

        insertSeedBooking(db, 1, (int) kh1, today, "08:00", "09:30", 120000, Booking.STATUS_BOOKED);
        insertSeedBooking(db, 2, (int) kh2, today, "17:00", "18:30", 150000, Booking.STATUS_BOOKED);
        insertSeedBooking(db, 3, (int) kh1, yesterday, "19:00", "20:00", 180000, Booking.STATUS_BOOKED);
        insertSeedBooking(db, 1, (int) kh2, twoDaysAgo, "18:00", "19:00", 120000, Booking.STATUS_CANCELLED);
    }

    private void seedServices(SQLiteDatabase db) {
        insertSeedServiceIfMissing(db, "Nước suối", Service.TYPE_SALE, "chai", 10000, 0, 0, 100);
        insertSeedServiceIfMissing(db, "Nước tăng lực", Service.TYPE_SALE, "chai", 20000, 0, 0, 50);
        insertSeedServiceIfMissing(db, "Khăn lạnh", Service.TYPE_SALE, "cái", 5000, 0, 0, 100);
        insertSeedServiceIfMissing(db, "Hộp bóng 3 quả", Service.TYPE_SALE, "hộp", 60000, 0, 0, 40);
        insertSeedServiceIfMissing(db, "Bóng Pickleball", Service.TYPE_SALE, "quả", 30000, 0, 0, 100);
        ensureRentalServices(db);
    }

    private void ensureRentalServices(SQLiteDatabase db) {
        insertSeedServiceIfMissing(db, "Thuê vợt", Service.TYPE_RENTAL, "cây/giờ",
                50000, 200000, 20000, 20);
        insertSeedServiceIfMissing(db, "Thuê bóng", Service.TYPE_RENTAL, "quả/giờ",
                20000, 50000, 10000, 30);
    }

    private void insertSeedServiceIfMissing(SQLiteDatabase db, String name, String type,
                                            String unit, int price, int deposit, int lateFee,
                                            int stock) {
        try (Cursor cursor = db.query(T_DV, new String[]{"ma_dv"}, "ten_dv = ?",
                new String[]{name}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return;
            }
        }
        insertSeedService(db, name, type, unit, price, deposit, lateFee, stock);
    }

    private void insertSeedService(SQLiteDatabase db, String name, String type, String unit,
                                   int price, int deposit, int lateFee, int stock) {
        ContentValues values = new ContentValues();
        values.put("ten_dv", name);
        values.put("loai", type);
        values.put("don_vi", unit);
        values.put("gia", price);
        values.put("tien_coc", deposit);
        values.put("phi_qua_han", lateFee);
        values.put("so_luong_ton", stock);
        values.put("trang_thai", Service.STATUS_ACTIVE);
        db.insert(T_DV, null, values);
    }

    private void seedSpecialDates(SQLiteDatabase db) {
        if (countRows(db, T_DIP) > 0) {
            return;
        }
        insertSeedHoliday(db, "Tết Dương lịch", 1, 1);
        insertSeedHoliday(db, "Ngày Quốc tế Phụ nữ", 3, 8);
        insertSeedHoliday(db, "Ngày Giải phóng miền Nam", 4, 30);
        insertSeedHoliday(db, "Ngày Quốc tế Lao động", 5, 1);
        insertSeedHoliday(db, "Quốc khánh", 9, 2);
        insertSeedHoliday(db, "Ngày Phụ nữ Việt Nam", 10, 20);
        insertSeedHoliday(db, "Ngày Nhà giáo Việt Nam", 11, 20);
        insertSeedHoliday(db, "Giáng sinh", 12, 24);
        insertSeedHoliday(db, "Giáng sinh", 12, 25);
    }

    private void insertSeedHoliday(SQLiteDatabase db, String name, int month, int day) {
        ContentValues values = new ContentValues();
        values.put("ten_dip", name);
        values.put("loai", SpecialDate.TYPE_HOLIDAY);
        values.put("thang", month);
        values.put("ngay", day);
        values.put("lap_lai_hang_nam", 1);
        values.put("trang_thai", SpecialDate.STATUS_ACTIVE);
        db.insert(T_DIP, null, values);
    }

    private int countRows(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private void insertSeedBooking(SQLiteDatabase db, int courtId, int customerId, String date,
                                   String start, String end, int pricePerHour, String status) {
        ContentValues values = new ContentValues();
        values.put("ma_san", courtId);
        values.put("ma_kh", customerId);
        values.put("ngay", date);
        values.put("gio_bat_dau", start);
        values.put("gio_ket_thuc", end);
        values.put("tong_tien", DateTimeUtils.estimateTotal(pricePerHour, start, end));
        values.put("trang_thai", status);
        db.insert(T_DAT, null, values);
    }

    public List<Court> getAllCourts() {
        return queryCourts(null, null);
    }

    public List<Court> getActiveCourts() {
        return queryCourts("trang_thai = ?", new String[]{Court.STATUS_ACTIVE});
    }

    public Court getCourt(int id) {
        return getCourt(getReadableDatabase(), id);
    }

    private Court getCourt(SQLiteDatabase db, int id) {
        try (Cursor cursor = db.query(T_SAN, null, "ma_san = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return readCourt(cursor);
            }
        }
        return null;
    }

    private List<Court> queryCourts(String selection, String[] args) {
        List<Court> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(T_SAN, null, selection, args, null, null, "ma_san ASC")) {
            while (cursor.moveToNext()) {
                result.add(readCourt(cursor));
            }
        }
        return result;
    }

    private Court readCourt(Cursor cursor) {
        Court court = new Court();
        court.setId(cursor.getInt(cursor.getColumnIndexOrThrow("ma_san")));
        court.setName(cursor.getString(cursor.getColumnIndexOrThrow("ten_san")));
        court.setPricePerHour(cursor.getInt(cursor.getColumnIndexOrThrow("gia_gio")));
        court.setStatus(cursor.getString(cursor.getColumnIndexOrThrow("trang_thai")));
        return court;
    }

    public long insertCourt(Court court) {
        return insertCourt(getWritableDatabase(), court);
    }

    private long insertCourt(SQLiteDatabase db, Court court) {
        ContentValues values = new ContentValues();
        values.put("ten_san", court.getName());
        values.put("gia_gio", court.getPricePerHour());
        values.put("trang_thai", court.getStatus());
        return db.insert(T_SAN, null, values);
    }

    public int updateCourt(Court court) {
        ContentValues values = new ContentValues();
        values.put("ten_san", court.getName());
        values.put("gia_gio", court.getPricePerHour());
        values.put("trang_thai", court.getStatus());
        return getWritableDatabase().update(T_SAN, values, "ma_san = ?",
                new String[]{String.valueOf(court.getId())});
    }

    public int deleteCourt(int id) {
        int refs = countCourtReferences(id);
        if (refs > 0) {
            return (int) ERROR_REFERENCED;
        }
        int rows = getWritableDatabase().delete(T_SAN, "ma_san = ?",
                new String[]{String.valueOf(id)});
        if (rows > 0) {
            logActivity("XOA_SAN", "san", id, null, null, "OK");
        }
        return rows;
    }

    public int countBookingsForCourt(int courtId) {
        return queryInt("SELECT COUNT(*) FROM " + T_DAT + " WHERE ma_san = ?",
                new String[]{String.valueOf(courtId)});
    }

    public int countCourtReferences(int courtId) {
        String id = String.valueOf(courtId);
        int total = countBookingsForCourt(courtId);
        total += queryInt("SELECT COUNT(*) FROM " + T_PHIEN + " WHERE ma_san = ?",
                new String[]{id});
        total += queryInt("SELECT COUNT(*) FROM " + T_LOP + " WHERE ma_san = ?",
                new String[]{id});
        total += queryInt("SELECT COUNT(*) FROM " + T_LICH_SK + " WHERE ma_san = ?",
                new String[]{id});
        total += queryInt("SELECT COUNT(*) FROM " + T_SK_SAN + " WHERE ma_san = ?",
                new String[]{id});
        return total;
    }

    public List<Customer> getAllCustomers() {
        return searchCustomers(null);
    }

    public List<Customer> searchCustomers(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        String selection = "trang_thai <> ?";
        String[] args;
        if (normalized.isEmpty()) {
            args = new String[]{"NGUNG"};
        } else {
            selection += " AND (ho_ten LIKE ? OR so_dien_thoai LIKE ?)";
            String like = "%" + normalized + "%";
            args = new String[]{"NGUNG", like, like};
        }

        List<Customer> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(T_KH, null, selection, args,
                null, null, "ho_ten COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                result.add(readCustomer(cursor));
            }
        }
        return result;
    }

    public List<Customer> getInactiveCustomers(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        String selection = "trang_thai = ?";
        String[] args;
        if (normalized.isEmpty()) {
            args = new String[]{"NGUNG"};
        } else {
            selection += " AND (ho_ten LIKE ? OR so_dien_thoai LIKE ?)";
            String like = "%" + normalized + "%";
            args = new String[]{"NGUNG", like, like};
        }
        List<Customer> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(T_KH, null, selection, args,
                null, null, "ho_ten COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                result.add(readCustomer(cursor));
            }
        }
        return result;
    }

    public Customer findCustomerByPhone(String phone) {
        return findCustomerByPhone(getReadableDatabase(), phone);
    }

    private Customer findCustomerByPhone(SQLiteDatabase db, String phone) {
        try (Cursor cursor = db.query(T_KH, null, "so_dien_thoai = ?",
                new String[]{phone}, null, null, null, "1")) {
            if (cursor.moveToFirst()) {
                return readCustomer(cursor);
            }
        }
        return null;
    }

    public Customer getCustomer(int id) {
        try (Cursor cursor = getReadableDatabase().query(T_KH, null, "ma_kh = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return readCustomer(cursor);
            }
        }
        return null;
    }

    private Customer readCustomer(Cursor cursor) {
        Customer customer = new Customer();
        customer.setId(cursor.getInt(cursor.getColumnIndexOrThrow("ma_kh")));
        customer.setName(cursor.getString(cursor.getColumnIndexOrThrow("ho_ten")));
        customer.setPhone(cursor.getString(cursor.getColumnIndexOrThrow("so_dien_thoai")));
        return customer;
    }

    public long insertCustomer(Customer customer) {
        return insertCustomer(getWritableDatabase(), customer);
    }

    private long insertCustomer(SQLiteDatabase db, Customer customer) {
        ContentValues values = new ContentValues();
        values.put("ho_ten", customer.getName());
        values.put("so_dien_thoai", customer.getPhone());
        return db.insert(T_KH, null, values);
    }

    public int updateCustomer(Customer customer) {
        ContentValues values = new ContentValues();
        values.put("ho_ten", customer.getName());
        values.put("so_dien_thoai", customer.getPhone());
        return getWritableDatabase().update(T_KH, values, "ma_kh = ?",
                new String[]{String.valueOf(customer.getId())});
    }

    public int deleteCustomer(int id) {
        ContentValues values = new ContentValues();
        values.put("trang_thai", "NGUNG");
        int rows = getWritableDatabase().update(T_KH, values, "ma_kh = ?",
                new String[]{String.valueOf(id)});
        if (rows > 0) {
            logActivity("NGUNG_KHACH", "khach_hang", id, "HOAT_DONG", "NGUNG", "OK");
        }
        return rows;
    }

    public int restoreCustomer(int id) {
        ContentValues values = new ContentValues();
        values.put("trang_thai", "HOAT_DONG");
        return getWritableDatabase().update(T_KH, values, "ma_kh = ?",
                new String[]{String.valueOf(id)});
    }

    public int countBookingsForCustomer(int customerId) {
        return queryInt("SELECT COUNT(*) FROM " + T_DAT + " WHERE ma_kh = ?",
                new String[]{String.valueOf(customerId)});
    }

    public List<Booking> searchBookings(String keyword, String date, int courtId,
                                        String status) {
        return searchBookings(keyword, date, null, courtId, status);
    }

    public List<Booking> searchBookings(String keyword, String fromDate, String toDate, int courtId,
                                        String status) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT b.ma_dat, b.ma_san, b.ma_kh, b.ngay, b.gio_bat_dau, b.gio_ket_thuc,")
                .append(" b.tong_tien, b.trang_thai, b.ghi_chu,")
                .append(" s.ten_san, s.gia_gio, k.ho_ten, k.so_dien_thoai")
                .append(" FROM ").append(T_DAT).append(" b")
                .append(" JOIN ").append(T_SAN).append(" s ON s.ma_san = b.ma_san")
                .append(" JOIN ").append(T_KH).append(" k ON k.ma_kh = b.ma_kh")
                .append(" WHERE 1 = 1");

        List<String> args = new ArrayList<>();
        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append(" AND b.ngay >= ?");
            args.add(fromDate);
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append(" AND b.ngay <= ?");
            args.add(toDate);
        }
        if (courtId > 0) {
            sql.append(" AND b.ma_san = ?");
            args.add(String.valueOf(courtId));
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND b.trang_thai = ?");
            args.add(status);
        }
        String normalized = keyword == null ? "" : keyword.trim();
        if (!normalized.isEmpty()) {
            sql.append(" AND (k.ho_ten LIKE ? OR k.so_dien_thoai LIKE ?)");
            String like = "%" + normalized + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY b.ngay DESC, b.gio_bat_dau ASC, b.ma_dat DESC");

        List<Booking> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(sql.toString(),
                args.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                result.add(readBooking(cursor));
            }
        }
        return result;
    }

    public Booking getBooking(int id) {
        return getBooking(getReadableDatabase(), id);
    }

    private Booking getBooking(SQLiteDatabase db, int id) {
        String sql = "SELECT b.ma_dat, b.ma_san, b.ma_kh, b.ngay, b.gio_bat_dau, b.gio_ket_thuc,"
                + " b.tong_tien, b.trang_thai, b.ghi_chu,"
                + " s.ten_san, s.gia_gio, k.ho_ten, k.so_dien_thoai"
                + " FROM " + T_DAT + " b"
                + " JOIN " + T_SAN + " s ON s.ma_san = b.ma_san"
                + " JOIN " + T_KH + " k ON k.ma_kh = b.ma_kh"
                + " WHERE b.ma_dat = ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return readBooking(cursor);
            }
        }
        return null;
    }

    private Booking readBooking(Cursor cursor) {
        Booking booking = new Booking();
        booking.setId(cursor.getInt(cursor.getColumnIndexOrThrow("ma_dat")));
        booking.setCourtId(cursor.getInt(cursor.getColumnIndexOrThrow("ma_san")));
        booking.setCustomerId(cursor.getInt(cursor.getColumnIndexOrThrow("ma_kh")));
        booking.setDate(cursor.getString(cursor.getColumnIndexOrThrow("ngay")));
        booking.setStartTime(cursor.getString(cursor.getColumnIndexOrThrow("gio_bat_dau")));
        booking.setEndTime(cursor.getString(cursor.getColumnIndexOrThrow("gio_ket_thuc")));
        booking.setTotalAmount(cursor.getInt(cursor.getColumnIndexOrThrow("tong_tien")));
        booking.setStatus(cursor.getString(cursor.getColumnIndexOrThrow("trang_thai")));
        booking.setNote(cursor.getString(cursor.getColumnIndexOrThrow("ghi_chu")));
        booking.setCourtName(cursor.getString(cursor.getColumnIndexOrThrow("ten_san")));
        booking.setPricePerHour(cursor.getInt(cursor.getColumnIndexOrThrow("gia_gio")));
        booking.setCustomerName(cursor.getString(cursor.getColumnIndexOrThrow("ho_ten")));
        booking.setCustomerPhone(cursor.getString(cursor.getColumnIndexOrThrow("so_dien_thoai")));
        return booking;
    }

    public boolean isOverlap(int courtId, String date, String startTime, String endTime,
                             int excludeId) {
        return isOverlap(getReadableDatabase(), courtId, date, startTime, endTime, excludeId);
    }

    private boolean isOverlap(SQLiteDatabase db, int courtId, String date, String startTime,
                              String endTime, int excludeId) {
        String sql = "SELECT 1 FROM " + T_DAT
                + " WHERE ma_san = ? AND ngay = ? AND trang_thai = ?"
                + " AND gio_bat_dau < ? AND gio_ket_thuc > ? AND ma_dat <> ? LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(courtId), date, Booking.STATUS_BOOKED,
                endTime, startTime, String.valueOf(excludeId)})) {
            return cursor.moveToFirst();
        }
    }

    private boolean activeSessionOverlaps(SQLiteDatabase db, int courtId, String date,
                                          String startTime, String endTime) {
        CourtSession session = getActiveSession(db, courtId);
        if (session == null || session.startTime == null) {
            return false;
        }
        String bookingStart = date + " " + startTime;
        String bookingEnd = date + " " + endTime;
        if (bookingEnd.compareTo(session.startTime) <= 0) {
            return false;
        }
        String now = DateTimeUtils.nowDbDateTime();
        // Phiên vãng lai chưa có giờ kết thúc: coi như sân còn bận trong 2 giờ tới.
        // Nhờ vậy phiên qua 0h vẫn chặn được lịch đặt sát giờ ở ngày kế tiếp.
        String safeStart = DateTimeUtils.addHoursToDbDateTime(now, 2);
        if (bookingStart.compareTo(safeStart) < 0) {
            return true;
        }
        String sessionDate = DateTimeUtils.datePart(session.startTime);
        if (sessionDate.equals(date)) {
            String sessionStart = DateTimeUtils.timePart(session.startTime);
            String sessionEnd = now.compareTo(session.startTime) > 0
                    ? DateTimeUtils.timePart(now) : "23:59";
            return startTime.compareTo(sessionEnd) < 0 && endTime.compareTo(sessionStart) > 0;
        }
        return false;
    }

    private boolean isBookingAt(SQLiteDatabase db, int courtId, String date, String time) {
        String sql = "SELECT 1 FROM " + T_DAT
                + " WHERE ma_san = ? AND ngay = ? AND trang_thai = ?"
                + " AND gio_bat_dau <= ? AND gio_ket_thuc > ? LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(courtId), date, Booking.STATUS_BOOKED, time, time})) {
            return cursor.moveToFirst();
        }
    }

    private boolean hasClassAt(SQLiteDatabase db, int courtId, String date, String time) {
        int weekday = DateTimeUtils.weekday(date);
        String sql = "SELECT 1 FROM " + T_LICH_LOP + " ll"
                + " JOIN " + T_LOP + " l ON l.ma_lop = ll.ma_lop"
                + " WHERE l.ma_san = ? AND ll.thu = ?"
                + " AND ll.gio_bat_dau <= ? AND ll.gio_ket_thuc > ?"
                + " AND l.trang_thai IN ('MO_DANG_KY','DANG_HOC')"
                + " AND (l.ngay_bat_dau IS NULL OR l.ngay_bat_dau <= ?)"
                + " AND (l.ngay_ket_thuc IS NULL OR l.ngay_ket_thuc >= ?) LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(courtId), String.valueOf(weekday), time, time, date, date})) {
            return cursor.moveToFirst();
        }
    }

    private boolean hasEventAt(SQLiteDatabase db, int courtId, String date, String time) {
        String sql = "SELECT 1 FROM " + T_LICH_SK + " l"
                + " JOIN " + T_SK + " s ON s.ma_sk = l.ma_sk"
                + " WHERE l.ma_san = ? AND l.ngay = ?"
                + " AND l.gio_bat_dau <= ? AND l.gio_ket_thuc > ?"
                + " AND s.trang_thai NOT IN ('KET_THUC','HUY') LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(courtId), date, time, time})) {
            return cursor.moveToFirst();
        }
    }

    private boolean hasClassRange(SQLiteDatabase db, int courtId, String date,
                                  String startTime, String endTime) {
        int weekday = DateTimeUtils.weekday(date);
        String sql = "SELECT 1 FROM " + T_LICH_LOP + " ll"
                + " JOIN " + T_LOP + " l ON l.ma_lop = ll.ma_lop"
                + " WHERE l.ma_san = ? AND ll.thu = ?"
                + " AND ll.gio_bat_dau < ? AND ll.gio_ket_thuc > ?"
                + " AND l.trang_thai IN ('MO_DANG_KY','DANG_HOC')"
                + " AND (l.ngay_bat_dau IS NULL OR l.ngay_bat_dau <= ?)"
                + " AND (l.ngay_ket_thuc IS NULL OR l.ngay_ket_thuc >= ?) LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(courtId), String.valueOf(weekday),
                endTime, startTime, date, date})) {
            return cursor.moveToFirst();
        }
    }

    private boolean hasEventRange(SQLiteDatabase db, int courtId, String date,
                                  String startTime, String endTime) {
        String sql = "SELECT 1 FROM " + T_LICH_SK + " l"
                + " JOIN " + T_SK + " s ON s.ma_sk = l.ma_sk"
                + " WHERE l.ma_san = ? AND l.ngay = ?"
                + " AND l.gio_bat_dau < ? AND l.gio_ket_thuc > ?"
                + " AND s.trang_thai NOT IN ('KET_THUC','HUY') LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(courtId), date, endTime, startTime})) {
            return cursor.moveToFirst();
        }
    }

    public boolean isCourtBlockedAt(int courtId, String date, String time) {
        SQLiteDatabase db = getReadableDatabase();
        return isBookingAt(db, courtId, date, time)
                || hasClassAt(db, courtId, date, time)
                || hasEventAt(db, courtId, date, time)
                || getActiveSession(db, courtId) != null;
    }

    public long insertBooking(Booking booking) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Court court = getCourt(db, booking.getCourtId());
            if (court == null || !court.isActive()) {
                return ERROR_COURT_UNAVAILABLE;
            }
            if (isOverlap(db, booking.getCourtId(), booking.getDate(),
                    booking.getStartTime(), booking.getEndTime(), -1)) {
                return ERROR_OVERLAP;
            }
            if (activeSessionOverlaps(db, booking.getCourtId(), booking.getDate(),
                    booking.getStartTime(), booking.getEndTime())) {
                return ERROR_SESSION_OVERLAP;
            }
            if (hasClassRange(db, booking.getCourtId(), booking.getDate(),
                    booking.getStartTime(), booking.getEndTime())
                    || hasEventRange(db, booking.getCourtId(), booking.getDate(),
                    booking.getStartTime(), booking.getEndTime())) {
                return ERROR_COURT_BUSY;
            }
            int customerId = resolveCustomer(db, booking.getCustomerName(),
                    booking.getCustomerPhone());
            if (customerId <= 0) {
                return ERROR;
            }

            ContentValues values = new ContentValues();
            values.put("ma_san", booking.getCourtId());
            values.put("ma_kh", customerId);
            values.put("ngay", booking.getDate());
            values.put("gio_bat_dau", booking.getStartTime());
            values.put("gio_ket_thuc", booking.getEndTime());
            values.put("tong_tien", DateTimeUtils.estimateTotal(court.getPricePerHour(),
                    booking.getStartTime(), booking.getEndTime()));
            values.put("trang_thai", Booking.STATUS_BOOKED);
            values.put("ghi_chu", booking.getNote());

            long id = db.insert(T_DAT, null, values);
            if (id <= 0) {
                return ERROR;
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public long updateBooking(Booking booking) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Court court = getCourt(db, booking.getCourtId());
            if (court == null) {
                return ERROR_COURT_UNAVAILABLE;
            }
            if (isOverlap(db, booking.getCourtId(), booking.getDate(),
                    booking.getStartTime(), booking.getEndTime(), booking.getId())) {
                return ERROR_OVERLAP;
            }
            if (activeSessionOverlaps(db, booking.getCourtId(), booking.getDate(),
                    booking.getStartTime(), booking.getEndTime())) {
                return ERROR_SESSION_OVERLAP;
            }
            if (hasClassRange(db, booking.getCourtId(), booking.getDate(),
                    booking.getStartTime(), booking.getEndTime())
                    || hasEventRange(db, booking.getCourtId(), booking.getDate(),
                    booking.getStartTime(), booking.getEndTime())) {
                return ERROR_COURT_BUSY;
            }
            int customerId = resolveCustomer(db, booking.getCustomerName(),
                    booking.getCustomerPhone());
            if (customerId <= 0) {
                return ERROR;
            }

            ContentValues values = new ContentValues();
            values.put("ma_san", booking.getCourtId());
            values.put("ma_kh", customerId);
            values.put("ngay", booking.getDate());
            values.put("gio_bat_dau", booking.getStartTime());
            values.put("gio_ket_thuc", booking.getEndTime());
            values.put("tong_tien", DateTimeUtils.estimateTotal(court.getPricePerHour(),
                    booking.getStartTime(), booking.getEndTime()));
            values.put("trang_thai", booking.getStatus());
            values.put("ghi_chu", booking.getNote());

            int rows = db.update(T_DAT, values, "ma_dat = ?",
                    new String[]{String.valueOf(booking.getId())});
            if (rows <= 0) {
                return ERROR;
            }
            db.setTransactionSuccessful();
            return rows;
        } finally {
            db.endTransaction();
        }
    }

    private int resolveCustomer(SQLiteDatabase db, String name, String phone) {
        Customer existing = findCustomerByPhone(db, phone);
        if (existing != null) {
            if (name != null && !name.equals(existing.getName())) {
                ContentValues values = new ContentValues();
                values.put("ho_ten", name);
                values.put("trang_thai", "HOAT_DONG");
                db.update(T_KH, values, "ma_kh = ?",
                        new String[]{String.valueOf(existing.getId())});
            }
            return existing.getId();
        }
        ContentValues values = new ContentValues();
        values.put("ho_ten", name);
        values.put("so_dien_thoai", phone);
        return (int) db.insert(T_KH, null, values);
    }

    public int findOrCreateCustomer(String name, String phone) {
        if (name == null || name.trim().isEmpty() || phone == null) {
            return (int) ERROR_INVALID;
        }
        String normalized = PhoneUtils.normalize(phone);
        if (!PhoneUtils.isValid(normalized)) {
            return (int) ERROR_INVALID;
        }
        return resolveCustomer(getWritableDatabase(), name.trim(), normalized);
    }

    public int cancelBooking(int id) {
        ContentValues values = new ContentValues();
        values.put("trang_thai", Booking.STATUS_CANCELLED);
        int rows = getWritableDatabase().update(T_DAT, values, "ma_dat = ?",
                new String[]{String.valueOf(id)});
        if (rows > 0) {
            logActivity("HUY_LICH", "dat_lich", id, Booking.STATUS_BOOKED,
                    Booking.STATUS_CANCELLED, "OK");
        }
        return rows;
    }

    public int deleteBooking(int id) {
        int rows = getWritableDatabase().delete(T_DAT, "ma_dat = ?",
                new String[]{String.valueOf(id)});
        if (rows > 0) {
            logActivity("XOA_LICH", "dat_lich", id, null, null, "OK");
        }
        return rows;
    }

    public int sumRevenue(String fromDate, String toDate) {
        return queryInt("SELECT COALESCE(SUM(tong_tien), 0) FROM " + T_DAT
                        + " WHERE trang_thai = ? AND ngay >= ? AND ngay <= ?",
                new String[]{Booking.STATUS_BOOKED, fromDate, toDate});
    }

    public int countByStatus(String fromDate, String toDate, String status) {
        return queryInt("SELECT COUNT(*) FROM " + T_DAT
                        + " WHERE trang_thai = ? AND ngay >= ? AND ngay <= ?",
                new String[]{status, fromDate, toDate});
    }

    public int sumBookedMinutes(String fromDate, String toDate) {
        String sql = "SELECT COALESCE(SUM("
                + "(CAST(substr(gio_ket_thuc, 1, 2) AS INTEGER) * 60"
                + " + CAST(substr(gio_ket_thuc, 4, 2) AS INTEGER))"
                + " - (CAST(substr(gio_bat_dau, 1, 2) AS INTEGER) * 60"
                + " + CAST(substr(gio_bat_dau, 4, 2) AS INTEGER))"
                + "), 0) FROM " + T_DAT
                + " WHERE trang_thai = ? AND ngay >= ? AND ngay <= ?";
        return queryInt(sql, new String[]{Booking.STATUS_BOOKED, fromDate, toDate});
    }

    public List<CourtStat> topCourts(String fromDate, String toDate, int limit) {
        List<CourtStat> result = new ArrayList<>();
        String sql = "SELECT s.ten_san, COUNT(*) AS so_lich, SUM(p.tong_thanh_toan) AS doanh_thu"
                + " FROM " + T_PHIEN + " p"
                + " JOIN " + T_SAN + " s ON s.ma_san = p.ma_san"
                + " WHERE p.trang_thai = ? AND substr(p.bat_dau, 1, 10) >= ?"
                + " AND substr(p.bat_dau, 1, 10) <= ?"
                + " GROUP BY p.ma_san"
                + " ORDER BY doanh_thu DESC, so_lich DESC LIMIT ?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{
                CourtSession.STATUS_FINISHED, fromDate, toDate, String.valueOf(limit)})) {
            while (cursor.moveToNext()) {
                result.add(new CourtStat(
                        cursor.getString(cursor.getColumnIndexOrThrow("ten_san")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("so_lich")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("doanh_thu"))));
            }
        }
        return result;
    }

    public List<DayRevenue> revenueByDay(String fromDate, String toDate) {
        List<DayRevenue> result = new ArrayList<>();
        String sql = "SELECT ngay, COALESCE(SUM(doanh_thu), 0) AS doanh_thu FROM ("
                + " SELECT substr(bat_dau, 1, 10) AS ngay,"
                + " SUM(tien_san + tien_dich_vu - tien_giam) AS doanh_thu FROM " + T_PHIEN
                + " WHERE trang_thai = ? AND substr(bat_dau, 1, 10) >= ?"
                + " AND substr(bat_dau, 1, 10) <= ? GROUP BY ngay"
                + " UNION ALL"
                + " SELECT substr(ngay_dang_ky, 1, 10) AS ngay,"
                + " SUM(hoc_phi - tien_giam) AS doanh_thu FROM " + T_DK_LOP
                + " WHERE trang_thai = ? AND da_thanh_toan = 1"
                + " AND substr(ngay_dang_ky, 1, 10) >= ?"
                + " AND substr(ngay_dang_ky, 1, 10) <= ? GROUP BY ngay"
                + " UNION ALL"
                + " SELECT substr(ngay_dang_ky, 1, 10) AS ngay,"
                + " SUM(le_phi - tien_giam) AS doanh_thu FROM " + T_DK_SK
                + " WHERE trang_thai = ? AND da_thanh_toan = 1"
                + " AND substr(ngay_dang_ky, 1, 10) >= ?"
                + " AND substr(ngay_dang_ky, 1, 10) <= ? GROUP BY ngay"
                + " UNION ALL"
                + " SELECT substr(ngay_thue, 1, 10) AS ngay,"
                + " SUM(tien_thue + phi_qua_han + tien_boi_thuong) AS doanh_thu FROM "
                + T_PHIEU_THUE
                + " WHERE substr(ngay_thue, 1, 10) >= ?"
                + " AND substr(ngay_thue, 1, 10) <= ? GROUP BY ngay"
                + " ) GROUP BY ngay ORDER BY ngay";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{
                CourtSession.STATUS_FINISHED, fromDate, toDate,
                ClassEnrollment.STATUS_ACTIVE, fromDate, toDate,
                EventRegistration.STATUS_ACTIVE, fromDate, toDate,
                fromDate, toDate})) {
            while (cursor.moveToNext()) {
                result.add(new DayRevenue(
                        cursor.getString(cursor.getColumnIndexOrThrow("ngay")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("doanh_thu"))));
            }
        }
        return result;
    }

    public List<TopCustomer> getTopCustomers(String fromDate, String toDate, int limit) {
        List<TopCustomer> result = new ArrayList<>();
        String sql = "SELECT k.ho_ten, k.so_dien_thoai, COUNT(*) AS so_lich,"
                + " SUM(p.tong_thanh_toan) AS doanh_thu"
                + " FROM " + T_PHIEN + " p"
                + " JOIN " + T_KH + " k ON k.ma_kh = p.ma_kh"
                + " WHERE p.trang_thai = ? AND substr(p.bat_dau, 1, 10) >= ?"
                + " AND substr(p.bat_dau, 1, 10) <= ?"
                + " GROUP BY p.ma_kh"
                + " ORDER BY doanh_thu DESC, so_lich DESC LIMIT ?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{
                CourtSession.STATUS_FINISHED, fromDate, toDate, String.valueOf(limit)})) {
            while (cursor.moveToNext()) {
                result.add(new TopCustomer(
                        cursor.getString(cursor.getColumnIndexOrThrow("ho_ten")),
                        cursor.getString(cursor.getColumnIndexOrThrow("so_dien_thoai")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("so_lich")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("doanh_thu"))));
            }
        }
        return result;
    }

    public List<PeakHour> getPeakHours(String fromDate, String toDate) {
        List<PeakHour> result = new ArrayList<>();
        String sql = "SELECT substr(bat_dau, 12, 2) AS gio, COUNT(*) AS so_lich"
                + " FROM " + T_PHIEN
                + " WHERE trang_thai = ? AND substr(bat_dau, 1, 10) >= ?"
                + " AND substr(bat_dau, 1, 10) <= ?"
                + " GROUP BY gio ORDER BY so_lich DESC, gio ASC";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{
                CourtSession.STATUS_FINISHED, fromDate, toDate})) {
            while (cursor.moveToNext()) {
                result.add(new PeakHour(
                        cursor.getString(cursor.getColumnIndexOrThrow("gio")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("so_lich"))));
            }
        }
        return result;
    }

    public List<Service> getAllServices() {
        return queryServices(null, null);
    }

    public List<Service> getActiveServices() {
        return queryServices("trang_thai = ?", new String[]{Service.STATUS_ACTIVE});
    }

    public List<Service> getServicesByType(String type) {
        return queryServices("loai = ? AND trang_thai = ?",
                new String[]{type, Service.STATUS_ACTIVE});
    }

    public Service getService(int id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(T_DV, null, "ma_dv = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return readService(cursor);
            }
        }
        return null;
    }

    private List<Service> queryServices(String selection, String[] args) {
        List<Service> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(T_DV, null, selection, args,
                null, null, "loai DESC, ten_dv ASC")) {
            while (cursor.moveToNext()) {
                result.add(readService(cursor));
            }
        }
        return result;
    }

    private Service readService(Cursor cursor) {
        Service service = new Service();
        service.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_dv"));
        service.name = cursor.getString(cursor.getColumnIndexOrThrow("ten_dv"));
        service.type = cursor.getString(cursor.getColumnIndexOrThrow("loai"));
        service.unit = cursor.getString(cursor.getColumnIndexOrThrow("don_vi"));
        service.price = cursor.getInt(cursor.getColumnIndexOrThrow("gia"));
        service.deposit = cursor.getInt(cursor.getColumnIndexOrThrow("tien_coc"));
        service.lateFee = cursor.getInt(cursor.getColumnIndexOrThrow("phi_qua_han"));
        service.stock = cursor.getInt(cursor.getColumnIndexOrThrow("so_luong_ton"));
        service.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
        return service;
    }

    public long insertService(Service service) {
        ContentValues values = serviceValues(service);
        return getWritableDatabase().insert(T_DV, null, values);
    }

    public int updateService(Service service) {
        return getWritableDatabase().update(T_DV, serviceValues(service), "ma_dv = ?",
                new String[]{String.valueOf(service.id)});
    }

    private ContentValues serviceValues(Service service) {
        ContentValues values = new ContentValues();
        values.put("ten_dv", service.name);
        values.put("loai", service.type);
        values.put("don_vi", service.unit);
        values.put("gia", service.price);
        values.put("tien_coc", service.deposit);
        values.put("phi_qua_han", service.lateFee);
        values.put("so_luong_ton", service.stock);
        values.put("trang_thai", service.status);
        return values;
    }

    public int deleteService(int id) {
        int refs = queryInt("SELECT (SELECT COUNT(*) FROM " + T_DON_DV
                + " WHERE ma_dv = ?) + (SELECT COUNT(*) FROM " + T_CT_THUE
                + " WHERE ma_dv = ?)", new String[]{String.valueOf(id), String.valueOf(id)});
        if (refs > 0) {
            return (int) ERROR_REFERENCED;
        }
        int rows = getWritableDatabase().delete(T_DV, "ma_dv = ?",
                new String[]{String.valueOf(id)});
        if (rows > 0) {
            logActivity("XOA_DICH_VU", "dich_vu", id, null, null, "OK");
        }
        return rows;
    }

    public List<WalkInSlot> getWalkInSlots() {
        return queryWalkInSlots(null, null);
    }

    public List<WalkInSlot> getWalkInSlotsForCourt(int courtId) {
        return queryWalkInSlots("g.ma_san = ?", new String[]{String.valueOf(courtId)});
    }

    private List<WalkInSlot> queryWalkInSlots(String selection, String[] args) {
        List<WalkInSlot> result = new ArrayList<>();
        String sql = "SELECT g.*, s.ten_san FROM " + T_GIO + " g"
                + " JOIN " + T_SAN + " s ON s.ma_san = g.ma_san"
                + (selection == null ? "" : " WHERE " + selection)
                + " ORDER BY g.ma_san, g.thu, g.gio_bat_dau";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                WalkInSlot slot = new WalkInSlot();
                slot.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_gio"));
                slot.courtId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_san"));
                slot.weekday = cursor.getInt(cursor.getColumnIndexOrThrow("thu"));
                slot.startTime = cursor.getString(cursor.getColumnIndexOrThrow("gio_bat_dau"));
                slot.endTime = cursor.getString(cursor.getColumnIndexOrThrow("gio_ket_thuc"));
                slot.price = cursor.getInt(cursor.getColumnIndexOrThrow("gia"));
                slot.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
                slot.courtName = cursor.getString(cursor.getColumnIndexOrThrow("ten_san"));
                result.add(slot);
            }
        }
        return result;
    }

    public long insertWalkInSlot(WalkInSlot slot) {
        if (isWalkInSlotOverlap(getWritableDatabase(), slot, 0)) {
            return ERROR_OVERLAP;
        }
        return getWritableDatabase().insert(T_GIO, null, walkInSlotValues(slot));
    }

    public int updateWalkInSlot(WalkInSlot slot) {
        if (isWalkInSlotOverlap(getWritableDatabase(), slot, slot.id)) {
            return (int) ERROR_OVERLAP;
        }
        return getWritableDatabase().update(T_GIO, walkInSlotValues(slot), "ma_gio = ?",
                new String[]{String.valueOf(slot.id)});
    }

    private ContentValues walkInSlotValues(WalkInSlot slot) {
        ContentValues values = new ContentValues();
        values.put("ma_san", slot.courtId);
        values.put("thu", slot.weekday);
        values.put("gio_bat_dau", slot.startTime);
        values.put("gio_ket_thuc", slot.endTime);
        values.put("gia", slot.price);
        values.put("trang_thai", slot.status);
        return values;
    }

    private boolean isWalkInSlotOverlap(SQLiteDatabase db, WalkInSlot slot, int exceptId) {
        String sql = "SELECT COUNT(*) FROM " + T_GIO
                + " WHERE ma_san = ? AND thu = ? AND ma_gio != ?"
                + " AND gio_bat_dau < ? AND gio_ket_thuc > ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(slot.courtId), String.valueOf(slot.weekday),
                String.valueOf(exceptId), slot.endTime, slot.startTime})) {
            return cursor.moveToFirst() && cursor.getInt(0) > 0;
        }
    }

    public int deleteWalkInSlot(int id) {
        return getWritableDatabase().delete(T_GIO, "ma_gio = ?",
                new String[]{String.valueOf(id)});
    }

    private WalkInSlot findWalkInSlot(SQLiteDatabase db, int courtId, String dateTime) {
        int weekday = DateTimeUtils.weekday(DateTimeUtils.datePart(dateTime));
        String time = DateTimeUtils.timePart(dateTime);
        String sql = "SELECT * FROM " + T_GIO
                + " WHERE ma_san = ? AND thu = ? AND trang_thai = ?"
                + " AND gio_bat_dau <= ? AND gio_ket_thuc > ?"
                + " ORDER BY gio_bat_dau LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(courtId), String.valueOf(weekday),
                WalkInSlot.STATUS_ACTIVE, time, time})) {
            if (cursor.moveToFirst()) {
                WalkInSlot slot = new WalkInSlot();
                slot.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_gio"));
                slot.courtId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_san"));
                slot.weekday = cursor.getInt(cursor.getColumnIndexOrThrow("thu"));
                slot.startTime = cursor.getString(cursor.getColumnIndexOrThrow("gio_bat_dau"));
                slot.endTime = cursor.getString(cursor.getColumnIndexOrThrow("gio_ket_thuc"));
                slot.price = cursor.getInt(cursor.getColumnIndexOrThrow("gia"));
                slot.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
                return slot;
            }
        }
        return null;
    }

    public long startWalkInSession(int courtId, int customerId, String startDateTime) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Court court = getCourt(db, courtId);
            if (court == null || !court.isActive()) {
                return ERROR_COURT_UNAVAILABLE;
            }
            WalkInSlot slot = findWalkInSlot(db, courtId, startDateTime);
            if (slot == null) {
                return ERROR_OUTSIDE_WALKIN;
            }
            if (getActiveSession(db, courtId) != null) {
                return ERROR_SESSION_OVERLAP;
            }
            String date = DateTimeUtils.datePart(startDateTime);
            String time = DateTimeUtils.timePart(startDateTime);
            if (isBookingAt(db, courtId, date, time) || hasClassAt(db, courtId, date, time)
                    || hasEventAt(db, courtId, date, time)) {
                return ERROR_COURT_BUSY;
            }
            ContentValues values = new ContentValues();
            values.put("ma_san", courtId);
            values.put("ma_kh", customerId > 0 ? customerId : null);
            values.put("ma_gio", slot.id);
            values.put("loai_phien", CourtSession.TYPE_WALK_IN);
            values.put("bat_dau", startDateTime);
            values.put("don_gia_vang_lai", slot.price);
            values.put("trang_thai", CourtSession.STATUS_PLAYING);
            long id = db.insert(T_PHIEN, null, values);
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public long startBookingSession(int bookingId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Booking booking = getBooking(db, bookingId);
            if (booking == null || Booking.STATUS_CANCELLED.equals(booking.getStatus())) {
                return ERROR_INVALID;
            }
            if (getActiveSession(db, booking.getCourtId()) != null) {
                return ERROR_SESSION_OVERLAP;
            }
            ContentValues values = new ContentValues();
            values.put("ma_san", booking.getCourtId());
            values.put("ma_dat", booking.getId());
            values.put("ma_kh", booking.getCustomerId());
            values.put("loai_phien", CourtSession.TYPE_BOOKING);
            values.put("bat_dau", booking.getDate() + " " + booking.getStartTime());
            values.put("tien_san", booking.getTotalAmount());
            values.put("trang_thai", CourtSession.STATUS_PLAYING);
            long id = db.insert(T_PHIEN, null, values);
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public CourtSession getActiveSession(int courtId) {
        return getActiveSession(getReadableDatabase(), courtId);
    }

    private CourtSession getActiveSession(SQLiteDatabase db, int courtId) {
        try (Cursor cursor = db.query(T_PHIEN, null,
                "ma_san = ? AND trang_thai = ?",
                new String[]{String.valueOf(courtId), CourtSession.STATUS_PLAYING},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return readSession(cursor);
            }
        }
        return null;
    }

    public CourtSession getSession(int id) {
        String sql = "SELECT p.*, s.ten_san, k.ho_ten, k.so_dien_thoai"
                + " FROM " + T_PHIEN + " p"
                + " JOIN " + T_SAN + " s ON s.ma_san = p.ma_san"
                + " LEFT JOIN " + T_KH + " k ON k.ma_kh = p.ma_kh"
                + " WHERE p.ma_phien = ?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                CourtSession session = readSession(cursor);
                session.courtName = cursor.getString(cursor.getColumnIndexOrThrow("ten_san"));
                session.customerName = cursor.getString(cursor.getColumnIndexOrThrow("ho_ten"));
                session.customerPhone = cursor.getString(
                        cursor.getColumnIndexOrThrow("so_dien_thoai"));
                return session;
            }
        }
        return null;
    }

    public List<CourtSession> getSessionsByDate(String date) {
        List<CourtSession> result = new ArrayList<>();
        String sql = "SELECT p.*, s.ten_san, k.ho_ten, k.so_dien_thoai"
                + " FROM " + T_PHIEN + " p"
                + " JOIN " + T_SAN + " s ON s.ma_san = p.ma_san"
                + " LEFT JOIN " + T_KH + " k ON k.ma_kh = p.ma_kh"
                + " WHERE substr(p.bat_dau, 1, 10) = ?"
                + " OR (p.trang_thai = ? AND substr(p.bat_dau, 1, 10) < ?)"
                + " ORDER BY p.bat_dau";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{
                date, CourtSession.STATUS_PLAYING, date})) {
            while (cursor.moveToNext()) {
                CourtSession session = readSession(cursor);
                session.courtName = cursor.getString(cursor.getColumnIndexOrThrow("ten_san"));
                session.customerName = cursor.getString(cursor.getColumnIndexOrThrow("ho_ten"));
                session.customerPhone = cursor.getString(cursor.getColumnIndexOrThrow("so_dien_thoai"));
                result.add(session);
            }
        }
        return result;
    }

    private CourtSession readSession(Cursor cursor) {
        CourtSession session = new CourtSession();
        session.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_phien"));
        session.courtId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_san"));
        session.bookingId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_dat"));
        session.customerId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_kh"));
        session.slotId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_gio"));
        session.type = cursor.getString(cursor.getColumnIndexOrThrow("loai_phien"));
        session.startTime = cursor.getString(cursor.getColumnIndexOrThrow("bat_dau"));
        session.endTime = cursor.getString(cursor.getColumnIndexOrThrow("ket_thuc"));
        session.walkInPrice = cursor.getInt(cursor.getColumnIndexOrThrow("don_gia_vang_lai"));
        session.courtFee = cursor.getInt(cursor.getColumnIndexOrThrow("tien_san"));
        session.serviceFee = cursor.getInt(cursor.getColumnIndexOrThrow("tien_dich_vu"));
        session.rentalFee = cursor.getInt(cursor.getColumnIndexOrThrow("tien_thue"));
        session.discountCourt = cursor.getInt(cursor.getColumnIndexOrThrow("giam_san"));
        session.discountService = cursor.getInt(cursor.getColumnIndexOrThrow("giam_dich_vu"));
        session.discount = cursor.getInt(cursor.getColumnIndexOrThrow("tien_giam"));
        session.promotionId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_ud"));
        session.promotionCode = cursor.getString(cursor.getColumnIndexOrThrow("ma_code"));
        session.total = cursor.getInt(cursor.getColumnIndexOrThrow("tong_thanh_toan"));
        session.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
        session.note = cursor.getString(cursor.getColumnIndexOrThrow("ghi_chu"));
        session.invoiceNo = cursor.getString(cursor.getColumnIndexOrThrow("ma_hoa_don"));
        session.paymentStatus = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai_tt"));
        int methodColumn = cursor.getColumnIndex("phuong_thuc_tt_moi");
        String paymentMethod = methodColumn >= 0 ? cursor.getString(methodColumn) : null;
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            paymentMethod = cursor.getString(cursor.getColumnIndexOrThrow("phuong_thuc_tt"));
        }
        session.paymentMethod = paymentMethod;
        session.paymentTransactionId = cursor.getString(cursor.getColumnIndexOrThrow("ma_giao_dich"));
        session.paidAt = cursor.getString(cursor.getColumnIndexOrThrow("thoi_gian_tt"));
        return session;
    }

    public int previewSessionTotal(int sessionId, String endDateTime) {
        CourtSession session = getSession(sessionId);
        if (session == null) {
            return (int) ERROR;
        }
        if (CourtSession.STATUS_FINISHED.equals(session.status)) {
            return session.total;
        }
        try {
            return calculateSessionAmounts(getReadableDatabase(), session, endDateTime)[3];
        } catch (IllegalArgumentException | ArithmeticException e) {
            Log.e(TAG, "Cannot preview checkout amount", e);
            return (int) ERROR_INVALID;
        }
    }

    public int finishSession(int sessionId, String endDateTime) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            CourtSession session = getSession(sessionId);
            if (session == null || CourtSession.STATUS_FINISHED.equals(session.status)) {
                return (int) ERROR_INVALID;
            }
            int[] amounts = calculateSessionAmounts(db, session, endDateTime);
            ContentValues values = new ContentValues();
            values.put("ket_thuc", endDateTime);
            values.put("tien_san", amounts[0]);
            values.put("tien_dich_vu", amounts[1]);
            values.put("tien_thue", amounts[2]);
            values.put("tien_giam", session.discountCourt + session.discountService);
            values.put("tong_thanh_toan", amounts[3]);
            values.put("trang_thai", CourtSession.STATUS_FINISHED);
            int rows = db.update(T_PHIEN, values, "ma_phien = ?",
                    new String[]{String.valueOf(sessionId)});
            db.setTransactionSuccessful();
            return rows;
        } finally {
            db.endTransaction();
        }
    }

    private int[] calculateSessionAmounts(SQLiteDatabase db, CourtSession session,
                                          String endDateTime) {
        int courtFee = session.courtFee;
        if (CourtSession.TYPE_WALK_IN.equals(session.type)) {
            courtFee = DateTimeUtils.estimateSessionFee(
                    session.walkInPrice, session.startTime, endDateTime);
        }
        int serviceFee = queryInt(db, "SELECT COALESCE(SUM(thanh_tien), 0) FROM "
                + T_DON_DV + " WHERE ma_phien = ?", new String[]{String.valueOf(session.id)});
        int rentalFee = queryInt(db, "SELECT COALESCE(SUM(tien_thue), 0) FROM "
                + T_PHIEU_THUE + " WHERE ma_phien = ?", new String[]{String.valueOf(session.id)});
        int total = com.example.courtmatepickleballcourtmanager.util.PaymentAmountMath.netTotal(
                courtFee, serviceFee, rentalFee,
                session.discountCourt, session.discountService);
        return new int[]{courtFee, serviceFee, rentalFee, total};
    }

    public int markSessionPaid(int sessionId, String method, String transactionId,
                               String paidAt) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            CourtSession session = getSession(sessionId);
            if (session == null) {
                return (int) ERROR;
            }
            if (CourtSession.PAYMENT_PAID.equals(session.paymentStatus)) {
                return (int) ERROR_INVALID;
            }
            if (!CourtSession.STATUS_FINISHED.equals(session.status)) {
                return (int) ERROR_INVALID;
            }
            String invoiceNo = session.invoiceNo;
            if (invoiceNo == null || invoiceNo.trim().isEmpty()) {
                invoiceNo = buildInvoiceNo(sessionId, paidAt);
            }
            ContentValues values = new ContentValues();
            values.put("ma_hoa_don", invoiceNo);
            values.put("trang_thai_tt", CourtSession.PAYMENT_PAID);
            // Cột cũ có CHECK chỉ nhận TIEN_MAT/MOMO; QR nội bộ lưu ở cột mới.
            values.put("phuong_thuc_tt", CourtSession.METHOD_CASH.equals(method)
                    ? CourtSession.METHOD_CASH : CourtSession.METHOD_MOMO);
            values.put("phuong_thuc_tt_moi", method);
            values.put("ma_giao_dich", transactionId);
            values.put("thoi_gian_tt", paidAt);
            int rows = db.update(T_PHIEN, values, "ma_phien = ? AND trang_thai_tt <> ?",
                    new String[]{String.valueOf(sessionId), CourtSession.PAYMENT_PAID});
            if (rows > 0) {
                ContentValues payment = new ContentValues();
                payment.put("loai_doi_tuong", Payment.TARGET_SESSION);
                payment.put("ma_doi_tuong", sessionId);
                payment.put("loai_giao_dich", Payment.TYPE_PAYMENT);
                payment.put("so_tien", session.total);
                payment.put("phuong_thuc", method);
                payment.put("ma_giao_dich", transactionId);
                payment.put("thoi_gian", paidAt);
                payment.put("ghi_chu", invoiceNo);
                db.insert(T_THANH_TOAN, null, payment);
                logActivity("THANH_TOAN", "phien_choi", sessionId,
                        CourtSession.PAYMENT_UNPAID, CourtSession.PAYMENT_PAID, invoiceNo);
                db.setTransactionSuccessful();
            }
            return rows;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Finishes a playing session and records the payment in ONE SQLite transaction.
     * Returns 1 on success; an already-paid session cannot be charged twice.
     * This is for manual cash / internal QR confirmation, NOT verified bank settlement.
     */
    public int completeSessionPayment(int sessionId, String endDateTime,
                                      String method, String transactionId, String paidAt) {
        if (!CourtSession.METHOD_CASH.equals(method)
                && !CourtSession.METHOD_QR.equals(method)) {
            return (int) ERROR_INVALID;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            CourtSession session = getSession(sessionId);
            if (session == null || CourtSession.PAYMENT_PAID.equals(session.paymentStatus)) {
                return (int) ERROR_INVALID;
            }
            if (!CourtSession.STATUS_PLAYING.equals(session.status)
                    && !CourtSession.STATUS_FINISHED.equals(session.status)) {
                return (int) ERROR_INVALID;
            }
            int payable;
            if (CourtSession.STATUS_PLAYING.equals(session.status)) {
                if (endDateTime == null || endDateTime.compareTo(session.startTime) < 0) {
                    return (int) ERROR_INVALID;
                }
                int[] amounts = calculateSessionAmounts(db, session, endDateTime);
                ContentValues finishing = new ContentValues();
                finishing.put("ket_thuc", endDateTime);
                finishing.put("tien_san", amounts[0]);
                finishing.put("tien_dich_vu", amounts[1]);
                finishing.put("tien_thue", amounts[2]);
                finishing.put("tien_giam", session.discountCourt + session.discountService);
                finishing.put("tong_thanh_toan", amounts[3]);
                finishing.put("trang_thai", CourtSession.STATUS_FINISHED);
                if (db.update(T_PHIEN, finishing, "ma_phien = ? AND trang_thai = ?",
                        new String[]{String.valueOf(sessionId), CourtSession.STATUS_PLAYING}) != 1) {
                    return (int) ERROR_INVALID;
                }
                payable = amounts[3];
            } else {
                payable = session.total;
            }
            String invoiceNo = session.invoiceNo;
            if (invoiceNo == null || invoiceNo.trim().isEmpty()) {
                invoiceNo = buildInvoiceNo(sessionId, paidAt);
            }
            ContentValues values = new ContentValues();
            values.put("ma_hoa_don", invoiceNo);
            values.put("trang_thai_tt", CourtSession.PAYMENT_PAID);
            // Legacy column only accepts TIEN_MAT/MOMO; preserve backwards compatibility.
            values.put("phuong_thuc_tt", CourtSession.METHOD_CASH.equals(method)
                    ? CourtSession.METHOD_CASH : CourtSession.METHOD_MOMO);
            values.put("phuong_thuc_tt_moi", method);
            values.put("ma_giao_dich", transactionId);
            values.put("thoi_gian_tt", paidAt);
            int rows = db.update(T_PHIEN, values,
                    "ma_phien = ? AND trang_thai_tt <> ? AND trang_thai = ?",
                    new String[]{String.valueOf(sessionId), CourtSession.PAYMENT_PAID,
                            CourtSession.STATUS_FINISHED});
            if (rows != 1) {
                return (int) ERROR_INVALID;
            }
            ContentValues payment = new ContentValues();
            payment.put("loai_doi_tuong", Payment.TARGET_SESSION);
            payment.put("ma_doi_tuong", sessionId);
            payment.put("loai_giao_dich", Payment.TYPE_PAYMENT);
            payment.put("so_tien", payable);
            payment.put("phuong_thuc", method);
            payment.put("ma_giao_dich", transactionId);
            payment.put("thoi_gian", paidAt);
            payment.put("ghi_chu", invoiceNo);
            db.insertOrThrow(T_THANH_TOAN, null, payment);
            // Audit trail shares the same transaction; a failure rolls the entire checkout back.
            ContentValues audit = new ContentValues();
            audit.put("thoi_gian", paidAt);
            audit.put("loai_thao_tac", "THANH_TOAN");
            audit.put("doi_tuong", "phien_choi");
            audit.put("ma_doi_tuong", sessionId);
            audit.put("gia_tri_cu", CourtSession.PAYMENT_UNPAID);
            audit.put("gia_tri_moi", CourtSession.PAYMENT_PAID);
            audit.put("ket_qua", invoiceNo);
            audit.put("nguoi_thuc_hien", com.example.courtmatepickleballcourtmanager.security.AuthSession.currentUsername());
            db.insertOrThrow(T_NHAT_KY, null, audit);
            db.setTransactionSuccessful();
            return 1;
        } catch (android.database.SQLException | ArithmeticException e) {
            android.util.Log.e(TAG, "Atomic checkout failed", e);
            return (int) ERROR;
        } finally {
            db.endTransaction();
        }
    }

    private String buildInvoiceNo(int sessionId, String paidAt) {
        String date = DateTimeUtils.datePart(paidAt).replace("-", "");
        return "HD" + date + "-" + String.format(java.util.Locale.US, "%05d", sessionId);
    }

    public List<CourtSession> getRecentFinishedSessions(int limit) {
        List<CourtSession> result = new ArrayList<>();
        String sql = "SELECT p.*, s.ten_san, k.ho_ten, k.so_dien_thoai"
                + " FROM " + T_PHIEN + " p"
                + " JOIN " + T_SAN + " s ON s.ma_san = p.ma_san"
                + " LEFT JOIN " + T_KH + " k ON k.ma_kh = p.ma_kh"
                + " WHERE p.trang_thai = ?"
                + " ORDER BY COALESCE(p.ket_thuc, p.bat_dau) DESC LIMIT ?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{CourtSession.STATUS_FINISHED, String.valueOf(limit)})) {
            while (cursor.moveToNext()) {
                CourtSession session = readSession(cursor);
                session.courtName = cursor.getString(cursor.getColumnIndexOrThrow("ten_san"));
                session.customerName = cursor.getString(cursor.getColumnIndexOrThrow("ho_ten"));
                session.customerPhone = cursor.getString(cursor.getColumnIndexOrThrow("so_dien_thoai"));
                result.add(session);
            }
        }
        return result;
    }

    public List<Rental> getRentalsForSession(int sessionId) {
        List<Rental> result = new ArrayList<>();
        String sql = "SELECT p.*, k.ho_ten, k.so_dien_thoai, s.ten_san"
                + " FROM " + T_PHIEU_THUE + " p"
                + " JOIN " + T_KH + " k ON k.ma_kh = p.ma_kh"
                + " LEFT JOIN " + T_PHIEN + " ps ON ps.ma_phien = p.ma_phien"
                + " LEFT JOIN " + T_SAN + " s ON s.ma_san = ps.ma_san"
                + " WHERE p.ma_phien = ? ORDER BY p.ma_phieu";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(sessionId)})) {
            while (cursor.moveToNext()) {
                result.add(readRental(cursor));
            }
        }
        return result;
    }

    public int applySessionDiscount(int sessionId, Promotion promotion, String code,
                                    int discountCourt, int discountService) {
        int discount = Math.max(0, discountCourt) + Math.max(0, discountService);
        ContentValues values = new ContentValues();
        values.put("ma_ud", promotion == null ? null : promotion.id);
        values.put("ma_code", code);
        values.put("giam_san", Math.max(0, discountCourt));
        values.put("giam_dich_vu", Math.max(0, discountService));
        values.put("tien_giam", discount);
        return getWritableDatabase().update(T_PHIEN, values, "ma_phien = ?",
                new String[]{String.valueOf(sessionId)});
    }

    public int cancelSession(int sessionId) {
        CourtSession session = getSession(sessionId);
        if (session == null) {
            return (int) ERROR;
        }
        if (CourtSession.TYPE_WALK_IN.equals(session.type)) {
            return getWritableDatabase().delete(T_PHIEN, "ma_phien = ?",
                    new String[]{String.valueOf(sessionId)});
        }
        ContentValues values = new ContentValues();
        values.put("trang_thai", CourtSession.STATUS_FINISHED);
        values.put("ket_thuc", DateTimeUtils.nowDbDateTime());
        return getWritableDatabase().update(T_PHIEN, values, "ma_phien = ?",
                new String[]{String.valueOf(sessionId)});
    }

    public long addServiceOrder(int sessionId, int serviceId, int quantity) {
        if (quantity <= 0) {
            return ERROR_INVALID;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            CourtSession session = getSession(sessionId);
            Service service = getService(serviceId);
            if (session == null || service == null
                    || !CourtSession.STATUS_PLAYING.equals(session.status)
                    || !Service.TYPE_SALE.equals(service.type) || !service.isActive()) {
                return ERROR_INVALID;
            }
            if (service.stock < quantity) {
                return ERROR_STOCK;
            }
            long amountLong = (long) service.price * quantity;
            if (amountLong < 0 || amountLong > Integer.MAX_VALUE) {
                return ERROR_INVALID;
            }
            int amount = (int) amountLong;
            ContentValues values = new ContentValues();
            values.put("ma_phien", sessionId);
            values.put("ma_dv", serviceId);
            values.put("so_luong", quantity);
            values.put("don_gia", service.price);
            values.put("thanh_tien", amount);
            values.put("thoi_gian", DateTimeUtils.nowDbDateTime());
            long id = db.insert(T_DON_DV, null, values);
            if (id <= 0) {
                return ERROR;
            }
            changeStock(db, serviceId, -quantity);
            recalculateSessionService(db, sessionId);
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public List<ServiceOrder> getServiceOrders(int sessionId) {
        List<ServiceOrder> result = new ArrayList<>();
        String sql = "SELECT d.*, v.ten_dv FROM " + T_DON_DV + " d"
                + " JOIN " + T_DV + " v ON v.ma_dv = d.ma_dv"
                + " WHERE d.ma_phien = ? ORDER BY d.ma_don";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(sessionId)})) {
            while (cursor.moveToNext()) {
                ServiceOrder order = new ServiceOrder();
                order.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_don"));
                order.sessionId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_phien"));
                order.serviceId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_dv"));
                order.serviceName = cursor.getString(cursor.getColumnIndexOrThrow("ten_dv"));
                order.quantity = cursor.getInt(cursor.getColumnIndexOrThrow("so_luong"));
                order.unitPrice = cursor.getInt(cursor.getColumnIndexOrThrow("don_gia"));
                order.amount = cursor.getInt(cursor.getColumnIndexOrThrow("thanh_tien"));
                order.time = cursor.getString(cursor.getColumnIndexOrThrow("thoi_gian"));
                result.add(order);
            }
        }
        return result;
    }

    private void changeStock(SQLiteDatabase db, int serviceId, int delta) {
        db.execSQL("UPDATE " + T_DV + " SET so_luong_ton = so_luong_ton + ? WHERE ma_dv = ?",
                new Object[]{delta, serviceId});
    }

    private void recalculateSessionService(SQLiteDatabase db, int sessionId) {
        int serviceFee = queryInt(db, "SELECT COALESCE(SUM(thanh_tien), 0) FROM "
                + T_DON_DV + " WHERE ma_phien = ?", new String[]{String.valueOf(sessionId)});
        CourtSession session = getSession(sessionId);
        if (session == null) {
            return;
        }
        int total = Math.max(0, session.courtFee + serviceFee + session.rentalFee
                - session.discountCourt - session.discountService);
        ContentValues values = new ContentValues();
        values.put("tien_dich_vu", serviceFee);
        values.put("tong_thanh_toan", total);
        db.update(T_PHIEN, values, "ma_phien = ?", new String[]{String.valueOf(sessionId)});
    }

    public long createRental(int customerId, int sessionId, String rentTime, String dueTime,
                             List<RentalItem> items) {
        if (items == null || items.isEmpty()) {
            return ERROR_INVALID;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int deposit = 0;
            int rentFee = 0;
            for (RentalItem item : items) {
                Service service = getService(item.serviceId);
                if (service == null || !service.isRental() || item.quantity <= 0) {
                    return ERROR_INVALID;
                }
                if (service.stock < item.quantity) {
                    return ERROR_STOCK;
                }
                deposit += service.deposit * item.quantity;
                rentFee += service.price * item.quantity;
            }
            ContentValues values = new ContentValues();
            values.put("ma_kh", customerId);
            values.put("ma_phien", sessionId > 0 ? sessionId : null);
            values.put("ngay_thue", rentTime);
            values.put("hen_tra", dueTime);
            values.put("tien_coc", deposit);
            values.put("tien_thue", rentFee);
            values.put("trang_thai", Rental.STATUS_RENTING);
            long rentalId = db.insert(T_PHIEU_THUE, null, values);
            if (rentalId <= 0) {
                return ERROR;
            }
            for (RentalItem item : items) {
                Service service = getService(item.serviceId);
                ContentValues detail = new ContentValues();
                detail.put("ma_phieu", rentalId);
                detail.put("ma_dv", service.id);
                detail.put("so_luong", item.quantity);
                detail.put("don_gia", service.price);
                detail.put("don_vi", service.unit);
                detail.put("phi_qua_han", service.lateFee);
                detail.put("thanh_tien", service.price * item.quantity);
                db.insert(T_CT_THUE, null, detail);
                changeStock(db, service.id, -item.quantity);
            }
            if (deposit > 0) {
                ContentValues payment = new ContentValues();
                payment.put("loai_doi_tuong", Payment.TARGET_RENTAL);
                payment.put("ma_doi_tuong", rentalId);
                payment.put("loai_giao_dich", Payment.TYPE_DEPOSIT);
                payment.put("so_tien", deposit);
                payment.put("phuong_thuc", CourtSession.METHOD_CASH);
                payment.put("thoi_gian", rentTime);
                payment.put("ghi_chu", "Cọc thuê thiết bị");
                db.insert(T_THANH_TOAN, null, payment);
            }
            if (sessionId > 0) {
                updateSessionRentalFee(db, sessionId);
            }
            db.setTransactionSuccessful();
            return rentalId;
        } finally {
            db.endTransaction();
        }
    }

    private void updateSessionRentalFee(SQLiteDatabase db, int sessionId) {
        int rentalFee = queryInt(db, "SELECT COALESCE(SUM(tien_thue), 0) FROM "
                + T_PHIEU_THUE + " WHERE ma_phien = ?", new String[]{String.valueOf(sessionId)});
        db.execSQL("UPDATE " + T_PHIEN + " SET tien_thue = ? WHERE ma_phien = ?",
                new Object[]{rentalFee, sessionId});
        recalculateSessionService(db, sessionId);
    }

    public Rental getRental(int id) {
        String sql = "SELECT p.*, k.ho_ten, k.so_dien_thoai, s.ten_san"
                + " FROM " + T_PHIEU_THUE + " p"
                + " JOIN " + T_KH + " k ON k.ma_kh = p.ma_kh"
                + " LEFT JOIN " + T_PHIEN + " ps ON ps.ma_phien = p.ma_phien"
                + " LEFT JOIN " + T_SAN + " s ON s.ma_san = ps.ma_san"
                + " WHERE p.ma_phieu = ?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return readRental(cursor);
            }
        }
        return null;
    }

    public List<Rental> getRentals(String statusFilter) {
        List<Rental> result = new ArrayList<>();
        String selection = statusFilter == null ? "" : " WHERE p.trang_thai = ?";
        String sql = "SELECT p.*, k.ho_ten, k.so_dien_thoai, s.ten_san"
                + " FROM " + T_PHIEU_THUE + " p"
                + " JOIN " + T_KH + " k ON k.ma_kh = p.ma_kh"
                + " LEFT JOIN " + T_PHIEN + " ps ON ps.ma_phien = p.ma_phien"
                + " LEFT JOIN " + T_SAN + " s ON s.ma_san = ps.ma_san"
                + selection + " ORDER BY p.ngay_thue DESC";
        String[] args = statusFilter == null ? null : new String[]{statusFilter};
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                result.add(readRental(cursor));
            }
        }
        return result;
    }

    private Rental readRental(Cursor cursor) {
        Rental rental = new Rental();
        rental.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_phieu"));
        rental.customerId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_kh"));
        rental.sessionId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_phien"));
        rental.rentTime = cursor.getString(cursor.getColumnIndexOrThrow("ngay_thue"));
        rental.dueTime = cursor.getString(cursor.getColumnIndexOrThrow("hen_tra"));
        rental.returnTime = cursor.getString(cursor.getColumnIndexOrThrow("ngay_tra"));
        rental.deposit = cursor.getInt(cursor.getColumnIndexOrThrow("tien_coc"));
        rental.rentFee = cursor.getInt(cursor.getColumnIndexOrThrow("tien_thue"));
        rental.lateFee = cursor.getInt(cursor.getColumnIndexOrThrow("phi_qua_han"));
        rental.compensation = cursor.getInt(cursor.getColumnIndexOrThrow("tien_boi_thuong"));
        rental.refund = cursor.getInt(cursor.getColumnIndexOrThrow("tien_hoan_coc"));
        rental.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
        rental.note = cursor.getString(cursor.getColumnIndexOrThrow("ghi_chu"));
        rental.customerName = cursor.getString(cursor.getColumnIndexOrThrow("ho_ten"));
        rental.customerPhone = cursor.getString(cursor.getColumnIndexOrThrow("so_dien_thoai"));
        rental.courtName = cursor.getString(cursor.getColumnIndexOrThrow("ten_san"));
        return rental;
    }

    public List<RentalItem> getRentalItems(int rentalId) {
        List<RentalItem> result = new ArrayList<>();
        String sql = "SELECT c.*, v.ten_dv, v.tien_coc FROM " + T_CT_THUE + " c"
                + " JOIN " + T_DV + " v ON v.ma_dv = c.ma_dv"
                + " WHERE c.ma_phieu = ? ORDER BY c.ma_ct";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(rentalId)})) {
            while (cursor.moveToNext()) {
                RentalItem item = new RentalItem();
                item.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_ct"));
                item.rentalId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_phieu"));
                item.serviceId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_dv"));
                item.serviceName = cursor.getString(cursor.getColumnIndexOrThrow("ten_dv"));
                item.quantity = cursor.getInt(cursor.getColumnIndexOrThrow("so_luong"));
                item.unitPrice = cursor.getInt(cursor.getColumnIndexOrThrow("don_gia"));
                item.deposit = cursor.getInt(cursor.getColumnIndexOrThrow("tien_coc"));
                item.unit = cursor.getString(cursor.getColumnIndexOrThrow("don_vi"));
                item.lateFee = cursor.getInt(cursor.getColumnIndexOrThrow("phi_qua_han"));
                item.returnedQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("so_luong_da_tra"));
                item.condition = cursor.getString(cursor.getColumnIndexOrThrow("tinh_trang"));
                item.compensation = cursor.getInt(cursor.getColumnIndexOrThrow("tien_boi_thuong"));
                item.amount = cursor.getInt(cursor.getColumnIndexOrThrow("thanh_tien"));
                result.add(item);
            }
        }
        return result;
    }

    public int refreshOverdueRentals(String now) {
        ContentValues values = new ContentValues();
        values.put("trang_thai", Rental.STATUS_OVERDUE);
        return getWritableDatabase().update(T_PHIEU_THUE, values,
                "trang_thai = ? AND hen_tra < ?",
                new String[]{Rental.STATUS_RENTING, now});
    }

    public int returnRental(int rentalId, String returnTime, int lateFee,
                            int compensation, String note) {
        return returnRental(rentalId, returnTime, lateFee, compensation, note, null);
    }

    public int returnRental(int rentalId, String returnTime, int lateFee,
                            int compensation, String note,
                            Map<Integer, Integer> returnedByItemId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Rental rental = getRental(rentalId);
            if (rental == null || Rental.STATUS_RETURNED.equals(rental.status)) {
                return (int) ERROR_INVALID;
            }
            if (lateFee < 0 || compensation < 0) {
                return (int) ERROR_INVALID;
            }
            List<RentalItem> items = getRentalItems(rentalId);
            if (items.isEmpty()) {
                return (int) ERROR_INVALID;
            }
            // Validate the entire request before changing stock or any rental row.
            if (returnedByItemId != null) {
                for (Integer itemId : returnedByItemId.keySet()) {
                    boolean found = false;
                    for (RentalItem item : items) {
                        if (item.id == itemId) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return (int) ERROR_INVALID;
                    }
                }
            }
            boolean fullyReturned = true;
            boolean returnedSomething = false;
            for (RentalItem item : items) {
                int remaining = Math.max(0, item.quantity - item.returnedQuantity);
                int requested = returnedByItemId == null
                        ? remaining
                        : returnedByItemId.getOrDefault(item.id, 0);
                if (requested < 0 || requested > remaining) {
                    return (int) ERROR_INVALID;
                }
                int returnNow = requested;
                returnedSomething |= returnNow > 0;
                if (returnNow > 0) {
                    changeStock(db, item.serviceId, returnNow);
                    ContentValues detail = new ContentValues();
                    detail.put("so_luong_da_tra", item.returnedQuantity + returnNow);
                    db.update(T_CT_THUE, detail, "ma_ct = ?",
                            new String[]{String.valueOf(item.id)});
                }
                if (item.returnedQuantity + returnNow < item.quantity) {
                    fullyReturned = false;
                }
            }
            if (!returnedSomething) {
                return (int) ERROR_INVALID;
            }
            // Each partial return contributes additional charges; do not erase old charges.
            long cumulativeLate = (long) rental.lateFee + lateFee;
            long cumulativeDamage = (long) rental.compensation + compensation;
            if (cumulativeLate > Integer.MAX_VALUE || cumulativeDamage > Integer.MAX_VALUE) {
                return (int) ERROR_INVALID;
            }
            int late = (int) cumulativeLate;
            int damage = (int) cumulativeDamage;
            int refund = fullyReturned
                    ? (int) Math.max(0L, (long) rental.deposit - late - damage) : 0;
            ContentValues values = new ContentValues();
            values.put("ngay_tra", returnTime);
            values.put("phi_qua_han", late);
            values.put("tien_boi_thuong", damage);
            values.put("tien_hoan_coc", refund);
            values.put("trang_thai", fullyReturned
                    ? Rental.STATUS_RETURNED : rental.status);
            if (note != null) {
                values.put("ghi_chu", note);
            }
            int rows = db.update(T_PHIEU_THUE, values, "ma_phieu = ?",
                    new String[]{String.valueOf(rentalId)});
            if (rows > 0) {
                logActivity("TRA_THIET_BI", "phieu_thue", rentalId,
                        rental.status, fullyReturned
                                ? Rental.STATUS_RETURNED : rental.status, "OK");
                if (fullyReturned) {
                    int retained = Math.max(0, rental.deposit - refund);
                    if (refund > 0) {
                        ContentValues payment = new ContentValues();
                        payment.put("loai_doi_tuong", Payment.TARGET_RENTAL);
                        payment.put("ma_doi_tuong", rentalId);
                        payment.put("loai_giao_dich", Payment.TYPE_REFUND);
                        payment.put("so_tien", refund);
                        payment.put("phuong_thuc", CourtSession.METHOD_CASH);
                        payment.put("thoi_gian", returnTime);
                        payment.put("ghi_chu", "Hoàn cọc thuê thiết bị");
                        db.insert(T_THANH_TOAN, null, payment);
                    }
                    if (retained > 0) {
                        ContentValues payment = new ContentValues();
                        payment.put("loai_doi_tuong", Payment.TARGET_RENTAL);
                        payment.put("ma_doi_tuong", rentalId);
                        payment.put("loai_giao_dich", Payment.TYPE_ADJUST);
                        payment.put("so_tien", retained);
                        payment.put("phuong_thuc", CourtSession.METHOD_CASH);
                        payment.put("thoi_gian", returnTime);
                        payment.put("ghi_chu", "Cấn trừ cọc vào phí thuê");
                        db.insert(T_THANH_TOAN, null, payment);
                    }
                }
            }
            if (rows > 0 && rental.sessionId > 0 && fullyReturned) {
                updateSessionRentalFee(db, rental.sessionId);
            }
            db.setTransactionSuccessful();
            return rows;
        } finally {
            db.endTransaction();
        }
    }

    public int returnRentalItem(int rentalId, int rentalItemId, int quantity,
                                String returnTime) {
        Map<Integer, Integer> map = new java.util.HashMap<>();
        map.put(rentalItemId, quantity);
        return returnRental(rentalId, returnTime, 0, 0, null, map);
    }

    public List<Coach> getCoaches() {
        List<Coach> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(T_HLV, null, null, null,
                null, null, "ho_ten ASC")) {
            while (cursor.moveToNext()) {
                result.add(readCoach(cursor));
            }
        }
        return result;
    }

    public Coach getCoach(int id) {
        try (Cursor cursor = getReadableDatabase().query(T_HLV, null, "ma_hlv = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return readCoach(cursor);
            }
        }
        return null;
    }

    private Coach readCoach(Cursor cursor) {
        Coach coach = new Coach();
        coach.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_hlv"));
        coach.name = cursor.getString(cursor.getColumnIndexOrThrow("ho_ten"));
        coach.phone = cursor.getString(cursor.getColumnIndexOrThrow("so_dien_thoai"));
        coach.specialty = cursor.getString(cursor.getColumnIndexOrThrow("chuyen_mon"));
        coach.experience = cursor.getString(cursor.getColumnIndexOrThrow("kinh_nghiem"));
        coach.hourlyRate = cursor.getInt(cursor.getColumnIndexOrThrow("phi_theo_gio"));
        coach.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
        return coach;
    }

    public long insertCoach(Coach coach) {
        return getWritableDatabase().insert(T_HLV, null, coachValues(coach));
    }

    public int updateCoach(Coach coach) {
        return getWritableDatabase().update(T_HLV, coachValues(coach), "ma_hlv = ?",
                new String[]{String.valueOf(coach.id)});
    }

    private ContentValues coachValues(Coach coach) {
        ContentValues values = new ContentValues();
        values.put("ho_ten", coach.name);
        values.put("so_dien_thoai", coach.phone);
        values.put("chuyen_mon", coach.specialty);
        values.put("kinh_nghiem", coach.experience);
        values.put("phi_theo_gio", coach.hourlyRate);
        values.put("trang_thai", coach.status);
        return values;
    }

    public int deleteCoach(int id) {
        return getWritableDatabase().delete(T_HLV, "ma_hlv = ?",
                new String[]{String.valueOf(id)});
    }

    public List<ClassRoom> getClasses() {
        List<ClassRoom> result = new ArrayList<>();
        String sql = "SELECT l.*, h.ho_ten AS ten_hlv, s.ten_san,"
                + " (SELECT COUNT(*) FROM " + T_DK_LOP + " d"
                + " WHERE d.ma_lop = l.ma_lop AND d.trang_thai = 'DANG_KY') AS si_so"
                + " FROM " + T_LOP + " l"
                + " JOIN " + T_HLV + " h ON h.ma_hlv = l.ma_hlv"
                + " JOIN " + T_SAN + " s ON s.ma_san = l.ma_san"
                + " ORDER BY l.ngay_bat_dau DESC, l.ten_lop ASC";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                result.add(readClass(cursor));
            }
        }
        return result;
    }

    public ClassRoom getClassRoom(int id) {
        String sql = "SELECT l.*, h.ho_ten AS ten_hlv, s.ten_san,"
                + " (SELECT COUNT(*) FROM " + T_DK_LOP + " d"
                + " WHERE d.ma_lop = l.ma_lop AND d.trang_thai = 'DANG_KY') AS si_so"
                + " FROM " + T_LOP + " l"
                + " JOIN " + T_HLV + " h ON h.ma_hlv = l.ma_hlv"
                + " JOIN " + T_SAN + " s ON s.ma_san = l.ma_san"
                + " WHERE l.ma_lop = ?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return readClass(cursor);
            }
        }
        return null;
    }

    private ClassRoom readClass(Cursor cursor) {
        ClassRoom room = new ClassRoom();
        room.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_lop"));
        room.name = cursor.getString(cursor.getColumnIndexOrThrow("ten_lop"));
        room.coachId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_hlv"));
        room.courtId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_san"));
        room.level = cursor.getString(cursor.getColumnIndexOrThrow("trinh_do"));
        room.startDate = cursor.getString(cursor.getColumnIndexOrThrow("ngay_bat_dau"));
        room.endDate = cursor.getString(cursor.getColumnIndexOrThrow("ngay_ket_thuc"));
        room.capacity = cursor.getInt(cursor.getColumnIndexOrThrow("suc_chua"));
        room.fee = cursor.getInt(cursor.getColumnIndexOrThrow("hoc_phi"));
        room.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
        room.note = cursor.getString(cursor.getColumnIndexOrThrow("ghi_chu"));
        room.coachName = cursor.getString(cursor.getColumnIndexOrThrow("ten_hlv"));
        room.courtName = cursor.getString(cursor.getColumnIndexOrThrow("ten_san"));
        room.enrolled = cursor.getInt(cursor.getColumnIndexOrThrow("si_so"));
        return room;
    }

    public long insertClassRoom(ClassRoom room) {
        return getWritableDatabase().insert(T_LOP, null, classValues(room));
    }

    public int updateClassRoom(ClassRoom room) {
        return getWritableDatabase().update(T_LOP, classValues(room), "ma_lop = ?",
                new String[]{String.valueOf(room.id)});
    }

    private ContentValues classValues(ClassRoom room) {
        ContentValues values = new ContentValues();
        values.put("ten_lop", room.name);
        values.put("ma_hlv", room.coachId);
        values.put("ma_san", room.courtId);
        values.put("trinh_do", room.level);
        values.put("ngay_bat_dau", room.startDate);
        values.put("ngay_ket_thuc", room.endDate);
        values.put("suc_chua", room.capacity);
        values.put("hoc_phi", room.fee);
        values.put("trang_thai", room.status);
        values.put("ghi_chu", room.note);
        return values;
    }

    public int deleteClassRoom(int id) {
        return getWritableDatabase().delete(T_LOP, "ma_lop = ?",
                new String[]{String.valueOf(id)});
    }

    public List<ClassSchedule> getClassSchedules(int classId) {
        List<ClassSchedule> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(T_LICH_LOP, null, "ma_lop = ?",
                new String[]{String.valueOf(classId)}, null, null, "thu, gio_bat_dau")) {
            while (cursor.moveToNext()) {
                ClassSchedule schedule = new ClassSchedule();
                schedule.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_lich"));
                schedule.classId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_lop"));
                schedule.weekday = cursor.getInt(cursor.getColumnIndexOrThrow("thu"));
                schedule.startTime = cursor.getString(cursor.getColumnIndexOrThrow("gio_bat_dau"));
                schedule.endTime = cursor.getString(cursor.getColumnIndexOrThrow("gio_ket_thuc"));
                result.add(schedule);
            }
        }
        return result;
    }

    public void saveClassSchedules(int classId, List<ClassSchedule> schedules) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(T_LICH_LOP, "ma_lop = ?", new String[]{String.valueOf(classId)});
            for (ClassSchedule schedule : schedules) {
                ContentValues values = new ContentValues();
                values.put("ma_lop", classId);
                values.put("thu", schedule.weekday);
                values.put("gio_bat_dau", schedule.startTime);
                values.put("gio_ket_thuc", schedule.endTime);
                db.insert(T_LICH_LOP, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<ClassEnrollment> getClassEnrollments(int classId) {
        List<ClassEnrollment> result = new ArrayList<>();
        String sql = "SELECT d.*, k.ho_ten, k.so_dien_thoai, l.ten_lop"
                + " FROM " + T_DK_LOP + " d"
                + " JOIN " + T_KH + " k ON k.ma_kh = d.ma_kh"
                + " JOIN " + T_LOP + " l ON l.ma_lop = d.ma_lop"
                + " WHERE d.ma_lop = ? ORDER BY d.ngay_dang_ky DESC";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(classId)})) {
            while (cursor.moveToNext()) {
                result.add(readClassEnrollment(cursor));
            }
        }
        return result;
    }

    private ClassEnrollment readClassEnrollment(Cursor cursor) {
        ClassEnrollment enrollment = new ClassEnrollment();
        enrollment.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_dk"));
        enrollment.classId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_lop"));
        enrollment.customerId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_kh"));
        enrollment.date = cursor.getString(cursor.getColumnIndexOrThrow("ngay_dang_ky"));
        enrollment.fee = cursor.getInt(cursor.getColumnIndexOrThrow("hoc_phi"));
        enrollment.discount = cursor.getInt(cursor.getColumnIndexOrThrow("tien_giam"));
        enrollment.promotionId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_ud"));
        enrollment.paid = cursor.getInt(cursor.getColumnIndexOrThrow("da_thanh_toan"));
        enrollment.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
        enrollment.customerName = cursor.getString(cursor.getColumnIndexOrThrow("ho_ten"));
        enrollment.customerPhone = cursor.getString(cursor.getColumnIndexOrThrow("so_dien_thoai"));
        enrollment.className = cursor.getString(cursor.getColumnIndexOrThrow("ten_lop"));
        return enrollment;
    }

    public long registerForClass(int classId, String customerName, String customerPhone,
                                 int discount, int promotionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ClassRoom room = getClassRoom(classId);
            if (room == null || ClassRoom.STATUS_CANCELLED.equals(room.status)
                    || ClassRoom.STATUS_FINISHED.equals(room.status)) {
                return ERROR_INVALID;
            }
            if (room.enrolled >= room.capacity) {
                return ERROR_CAPACITY;
            }
            int customerId = resolveCustomer(db, customerName, customerPhone);
            if (customerId <= 0) {
                return ERROR;
            }
            int existingId = queryInt(db, "SELECT ma_dk FROM " + T_DK_LOP
                            + " WHERE ma_lop = ? AND ma_kh = ?",
                    new String[]{String.valueOf(classId), String.valueOf(customerId)});
            ContentValues values = new ContentValues();
            values.put("ma_lop", classId);
            values.put("ma_kh", customerId);
            values.put("ngay_dang_ky", DateTimeUtils.nowDbDateTime());
            values.put("hoc_phi", room.fee);
            values.put("tien_giam", Math.max(0, Math.min(discount, room.fee)));
            values.put("ma_ud", promotionId > 0 ? promotionId : null);
            values.put("da_thanh_toan", 0);
            values.put("trang_thai", ClassEnrollment.STATUS_ACTIVE);
            long id;
            if (existingId > 0) {
                String existingStatus = queryString(db, "SELECT trang_thai FROM " + T_DK_LOP
                        + " WHERE ma_dk = ?", new String[]{String.valueOf(existingId)});
                if (ClassEnrollment.STATUS_ACTIVE.equals(existingStatus)) {
                    return ERROR_INVALID;
                }
                id = db.update(T_DK_LOP, values, "ma_dk = ?",
                        new String[]{String.valueOf(existingId)}) > 0 ? existingId : ERROR;
            } else {
                id = db.insert(T_DK_LOP, null, values);
            }
            if (id <= 0) {
                return ERROR;
            }
            db.setTransactionSuccessful();
            return id;
        } catch (Exception e) {
            return ERROR;
        } finally {
            db.endTransaction();
        }
    }

    public int cancelClassEnrollment(int enrollmentId) {
        ContentValues values = new ContentValues();
        values.put("trang_thai", ClassEnrollment.STATUS_CANCELLED);
        return getWritableDatabase().update(T_DK_LOP, values, "ma_dk = ?",
                new String[]{String.valueOf(enrollmentId)});
    }

    public int markClassEnrollmentPaid(int enrollmentId, int paid) {
        ContentValues values = new ContentValues();
        values.put("da_thanh_toan", paid);
        return getWritableDatabase().update(T_DK_LOP, values, "ma_dk = ?",
                new String[]{String.valueOf(enrollmentId)});
    }

    public List<Event> getEvents() {
        List<Event> result = new ArrayList<>();
        String sql = "SELECT s.*, (SELECT COUNT(*) FROM " + T_DK_SK + " d"
                + " WHERE d.ma_sk = s.ma_sk AND d.trang_thai = 'DANG_KY') AS da_dang_ky"
                + " FROM " + T_SK + " s ORDER BY s.ngay_bat_dau DESC, s.ten_sk ASC";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                result.add(readEvent(cursor));
            }
        }
        return result;
    }

    public Event getEvent(int id) {
        String sql = "SELECT s.*, (SELECT COUNT(*) FROM " + T_DK_SK + " d"
                + " WHERE d.ma_sk = s.ma_sk AND d.trang_thai = 'DANG_KY') AS da_dang_ky"
                + " FROM " + T_SK + " s WHERE s.ma_sk = ?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return readEvent(cursor);
            }
        }
        return null;
    }

    private Event readEvent(Cursor cursor) {
        Event event = new Event();
        event.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_sk"));
        event.name = cursor.getString(cursor.getColumnIndexOrThrow("ten_sk"));
        event.type = cursor.getString(cursor.getColumnIndexOrThrow("loai"));
        event.description = cursor.getString(cursor.getColumnIndexOrThrow("mo_ta"));
        event.startDate = cursor.getString(cursor.getColumnIndexOrThrow("ngay_bat_dau"));
        event.endDate = cursor.getString(cursor.getColumnIndexOrThrow("ngay_ket_thuc"));
        event.deadline = cursor.getString(cursor.getColumnIndexOrThrow("han_dang_ky"));
        event.fee = cursor.getInt(cursor.getColumnIndexOrThrow("le_phi"));
        event.capacity = cursor.getInt(cursor.getColumnIndexOrThrow("suc_chua"));
        event.annual = cursor.getInt(cursor.getColumnIndexOrThrow("lap_lai_hang_nam"));
        event.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
        event.registered = cursor.getInt(cursor.getColumnIndexOrThrow("da_dang_ky"));
        return event;
    }

    public long insertEvent(Event event) {
        return getWritableDatabase().insert(T_SK, null, eventValues(event));
    }

    public int updateEvent(Event event) {
        return getWritableDatabase().update(T_SK, eventValues(event), "ma_sk = ?",
                new String[]{String.valueOf(event.id)});
    }

    private ContentValues eventValues(Event event) {
        ContentValues values = new ContentValues();
        values.put("ten_sk", event.name);
        values.put("loai", event.type);
        values.put("mo_ta", event.description);
        values.put("ngay_bat_dau", event.startDate);
        values.put("ngay_ket_thuc", event.endDate);
        values.put("han_dang_ky", event.deadline);
        values.put("le_phi", event.fee);
        values.put("suc_chua", event.capacity);
        values.put("lap_lai_hang_nam", event.annual);
        values.put("trang_thai", event.status);
        return values;
    }

    public int deleteEvent(int id) {
        return getWritableDatabase().delete(T_SK, "ma_sk = ?",
                new String[]{String.valueOf(id)});
    }

    public List<Court> getEventCourts(int eventId) {
        List<Court> result = new ArrayList<>();
        String sql = "SELECT s.* FROM " + T_SK_SAN + " es"
                + " JOIN " + T_SAN + " s ON s.ma_san = es.ma_san"
                + " WHERE es.ma_sk = ? ORDER BY s.ma_san";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(eventId)})) {
            while (cursor.moveToNext()) {
                result.add(readCourt(cursor));
            }
        }
        return result;
    }

    public void saveEventCourts(int eventId, List<Integer> courtIds) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(T_SK_SAN, "ma_sk = ?", new String[]{String.valueOf(eventId)});
            for (Integer courtId : courtIds) {
                ContentValues values = new ContentValues();
                values.put("ma_sk", eventId);
                values.put("ma_san", courtId);
                db.insert(T_SK_SAN, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<EventSchedule> getEventSchedules(int eventId) {
        List<EventSchedule> result = new ArrayList<>();
        String sql = "SELECT l.*, s.ten_san FROM " + T_LICH_SK + " l"
                + " JOIN " + T_SAN + " s ON s.ma_san = l.ma_san"
                + " WHERE l.ma_sk = ? ORDER BY l.ngay, l.gio_bat_dau";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(eventId)})) {
            while (cursor.moveToNext()) {
                EventSchedule schedule = new EventSchedule();
                schedule.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_lich"));
                schedule.eventId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_sk"));
                schedule.courtId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_san"));
                schedule.date = cursor.getString(cursor.getColumnIndexOrThrow("ngay"));
                schedule.startTime = cursor.getString(cursor.getColumnIndexOrThrow("gio_bat_dau"));
                schedule.endTime = cursor.getString(cursor.getColumnIndexOrThrow("gio_ket_thuc"));
                schedule.content = cursor.getString(cursor.getColumnIndexOrThrow("noi_dung"));
                schedule.courtName = cursor.getString(cursor.getColumnIndexOrThrow("ten_san"));
                result.add(schedule);
            }
        }
        return result;
    }

    public void saveEventSchedules(int eventId, List<EventSchedule> schedules) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(T_LICH_SK, "ma_sk = ?", new String[]{String.valueOf(eventId)});
            for (EventSchedule schedule : schedules) {
                ContentValues values = new ContentValues();
                values.put("ma_sk", eventId);
                values.put("ma_san", schedule.courtId);
                values.put("ngay", schedule.date);
                values.put("gio_bat_dau", schedule.startTime);
                values.put("gio_ket_thuc", schedule.endTime);
                values.put("noi_dung", schedule.content);
                db.insert(T_LICH_SK, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<EventRegistration> getEventRegistrations(int eventId) {
        List<EventRegistration> result = new ArrayList<>();
        String sql = "SELECT d.*, k.ho_ten, k.so_dien_thoai, s.ten_sk"
                + " FROM " + T_DK_SK + " d"
                + " JOIN " + T_KH + " k ON k.ma_kh = d.ma_kh"
                + " JOIN " + T_SK + " s ON s.ma_sk = d.ma_sk"
                + " WHERE d.ma_sk = ? ORDER BY d.ngay_dang_ky DESC";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(eventId)})) {
            while (cursor.moveToNext()) {
                EventRegistration registration = new EventRegistration();
                registration.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_dk"));
                registration.eventId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_sk"));
                registration.customerId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_kh"));
                registration.date = cursor.getString(cursor.getColumnIndexOrThrow("ngay_dang_ky"));
                registration.fee = cursor.getInt(cursor.getColumnIndexOrThrow("le_phi"));
                registration.discount = cursor.getInt(cursor.getColumnIndexOrThrow("tien_giam"));
                registration.promotionId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_ud"));
                registration.paid = cursor.getInt(cursor.getColumnIndexOrThrow("da_thanh_toan"));
                registration.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
                registration.customerName = cursor.getString(cursor.getColumnIndexOrThrow("ho_ten"));
                registration.customerPhone = cursor.getString(cursor.getColumnIndexOrThrow("so_dien_thoai"));
                registration.eventName = cursor.getString(cursor.getColumnIndexOrThrow("ten_sk"));
                result.add(registration);
            }
        }
        return result;
    }

    public long registerForEvent(int eventId, String customerName, String customerPhone,
                                 int discount, int promotionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Event event = getEvent(eventId);
            if (event == null || Event.STATUS_CANCELLED.equals(event.status)
                    || Event.STATUS_FINISHED.equals(event.status)) {
                return ERROR_INVALID;
            }
            if (event.capacity > 0 && event.registered >= event.capacity) {
                return ERROR_CAPACITY;
            }
            int customerId = resolveCustomer(db, customerName, customerPhone);
            if (customerId <= 0) {
                return ERROR;
            }
            int existingId = queryInt(db, "SELECT ma_dk FROM " + T_DK_SK
                            + " WHERE ma_sk = ? AND ma_kh = ?",
                    new String[]{String.valueOf(eventId), String.valueOf(customerId)});
            ContentValues values = new ContentValues();
            values.put("ma_sk", eventId);
            values.put("ma_kh", customerId);
            values.put("ngay_dang_ky", DateTimeUtils.nowDbDateTime());
            values.put("le_phi", event.fee);
            values.put("tien_giam", Math.max(0, Math.min(discount, event.fee)));
            values.put("ma_ud", promotionId > 0 ? promotionId : null);
            values.put("da_thanh_toan", 0);
            values.put("trang_thai", EventRegistration.STATUS_ACTIVE);
            long id;
            if (existingId > 0) {
                String existingStatus = queryString(db, "SELECT trang_thai FROM " + T_DK_SK
                        + " WHERE ma_dk = ?", new String[]{String.valueOf(existingId)});
                if (EventRegistration.STATUS_ACTIVE.equals(existingStatus)) {
                    return ERROR_INVALID;
                }
                id = db.update(T_DK_SK, values, "ma_dk = ?",
                        new String[]{String.valueOf(existingId)}) > 0 ? existingId : ERROR;
            } else {
                id = db.insert(T_DK_SK, null, values);
            }
            if (id <= 0) {
                return ERROR;
            }
            db.setTransactionSuccessful();
            return id;
        } catch (Exception e) {
            return ERROR;
        } finally {
            db.endTransaction();
        }
    }

    public int cancelEventRegistration(int registrationId) {
        ContentValues values = new ContentValues();
        values.put("trang_thai", EventRegistration.STATUS_CANCELLED);
        return getWritableDatabase().update(T_DK_SK, values, "ma_dk = ?",
                new String[]{String.valueOf(registrationId)});
    }

    public int markEventRegistrationPaid(int registrationId, int paid) {
        ContentValues values = new ContentValues();
        values.put("da_thanh_toan", paid);
        return getWritableDatabase().update(T_DK_SK, values, "ma_dk = ?",
                new String[]{String.valueOf(registrationId)});
    }

    public long duplicateEventToNextYear(int eventId) {
        Event event = getEvent(eventId);
        if (event == null) {
            return ERROR;
        }
        event.name = event.name + " (năm sau)";
        event.startDate = DateTimeUtils.shiftYears(event.startDate, 1);
        event.endDate = DateTimeUtils.shiftYears(event.endDate, 1);
        event.deadline = DateTimeUtils.shiftYears(event.deadline, 1);
        event.status = Event.STATUS_UPCOMING;
        event.annual = 1;
        long newId = insertEvent(event);
        if (newId <= 0) {
            return ERROR;
        }
        List<Integer> courtIds = new ArrayList<>();
        for (Court court : getEventCourts(eventId)) {
            courtIds.add(court.getId());
        }
        saveEventCourts((int) newId, courtIds);
        List<EventSchedule> schedules = getEventSchedules(eventId);
        for (EventSchedule schedule : schedules) {
            schedule.eventId = (int) newId;
            schedule.date = DateTimeUtils.shiftYears(schedule.date, 1);
        }
        saveEventSchedules((int) newId, schedules);
        return newId;
    }

    public List<SpecialDate> getSpecialDates() {
        List<SpecialDate> result = new ArrayList<>();
        String sql = "SELECT d.*, s.ten_san FROM " + T_DIP + " d"
                + " LEFT JOIN " + T_SAN + " s ON s.ma_san = d.ma_san"
                + " ORDER BY d.thang, d.ngay";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                SpecialDate date = new SpecialDate();
                date.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_dip"));
                date.name = cursor.getString(cursor.getColumnIndexOrThrow("ten_dip"));
                date.type = cursor.getString(cursor.getColumnIndexOrThrow("loai"));
                date.month = cursor.getInt(cursor.getColumnIndexOrThrow("thang"));
                date.day = cursor.getInt(cursor.getColumnIndexOrThrow("ngay"));
                date.year = cursor.getInt(cursor.getColumnIndexOrThrow("nam"));
                date.courtId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_san"));
                date.annual = cursor.getInt(cursor.getColumnIndexOrThrow("lap_lai_hang_nam"));
                date.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
                date.courtName = cursor.getString(cursor.getColumnIndexOrThrow("ten_san"));
                result.add(date);
            }
        }
        return result;
    }

    public long insertSpecialDate(SpecialDate date) {
        return getWritableDatabase().insert(T_DIP, null, specialDateValues(date));
    }

    public int updateSpecialDate(SpecialDate date) {
        return getWritableDatabase().update(T_DIP, specialDateValues(date), "ma_dip = ?",
                new String[]{String.valueOf(date.id)});
    }

    private ContentValues specialDateValues(SpecialDate date) {
        ContentValues values = new ContentValues();
        values.put("ten_dip", date.name);
        values.put("loai", date.type);
        values.put("thang", date.month);
        values.put("ngay", date.day);
        values.put("nam", date.annual == 1 ? null : date.year);
        values.put("ma_san", date.courtId > 0 ? date.courtId : null);
        values.put("lap_lai_hang_nam", date.annual);
        values.put("trang_thai", date.status);
        return values;
    }

    public int deleteSpecialDate(int id) {
        return getWritableDatabase().delete(T_DIP, "ma_dip = ?",
                new String[]{String.valueOf(id)});
    }

    public List<Promotion> getPromotions() {
        return queryPromotions(null, null);
    }

    public List<Promotion> getActivePromotions() {
        return queryPromotions("trang_thai = ?", new String[]{Promotion.STATUS_ACTIVE});
    }

    private List<Promotion> queryPromotions(String selection, String[] args) {
        List<Promotion> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(T_UU_DAI, null, selection, args,
                null, null, "tu_ngay DESC, ma_ud DESC")) {
            while (cursor.moveToNext()) {
                result.add(readPromotion(cursor));
            }
        }
        return result;
    }

    public Promotion getPromotion(int id) {
        try (Cursor cursor = getReadableDatabase().query(T_UU_DAI, null, "ma_ud = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return readPromotion(cursor);
            }
        }
        return null;
    }

    public Promotion findPromotionByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        try (Cursor cursor = getReadableDatabase().query(T_UU_DAI, null, "ma_code = ?",
                new String[]{code.trim().toUpperCase()}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return readPromotion(cursor);
            }
        }
        return null;
    }

    private Promotion readPromotion(Cursor cursor) {
        Promotion promotion = new Promotion();
        promotion.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_ud"));
        promotion.name = cursor.getString(cursor.getColumnIndexOrThrow("ten_ud"));
        promotion.discountType = cursor.getString(cursor.getColumnIndexOrThrow("loai_giam"));
        promotion.value = cursor.getInt(cursor.getColumnIndexOrThrow("gia_tri"));
        promotion.scope = cursor.getString(cursor.getColumnIndexOrThrow("pham_vi"));
        promotion.minOrder = cursor.getInt(cursor.getColumnIndexOrThrow("gia_tri_don_toi_thieu"));
        promotion.maxDiscount = cursor.getInt(cursor.getColumnIndexOrThrow("giam_toi_da"));
        promotion.fromDate = cursor.getString(cursor.getColumnIndexOrThrow("tu_ngay"));
        promotion.toDate = cursor.getString(cursor.getColumnIndexOrThrow("den_ngay"));
        promotion.annual = cursor.getInt(cursor.getColumnIndexOrThrow("lap_lai_hang_nam"));
        promotion.code = cursor.getString(cursor.getColumnIndexOrThrow("ma_code"));
        promotion.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
        promotion.note = cursor.getString(cursor.getColumnIndexOrThrow("ghi_chu"));
        return promotion;
    }

    public long insertPromotion(Promotion promotion) {
        return getWritableDatabase().insert(T_UU_DAI, null, promotionValues(promotion));
    }

    public int updatePromotion(Promotion promotion) {
        return getWritableDatabase().update(T_UU_DAI, promotionValues(promotion), "ma_ud = ?",
                new String[]{String.valueOf(promotion.id)});
    }

    private ContentValues promotionValues(Promotion promotion) {
        ContentValues values = new ContentValues();
        values.put("ten_ud", promotion.name);
        values.put("loai_giam", promotion.discountType);
        values.put("gia_tri", promotion.value);
        values.put("pham_vi", promotion.scope);
        values.put("gia_tri_don_toi_thieu", promotion.minOrder);
        values.put("giam_toi_da", promotion.maxDiscount);
        values.put("tu_ngay", promotion.fromDate);
        values.put("den_ngay", promotion.toDate);
        values.put("lap_lai_hang_nam", promotion.annual);
        values.put("ma_code", promotion.code == null || promotion.code.trim().isEmpty()
                ? null : promotion.code.trim().toUpperCase());
        values.put("trang_thai", promotion.status);
        values.put("ghi_chu", promotion.note);
        return values;
    }

    public int deletePromotion(int id) {
        return getWritableDatabase().delete(T_UU_DAI, "ma_ud = ?",
                new String[]{String.valueOf(id)});
    }

    public List<Integer> getPromotionSpecialDateIds(int promotionId) {
        List<Integer> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(T_UU_DAI_DIP, new String[]{"ma_dip"},
                "ma_ud = ?", new String[]{String.valueOf(promotionId)}, null, null, null)) {
            while (cursor.moveToNext()) {
                result.add(cursor.getInt(0));
            }
        }
        return result;
    }

    public void savePromotionSpecialDates(int promotionId, List<Integer> specialDateIds) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(T_UU_DAI_DIP, "ma_ud = ?", new String[]{String.valueOf(promotionId)});
            for (Integer id : specialDateIds) {
                ContentValues values = new ContentValues();
                values.put("ma_ud", promotionId);
                values.put("ma_dip", id);
                db.insert(T_UU_DAI_DIP, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Promotion> getAutomaticPromotions(String date, int courtId) {
        List<Promotion> result = new ArrayList<>();
        int month = Integer.parseInt(date.substring(5, 7));
        int day = Integer.parseInt(date.substring(8, 10));
        int year = Integer.parseInt(date.substring(0, 4));
        String sql = "SELECT DISTINCT u.* FROM " + T_UU_DAI + " u"
                + " JOIN " + T_UU_DAI_DIP + " ud ON ud.ma_ud = u.ma_ud"
                + " JOIN " + T_DIP + " d ON d.ma_dip = ud.ma_dip"
                + " WHERE u.trang_thai = ? AND d.trang_thai = ?"
                + " AND d.thang = ? AND d.ngay = ?"
                + " AND (d.lap_lai_hang_nam = 1 OR d.nam = ?)"
                + " AND (d.ma_san IS NULL OR d.ma_san = ?)"
                + " AND (u.tu_ngay IS NULL OR u.tu_ngay <= ?)"
                + " AND (u.den_ngay IS NULL OR u.den_ngay >= ?)"
                + " ORDER BY u.ma_ud";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{
                Promotion.STATUS_ACTIVE, SpecialDate.STATUS_ACTIVE,
                String.valueOf(month), String.valueOf(day), String.valueOf(year),
                String.valueOf(courtId), date, date})) {
            while (cursor.moveToNext()) {
                result.add(readPromotion(cursor));
            }
        }
        return result;
    }

    public LightConfig getLightConfig(int courtId) {
        try (Cursor cursor = getReadableDatabase().query(T_DEN, null, "ma_san = ?",
                new String[]{String.valueOf(courtId)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                LightConfig config = new LightConfig();
                config.courtId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_san"));
                config.type = cursor.getString(cursor.getColumnIndexOrThrow("loai"));
                config.onUrl = cursor.getString(cursor.getColumnIndexOrThrow("url_bat"));
                config.offUrl = cursor.getString(cursor.getColumnIndexOrThrow("url_tat"));
                config.entityId = cursor.getString(cursor.getColumnIndexOrThrow("entity_id"));
                config.token = cursor.getString(cursor.getColumnIndexOrThrow("token"));
                config.method = cursor.getString(cursor.getColumnIndexOrThrow("phuong_thuc"));
                config.timeoutMs = cursor.getInt(cursor.getColumnIndexOrThrow("timeout_ms"));
                config.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
                config.lastError = cursor.getString(cursor.getColumnIndexOrThrow("loi_cuoi"));
                config.autoOff = cursor.getInt(cursor.getColumnIndexOrThrow("tu_dong_tat")) != 0;
                config.graceMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("phut_tre"));
                return config;
            }
        }
        return null;
    }

    public List<LightConfig> getLightConfigs() {
        List<LightConfig> result = new ArrayList<>();
        String sql = "SELECT c.*, s.ten_san FROM " + T_DEN + " c"
                + " JOIN " + T_SAN + " s ON s.ma_san = c.ma_san ORDER BY c.ma_san";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                LightConfig config = new LightConfig();
                config.courtId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_san"));
                config.courtName = cursor.getString(cursor.getColumnIndexOrThrow("ten_san"));
                config.type = cursor.getString(cursor.getColumnIndexOrThrow("loai"));
                config.onUrl = cursor.getString(cursor.getColumnIndexOrThrow("url_bat"));
                config.offUrl = cursor.getString(cursor.getColumnIndexOrThrow("url_tat"));
                config.entityId = cursor.getString(cursor.getColumnIndexOrThrow("entity_id"));
                config.token = cursor.getString(cursor.getColumnIndexOrThrow("token"));
                config.method = cursor.getString(cursor.getColumnIndexOrThrow("phuong_thuc"));
                config.timeoutMs = cursor.getInt(cursor.getColumnIndexOrThrow("timeout_ms"));
                config.status = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai"));
                config.lastError = cursor.getString(cursor.getColumnIndexOrThrow("loi_cuoi"));
                config.autoOff = cursor.getInt(cursor.getColumnIndexOrThrow("tu_dong_tat")) != 0;
                config.graceMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("phut_tre"));
                result.add(config);
            }
        }
        return result;
    }

    public long saveLightConfig(LightConfig config) {
        ContentValues values = new ContentValues();
        values.put("ma_san", config.courtId);
        values.put("loai", config.type);
        values.put("url_bat", config.onUrl);
        values.put("url_tat", config.offUrl);
        values.put("entity_id", config.entityId);
        values.put("token", config.token);
        values.put("phuong_thuc", config.method);
        values.put("timeout_ms", config.timeoutMs);
        values.put("trang_thai", config.status);
        values.put("loi_cuoi", config.lastError);
        values.put("tu_dong_tat", config.autoOff ? 1 : 0);
        values.put("phut_tre", Math.max(0, config.graceMinutes));
        return getWritableDatabase().insertWithOnConflict(T_DEN, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public int updateLightStatus(int courtId, String status, String error) {
        ContentValues values = new ContentValues();
        values.put("trang_thai", status);
        values.put("loi_cuoi", error);
        return getWritableDatabase().update(T_DEN, values, "ma_san = ?",
                new String[]{String.valueOf(courtId)});
    }

    public RevenueSummary getRevenueSummary(String fromDate, String toDate) {
        RevenueSummary summary = new RevenueSummary();
        String[] args = new String[]{fromDate, toDate};
        String paidSession = "trang_thai = ? AND trang_thai_tt = ?"
                + " AND substr(bat_dau, 1, 10) >= ? AND substr(bat_dau, 1, 10) <= ?";
        String[] paidSessionArgs = new String[]{CourtSession.STATUS_FINISHED,
                CourtSession.PAYMENT_PAID, fromDate, toDate};
        summary.court = queryInt("SELECT COALESCE(SUM(tien_san - giam_san), 0) FROM "
                + T_PHIEN + " WHERE " + paidSession, paidSessionArgs);
        summary.discount = queryInt("SELECT COALESCE(SUM(tien_giam), 0) FROM "
                + T_PHIEN + " WHERE " + paidSession, paidSessionArgs);
        summary.service = queryInt("SELECT COALESCE(SUM(tien_dich_vu - giam_dich_vu), 0)"
                + " FROM " + T_PHIEN + " WHERE " + paidSession, paidSessionArgs);
        summary.rental = queryInt("SELECT COALESCE(SUM(tien_thue + phi_qua_han"
                + " + tien_boi_thuong), 0) FROM " + T_PHIEU_THUE
                + " WHERE trang_thai = ? AND substr(ngay_thue, 1, 10) >= ?"
                + " AND substr(ngay_thue, 1, 10) <= ?",
                new String[]{Rental.STATUS_RETURNED, fromDate, toDate});
        summary.classFee = queryInt("SELECT COALESCE(SUM(hoc_phi - tien_giam), 0) FROM "
                + T_DK_LOP + " WHERE trang_thai = ? AND da_thanh_toan = 1"
                + " AND substr(ngay_dang_ky, 1, 10) >= ? AND substr(ngay_dang_ky, 1, 10) <= ?",
                new String[]{ClassEnrollment.STATUS_ACTIVE, fromDate, toDate});
        summary.eventFee = queryInt("SELECT COALESCE(SUM(le_phi - tien_giam), 0) FROM "
                + T_DK_SK + " WHERE trang_thai = ? AND da_thanh_toan = 1"
                + " AND substr(ngay_dang_ky, 1, 10) >= ? AND substr(ngay_dang_ky, 1, 10) <= ?",
                new String[]{EventRegistration.STATUS_ACTIVE, fromDate, toDate});
        summary.total = summary.court + summary.service + summary.rental
                + summary.classFee + summary.eventFee;
        summary.receivable = getReceivable(fromDate, toDate);
        summary.depositsHeld = getDepositsHeld();
        return summary;
    }

    public int getReceivable(String fromDate, String toDate) {
        int sessions = queryInt("SELECT COALESCE(SUM(tong_thanh_toan), 0) FROM " + T_PHIEN
                + " WHERE trang_thai = ? AND trang_thai_tt = ?"
                + " AND substr(bat_dau, 1, 10) >= ? AND substr(bat_dau, 1, 10) <= ?",
                new String[]{CourtSession.STATUS_FINISHED,
                        CourtSession.PAYMENT_UNPAID, fromDate, toDate});
        int classes = queryInt("SELECT COALESCE(SUM(hoc_phi - tien_giam), 0) FROM " + T_DK_LOP
                + " WHERE trang_thai = ? AND da_thanh_toan = 0"
                + " AND substr(ngay_dang_ky, 1, 10) >= ? AND substr(ngay_dang_ky, 1, 10) <= ?",
                new String[]{ClassEnrollment.STATUS_ACTIVE, fromDate, toDate});
        int events = queryInt("SELECT COALESCE(SUM(le_phi - tien_giam), 0) FROM " + T_DK_SK
                + " WHERE trang_thai = ? AND da_thanh_toan = 0"
                + " AND substr(ngay_dang_ky, 1, 10) >= ? AND substr(ngay_dang_ky, 1, 10) <= ?",
                new String[]{EventRegistration.STATUS_ACTIVE, fromDate, toDate});
        return sessions + classes + events;
    }

    public int getReceivable() {
        return getReceivable("0000-01-01", "9999-12-31");
    }

    public int getDepositsHeld() {
        int held = queryInt("SELECT COALESCE(SUM(CASE"
                + " WHEN loai_giao_dich = 'COC' THEN so_tien"
                + " WHEN loai_giao_dich IN ('HOAN_COC','DIEU_CHINH') THEN -so_tien"
                + " ELSE 0 END), 0) FROM " + T_THANH_TOAN
                + " WHERE loai_giao_dich IN ('COC','HOAN_COC','DIEU_CHINH')", null);
        return Math.max(0, held);
    }

    public long recordPayment(String targetType, int targetId, String transactionType,
                              int amount, String method, String transactionId,
                              String time, String note) {
        ContentValues values = new ContentValues();
        values.put("loai_doi_tuong", targetType);
        values.put("ma_doi_tuong", targetId);
        values.put("loai_giao_dich", transactionType);
        values.put("so_tien", Math.max(0, amount));
        values.put("phuong_thuc", method == null ? CourtSession.METHOD_CASH : method);
        values.put("ma_giao_dich", transactionId);
        values.put("thoi_gian", time == null ? DateTimeUtils.nowDbDateTime() : time);
        values.put("ghi_chu", note);
        return getWritableDatabase().insert(T_THANH_TOAN, null, values);
    }

    public List<Payment> getPaymentsFor(String targetType, int targetId) {
        List<Payment> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(T_THANH_TOAN, null,
                "loai_doi_tuong = ? AND ma_doi_tuong = ?",
                new String[]{targetType, String.valueOf(targetId)},
                null, null, "thoi_gian DESC, ma_tt DESC")) {
            while (cursor.moveToNext()) {
                Payment payment = new Payment();
                payment.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_tt"));
                payment.targetType = cursor.getString(
                        cursor.getColumnIndexOrThrow("loai_doi_tuong"));
                payment.targetId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_doi_tuong"));
                payment.transactionType = cursor.getString(
                        cursor.getColumnIndexOrThrow("loai_giao_dich"));
                payment.amount = cursor.getInt(cursor.getColumnIndexOrThrow("so_tien"));
                payment.method = cursor.getString(cursor.getColumnIndexOrThrow("phuong_thuc"));
                payment.transactionId = cursor.getString(
                        cursor.getColumnIndexOrThrow("ma_giao_dich"));
                payment.time = cursor.getString(cursor.getColumnIndexOrThrow("thoi_gian"));
                payment.note = cursor.getString(cursor.getColumnIndexOrThrow("ghi_chu"));
                result.add(payment);
            }
        }
        return result;
    }

    public List<Payment> getRecentPayments(int limit) {
        List<Payment> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(T_THANH_TOAN, null, null, null,
                null, null, "thoi_gian DESC, ma_tt DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) {
                result.add(readPayment(cursor));
            }
        }
        return result;
    }

    private Payment readPayment(Cursor cursor) {
        Payment payment = new Payment();
        payment.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_tt"));
        payment.targetType = cursor.getString(
                cursor.getColumnIndexOrThrow("loai_doi_tuong"));
        payment.targetId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_doi_tuong"));
        payment.transactionType = cursor.getString(
                cursor.getColumnIndexOrThrow("loai_giao_dich"));
        payment.amount = cursor.getInt(cursor.getColumnIndexOrThrow("so_tien"));
        payment.method = cursor.getString(cursor.getColumnIndexOrThrow("phuong_thuc"));
        payment.transactionId = cursor.getString(
                cursor.getColumnIndexOrThrow("ma_giao_dich"));
        payment.time = cursor.getString(cursor.getColumnIndexOrThrow("thoi_gian"));
        payment.note = cursor.getString(cursor.getColumnIndexOrThrow("ghi_chu"));
        return payment;
    }

    public void logActivity(String action, String target, Integer targetId,
                            String oldValue, String newValue, String result) {
        ContentValues values = new ContentValues();
        values.put("thoi_gian", DateTimeUtils.nowDbDateTime());
        values.put("loai_thao_tac", action);
        values.put("doi_tuong", target);
        values.put("ma_doi_tuong", targetId);
        values.put("gia_tri_cu", oldValue);
        values.put("gia_tri_moi", newValue);
        values.put("ket_qua", result);
        values.put("nguoi_thuc_hien", com.example.courtmatepickleballcourtmanager.security.AuthSession.currentUsername());
        try {
            getWritableDatabase().insert(T_NHAT_KY, null, values);
        } catch (Exception ignored) {
            // Nhật ký không được làm hỏng nghiệp vụ chính.
        }
    }

    public List<ActivityLog> getRecentActivities(int limit) {
        List<ActivityLog> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(T_NHAT_KY, null, null, null,
                null, null, "thoi_gian DESC, ma_nk DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) {
                ActivityLog log = new ActivityLog();
                log.id = cursor.getInt(cursor.getColumnIndexOrThrow("ma_nk"));
                log.time = cursor.getString(cursor.getColumnIndexOrThrow("thoi_gian"));
                log.action = cursor.getString(cursor.getColumnIndexOrThrow("loai_thao_tac"));
                log.target = cursor.getString(cursor.getColumnIndexOrThrow("doi_tuong"));
                log.targetId = cursor.getInt(cursor.getColumnIndexOrThrow("ma_doi_tuong"));
                log.oldValue = cursor.getString(cursor.getColumnIndexOrThrow("gia_tri_cu"));
                log.newValue = cursor.getString(cursor.getColumnIndexOrThrow("gia_tri_moi"));
                log.result = cursor.getString(cursor.getColumnIndexOrThrow("ket_qua"));
                log.actor = cursor.getString(cursor.getColumnIndexOrThrow("nguoi_thuc_hien"));
                result.add(log);
            }
        }
        return result;
    }

    public int sumSessionRevenue(String fromDate, String toDate) {
        return queryInt("SELECT COALESCE(SUM(tong_thanh_toan), 0) FROM " + T_PHIEN
                + " WHERE trang_thai = ? AND trang_thai_tt = ?"
                + " AND substr(bat_dau, 1, 10) >= ? AND substr(bat_dau, 1, 10) <= ?",
                new String[]{CourtSession.STATUS_FINISHED,
                        CourtSession.PAYMENT_PAID, fromDate, toDate});
    }

    public int sumSessionMinutes(String fromDate, String toDate) {
        int total = 0;
        String sql = "SELECT bat_dau, ket_thuc FROM " + T_PHIEN
                + " WHERE trang_thai = ? AND substr(bat_dau, 1, 10) >= ?"
                + " AND substr(bat_dau, 1, 10) <= ?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{
                CourtSession.STATUS_FINISHED, fromDate, toDate})) {
            while (cursor.moveToNext()) {
                String start = cursor.getString(0);
                String end = cursor.getString(1);
                total += DateTimeUtils.minutesBetweenDbDateTime(start, end);
            }
        }
        return total;
    }

    public static class RevenueSummary {
        public int court;
        public int service;
        public int rental;
        public int classFee;
        public int eventFee;
        public int discount;
        public int total;
        public int receivable;
        public int depositsHeld;
    }

    private int queryInt(SQLiteDatabase db, String sql, String[] args) {
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    private String queryString(SQLiteDatabase db, String sql, String[] args) {
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return null;
    }

    private int queryInt(String sql, String[] args) {
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, args)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    public static class TopCustomer {
        private final String name;
        private final String phone;
        private final int bookings;
        private final int revenue;

        public TopCustomer(String name, String phone, int bookings, int revenue) {
            this.name = name;
            this.phone = phone;
            this.bookings = bookings;
            this.revenue = revenue;
        }

        public String getName() {
            return name;
        }

        public String getPhone() {
            return phone;
        }

        public int getBookings() {
            return bookings;
        }

        public int getRevenue() {
            return revenue;
        }
    }

    public static int getSchemaVersion() {
        return DB_VERSION;
    }

    public static synchronized void reset(Context context) {
        if (instance != null) {
            instance.close();
            instance = null;
        }
        File dbFile = context.getApplicationContext().getDatabasePath(DB_NAME);
        File quarantine = new File(dbFile.getParentFile(),
                "courtmate_reset_" + System.currentTimeMillis() + ".db");
        if (dbFile.exists() && !dbFile.renameTo(quarantine)) {
            removeWithSiblings(dbFile);
        }
        deleteQuietly(new File(dbFile.getPath() + "-wal"));
        deleteQuietly(new File(dbFile.getPath() + "-shm"));
    }

    private static void prepareForUpgrade(Context context) {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (!dbFile.exists() || dbFile.length() == 0) {
            return;
        }
        SQLiteDatabase probe = null;
        try {
            probe = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READWRITE);
            int version = probe.getVersion();
            probe.close();
            probe = null;
            if (version > 0 && version < DB_VERSION) {
                File backup = new File(dbFile.getParentFile(), "courtmate_pre_upgrade.db");
                removeWithSiblings(backup);
                copyWithSiblings(dbFile, backup);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Database unreadable, quarantining", e);
            quarantine(dbFile);
        } finally {
            if (probe != null) {
                try {
                    probe.close();
                } catch (RuntimeException ignored) {
                    // ignore
                }
            }
        }
    }

    private static void quarantine(File dbFile) {
        File target = new File(dbFile.getParentFile(),
                "courtmate_corrupt_" + System.currentTimeMillis() + ".db");
        boolean moved = dbFile.renameTo(target);
        if (!moved) {
            Log.e(TAG, "Cannot quarantine " + dbFile.getAbsolutePath());
        }
        deleteQuietly(new File(dbFile.getPath() + "-wal"));
        deleteQuietly(new File(dbFile.getPath() + "-shm"));
    }

    private static void copyWithSiblings(File source, File target) {
        try {
            copyFile(source, target);
            File wal = new File(source.getPath() + "-wal");
            if (wal.exists()) {
                copyFile(wal, new File(target.getPath() + "-wal"));
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot back up database before upgrade", e);
        }
    }

    private static void removeWithSiblings(File file) {
        deleteQuietly(file);
        deleteQuietly(new File(file.getPath() + "-wal"));
        deleteQuietly(new File(file.getPath() + "-shm"));
    }

    private static void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Cannot delete " + file.getAbsolutePath());
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent.getAbsolutePath());
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    public boolean backupDatabase(File target) {
        File dbFile = context.getDatabasePath(DB_NAME);
        try {
            SQLiteDatabase db = getWritableDatabase();
            try (Cursor cursor = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)) {
                cursor.moveToFirst();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot checkpoint database before backup", e);
        }
        close();
        if (!dbFile.exists()) {
            return false;
        }
        try {
            copyFile(dbFile, target);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Cannot back up database", e);
            return false;
        }
    }

    public static int readDatabaseVersion(File file) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            return db.getVersion();
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot read database version", e);
            return -1;
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (RuntimeException ignored) {
                    // ignore
                }
            }
        }
    }

    public static boolean hasRequiredTables(File file) {
        SQLiteDatabase db = null;
        String[] required = {T_SAN, T_KH, T_DAT, T_DV};
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            for (String table : required) {
                try (Cursor cursor = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                        new String[]{table})) {
                    if (!cursor.moveToFirst()) {
                        return false;
                    }
                }
            }
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot inspect database", e);
            return false;
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (RuntimeException ignored) {
                    // ignore
                }
            }
        }
    }

    public static class PeakHour {
        private final String hour;
        private final int bookings;

        public PeakHour(String hour, int bookings) {
            this.hour = hour;
            this.bookings = bookings;
        }

        public String getHour() {
            return hour;
        }

        public int getBookings() {
            return bookings;
        }
    }

    public static class CourtStat {
        private final String name;
        private final int bookings;
        private final int revenue;

        public CourtStat(String name, int bookings, int revenue) {
            this.name = name;
            this.bookings = bookings;
            this.revenue = revenue;
        }

        public String getName() {
            return name;
        }

        public int getBookings() {
            return bookings;
        }

        public int getRevenue() {
            return revenue;
        }
    }

    public static class DayRevenue {
        private final String date;
        private final int revenue;

        public DayRevenue(String date, int revenue) {
            this.date = date;
            this.revenue = revenue;
        }

        public String getDate() {
            return date;
        }

        public int getRevenue() {
            return revenue;
        }
    }
}
