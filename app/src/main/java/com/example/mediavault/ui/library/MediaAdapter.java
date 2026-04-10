package com.example.mediavault.ui.library;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import android.graphics.drawable.Drawable;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.example.mediavault.utils.ProgressValueUtils;

import java.io.File;
import java.util.Locale;
import java.util.List;

import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.widget.LinearLayout;
import androidx.appcompat.widget.PopupMenu;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.example.mediavault.api.MediaSearchManager;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(MediaItem item);
    }

    private List<MediaItem> mediaItems;
    private DatabaseHelper dbHelper;
    private MediaSearchManager searchManager;
    private OnItemClickListener listener;

    public MediaAdapter(List<MediaItem> mediaItems) {
        this.mediaItems = mediaItems;
    }

    public MediaAdapter(List<MediaItem> mediaItems, OnItemClickListener listener) {
        this.mediaItems = mediaItems;
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
            dbHelper = DatabaseHelper.getInstance(parent.getContext());
        }
        if (searchManager == null) {
            searchManager = new MediaSearchManager();
        }
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        MediaItem item = mediaItems.get(position);
        if (holder.title != null) holder.title.setText(item.getTitle() != null ? item.getTitle() : "Unknown");
        if (holder.subtitle != null) holder.subtitle.setText(item.getSubtitle() != null ? item.getSubtitle() : "");
        if (holder.rating != null) holder.rating.setText(String.format(Locale.getDefault(), "★ %.1f", item.getRatingValue()));
        if (holder.progressBar != null) {
            int capacity = Math.max(item.getCapacity(), 0);
            float progress = Math.min(Math.max(item.getProgress(), 0f), (float) capacity);
            float normalizedProgress = ProgressValueUtils.normalizeForUnit(progress, item.getUnit());
            holder.progressBar.setMax(Math.max(capacity, 1));
            holder.progressBar.setProgress((int) normalizedProgress);
            if (holder.progressText != null) {
                holder.progressText.setText(ProgressValueUtils.formatForDisplay(normalizedProgress, item.getUnit()) + "/" + Math.max(capacity, 0));
            }
        }
        styleCardByMediaType(holder, item);

        if (holder.pbPosterLoading != null) holder.pbPosterLoading.setVisibility(View.VISIBLE);

        if (item.getCoverPath() != null && !item.getCoverPath().isEmpty()) {
            String imagePath = item.getCoverPath();
            
            // Download image if it's a URL
            if (imagePath.startsWith("http")) {
                final String currentImagePath = imagePath;
                final int currentId = item.getId();
                com.example.mediavault.AppExecutor.getInstance().networkIO().execute(() -> {
                    String localPath = com.example.mediavault.ImageUtils.downloadAndSaveImage(holder.poster.getContext(), currentImagePath);
                    if (localPath != null && !localPath.equals(currentImagePath)) {
                        dbHelper.updateImagePath(currentId, localPath);
                        // Update the item object so next bind uses local path
                        item.setCoverPath(localPath);
                    }
                });
            }

            File imageFile = new File(imagePath);
            Object loadSource = imageFile.exists() ? imageFile : imagePath;

            Glide.with(holder.poster.getContext())
                    .load(loadSource)
                    .centerCrop()
                    .placeholder(R.color.grey_300)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                            if (holder.pbPosterLoading != null) holder.pbPosterLoading.setVisibility(View.GONE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                            if (holder.pbPosterLoading != null) holder.pbPosterLoading.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(holder.poster);
        } else {
            holder.poster.setImageResource(R.color.grey_300);
            // Auto-fetch missing cover art
            if (searchManager != null) {
                searchManager.searchAndDownloadImage(holder.poster.getContext(), item.getId(), item.getTitle(), item.getType());
            }
            if (holder.pbPosterLoading != null) holder.pbPosterLoading.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            } else {
                Intent intent = new Intent(v.getContext(), DescriptionActivity.class);
                intent.putExtra(DescriptionActivity.EXTRA_MEDIA_ID, item.getId());
                v.getContext().startActivity(intent);
            }
        });

        if (holder.btnMoreOptions != null) {
            holder.btnMoreOptions.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(v.getContext(), holder.btnMoreOptions);
                
                if ("Recently Deleted".equals(item.getStatus())) {
                    popup.getMenu().add(0, 1, 0, "Recover to Planning");
                    popup.getMenu().add(0, 2, 1, "Delete Permanently");
                } else {
                    if ("Book".equalsIgnoreCase(item.getType()) || "Manga".equalsIgnoreCase(item.getType())) {
                        popup.getMenu().add(0, 4, 0, "Add to Collection");
                    }
                    popup.getMenu().add(0, 3, 0, "Delete");
                }

                popup.setOnMenuItemClickListener(menuItem -> {
                    Context context = v.getContext();
                    switch (menuItem.getItemId()) {
                        case 1: // Recover
                            com.example.mediavault.AppExecutor.executeDb(() -> {
                                boolean success = dbHelper.updateProgress(item.getId(), item.getProgress(), "Planning", item.getRatingValue());
                                if (success) {
                                    com.example.mediavault.AppExecutor.runOnMain(() -> {
                                        ToastUtils.showCustomToast(context, "Recovered to Planning");
                                        item.setStatus("Planning");
                                        updateList(mediaItems); // Refresh list
                                    });
                                }
                            });
                            return true;
                        case 2: // Permanently Delete
                            new MaterialAlertDialogBuilder(context)
                                .setTitle("Delete Permanently?")
                                .setMessage("This action cannot be undone.")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    com.example.mediavault.AppExecutor.executeDb(() -> {
                                        boolean success = dbHelper.deleteMedia(item.getId());
                                        if (success) {
                                            com.example.mediavault.AppExecutor.runOnMain(() -> {
                                                int adapterPosition = holder.getBindingAdapterPosition();
                                                if (adapterPosition != RecyclerView.NO_POSITION) {
                                                    mediaItems.remove(adapterPosition);
                                                    notifyItemRemoved(adapterPosition);
                                                } else {
                                                    int fallbackIndex = mediaItems.indexOf(item);
                                                    if (fallbackIndex >= 0) {
                                                        mediaItems.remove(fallbackIndex);
                                                        notifyItemRemoved(fallbackIndex);
                                                    } else {
                                                        notifyDataSetChanged();
                                                    }
                                                }
                                                ToastUtils.showCustomToast(context, "Permanently deleted");
                                            });
                                        }
                                    });
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                            return true;
                        case 3: // Move to Trash
                            new MaterialAlertDialogBuilder(context)
                                .setTitle("Delete " + item.getTitle() + "?")
                                .setMessage("It will be moved to Recently Deleted.")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    com.example.mediavault.AppExecutor.executeDb(() -> {
                                        boolean success = dbHelper.updateProgress(item.getId(), item.getProgress(), "Recently Deleted", item.getRatingValue());
                                        if (success) {
                                            com.example.mediavault.AppExecutor.runOnMain(() -> {
                                                int adapterPosition = holder.getBindingAdapterPosition();
                                                if (adapterPosition != RecyclerView.NO_POSITION) {
                                                    mediaItems.remove(adapterPosition);
                                                    notifyItemRemoved(adapterPosition);
                                                } else {
                                                    int fallbackIndex = mediaItems.indexOf(item);
                                                    if (fallbackIndex >= 0) {
                                                        mediaItems.remove(fallbackIndex);
                                                        notifyItemRemoved(fallbackIndex);
                                                    } else {
                                                        notifyDataSetChanged();
                                                    }
                                                }
                                                ToastUtils.showCustomToast(context, "Moved to Recently Deleted");
                                            });
                                        }
                                    });
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                            return true;
                        case 4: // Add to Collection
                            showAddToCollectionDialog(context, item, holder);
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

        // FrameLayout doesn't have setStrokeColor - visual accent handled by glass_card_bg drawable
        if (holder.subtitle != null) {
            holder.subtitle.setTextColor(accentColor);
        }
        if (holder.rating != null) {
            holder.rating.setTextColor(Color.WHITE);
        }
    }

    private void showAddToCollectionDialog(@NonNull Context context, @NonNull MediaItem item, @NonNull MediaViewHolder holder) {
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        LinearLayout dialogContainer = new LinearLayout(context);
        dialogContainer.setOrientation(LinearLayout.VERTICAL);
        dialogContainer.setPadding(padding, padding / 2, padding, padding / 4);

        TextInputLayout inputLayout = new TextInputLayout(context);
        TextInputEditText input = new TextInputEditText(context);
        input.setSingleLine(true);
        input.setHint("Collection name");
        inputLayout.addView(input);
        dialogContainer.addView(inputLayout);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Add to Collection")
                .setView(dialogContainer)
                .setPositiveButton("Add", (dialog, which) -> {
                    String collectionName = input.getText() != null ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(collectionName)) {
                        ToastUtils.showCustomToast(context, "Collection name is required");
                        return;
                    }
                    com.example.mediavault.AppExecutor.executeDb(() -> {
                        boolean updated = dbHelper.addCollectionTag(item.getId(), collectionName);
                        com.example.mediavault.AppExecutor.runOnMain(() -> {
                            if (!updated) {
                                ToastUtils.showCustomToast(context, "Could not update collection");
                                return;
                            }
                            String currentGenre = item.getGenre() == null ? "" : item.getGenre();
                            if (!currentGenre.toLowerCase(Locale.ROOT).contains(collectionName.toLowerCase(Locale.ROOT))) {
                                String newGenre = currentGenre.isEmpty() ? collectionName : currentGenre + ", " + collectionName;
                                item.setGenre(newGenre);
                            }
                            int adapterPosition = holder.getBindingAdapterPosition();
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                notifyItemChanged(adapterPosition);
                            }
                            ToastUtils.showCustomToast(context, "Added to " + collectionName);
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static class MediaViewHolder extends RecyclerView.ViewHolder {
        FrameLayout card;
        ImageView poster;
        ImageView btnMoreOptions;
        TextView title;
        TextView subtitle;
        TextView rating;
        ProgressBar progressBar;
        TextView progressText;
        ProgressBar pbPosterLoading;

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
            pbPosterLoading = itemView.findViewById(R.id.pb_poster_loading);
        }
    }
}
