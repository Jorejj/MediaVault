package com.example.mediavault.ui.library;

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

import java.io.File;
import java.util.List;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {

    private List<MediaItem> mediaItems;
    private OnItemClickListener listener;

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
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        MediaItem item = mediaItems.get(position);
        if (holder.title != null) holder.title.setText(item.getTitle() != null ? item.getTitle() : "Unknown");
        if (holder.subtitle != null) holder.subtitle.setText(item.getSubtitle() != null ? item.getSubtitle() : "");
        if (holder.rating != null) holder.rating.setText("★ " + item.getRatingValue());

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
    }
    @Override
    public int getItemCount() {
        return mediaItems.size();
    }

    static class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView poster;
        TextView title;
        TextView subtitle;
        TextView rating;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.media_poster);
            title = itemView.findViewById(R.id.media_title);
            subtitle = itemView.findViewById(R.id.media_subtitle);
            rating = itemView.findViewById(R.id.media_rating);
        }
    }
}
