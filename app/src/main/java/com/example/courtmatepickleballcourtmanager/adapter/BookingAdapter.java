package com.example.courtmatepickleballcourtmanager.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.courtmatepickleballcourtmanager.R;
import com.example.courtmatepickleballcourtmanager.model.Booking;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

    public interface OnBookingClickListener {
        void onBookingClick(Booking booking);
    }

    private final List<Booking> items;
    private final OnBookingClickListener listener;

    public BookingAdapter(List<Booking> items, OnBookingClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void replaceAll(List<Booking> newItems) {
        List<Booking> incoming = new ArrayList<>(newItems);
        List<Booking> old = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return old.size();
            }

            @Override
            public int getNewListSize() {
                return incoming.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return old.get(oldPosition).getId() == incoming.get(newPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Booking a = old.get(oldPosition);
                Booking b = incoming.get(newPosition);
                return a.getCourtId() == b.getCourtId()
                        && a.getTotalAmount() == b.getTotalAmount()
                        && Objects.equals(a.getDate(), b.getDate())
                        && Objects.equals(a.getStartTime(), b.getStartTime())
                        && Objects.equals(a.getEndTime(), b.getEndTime())
                        && Objects.equals(a.getStatus(), b.getStatus())
                        && Objects.equals(a.getNote(), b.getNote())
                        && Objects.equals(a.getCourtName(), b.getCourtName())
                        && Objects.equals(a.getCustomerName(), b.getCustomerName())
                        && Objects.equals(a.getCustomerPhone(), b.getCustomerPhone());
            }
        });
        items.clear();
        items.addAll(incoming);
        diff.dispatchUpdatesTo(this);
    }

    public static class BookingViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDay;
        final TextView tvMonth;
        final TextView tvCourt;
        final TextView tvStatus;
        final TextView tvCustomer;
        final TextView tvDateTime;
        final TextView tvTotal;

        BookingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tvDay);
            tvMonth = itemView.findViewById(R.id.tvMonth);
            tvCourt = itemView.findViewById(R.id.tvCourt);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvCustomer = itemView.findViewById(R.id.tvCustomer);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvTotal = itemView.findViewById(R.id.tvTotal);
        }
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_booking, parent, false);
        return new BookingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        Booking booking = items.get(position);
        Context context = holder.itemView.getContext();

        String date = booking.getDate() == null ? "" : booking.getDate();
        if (date.length() >= 10) {
            holder.tvDay.setText(date.substring(8, 10));
            holder.tvMonth.setText(context.getString(R.string.month_year_short,
                    date.substring(5, 7), date.substring(0, 4)));
        } else {
            holder.tvDay.setText("");
            holder.tvMonth.setText("");
        }

        holder.tvCourt.setText(booking.getCourtName());
        holder.tvCustomer.setText(context.getString(R.string.customer_line,
                booking.getCustomerName(), booking.getCustomerPhone()));
        holder.tvDateTime.setText(context.getString(R.string.time_range,
                booking.getStartTime(), booking.getEndTime()));
        holder.tvTotal.setText(MoneyUtils.format(booking.getTotalAmount()));

        if (booking.isCancelled()) {
            holder.tvStatus.setText(R.string.status_cancelled);
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_cancelled);
            holder.tvStatus.setTextColor(ContextCompat.getColor(context,
                    R.color.status_cancelled_text));
            holder.itemView.setAlpha(0.6f);
        } else {
            holder.tvStatus.setText(R.string.status_booked);
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_active);
            holder.tvStatus.setTextColor(ContextCompat.getColor(context,
                    R.color.status_active_text));
            holder.itemView.setAlpha(1f);
        }

        holder.itemView.setOnClickListener(v -> listener.onBookingClick(booking));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
