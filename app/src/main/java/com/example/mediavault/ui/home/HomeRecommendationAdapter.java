package com.example.mediavault.ui.home;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mediavault.R;
import com.example.mediavault.ui.library.MediaItem;
import com.example.mediavault.utils.ProgressValueUtils;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeRecommendationAdapter extends RecyclerView.Adapter<HomeRecommendationAdapter.RecommendationViewHolder> {

    public interface Callback {
        void onOpen(@NonNull MediaItem item);
        void onAdd(@NonNull MediaItem item);
    }

    private final Context context;
    private final List<MediaItem> items = new ArrayList<>();
    private final Callback callback;

    public HomeRecommendationAdapter(@NonNull Context context, @NonNull Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void replaceItems(@NonNull List<MediaItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecommendationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_recommendation_card, parent, false);
        return new RecommendationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecommendationViewHolder holder, int position) {
        if (position < 0 || position >= items.size()) return;
        MediaItem item = items.get(position);
        if (item == null) return;

        String title = safe(item.getTitle(), context.getString(R.string.auto_untitled));
        holder.textTitle.setText(title);

        String mediaType = safe(item.getType(), context.getString(R.string.media_type));
        holder.textType.setText(mediaType);

        String meta = buildMeta(item);
        holder.textMeta.setText(meta);

        holder.textProgress.setText(buildProgress(item));

        if (isReadingType(item.getType())) {
            holder.buttonOpen.setText(R.string.auto_read_now);
        } else {
            holder.buttonOpen.setText(R.string.auto_watch_now);
        }

        bindCover(holder.imageCover, item.getCoverPath());

        View.OnClickListener openAction = v -> callback.onOpen(item);
        holder.itemView.setOnClickListener(openAction);
        holder.buttonOpen.setOnClickListener(openAction);
        holder.buttonAdd.setOnClickListener(v -> callback.onAdd(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void bindCover(@NonNull ImageView imageView, String coverPath) {
        if (coverPath == null || coverPath.trim().isEmpty()) {
            imageView.setImageResource(R.drawable.cinematic_bg);
            return;
        }
        File file = new File(coverPath);
        Object source = file.exists() ? file : coverPath;
        Glide.with(context)
                .load(source)
                .centerCrop()
                .placeholder(R.drawable.cinematic_bg)
                .error(R.drawable.cinematic_bg)
                .into(imageView);
    }

    private static String buildMeta(@NonNull MediaItem item) {
        String genre = safe(item.getGenre(), "");
        String status = safe(item.getStatus(), "");
        if (!genre.isEmpty() && !status.isEmpty()) {
            return String.format(Locale.getDefault(), "%s • %s", genre, status);
        }
        if (!genre.isEmpty()) return genre;
        if (!status.isEmpty()) return status;
        return "";
    }

    private static String buildProgress(@NonNull MediaItem item) {
        int current = Math.max(0, Math.round(item.getCurrentProgress()));
        int total = Math.max(0, item.getTotalCount());
        String unit = safe(item.getUnit(), "").trim();
        String formattedCurrent = ProgressValueUtils.formatForDisplay(current, unit);
        if (total > 0) {
            return String.format(Locale.getDefault(), "%s / %d %s", formattedCurrent, total, unit);
        }
        return String.format(Locale.getDefault(), "%s %s", formattedCurrent, unit).trim();
    }

    private static boolean isReadingType(String rawType) {
        String type = safe(rawType, "").toLowerCase(Locale.ROOT);
        return type.contains("book")
                || type.contains("novel")
                || type.contains("manga")
                || type.contains("manhwa")
                || type.contains("webtoon")
                || type.contains("comic");
    }

    private static String safe(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value.trim();
    }

    static class RecommendationViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageCover;
        final TextView textType;
        final TextView textTitle;
        final TextView textMeta;
        final TextView textProgress;
        final MaterialButton buttonOpen;
        final MaterialButton buttonAdd;

        RecommendationViewHolder(@NonNull View itemView) {
            super(itemView);
            imageCover = itemView.findViewById(R.id.image_recommend_cover);
            textType = itemView.findViewById(R.id.text_recommend_type);
            textTitle = itemView.findViewById(R.id.text_recommend_title);
            textMeta = itemView.findViewById(R.id.text_recommend_meta);
            textProgress = itemView.findViewById(R.id.text_recommend_progress);
            buttonOpen = itemView.findViewById(R.id.btn_recommend_open);
            buttonAdd = itemView.findViewById(R.id.btn_recommend_add);
        }
    }
}
