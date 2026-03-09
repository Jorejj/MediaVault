package com.example.mediavault.ui.library;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LibraryFragment extends Fragment {

    private DatabaseHelper dbHelper;
    private MediaAdapter adapter;
    private List<MediaItem> allMediaItems;
    private List<MediaItem> filteredItems;
    private RecyclerView recyclerView;
    private EditText searchBar;
    private ChipGroup chipGroup;
    private View emptyState;
    private ImageButton btnFilter;
    private String currentSearchQuery = "";
    private String currentCategory = "All";
    private int currentSortId = R.id.sort_title_asc;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DescriptionActivity.ACTION_MEDIA_UPDATED.equals(intent.getAction())) {
                refreshLibrary();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        recyclerView = view.findViewById(R.id.library_recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        searchBar = view.findViewById(R.id.search_bar);
        chipGroup = view.findViewById(R.id.chip_group_filter);
        emptyState = view.findViewById(R.id.empty_state_view);
        btnFilter = view.findViewById(R.id.btn_filter);

        allMediaItems = new ArrayList<>();
        filteredItems = new ArrayList<>();
        adapter = new MediaAdapter(filteredItems);
        recyclerView.setAdapter(adapter);

        setupListeners();
        refreshLibrary();

        return view;
    }

    private void setupListeners() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().toLowerCase().trim();
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_all) {
                currentCategory = "All";
            } else if (checkedId == R.id.chip_books) {
                currentCategory = "Book"; // Match DB type
            } else if (checkedId == R.id.chip_anime) {
                currentCategory = "Anime";
            } else if (checkedId == R.id.chip_series) {
                currentCategory = "Series";
            } else if (checkedId == R.id.chip_movies) {
                currentCategory = "Movie"; // Match DB type
            } else {
                currentCategory = "All";
            }
            updateChipAppearance(group, checkedId);
            applyFilters();
        });

        btnFilter.setOnClickListener(this::showSortMenu);
    }

    private void updateChipAppearance(ChipGroup group, int checkedId) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.getId() == checkedId) {
                    chip.setChipBackgroundColorResource(R.color.accent_blue);
                    chip.setTextColor(getResources().getColor(R.color.white));
                } else {
                    chip.setChipBackgroundColorResource(R.color.chip_unselected_bg);
                    chip.setTextColor(getResources().getColor(R.color.chip_unselected_text));
                }
            }
        }
    }

    private void showSortMenu(View v) {
        PopupMenu popup = new PopupMenu(requireContext(), v);
        
        popup.getMenu().add(0, R.id.sort_title_asc, 0, "Title (A-Z)");
        popup.getMenu().add(0, R.id.sort_title_desc, 1, "Title (Z-A)");
        popup.getMenu().add(0, R.id.sort_rating_desc, 2, "Highest Rating");
        popup.getMenu().add(0, R.id.sort_newest, 3, "Newest Added");

        popup.setOnMenuItemClickListener(item -> {
            currentSortId = item.getItemId();
            applyFilters();
            return true;
        });
        popup.show();
    }

    private void applyFilters() {
        filteredItems.clear();
        for (MediaItem item : allMediaItems) {
            boolean matchesCategory;
            if (currentCategory.equals("All")) {
                matchesCategory = true;
            } else if (currentCategory.equals("Book")) {
                // If "Books" chip is selected, maybe we want both Book and Manga
                matchesCategory = item.getType().equalsIgnoreCase("Book") || item.getType().equalsIgnoreCase("Manga");
            } else {
                matchesCategory = item.getType().equalsIgnoreCase(currentCategory);
            }

            boolean matchesSearch = item.getTitle().toLowerCase().contains(currentSearchQuery) ||
                    (item.getGenre() != null && item.getGenre().toLowerCase().contains(currentSearchQuery));

            if (matchesCategory && matchesSearch) {
                filteredItems.add(item);
            }
        }

        sortFilteredItems();
        adapter.notifyDataSetChanged();

        if (filteredItems.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void sortFilteredItems() {
        if (currentSortId == R.id.sort_title_asc) {
            Collections.sort(filteredItems, (o1, o2) -> o1.getTitle().compareToIgnoreCase(o2.getTitle()));
        } else if (currentSortId == R.id.sort_title_desc) {
            Collections.sort(filteredItems, (o1, o2) -> o2.getTitle().compareToIgnoreCase(o1.getTitle()));
        } else if (currentSortId == R.id.sort_rating_desc) {
            Collections.sort(filteredItems, (o1, o2) -> Float.compare(o2.getRating(), o1.getRating()));
        } else if (currentSortId == R.id.sort_newest) {
            Collections.sort(filteredItems, (o1, o2) -> Integer.compare(o2.getId(), o1.getId()));
        }
    }

    private void refreshLibrary() {
        allMediaItems.clear();
        Cursor cursor = dbHelper.getAllMedia();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int idIndex = cursor.getColumnIndex(DatabaseHelper.COL_ID);
                int titleIndex = cursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE);
                int genreIndex = cursor.getColumnIndex(DatabaseHelper.COL_GENRE);
                int statusIndex = cursor.getColumnIndex(DatabaseHelper.COL_STATUS);
                int progressIndex = cursor.getColumnIndex(DatabaseHelper.COL_CURRENT_PROGRESS);
                int capacityIndex = cursor.getColumnIndex(DatabaseHelper.COL_TOTAL_COUNT);
                int unitIndex = cursor.getColumnIndex(DatabaseHelper.COL_UNIT);
                int coverIndex = cursor.getColumnIndex(DatabaseHelper.COL_IMAGE_PATH);
                int ratingIndex = cursor.getColumnIndex(DatabaseHelper.COL_RATING);

                do {
                    int id = idIndex != -1 ? cursor.getInt(idIndex) : -1;
                    String title = titleIndex != -1 ? cursor.getString(titleIndex) : "Unknown";
                    String type = typeIndex != -1 ? cursor.getString(typeIndex) : "N/A";
                    String genre = genreIndex != -1 ? cursor.getString(genreIndex) : "";
                    String status = statusIndex != -1 ? cursor.getString(statusIndex) : "Planning";
                    int progress = progressIndex != -1 ? cursor.getInt(progressIndex) : 0;
                    int capacity = capacityIndex != -1 ? cursor.getInt(capacityIndex) : 0;
                    String unit = unitIndex != -1 ? cursor.getString(unitIndex) : "";
                    String cover = coverIndex != -1 ? cursor.getString(coverIndex) : null;
                    float rating = ratingIndex != -1 ? cursor.getFloat(ratingIndex) : 0f;

                    allMediaItems.add(new MediaItem(id, title, type, genre, status, progress, capacity, unit, cover, rating));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        applyFilters();
    }

    @Override
    public void onResume() {
        super.onResume();
        requireContext().registerReceiver(updateReceiver, new IntentFilter(DescriptionActivity.ACTION_MEDIA_UPDATED), Context.RECEIVER_NOT_EXPORTED);
        refreshLibrary();
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(updateReceiver);
    }
}
