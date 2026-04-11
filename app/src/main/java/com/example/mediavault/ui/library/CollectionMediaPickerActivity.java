package com.example.mediavault.ui.library;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediavault.AppExecutor;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.R;
import com.example.mediavault.widget.ToastUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CollectionMediaPickerActivity extends AppCompatActivity {
    public static final String EXTRA_COLLECTION_NAME = "extra_collection_name";
    public static final String EXTRA_TYPE_CONSTRAINT = "extra_type_constraint";

    private static final String TYPE_ANY = "Any Type";

    private DatabaseHelper dbHelper;
    private String collectionName;
    private String typeConstraint;
    private boolean isDestroyed;

    private ProgressBar progressBar;
    private TextView emptyView;
    private TextView titleView;
    private TextView subtitleView;
    private Button btnAdd;
    private Button btnCancel;
    private PickableMediaAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection_media_picker);

        collectionName = getIntent().getStringExtra(EXTRA_COLLECTION_NAME);
        typeConstraint = getIntent().getStringExtra(EXTRA_TYPE_CONSTRAINT);
        if (collectionName == null || collectionName.trim().isEmpty()) {
            finish();
            return;
        }
        if (typeConstraint == null || typeConstraint.trim().isEmpty()) {
            typeConstraint = TYPE_ANY;
        }

        dbHelper = DatabaseHelper.getInstance(getApplicationContext());

        progressBar = findViewById(R.id.pb_collection_picker_loading);
        emptyView = findViewById(R.id.tv_collection_picker_empty);
        titleView = findViewById(R.id.tv_collection_picker_title);
        subtitleView = findViewById(R.id.tv_collection_picker_subtitle);
        btnAdd = findViewById(R.id.btn_collection_picker_add);
        btnCancel = findViewById(R.id.btn_collection_picker_cancel);
        RecyclerView recyclerView = findViewById(R.id.rv_collection_picker_media);

        titleView.setText(getString(R.string.auto_add_to_collection_title, collectionName));
        subtitleView.setText(getString(R.string.auto_collection_type_filter, typeConstraint));

        adapter = new PickableMediaAdapter(new ArrayList<>(), this::updateAddButtonState);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnCancel.setOnClickListener(v -> finish());
        btnAdd.setOnClickListener(v -> addSelectedToCollection());

        updateAddButtonState();
        loadApplicableMedia();
    }

    private void loadApplicableMedia() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        AppExecutor.getInstance().diskIO().execute(() -> {
            List<PickableMediaItem> loaded = new ArrayList<>();
            Cursor cursor = dbHelper.getAllMedia();
            if (cursor != null) {
                int idIndex = cursor.getColumnIndex(DatabaseHelper.COL_ID);
                int titleIndex = cursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE);
                int statusIndex = cursor.getColumnIndex(DatabaseHelper.COL_STATUS);
                int genreIndex = cursor.getColumnIndex(DatabaseHelper.COL_GENRE);
                if (cursor.moveToFirst()) {
                    do {
                        int id = idIndex != -1 ? cursor.getInt(idIndex) : -1;
                        String title = titleIndex != -1 ? cursor.getString(titleIndex) : "Unknown";
                        String mediaType = typeIndex != -1 ? cursor.getString(typeIndex) : "";
                        String status = statusIndex != -1 ? cursor.getString(statusIndex) : "Planning";
                        String genre = genreIndex != -1 ? cursor.getString(genreIndex) : "";

                        if (id <= 0 || !isTypeConstraintMatch(mediaType)) {
                            continue;
                        }
                        if (containsCollectionTag(genre, collectionName)) {
                            continue;
                        }

                        String subtitle = (mediaType == null || mediaType.trim().isEmpty() ? "Media" : mediaType)
                                + " • "
                                + (status == null || status.trim().isEmpty() ? "Planning" : status);
                        loaded.add(new PickableMediaItem(id, title, subtitle));
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }

            AppExecutor.getInstance().mainThread().execute(() -> {
                if (isDestroyed) {
                    return;
                }
                progressBar.setVisibility(View.GONE);
                adapter.replaceItems(loaded);
                emptyView.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE);
                updateAddButtonState();
            });
        });
    }

    private void addSelectedToCollection() {
        List<Integer> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) {
            ToastUtils.showCustomToast(this, getString(R.string.auto_select_media_to_add));
            return;
        }

        btnAdd.setEnabled(false);
        btnCancel.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        AppExecutor.getInstance().diskIO().execute(() -> {
            int addedCount = 0;
            for (Integer mediaId : selectedIds) {
                if (mediaId == null) {
                    continue;
                }
                if (dbHelper.addCollectionTag(mediaId, collectionName)) {
                    addedCount++;
                }
            }

            final int successCount = addedCount;
            AppExecutor.getInstance().mainThread().execute(() -> {
                if (isDestroyed) {
                    return;
                }
                progressBar.setVisibility(View.GONE);
                if (successCount > 0) {
                    ToastUtils.showCustomToast(
                            this,
                            getString(R.string.auto_added_items_to_collection, successCount, collectionName)
                    );
                    setResult(Activity.RESULT_OK);
                    finish();
                } else {
                    btnAdd.setEnabled(true);
                    btnCancel.setEnabled(true);
                    updateAddButtonState();
                    ToastUtils.showCustomToast(this, getString(R.string.auto_no_changes_saved));
                }
            });
        });
    }

    private boolean isTypeConstraintMatch(String mediaType) {
        if (typeConstraint == null || typeConstraint.trim().isEmpty()) {
            return true;
        }
        String type = mediaType == null ? "" : mediaType.trim();
        if (TYPE_ANY.equalsIgnoreCase(typeConstraint) || "Any".equalsIgnoreCase(typeConstraint)) {
            return true;
        }
        return typeConstraint.equalsIgnoreCase(type);
    }

    private boolean containsCollectionTag(String genre, String collection) {
        if (genre == null || genre.trim().isEmpty() || collection == null || collection.trim().isEmpty()) {
            return false;
        }
        String target = collection.trim().toLowerCase(Locale.ROOT);
        String[] tokens = genre.split(",");
        for (String token : tokens) {
            if (target.equals(token.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void updateAddButtonState() {
        int selectedCount = adapter == null ? 0 : adapter.getSelectedCount();
        btnAdd.setEnabled(selectedCount > 0);
        if (selectedCount > 0) {
            btnAdd.setText(getString(R.string.auto_add_selected_count, selectedCount));
        } else {
            btnAdd.setText(R.string.auto_add_selected);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
    }

    private static class PickableMediaItem {
        final int id;
        final String title;
        final String subtitle;
        boolean selected;

        PickableMediaItem(int id, String title, String subtitle) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private static class PickableMediaAdapter extends RecyclerView.Adapter<PickableMediaAdapter.PickableViewHolder> {
        private final List<PickableMediaItem> items;
        private final Runnable onSelectionChanged;

        PickableMediaAdapter(List<PickableMediaItem> items, Runnable onSelectionChanged) {
            this.items = items;
            this.onSelectionChanged = onSelectionChanged;
        }

        void replaceItems(List<PickableMediaItem> newItems) {
            items.clear();
            if (newItems != null) {
                items.addAll(newItems);
            }
            notifyDataSetChanged();
            if (onSelectionChanged != null) {
                onSelectionChanged.run();
            }
        }

        int getSelectedCount() {
            int count = 0;
            for (PickableMediaItem item : items) {
                if (item.selected) {
                    count++;
                }
            }
            return count;
        }

        List<Integer> getSelectedIds() {
            List<Integer> selected = new ArrayList<>();
            for (PickableMediaItem item : items) {
                if (item.selected) {
                    selected.add(item.id);
                }
            }
            return selected;
        }

        @NonNull
        @Override
        public PickableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_collection_picker_media, parent, false);
            return new PickableViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PickableViewHolder holder, int position) {
            PickableMediaItem item = items.get(position);
            holder.titleView.setText(item.title);
            holder.subtitleView.setText(item.subtitle);

            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(item.selected);
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.selected = isChecked;
                if (onSelectionChanged != null) {
                    onSelectionChanged.run();
                }
            });

            holder.itemView.setOnClickListener(v -> holder.checkBox.performClick());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class PickableViewHolder extends RecyclerView.ViewHolder {
            final CheckBox checkBox;
            final TextView titleView;
            final TextView subtitleView;

            PickableViewHolder(@NonNull View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.cb_collection_picker_media);
                titleView = itemView.findViewById(R.id.tv_collection_picker_item_title);
                subtitleView = itemView.findViewById(R.id.tv_collection_picker_item_subtitle);
            }
        }
    }
}
