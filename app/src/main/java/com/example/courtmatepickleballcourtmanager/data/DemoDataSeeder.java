package com.example.courtmatepickleballcourtmanager.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Bộ dữ liệu chỉ dùng trình diễn/kiểm thử. Mọi bản ghi mới đều có khóa hoặc nhãn DEMO.
 * Không thay đổi dữ liệu người dùng hiện có và không tự tạo tài khoản hay điều khiển đèn.
 * Caller quản lý transaction (SQLiteOpenHelper.onCreate hoặc DatabaseHelper.addDemoData).
 */
final class DemoDataSeeder {
    private DemoDataSeeder() { }
    private static final String MARK = "[DEMO]";

    private static ContentValues row(Object... values) {
        ContentValues result = new ContentValues();
        for (int i = 0; i < values.length; i += 2) {
            String key = (String) values[i];
            Object value = values[i + 1];
            if (value == null) result.putNull(key);
            else if (value instanceof Integer) result.put(key, (Integer) value);
            else if (value instanceof Long) result.put(key, (Long) value);
            else result.put(key, String.valueOf(value));
        }
        return result;
    }

    private static long find(SQLiteDatabase db, String table, String id,
                             String key, String value) {
        try (Cursor c = db.query(table, new String[]{id}, key + "=?",
                new String[]{value}, null, null, null, "1")) {
            return c.moveToFirst() ? c.getLong(0) : -1L;
        }
    }

    private static long create(SQLiteDatabase db, String table, String id,
                               String key, String value, ContentValues values, int[] counter) {
        long existing = find(db, table, id, key, value);
        if (existing > 0) return existing;
        long added = db.insertOrThrow(table, null, values);
        counter[0]++;
        return added;
    }

    private static boolean exists(SQLiteDatabase db, String table, String condition, String[] args) {
        try (Cursor c = db.query(table, new String[]{"1"}, condition, args,
                null, null, null, "1")) {
            return c.moveToFirst();
        }
    }

    private static String day(int offset) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, offset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    private static String stamp(int offset, String hm) { return day(offset) + " " + hm; }

