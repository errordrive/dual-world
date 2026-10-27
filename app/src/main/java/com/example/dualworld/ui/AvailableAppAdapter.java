package com.example.dualworld.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.dualworld.R;
import com.example.dualworld.core.VirtualAppModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class AvailableAppAdapter extends RecyclerView.Adapter<AvailableAppAdapter.ViewHolder> {

    public interface OnCloneClickListener {
        void onCloneClick(VirtualAppModel app);
    }

    private final List<VirtualAppModel> mList = new ArrayList<>();
    private final OnCloneClickListener mListener;

    public AvailableAppAdapter(OnCloneClickListener listener) {
        mListener = listener;
    }

    public void updateData(List<VirtualAppModel> list) {
        mList.clear();
        if (list != null) {
            mList.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_available_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VirtualAppModel item = mList.get(position);
        holder.txtAppName.setText(item.appName);
        holder.txtPackageName.setText(item.packageName);

        if (item.icon != null) {
            holder.imgIcon.setImageDrawable(item.icon);
        } else {
            holder.imgIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        holder.btnClone.setOnClickListener(v -> mListener.onCloneClick(item));
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView txtAppName;
        TextView txtPackageName;
        MaterialButton btnClone;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgIcon);
            txtAppName = itemView.findViewById(R.id.txtAppName);
            txtPackageName = itemView.findViewById(R.id.txtPackageName);
            btnClone = itemView.findViewById(R.id.btnClone);
        }
    }
}
