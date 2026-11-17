package com.example.courtmatepickleballcourtmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.courtmatepickleballcourtmanager.adapter.CalendarDayAdapter;
import com.example.courtmatepickleballcourtmanager.data.DatabaseHelper;
import com.example.courtmatepickleballcourtmanager.util.DateTimeUtils;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CalendarViewActivity extends ProtectedActivity implements CalendarDayAdapter.OnDayClickListener {

    private DatabaseHelper db;
    private Calendar calendar;
    private TextView tvMonthTitle;
    private RecyclerView rvCalendar;
    private CalendarDayAdapter adapter;
    private final List<CalendarDayAdapter.DayItem> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
        setContentView(R.layout.activity_calendar_view);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.calendar_view);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvMonthTitle = findViewById(R.id.tvMonthTitle);
        rvCalendar = findViewById(R.id.rvCalendar);
        rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        adapter = new CalendarDayAdapter(items, this);
        rvCalendar.setAdapter(adapter);

        calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        findViewById(R.id.fabAddBookingCal).setOnClickListener(v ->
                startActivity(new Intent(this, BookingFormActivity.class)));

        loadCalendar();
    }

    private void loadCalendar() {
        items.clear();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        tvMonthTitle.setText(String.format(Locale.getDefault(), "%02d/%d", month + 1, year));

        Calendar firstDay = (Calendar) calendar.clone();
        int firstDayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK);
        int offset = firstDayOfWeek == Calendar.SUNDAY ? 6 : firstDayOfWeek - 2;

        Calendar cal = (Calendar) firstDay.clone();
        cal.add(Calendar.DAY_OF_MONTH, -offset);

        for (int i = 0; i < 42; i++) {
            CalendarDayAdapter.DayItem item = new CalendarDayAdapter.DayItem();
            item.date = DateTimeUtils.formatDbDate(cal.getTime());
            item.dayNumber = cal.get(Calendar.DAY_OF_MONTH);
            item.isCurrentMonth = cal.get(Calendar.MONTH) == month;
            item.bookingCount = db.searchBookings("", item.date, 0, null).size();
            items.add(item);
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDayClick(String date, int count) {
        if (count == 0) {
            Intent intent = new Intent(this, BookingFormActivity.class);
            startActivity(intent);
            return;
        }
        // Show bookings for this day via main activity filter
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
