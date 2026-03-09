package com.example.mediavault.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediavault.R;

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
        holder.title.setText(item.getTitle());
        holder.subtitle.setText(item.getSubtitle());
        holder.rating.setText(item.getRating());
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
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
