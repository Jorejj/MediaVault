package com.example.mediavault;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying manga pages in vertical scroll.
 * Uses Glide for efficient image loading and caching.
 */
public class MangaPageAdapter extends RecyclerView.Adapter<MangaPageAdapter.PageViewHolder> {
    
    private final Context context;
    private List<String> pageUrls = new ArrayList<>();
    
    public MangaPageAdapter(Context context) {
        this.context = context;
    }
    
    public void setPages(List<String> urls) {
        this.pageUrls = new ArrayList<>(urls);
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_manga_page, parent, false);
        return new PageViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        String pageUrl = pageUrls.get(position);
        
        // Show loading spinner
        holder.progressBar.setVisibility(View.VISIBLE);
        holder.imageView.setVisibility(View.GONE);
        
        // Load image with Glide
        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.color.black)
                .error(android.R.drawable.ic_menu_report_image);
        
        Glide.with(context)
                .load(pageUrl)
                .apply(options)
                .into(holder.imageView);
        
        // Hide spinner when loaded (Glide handles this automatically via placeholder/error)
        holder.progressBar.setVisibility(View.GONE);
        holder.imageView.setVisibility(View.VISIBLE);
    }
    
    @Override
    public int getItemCount() {
        return pageUrls.size();
    }
    
    static class PageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ProgressBar progressBar;
        
        PageViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_manga_page);
            progressBar = itemView.findViewById(R.id.pb_page_loading);
        }
    }
}
