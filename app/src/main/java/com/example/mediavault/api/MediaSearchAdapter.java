package com.example.mediavault.api;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mediavault.R;

import java.util.ArrayList;
import java.util.List;

public class MediaSearchAdapter extends RecyclerView.Adapter<MediaSearchAdapter.ViewHolder> {

    private List<MediaSearchResult> results = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(MediaSearchResult result);
    }

    public MediaSearchAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setResults(List<MediaSearchResult> results) {
        this.results = results;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_api_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaSearchResult result = results.get(position);
        holder.tvTitle.setText(result.getTitle());
        holder.tvType.setText(result.getType());
        holder.tvGenre.setText(result.getGenre());
        holder.tvAuthor.setText(result.getAuthor());

        Glide.with(holder.itemView.getContext())
                .load(result.getImageUrl())
                .placeholder(R.color.grey_200)
                .into(holder.ivCover);

        holder.itemView.setOnClickListener(v -> listener.onItemClick(result));
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvType, tvGenre, tvAuthor;
        ImageView ivCover;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvType = itemView.findViewById(R.id.tv_type);
            tvGenre = itemView.findViewById(R.id.tv_genre);
            tvAuthor = itemView.findViewById(R.id.tv_author);
            ivCover = itemView.findViewById(R.id.iv_cover);
        }
    }
}
