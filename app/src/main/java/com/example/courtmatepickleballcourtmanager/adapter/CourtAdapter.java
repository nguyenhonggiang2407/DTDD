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
import com.example.courtmatepickleballcourtmanager.model.Court;
import com.example.courtmatepickleballcourtmanager.util.MoneyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CourtAdapter extends RecyclerView.Adapter<CourtAdapter.CourtViewHolder> {

    public interface OnCourtClickListener {
        void onCourtClick(Court court);
    }

    private final List<Court> items;
    private final OnCourtClickListener listener;

    public CourtAdapter(List<Court> items, OnCourtClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void replaceAll(List<Court> newItems) {
        List<Court> incoming = new ArrayList<>(newItems);
        List<Court> old = new ArrayList<>(items);
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
                Court a = old.get(oldPosition);
                Court b = incoming.get(newPosition);
                return a.getPricePerHour() == b.getPricePerHour()
                        && Objects.equals(a.getName(), b.getName())
                        && Objects.equals(a.getStatus(), b.getStatus());
            }
        });
        items.clear();
        items.addAll(incoming);
        diff.dispatchUpdatesTo(this);
    }

    public static class CourtViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAvatar;
        final TextView tvName;
        final TextView tvStatus;
        final TextView tvPrice;

        CourtViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvCourtAvatar);
            tvName = itemView.findViewById(R.id.tvCourtName);
            tvStatus = itemView.findViewById(R.id.tvCourtStatus);
            tvPrice = itemView.findViewById(R.id.tvCourtPrice);
        }
    }

    @NonNull
    @Override
    public CourtViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_court, parent, false);
        return new CourtViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CourtViewHolder holder, int position) {
        Court court = items.get(position);
        Context context = holder.itemView.getContext();

        holder.tvName.setText(court.getName());
        holder.tvPrice.setText(context.getString(R.string.price_per_hour,
                MoneyUtils.format(court.getPricePerHour())));
        String name = court.getName() == null ? "" : court.getName().trim();
        holder.tvAvatar.setText(name.isEmpty()
                ? "" : name.substring(0, 1).toUpperCase(Locale.getDefault()));

        if (court.isActive()) {
            holder.tvStatus.setText(R.string.status_active);
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_active);
            holder.tvStatus.setTextColor(ContextCompat.getColor(context,
                    R.color.status_active_text));
        } else {
            holder.tvStatus.setText(R.string.status_maintenance);
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_maintenance);
            holder.tvStatus.setTextColor(ContextCompat.getColor(context,
                    R.color.status_maintenance_text));
        }

        holder.itemView.setOnClickListener(v -> listener.onCourtClick(court));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
