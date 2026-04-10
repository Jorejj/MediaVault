package com.example.mediavault;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.io.File;
import java.util.List;

public class ReaderAdapter extends RecyclerView.Adapter<ReaderAdapter.PageViewHolder> {

    private final List<String> pagePaths;
    private final Context context;

    public ReaderAdapter(Context context, List<String> pagePaths) {
        this.context = context;
        this.pagePaths = pagePaths;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_reader_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        String path = pagePaths.get(position);
        
        // OOM Prevention: Downscale large images and use Hardware Bitmaps
        Glide.with(context)
                .load(new File(path))
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache only the processed image
                .override(1080, 1920) // Limit resolution to prevent massive bitmap allocation
                .thumbnail(0.1f) // Show low-res version while loading
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return pagePaths.size();
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        super.onViewRecycled(holder);
        // OOM Prevention: Explicitly clear Glide when the view is recycled to free memory immediately
        Glide.with(context).clear(holder.imageView);
        holder.imageView.setImageDrawable(null);
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_reader_page);
        }
    }
}
