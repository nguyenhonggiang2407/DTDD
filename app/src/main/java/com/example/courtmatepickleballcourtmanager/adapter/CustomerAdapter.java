package com.example.courtmatepickleballcourtmanager.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.courtmatepickleballcourtmanager.R;
import com.example.courtmatepickleballcourtmanager.model.Customer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder> {

    public interface OnCustomerClickListener {
        void onCustomerClick(Customer customer);
    }

    private final List<Customer> items;
    private final OnCustomerClickListener listener;

    public CustomerAdapter(List<Customer> items, OnCustomerClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void replaceAll(List<Customer> newItems) {
        List<Customer> incoming = new ArrayList<>(newItems);
        List<Customer> old = new ArrayList<>(items);
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
                Customer a = old.get(oldPosition);
                Customer b = incoming.get(newPosition);
                return Objects.equals(a.getName(), b.getName())
                        && Objects.equals(a.getPhone(), b.getPhone());
            }
        });
        items.clear();
        items.addAll(incoming);
        diff.dispatchUpdatesTo(this);
    }

    public static class CustomerViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAvatar;
        final TextView tvName;
        final TextView tvPhone;

        CustomerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvCustomerAvatar);
            tvName = itemView.findViewById(R.id.tvCustomerName);
            tvPhone = itemView.findViewById(R.id.tvCustomerPhone);
        }
    }

    @NonNull
    @Override
    public CustomerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false);
        return new CustomerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CustomerViewHolder holder, int position) {
        Customer customer = items.get(position);
        holder.tvName.setText(customer.getName());
        holder.tvPhone.setText(customer.getPhone());
        String name = customer.getName() == null ? "" : customer.getName().trim();
        holder.tvAvatar.setText(name.isEmpty()
                ? "" : name.substring(0, 1).toUpperCase(Locale.getDefault()));
        holder.itemView.setOnClickListener(v -> listener.onCustomerClick(customer));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
