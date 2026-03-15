package com.example.mediavault.ui.library;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.List;

import android.content.Context;
import android.content.DialogInterface;
import androidx.appcompat.widget.PopupMenu;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {

    private List<MediaItem> mediaItems;
    private OnItemClickListener listener;
    private DatabaseHelper dbHelper;

    public interface OnItemClickListener {
        void onItemClick(MediaItem item);
    }

    public MediaAdapter(List<MediaItem> mediaItems) {
        this.mediaItems = mediaItems;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void updateList(List<MediaItem> newList) {
        this.mediaItems = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_media_card, parent, false);
        if (dbHelper == null) {
            dbHelper = new DatabaseHelper(parent.getContext());
        }
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        MediaItem item = mediaItems.get(position);
        if (holder.title != null) holder.title.setText(item.getTitle() != null ? item.getTitle() : "Unknown");
        if (holder.subtitle != null) holder.subtitle.setText(item.getSubtitle() != null ? item.getSubtitle() : "");
        if (holder.rating != null) holder.rating.setText("★ " + item.getRatingValue());
        if (holder.progressBar != null) {
            int capacity = Math.max(item.getCapacity(), 0);
            int progress = Math.min(Math.max(item.getProgress(), 0), capacity > 0 ? capacity : 0);
            holder.progressBar.setMax(capacity > 0 ? capacity : 1);
            holder.progressBar.setProgress(progress);
            if (holder.progressText != null) {
                holder.progressText.setText(progress + "/" + (capacity > 0 ? capacity : 0));
            }
        }
        styleCardByMediaType(holder, item);

        if (item.getCoverPath() != null && !item.getCoverPath().isEmpty()) {
            File imageFile = new File(item.getCoverPath());
            if (imageFile.exists()) {
                Glide.with(holder.poster.getContext())
                        .load(imageFile)
                        .centerCrop()
                        .placeholder(R.color.grey_300)
                        .into(holder.poster);
            } else {
                Glide.with(holder.poster.getContext())
                        .load(item.getCoverPath()) // Try as URL/URI if not a direct file
                        .centerCrop()
                        .placeholder(R.color.grey_300)
                        .into(holder.poster);
            }
        } else {
            holder.poster.setImageResource(R.color.grey_300);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), DescriptionActivity.class);
            intent.putExtra(DescriptionActivity.EXTRA_MEDIA_ID, item.getId());
            v.getContext().startActivity(intent);
        });

        if (holder.btnMoreOptions != null) {
            holder.btnMoreOptions.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(v.getContext(), holder.btnMoreOptions);
                
                if ("Recently Deleted".equals(item.getStatus())) {
                    popup.getMenu().add(0, 1, 0, "Recover to Planning");
                    popup.getMenu().add(0, 2, 1, "Delete Permanently");
                } else {
                    popup.getMenu().add(0, 3, 0, "Delete");
                }

                popup.setOnMenuItemClickListener(menuItem -> {
                    Context context = v.getContext();
                    switch (menuItem.getItemId()) {
                        case 1: // Recover
                            dbHelper.updateProgress(item.getId(), item.getProgress(), "Planning", item.getRatingValue());
                            ToastUtils.showCustomToast(context, "Recovered to Planning");
                            item.setStatus("Planning");
                            updateList(mediaItems); // Refresh list
                            return true;
                        case 2: // Permanently Delete
                            new MaterialAlertDialogBuilder(context)
                                .setTitle("Delete Permanently?")
                                .setMessage("This action cannot be undone.")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    dbHelper.deleteMedia(item.getId());
                                    mediaItems.remove(position);
                                    notifyItemRemoved(position);
                                    ToastUtils.showCustomToast(context, "Permanently deleted");
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                            return true;
                        case 3: // Move to Trash
                            new MaterialAlertDialogBuilder(context)
                                .setTitle("Delete " + item.getTitle() + "?")
                                .setMessage("It will be moved to Recently Deleted.")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    dbHelper.updateProgress(item.getId(), item.getProgress(), "Recently Deleted", item.getRatingValue());
                                    mediaItems.remove(position);
                                    notifyItemRemoved(position);
                                    ToastUtils.showCustomToast(context, "Moved to Recently Deleted");
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                            return true;
                        default:
                            return false;
                    }
                });
                popup.show();
            });
        }
    }
    @Override
    public int getItemCount() {
        return mediaItems.size();
    }

    private void styleCardByMediaType(@NonNull MediaViewHolder holder, @NonNull MediaItem item) {
        int accentColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.netflix_red);
        String type = item.getType() != null ? item.getType() : "";
        if ("Anime".equalsIgnoreCase(type) || "Manga".equalsIgnoreCase(type)) {
            accentColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.crunchy_orange);
        } else if ("Book".equalsIgnoreCase(type)) {
            accentColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.bookly_blue);
        }

        if ("Completed".equalsIgnoreCase(item.getStatus())) {
            accentColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.grey_600);
        }

        if (holder.card != null) {
            holder.card.setStrokeColor(accentColor);
        }
        if (holder.subtitle != null) {
            holder.subtitle.setTextColor(accentColor);
        }
        if (holder.rating != null) {
            holder.rating.setTextColor(Color.WHITE);
        }
    }

    static class MediaViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView poster;
        ImageView btnMoreOptions;
        TextView title;
        TextView subtitle;
        TextView rating;
        ProgressBar progressBar;
        TextView progressText;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_media);
            poster = itemView.findViewById(R.id.media_poster);
            btnMoreOptions = itemView.findViewById(R.id.btn_more_options);
            title = itemView.findViewById(R.id.media_title);
            subtitle = itemView.findViewById(R.id.media_subtitle);
            rating = itemView.findViewById(R.id.media_rating);
            progressBar = itemView.findViewById(R.id.media_progress);
            progressText = itemView.findViewById(R.id.media_progress_text);
        }
    }
}
