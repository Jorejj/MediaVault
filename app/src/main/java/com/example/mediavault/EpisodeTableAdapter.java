package com.example.mediavault;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediavault.api.providers.MediaProvider;

import java.util.ArrayList;
import java.util.List;

public class EpisodeTableAdapter extends RecyclerView.Adapter<EpisodeTableAdapter.ViewHolder> {
    public interface OnEpisodeClickListener {
        void onEpisodeClick(int episodeNumber);
    }

    private final OnEpisodeClickListener listener;
    private final List<MediaProvider.EpisodeInfo> episodes = new ArrayList<>();
    private int currentEpisode;

    public EpisodeTableAdapter(int currentEpisode, OnEpisodeClickListener listener) {
        this.currentEpisode = currentEpisode;
        this.listener = listener;
    }

    public void setEpisodes(List<MediaProvider.EpisodeInfo> items) {
        episodes.clear();
        if (items != null) {
            episodes.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode_table_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaProvider.EpisodeInfo episode = episodes.get(position);
        int episodeNumber = episode.number;

        holder.tvEpisodeNumber.setText(String.valueOf(episodeNumber));
        holder.tvTitle.setText(
                episode.title == null || episode.title.trim().isEmpty()
                        ? ("Episode " + episodeNumber)
                        : episode.title
        );
        holder.tvStatus.setText(getStatus(episodeNumber));
        holder.itemView.setOnClickListener(v -> listener.onEpisodeClick(episodeNumber));
    }

    @Override
    public int getItemCount() {
        return episodes.size();
    }

    private String getStatus(int episodeNumber) {
        if (episodeNumber < currentEpisode) return "Watched";
        if (episodeNumber == currentEpisode) return "Current";
        if (episodeNumber == currentEpisode + 1) return "Up Next";
        return "Pending";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEpisodeNumber;
        TextView tvTitle;
        TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEpisodeNumber = itemView.findViewById(R.id.tv_row_episode_number);
            tvTitle = itemView.findViewById(R.id.tv_row_episode_title);
            tvStatus = itemView.findViewById(R.id.tv_row_episode_status);
        }
    }
}

