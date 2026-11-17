package com.example.courtmatepickleballcourtmanager.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.courtmatepickleballcourtmanager.R;

import java.util.List;

public class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.DayViewHolder> {

    public interface OnDayClickListener {
        void onDayClick(String date, int count);
    }

    public static class DayItem {
        public String date;
        public int dayNumber;
        public int bookingCount;
        public boolean isCurrentMonth;
    }

    private final List<DayItem> items;
    private final OnDayClickListener listener;

    public CalendarDayAdapter(List<DayItem> items, OnDayClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_day, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        DayItem item = items.get(position);
        Context context = holder.itemView.getContext();
        holder.tvDayNumber.setText(String.valueOf(item.dayNumber));
        if (item.bookingCount > 0) {
            holder.tvBookingCount.setVisibility(View.VISIBLE);
            holder.tvBookingCount.setText(context.getString(R.string.stats_bookings_count, item.bookingCount));
        } else {
            holder.tvBookingCount.setVisibility(View.GONE);
        }
        holder.itemView.setAlpha(item.isCurrentMonth ? 1.0f : 0.4f);
        holder.itemView.setOnClickListener(v -> listener.onDayClick(item.date, item.bookingCount));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayNumber;
        TextView tvBookingCount;

        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayNumber = itemView.findViewById(R.id.tvDayNumber);
            tvBookingCount = itemView.findViewById(R.id.tvBookingCount);
        }
    }
}
