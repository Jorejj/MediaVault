package com.example.mediavault.ui.home;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.example.mediavault.ui.library.MediaItem;
import com.example.mediavault.utils.ProgressValueUtils;

import java.io.File;
import java.util.List;

public class SpotlightAdapter extends RecyclerView.Adapter<SpotlightAdapter.SpotlightViewHolder> {

    private final List<MediaItem> items;
    private final Context context;

    public SpotlightAdapter(Context context, List<MediaItem> items) {
        this.context = context;
        this.items = items != null ? items : new java.util.ArrayList<>();
    }

    @NonNull
    @Override
    public SpotlightViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_spotlight_card, parent, false);
        return new SpotlightViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SpotlightViewHolder holder, int position) {
        if (items == null || position >= items.size()) return;
        MediaItem item = items.get(position);
        if (item == null) return;

        if (holder.tvTitle != null) holder.tvTitle.setText(item.getTitle() != null ? item.getTitle() : "");
        
        if (holder.tvStatus != null) {
            String status = item.getStatus() != null ? item.getStatus() : "Unknown";
            String unit = item.getUnit() != null ? item.getUnit() : "";
            String formattedProgress = ProgressValueUtils.formatForDisplay(item.getProgress(), unit);
            holder.tvStatus.setText(String.format(java.util.Locale.getDefault(), "%s • %s/%d %s", status, formattedProgress, item.getCapacity(), unit));
        }
        
        if (holder.ivBackground != null) {
            String imagePath = item.getCoverPath();
            if (imagePath != null && !imagePath.isEmpty()) {
                File file = new File(imagePath);
                Object loadSource = file.exists() ? file : imagePath;
                Glide.with(context)
                        .load(loadSource)
                        .centerCrop()
                        .into(holder.ivBackground);
            } else {
                holder.ivBackground.setImageResource(R.drawable.cinematic_bg);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, DescriptionActivity.class);
            intent.putExtra(DescriptionActivity.EXTRA_MEDIA_ID, item.getId());
            context.startActivity(intent);
        });

        // Simplified alert logic for demo
        if (holder.tvAlert != null) {
            int percent = (int) ((item.getProgress() / (float) Math.max(item.getCapacity(), 1)) * 100f);
            holder.tvAlert.setText("Currently active: " + percent + "% complete");
        }
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class SpotlightViewHolder extends RecyclerView.ViewHolder {
        ImageView ivBackground;
        TextView tvLabel, tvTitle, tvStatus, tvAlert;

        public SpotlightViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBackground = itemView.findViewById(R.id.iv_spotlight_bg);
            tvLabel = itemView.findViewById(R.id.tv_spotlight_label);
            tvTitle = itemView.findViewById(R.id.tv_spotlight_title);
            tvStatus = itemView.findViewById(R.id.tv_spotlight_status);
            tvAlert = itemView.findViewById(R.id.tv_progress_alert);
        }
    }
}
