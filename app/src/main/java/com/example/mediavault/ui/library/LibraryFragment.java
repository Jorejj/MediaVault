package com.example.mediavault.ui.library;

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
import com.example.mediavault.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LibraryFragment extends Fragment {

    private RecyclerView recyclerView;
    private MediaAdapter adapter;
    private List<MediaItem> allMediaItems = new ArrayList<>();
    private List<MediaItem> filteredItems = new ArrayList<>();
    private EditText searchBar;
    private ImageButton btnFilter;
    private ChipGroup chipGroupFilter;
    private LinearLayout emptyStateView;
    private DatabaseHelper dbHelper;

    private String currentSearchQuery = "";
    private String currentFilterType = "All";
    
    // Sorting modes
    private enum SortMode {
        TITLE_AZ, RATING_HIGH, NEWEST
    }
    private SortMode currentSortMode = SortMode.NEWEST;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        
        // Initialize views
        recyclerView = view.findViewById(R.id.library_recycler_view);
        searchBar = view.findViewById(R.id.search_bar);
        btnFilter = view.findViewById(R.id.btn_filter);
        chipGroupFilter = view.findViewById(R.id.chip_group_filter);
        emptyStateView = view.findViewById(R.id.empty_state_view);

        setupRecyclerView();
        loadMediaData();
        setupSearch();
        setupFilterChips();
        setupFilterButton();

        return view;
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new MediaAdapter(filteredItems);
        adapter.setOnItemClickListener(item -> {
            Toast.makeText(requireContext(), "Clicked: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        });
        recyclerView.setAdapter(adapter);
    }

    private void loadMediaData() {
        allMediaItems.clear();
        Cursor cursor = dbHelper.getAllMedia();
        
        if (cursor != null && cursor.moveToFirst()) {
            do {
                String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                String type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TYPE));
                int progress = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROGRESS));
                int total = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAPACITY));
                String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
                float rating = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING));
                
                String subtitle = type + " • " + progress + "/" + total + " " + unit;
                String ratingStr = rating > 0 ? "★ " + rating : "No rating";
                
                allMediaItems.add(new MediaItem(title, subtitle, ratingStr, type, rating));
            } while (cursor.moveToNext());
            cursor.close();
        }

        // Add mock data if empty for demonstration
        if (allMediaItems.isEmpty()) {
            allMediaItems.add(new MediaItem("One Piece", "Anime • 1000+ eps", "★ 4.9", "Anime", 4.9f));
            allMediaItems.add(new MediaItem("The Great Gatsby", "Books • 180 pages", "★ 4.2", "Books", 4.2f));
            allMediaItems.add(new MediaItem("Inception", "Movies • 148 mins", "★ 4.8", "Movies", 4.8f));
            allMediaItems.add(new MediaItem("Breaking Bad", "Series • 62 eps", "★ 5.0", "Series", 5.0f));
            allMediaItems.add(new MediaItem("Attack on Titan", "Anime • 87 eps", "★ 4.9", "Anime", 4.9f));
            allMediaItems.add(new MediaItem("1984", "Books • 328 pages", "★ 4.5", "Books", 4.5f));
        }

        applyFilters();
    }

    private void setupSearch() {
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
    }

    private void setupFilterChips() {
        chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < group.getChildCount(); i++) {
                Chip chip = (Chip) group.getChildAt(i);
                if (chip.getId() == checkedId) {
                    currentFilterType = chip.getText().toString();
                    // Active Style
                    chip.setChipBackgroundColorResource(R.color.accent_blue);
                    chip.setTextColor(getResources().getColor(R.color.white));
                    applyFilters();
                } else {
                    // Unselected Style
                    chip.setChipBackgroundColorResource(R.color.chip_unselected_bg);
                    chip.setTextColor(getResources().getColor(R.color.chip_unselected_text));
                }
            }
        });
    }

    private void setupFilterButton() {
        btnFilter.setOnClickListener(v -> showSortPopupMenu(v));
    }

    private void showSortPopupMenu(View view) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenu().add(0, 1, 0, "Sort by: Newest");
        popup.getMenu().add(0, 2, 1, "Sort by: Title (A-Z)");
        popup.getMenu().add(0, 3, 2, "Sort by: Highest Rating");
        
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    currentSortMode = SortMode.NEWEST;
                    Toast.makeText(getContext(), "Sorted by Newest", Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    currentSortMode = SortMode.TITLE_AZ;
                    Toast.makeText(getContext(), "Sorted by Title (A-Z)", Toast.LENGTH_SHORT).show();
                    break;
                case 3:
                    currentSortMode = SortMode.RATING_HIGH;
                    Toast.makeText(getContext(), "Sorted by Rating", Toast.LENGTH_SHORT).show();
                    break;
            }
            applyFilters();
            return true;
        });
        popup.show();
    }

    private void applyFilters() {
        filteredItems.clear();
        
        // 1. Filter by Search and Type
        for (MediaItem item : allMediaItems) {
            boolean matchesSearch = item.getTitle().toLowerCase().contains(currentSearchQuery);
            boolean matchesType = currentFilterType.equals("All") || item.getType().equalsIgnoreCase(currentFilterType);
            
            if (matchesSearch && matchesType) {
                filteredItems.add(item);
            }
        }
        
        // 2. Apply Sorting
        sortFilteredItems();
        
        adapter.updateList(filteredItems);
        
        // 3. Show/hide empty state
        if (filteredItems.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateView.setVisibility(View.GONE);
        }
    }

    private void sortFilteredItems() {
        switch (currentSortMode) {
            case TITLE_AZ:
                Collections.sort(filteredItems, (o1, o2) -> o1.getTitle().compareToIgnoreCase(o2.getTitle()));
                break;
            case RATING_HIGH:
                Collections.sort(filteredItems, (o1, o2) -> Float.compare(o2.getRatingValue(), o1.getRatingValue()));
                break;
            case NEWEST:
                // For mock data, newest is just the original order. 
                // With DB data, loadMediaData already sorts by modified time.
                // If we want to force it here, we'd need a timestamp in MediaItem.
                break;
        }
    }
}