    static int populate(SQLiteDatabase db) {
        int[] count = {0};
        // Ít nhất ba KHÁCH DEMO độc lập để dùng thử đăng ký lớp, giải và thuê dụng cụ.
        long[] customers = new long[3];
        String[] people = {"Khách demo • Minh Anh", "Khách demo • Gia Huy", "Khách demo • Thanh Hà"};
        String[] phones = {"0988800101", "0988800102", "0988800103"};
        for (int i = 0; i < 3; i++) {
            customers[i] = create(db, "khach_hang", "ma_kh", "so_dien_thoai", phones[i],
                    row("ho_ten", people[i], "so_dien_thoai", phones[i]), count);
        }
        long[] courts = new long[10];
        for (int i = 0; i < 10; i++) courts[i] = find(db,"san","ma_san","ten_san","Sân " +(i+1));

        // Bán hàng đã có >=3 mục, bổ sung 1 thiết bị để đủ 3 loại cho thuê.
        long shoe = create(db,"dich_vu","ma_dv","ten_dv","Thuê giày Pickleball (Demo)",
                row("ten_dv","Thuê giày Pickleball (Demo)", "loai","THUE", "don_vi","đôi/lượt",
                        "gia",35000, "tien_coc",150000,"phi_qua_han",15000,
                        "so_luong_ton",12,"trang_thai","DANG_BAN"),count);
        long[] rentalServices = {
                find(db,"dich_vu","ma_dv","ten_dv","Thuê vợt"),
                find(db,"dich_vu","ma_dv","ten_dv","Thuê bóng"), shoe};

        String[] coaches = {"HLV Demo • Hải Nam", "HLV Demo • Ngọc Linh", "HLV Demo • Quốc Bảo"};
        long[] coachIds = new long[3];
        for (int i = 0; i < 3; i++) {
            coachIds[i] = create(db,"huan_luyen_vien","ma_hlv","ho_ten",coaches[i],
                    row("ho_ten",coaches[i],"so_dien_thoai","097770010"+(i+1),
                            "chuyen_mon",new String[]{"Cơ bản","Kỹ thuật đôi","Thi đấu"}[i],
                            "kinh_nghiem",(3+i)+" năm (demo)","phi_theo_gio",150000+i*50000,
                            "trang_thai","HOAT_DONG"),count);
        }

        String[] lessons = {"Lớp demo • Làm quen Pickleball",
                "Lớp demo • Đánh đôi nâng cao", "Lớp demo • Chiến thuật thi đấu"};
        for (int i = 0; i < 3; i++) {
            if (coachIds[i] < 0 || courts[i+4] < 0) continue;
            long id = create(db,"lop_hoc","ma_lop","ten_lop",lessons[i],
                    row("ten_lop",lessons[i],"ma_hlv",coachIds[i],"ma_san",courts[i+4],
                            "trinh_do",new String[]{"Mới bắt đầu","Trung cấp","Nâng cao"}[i],
                            "ngay_bat_dau",day(7),"ngay_ket_thuc",day(90),
                            "suc_chua",8+i*2,"hoc_phi",450000+i*150000,
                            "trang_thai","MO_DANG_KY","ghi_chu",MARK+" Lớp mẫu"),count);
            if (!exists(db,"lich_lop","ma_lop=?",new String[]{String.valueOf(id)})) {
                db.insertOrThrow("lich_lop",null,row("ma_lop",id,"thu",i+2,
                        "gio_bat_dau","06:30","gio_ket_thuc","07:30")); count[0]++;
            }
            if (!exists(db,"dang_ky_lop","ma_lop=? AND ma_kh=?",
                    new String[]{String.valueOf(id),String.valueOf(customers[i])})) {
                db.insertOrThrow("dang_ky_lop",null,row("ma_lop",id,"ma_kh",customers[i],
                        "ngay_dang_ky",day(0),"hoc_phi",450000+i*150000,
                        "da_thanh_toan",0,"trang_thai","DANG_KY")); count[0]++;
            }
        }

        String[] events = {"Giải Demo • Pickleball Open",
                "Sự kiện Demo • Giao lưu cuối tuần","Giải Demo • Đôi nam nữ"};
        for (int i=0; i<3; i++) {
            if(courts[i+7]<0)continue;
            long id = create(db,"su_kien","ma_sk","ten_sk",events[i],
                    row("ten_sk",events[i],"loai",i==1?"SU_KIEN":"GIAI_DAU",
                            "mo_ta",MARK+" Sự kiện minh họa","ngay_bat_dau",day(21+i),
                            "ngay_ket_thuc",day(21+i),"han_dang_ky",day(14+i),
                            "le_phi",100000+i*50000,"suc_chua",16+i*8,
                            "trang_thai","DANG_DANG_KY"),count);
            if(!exists(db,"su_kien_san","ma_sk=? AND ma_san=?",
                    new String[]{String.valueOf(id),String.valueOf(courts[i+7])})) {
                db.insertOrThrow("su_kien_san",null,row("ma_sk",id,"ma_san",courts[i+7])); count[0]++;
            }
            if(!exists(db,"lich_su_kien","ma_sk=?",new String[]{String.valueOf(id)})) {
                db.insertOrThrow("lich_su_kien",null,row("ma_sk",id,"ma_san",courts[i+7],
                        "ngay",day(21+i),"gio_bat_dau","13:00","gio_ket_thuc","15:00",
                        "noi_dung",MARK+" Chương trình mẫu")); count[0]++;
            }
            if(!exists(db,"dang_ky_su_kien","ma_sk=? AND ma_kh=?",
                    new String[]{String.valueOf(id),String.valueOf(customers[i])})) {
                db.insertOrThrow("dang_ky_su_kien",null,row("ma_sk",id,"ma_kh",customers[i],
                        "ngay_dang_ky",day(0),"le_phi",100000+i*50000,
                        "da_thanh_toan",0,"trang_thai","DANG_KY")); count[0]++;
            }
        }

        for(int i=0;i<3;i++) {
            String[] titles={"Ưu đãi Demo • Giờ vàng 10%","Ưu đãi Demo • Dịch vụ 15K",
                    "Ưu đãi Demo • Khóa học 5%"};
            String[] codes={"DEMO_GIOVANG","DEMO_DICHVU","DEMO_LOPHOC"};
            create(db,"uu_dai","ma_ud","ma_code",codes[i],
                    row("ten_ud",titles[i],"loai_giam",i==1?"SO_TIEN":"PHAN_TRAM",
                            "gia_tri",new int[]{10,15000,5}[i],
                            "pham_vi",new String[]{"SAN","DICH_VU","LOP_HOC"}[i],
                            "gia_tri_don_toi_thieu",0,"giam_toi_da",i==1?0:50000,
                            "tu_ngay",day(0),"den_ngay",day(90),
                            "ma_code",codes[i],"trang_thai","HOAT_DONG","ghi_chu",MARK+" Mã chỉ dùng thử"),count);
        }

        // Khung vãng lai ở ba sân, một ngày trong tuần mỗi sân; dùng khung chưa trùng.
        for(int i=0;i<3;i++) {
            if(courts[i]<0) continue;
            String[] args={String.valueOf(courts[i]),String.valueOf(i+1),"14:00","16:00"};
            if(!exists(db,"gio_vang_lai","ma_san=? AND thu=? AND gio_bat_dau=? AND gio_ket_thuc=?",args)
                && !exists(db,"gio_vang_lai","ma_san=? AND thu=? AND gio_bat_dau < ? AND gio_ket_thuc > ?",
                    new String[]{args[0],args[1],"16:00","14:00"})) {
                db.insertOrThrow("gio_vang_lai",null,row("ma_san",courts[i],"thu",i+1,
                        "gio_bat_dau","14:00","gio_ket_thuc","16:00", "gia",100000+i*20000,
                        "trang_thai","DANG_AP_DUNG")); count[0]++;
            }
        }

        // Đặt trước cho ba ngày tương lai, kiểm tra lịch sân để không ghi đè dữ liệu khác.
        for(int i=0;i<3;i++) {
            if(courts[i]<0) continue;
            String date=day(31+i);
            String note=MARK+" Đặt sân mẫu "+(i+1);
            if (!exists(db,"dat_lich","ghi_chu=?",new String[]{note})
                && !exists(db,"dat_lich","ma_san=? AND ngay=? AND trang_thai='DA_DAT' AND gio_bat_dau < ? AND gio_ket_thuc > ?",
                    new String[]{String.valueOf(courts[i]),date,"11:30","10:00"})
                && !exists(db,"lich_su_kien","ma_san=? AND ngay=? AND gio_bat_dau < ? AND gio_ket_thuc > ?",
                    new String[]{String.valueOf(courts[i]),date,"11:30","10:00"})) {
                db.insertOrThrow("dat_lich",null,row("ma_san",courts[i],"ma_kh",customers[i],
                        "ngay",date,"gio_bat_dau","10:00","gio_ket_thuc","11:30",
                        "tong_tien",new int[]{180000,225000,270000}[i],
                        "trang_thai","DA_DAT","ghi_chu",note)); count[0]++;
            }
        }

        // Ba phiên ĐÃ KẾT THÚC + hóa đơn + thanh toán mẫu, không tạo phiên đang chơi.
        for(int i=0;i<3;i++) {
            if(courts[i]<0)continue;
            String memo=MARK+" Phiên thu ngân demo "+(i+1);
            if (exists(db,"phien_choi","ghi_chu=?",new String[]{memo})) continue;
            String start=stamp(-30-i,"10:00"), end=stamp(-30-i,"11:30");
            if (exists(db,"phien_choi","ma_san=? AND bat_dau < ? AND (ket_thuc IS NULL OR ket_thuc > ?)",
                    new String[]{String.valueOf(courts[i]),end,start})) continue;
            int fee=180000+i*30000;
            long session=db.insertOrThrow("phien_choi",null,row("ma_san",courts[i],
                    "ma_kh",customers[i],"loai_phien","VANG_LAI","bat_dau",start,"ket_thuc",end,
                    "don_gia_vang_lai",120000+i*20000,"tien_san",fee,
                    "tong_thanh_toan",fee,"trang_thai","DA_KET_THUC",
                    "ma_hoa_don","DEMO-"+(i+1)+"-"+day(-30-i).replace("-",""),
                    "trang_thai_tt","DA_THANH_TOAN","phuong_thuc_tt","TIEN_MAT",
                    "thoi_gian_tt",end,"ghi_chu",memo)); count[0]++;
            db.insertOrThrow("thanh_toan",null,row("loai_doi_tuong","PHIEN",
                    "ma_doi_tuong",session,"loai_giao_dich","THANH_TOAN","so_tien",fee,
                    "phuong_thuc","TIEN_MAT","thoi_gian",end,"ghi_chu",MARK+" Doanh thu giả lập")); count[0]++;
        }

        // Ba phiếu thuê đang mở. Không trừ kho lần hai khi nạp lại.
        for(int i=0;i<3;i++) {
            if(rentalServices[i]<0)continue;
            String note=MARK+" Phiếu thuê demo "+(i+1);
            if(exists(db,"phieu_thue","ghi_chu=?",new String[]{note})) continue;
            long sid=rentalServices[i];
            int deposit=new int[]{200000,50000,150000}[i];
            int price=new int[]{50000,20000,35000}[i];
            int late=new int[]{20000,10000,15000}[i];
            // Bảo toàn tồn kho cho thuê khi tạo một phiếu đang mở.
            // Kiểm tra tồn kho trước khi tạo phiếu trong cùng transaction nạp demo.
            if (!exists(db,"dich_vu","ma_dv=? AND so_luong_ton >= 1",
                    new String[]{String.valueOf(sid)})) continue;
            db.execSQL("UPDATE dich_vu SET so_luong_ton=so_luong_ton-1 WHERE ma_dv=? AND so_luong_ton >= 1",
                    new Object[]{sid});
            long rent=db.insertOrThrow("phieu_thue",null,row("ma_kh",customers[i],
                    "ngay_thue",stamp(0,"09:00"),"hen_tra",stamp(3+i,"19:00"),
                    "tien_coc",deposit,"tien_thue",price,"trang_thai","DANG_THUE","ghi_chu",note)); count[0]++;
            db.insertOrThrow("chi_tiet_thue",null,row("ma_phieu",rent,"ma_dv",sid,
                    "so_luong",1,"don_gia",price,"don_vi","lượt","phi_qua_han",late,
                    "so_luong_da_tra",0,"thanh_tien",price)); count[0]++;
            db.insertOrThrow("thanh_toan",null,row("loai_doi_tuong","THUE","ma_doi_tuong",rent,
                    "loai_giao_dich","COC","so_tien",deposit,"phuong_thuc","TIEN_MAT",
                    "thoi_gian",stamp(0,"09:00"),"ghi_chu",MARK+" Cọc thuê dụng cụ")); count[0]++;
        }
        return count[0];
    }
}
