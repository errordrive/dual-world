package com.example.dualworld.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.dualworld.R;
import com.example.dualworld.core.VirtualAppModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class ClonedAppAdapter extends RecyclerView.Adapter<ClonedAppAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onLaunchClick(VirtualAppModel app);
        void onDeleteClick(VirtualAppModel app);
        void onRenameClick(VirtualAppModel app);
    }

    private final List<VirtualAppModel> mList = new ArrayList<>();
    private final OnItemClickListener mListener;

    public ClonedAppAdapter(OnItemClickListener listener) {
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cloned_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VirtualAppModel item = mList.get(position);
        holder.txtAppName.setText(item.appName);
        holder.txtPackageName.setText(item.packageName);
        holder.txtUserTag.setText(holder.itemView.getContext().getString(R.string.user_tag, item.userId + 1));

        if (item.icon != null) {
            holder.imgIcon.setImageDrawable(item.icon);
        } else {
            holder.imgIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        holder.btnLaunch.setOnClickListener(v -> mListener.onLaunchClick(item));
        holder.btnDelete.setOnClickListener(v -> mListener.onDeleteClick(item));
        holder.btnRename.setOnClickListener(v -> mListener.onRenameClick(item));
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView txtAppName;
        TextView txtUserTag;
        TextView txtPackageName;
        MaterialButton btnLaunch;
        ImageButton btnDelete;
        ImageButton btnRename;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgIcon);
            txtAppName = itemView.findViewById(R.id.txtAppName);
            txtUserTag = itemView.findViewById(R.id.txtUserTag);
            txtPackageName = itemView.findViewById(R.id.txtPackageName);
            btnLaunch = itemView.findViewById(R.id.btnLaunch);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnRename = itemView.findViewById(R.id.btnRename);
        }
    }
}
