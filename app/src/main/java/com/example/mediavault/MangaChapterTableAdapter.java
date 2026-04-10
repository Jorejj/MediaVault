package com.example.mediavault;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediavault.api.providers.MediaProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MangaChapterTableAdapter extends RecyclerView.Adapter<MangaChapterTableAdapter.ViewHolder> {
    public interface OnChapterClickListener {
        void onChapterClick(int chapterNumber);
    }

    private final List<MediaProvider.ChapterInfo> chapters = new ArrayList<>();
    private final OnChapterClickListener listener;
    private int currentChapter;

    public MangaChapterTableAdapter(int currentChapter, OnChapterClickListener listener) {
        this.currentChapter = currentChapter;
        this.listener = listener;
    }

    public void setChapters(List<MediaProvider.ChapterInfo> items) {
        chapters.clear();
        if (items != null) {
            chapters.addAll(items);
            chapters.sort(Comparator.comparingDouble(c -> c.number));
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manga_chapter_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaProvider.ChapterInfo chapter = chapters.get(position);
        int chapterNumber = Math.max(1, Math.round(chapter.number));
        holder.tvChapterNumber.setText(String.valueOf(chapterNumber));
        holder.tvChapterTitle.setText(
                chapter.title == null || chapter.title.trim().isEmpty()
                        ? ("Chapter " + chapterNumber)
                        : chapter.title
        );
        holder.tvStatus.setText(getStatus(chapterNumber));
        holder.itemView.setOnClickListener(v -> listener.onChapterClick(chapterNumber));
    }

    @Override
    public int getItemCount() {
        return chapters.size();
    }

    private String getStatus(int chapterNumber) {
        if (chapterNumber < currentChapter) return "Read";
        if (chapterNumber == currentChapter) return "Current";
        if (chapterNumber == currentChapter + 1) return "Up Next";
        return "Pending";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvChapterNumber;
        TextView tvChapterTitle;
        TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvChapterNumber = itemView.findViewById(R.id.tv_row_chapter_number);
            tvChapterTitle = itemView.findViewById(R.id.tv_row_chapter_title);
            tvStatus = itemView.findViewById(R.id.tv_row_chapter_status);
        }
    }
}

