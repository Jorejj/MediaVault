package com.example.mediavault;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying manga pages in vertical scroll.
 * Uses Glide for efficient image loading and caching.
 */
public class MangaPageAdapter extends RecyclerView.Adapter<MangaPageAdapter.PageViewHolder> {
    private static final long PAGE_LOAD_TIMEOUT_MS = 15000L;
    private static final int REQUEST_TIMEOUT_MS = 15000;

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

        holder.boundUrl = pageUrl;
        if (holder.timeoutRunnable != null) {
            holder.itemView.removeCallbacks(holder.timeoutRunnable);
            holder.timeoutRunnable = null;
        }
        Glide.with(holder.imageView.getContext()).clear(holder.imageView);

        // Show loading spinner
        holder.progressBar.setVisibility(View.VISIBLE);
        holder.imageView.setVisibility(View.GONE);

        Object loadModel = buildLoadModel(pageUrl);

        // Load image with Glide
        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .timeout(REQUEST_TIMEOUT_MS)
                .placeholder(android.R.color.black)
                .error(android.R.drawable.ic_menu_report_image);

        final String requestUrl = pageUrl;
        holder.timeoutRunnable = () -> {
            if (!requestUrl.equals(holder.boundUrl)) {
                return;
            }
            holder.progressBar.setVisibility(View.GONE);
            holder.imageView.setVisibility(View.VISIBLE);
            holder.imageView.setImageResource(android.R.drawable.ic_menu_report_image);
        };
        holder.itemView.postDelayed(holder.timeoutRunnable, PAGE_LOAD_TIMEOUT_MS);

        Glide.with(context)
                .load(loadModel)
                .apply(options)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        finishLoad(holder, requestUrl);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        finishLoad(holder, requestUrl);
                        return false;
                    }
                })
                .into(holder.imageView);
    }

    private void finishLoad(@NonNull PageViewHolder holder, @NonNull String requestUrl) {
        if (!requestUrl.equals(holder.boundUrl)) {
            return;
        }
        if (holder.timeoutRunnable != null) {
            holder.itemView.removeCallbacks(holder.timeoutRunnable);
            holder.timeoutRunnable = null;
        }
        holder.progressBar.setVisibility(View.GONE);
        holder.imageView.setVisibility(View.VISIBLE);
    }

    private Object buildLoadModel(@Nullable String pageUrl) {
        if (pageUrl == null) {
            return "";
        }
        String normalized = pageUrl.toLowerCase(Locale.US);
        if (normalized.contains("mangadex")) {
            return new GlideUrl(
                    pageUrl,
                    new LazyHeaders.Builder()
                            .addHeader("Referer", "https://mangadex.org/")
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; MediaVault)")
                            .build()
            );
        }
        return pageUrl;
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        if (holder.timeoutRunnable != null) {
            holder.itemView.removeCallbacks(holder.timeoutRunnable);
            holder.timeoutRunnable = null;
        }
        holder.boundUrl = null;
        Glide.with(holder.imageView.getContext()).clear(holder.imageView);
        super.onViewRecycled(holder);
    }
    
    @Override
    public int getItemCount() {
        return pageUrls.size();
    }
    
    static class PageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ProgressBar progressBar;
        String boundUrl;
        Runnable timeoutRunnable;

        PageViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_manga_page);
            progressBar = itemView.findViewById(R.id.pb_page_loading);
        }
    }
}
