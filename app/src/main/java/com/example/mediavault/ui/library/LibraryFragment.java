package com.example.mediavault.ui.library;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LibraryFragment extends Fragment {

    private DatabaseHelper dbHelper;
    private MediaAdapter adapter;
    private List<MediaItem> allMediaItems;
    private List<MediaItem> filteredItems;
    private RecyclerView recyclerView;
    private EditText searchBar;
    private ChipGroup chipGroup, chipGroupStatus;
    private Chip chipCollectionAdd;
    private View emptyState;
    private ImageButton btnFilter, btnAddCollection;
    private String currentSearchQuery = "";
    private String currentCategory = "All";
    private String currentCollectionFilter = "All";
    private String currentCollectionTypeConstraint = "Any Type";
    private int currentSortId = R.id.sort_title_asc;
    private boolean isReceiverRegistered = false;
    private final Map<Integer, CollectionFilter> customCollectionByChipId = new HashMap<>();
    private static final String COLLECTION_TYPE_ANY = "Any Type";
    private static final String COLLECTION_PREFS = "library_collection_prefs";
    private static final String KEY_CUSTOM_COLLECTIONS_JSON = "custom_collections_json";
    private final ActivityResultLauncher<Intent> collectionPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    refreshLibrary();
                }
            });

    private static class CollectionFilter {
        final String name;
        final String typeConstraint;

        CollectionFilter(@NonNull String name, @NonNull String typeConstraint) {
            this.name = name;
            this.typeConstraint = typeConstraint;
        }
    }

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

        dbHelper = DatabaseHelper.getInstance(requireContext());
        recyclerView = view.findViewById(R.id.library_recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        searchBar = view.findViewById(R.id.search_bar);
        chipGroup = view.findViewById(R.id.chip_group_filter);
        chipGroupStatus = view.findViewById(R.id.chip_group_status);
        chipCollectionAdd = view.findViewById(R.id.chip_collection_add);
        emptyState = view.findViewById(R.id.empty_state_view);
        btnFilter = view.findViewById(R.id.btn_filter);
        btnAddCollection = view.findViewById(R.id.btn_add_collection);

        allMediaItems = new ArrayList<>();
        filteredItems = new ArrayList<>();
        adapter = new MediaAdapter(filteredItems);
        recyclerView.setAdapter(adapter);

        setupListeners();
        restoreCustomCollections();
        updateAddCollectionChipVisibility();
        refreshLibrary();

        return view;
    }

    private void setupListeners() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().toLowerCase(Locale.ROOT).trim();
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_all) {
                currentCategory = "All";
            } else if (checkedId == R.id.chip_books) {
                currentCategory = "Book";
            } else if (checkedId == R.id.chip_anime) {
                currentCategory = "Anime";
            } else if (checkedId == R.id.chip_series) {
                currentCategory = "Series";
            } else if (checkedId == R.id.chip_movies) {
                currentCategory = "Movie";
            } else {
                currentCategory = "All";
            }
            updateChipAppearance(group, checkedId);
            applyFilters();
        });

        if (chipGroupStatus != null) {
            chipGroupStatus.setOnCheckedChangeListener((group, checkedId) -> {
                currentCollectionTypeConstraint = COLLECTION_TYPE_ANY;
                if (checkedId == R.id.chip_collection_add) {
                    showAddCollectionDialog();
                    chipGroupStatus.clearCheck();
                    return;
                }
                if (checkedId == R.id.chip_collection_ongoing) {
                    currentCollectionFilter = "Ongoing";
                } else if (checkedId == R.id.chip_collection_completed) {
                    currentCollectionFilter = "Completed";
                } else if (checkedId == R.id.chip_collection_planning) {
                    currentCollectionFilter = "Planning";
                } else if (checkedId == R.id.chip_collection_dropped) {
                    currentCollectionFilter = "Dropped";
                } else if (checkedId == R.id.chip_collection_favorites) {
                    currentCollectionFilter = "Favorites";
                } else if (customCollectionByChipId.containsKey(checkedId)) {
                    CollectionFilter filter = customCollectionByChipId.get(checkedId);
                    if (filter != null) {
                        currentCollectionFilter = filter.name;
                        currentCollectionTypeConstraint = filter.typeConstraint;
                    }
                } else {
                    currentCollectionFilter = "All";
                    currentCollectionTypeConstraint = COLLECTION_TYPE_ANY;
                }
                updateStatusChipAppearance(group, checkedId);
                applyFilters();
            });
        }

        btnFilter.setOnClickListener(this::showSortMenu);
        if (btnAddCollection != null) {
            btnAddCollection.setOnClickListener(v -> onCollectionPlusClicked());
        }
        if (chipCollectionAdd != null) {
            chipCollectionAdd.setOnClickListener(v -> onCollectionPlusClicked());
        }
    }

    private void onCollectionPlusClicked() {
        if (chipGroupStatus != null) {
            int checkedId = chipGroupStatus.getCheckedChipId();
            if (customCollectionByChipId.containsKey(checkedId)) {
                openCollectionPicker(checkedId);
                return;
            }
        }
        Integer activeCustomId = findCustomCollectionChipId(currentCollectionFilter, currentCollectionTypeConstraint);
        if (activeCustomId != null) {
            openCollectionPicker(activeCustomId);
            return;
        }
        showAddCollectionDialog();
    }

    @Nullable
    private Integer findCustomCollectionChipId(@Nullable String collectionName, @Nullable String typeConstraint) {
        if (collectionName == null || collectionName.trim().isEmpty()) {
            return null;
        }
        String targetName = collectionName.trim();
        String targetType = typeConstraint == null || typeConstraint.trim().isEmpty()
                ? COLLECTION_TYPE_ANY
                : typeConstraint.trim();
        for (Map.Entry<Integer, CollectionFilter> entry : customCollectionByChipId.entrySet()) {
            CollectionFilter filter = entry.getValue();
            if (filter.name.equalsIgnoreCase(targetName)
                    && filter.typeConstraint.equalsIgnoreCase(targetType)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void updateChipAppearance(ChipGroup group, int checkedId) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.getId() == checkedId) {
                    chip.setChipBackgroundColorResource(R.color.netflix_red);
                    chip.setTextColor(getResources().getColor(R.color.white));
                } else {
                    chip.setChipBackgroundColorResource(R.color.chip_unselected_bg);
                    chip.setTextColor(getResources().getColor(R.color.chip_unselected_text));
                }
            }
        }
    }

    private void updateStatusChipAppearance(ChipGroup group, int checkedId) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.getId() == checkedId) {
                    chip.setChipBackgroundColorResource(R.color.accent_blue);
                    chip.setTextColor(getResources().getColor(R.color.white));
                    if (chip.isCloseIconVisible()) {
                        chip.setCloseIconTintResource(R.color.white);
                    }
                } else {
                    chip.setChipBackgroundColorResource(R.color.chip_unselected_bg);
                    chip.setTextColor(getResources().getColor(R.color.chip_unselected_text));
                    if (chip.isCloseIconVisible()) {
                        chip.setCloseIconTintResource(R.color.chip_unselected_text);
                    }
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
            boolean matchesCollection;

            // Exclude items in Trash from normal view
            if ("Recently Deleted".equals(item.getStatus())) {
                continue; 
            }

            if (currentCategory.equals("All")) {
                matchesCategory = true;
            } else if (currentCategory.equals("Book")) {
                matchesCategory = item.getType().equalsIgnoreCase("Book") || item.getType().equalsIgnoreCase("Manga");
            } else {
                matchesCategory = item.getType().equalsIgnoreCase(currentCategory);
            }

            // Handle Collection filter
            if (currentCollectionFilter.equals("All")) {
                matchesCollection = true;
            } else if (currentCollectionFilter.equals("Favorites")) {
                matchesCollection = item.isFavorite();
            } else if ("Ongoing".equalsIgnoreCase(currentCollectionFilter)
                    || "Completed".equalsIgnoreCase(currentCollectionFilter)
                    || "Planning".equalsIgnoreCase(currentCollectionFilter)
                    || "Dropped".equalsIgnoreCase(currentCollectionFilter)) {
                matchesCollection = item.getStatus().equalsIgnoreCase(currentCollectionFilter);
            } else {
                boolean matchesName = hasCollectionTag(item.getGenre(), currentCollectionFilter);
                boolean matchesTypeConstraint = isTypeConstraintMatch(item.getType(), currentCollectionTypeConstraint);
                matchesCollection = matchesName && matchesTypeConstraint;
            }

            boolean matchesSearch = item.getTitle().toLowerCase(Locale.ROOT).contains(currentSearchQuery) ||
                    (item.getGenre() != null && item.getGenre().toLowerCase(Locale.ROOT).contains(currentSearchQuery));

            if (matchesCategory && matchesCollection && matchesSearch) {
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
            Collections.sort(filteredItems, (o1, o2) -> Float.compare(o2.getRatingValue(), o1.getRatingValue()));
        } else if (currentSortId == R.id.sort_newest) {
            Collections.sort(filteredItems, (o1, o2) -> Integer.compare(o2.getId(), o1.getId()));
        }
    }

    private void showAddCollectionDialog() {
        if (getContext() == null) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_collection, null);
        
        TextInputEditText etName = dialogView.findViewById(R.id.et_collection_name);
        AutoCompleteTextView actvType = dialogView.findViewById(R.id.actv_media_type);

        String[] mediaTypes = getResources().getStringArray(R.array.media_types);
        String[] typeOptions = new String[mediaTypes.length + 1];
        typeOptions[0] = COLLECTION_TYPE_ANY;
        System.arraycopy(mediaTypes, 0, typeOptions, 1, mediaTypes.length);
        
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, typeOptions);
        actvType.setAdapter(typeAdapter);
        actvType.setText(COLLECTION_TYPE_ANY, false); // false to not filter list

        AlertDialog addCollectionDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Collection")
                .setView(dialogView)
                .setPositiveButton("Add", (d, which) -> {
                    String collectionName = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (collectionName.isEmpty()) {
                        ToastUtils.showCustomToast(requireContext(), "Collection name is required");
                        return;
                    }
                    String selectedType = actvType.getText() != null ? actvType.getText().toString().trim() : COLLECTION_TYPE_ANY;
                    if (selectedType.isEmpty()) {
                        selectedType = COLLECTION_TYPE_ANY;
                    }
                    addCustomCollectionChip(collectionName, selectedType);
                })
                .setNegativeButton("Cancel", null)
                .show();

        if (addCollectionDialog.getWindow() != null) {
            int dialogWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            addCollectionDialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void addCustomCollectionChip(@NonNull String collectionName, @NonNull String typeConstraint) {
        addCustomCollectionChipInternal(collectionName, typeConstraint, true, true);
    }

    private void addCustomCollectionChipInternal(
            @NonNull String collectionName,
            @NonNull String typeConstraint,
            boolean selectChip,
            boolean showToast
    ) {
        if (chipGroupStatus == null) {
            return;
        }
        for (Map.Entry<Integer, CollectionFilter> entry : customCollectionByChipId.entrySet()) {
            CollectionFilter existing = entry.getValue();
            if (existing.name.equalsIgnoreCase(collectionName) && existing.typeConstraint.equalsIgnoreCase(typeConstraint)) {
                if (selectChip) {
                    chipGroupStatus.check(entry.getKey());
                    currentCollectionFilter = existing.name;
                    currentCollectionTypeConstraint = existing.typeConstraint;
                    applyFilters();
                }
                if (showToast) {
                    ToastUtils.showCustomToast(requireContext(), "Collection already exists");
                }
                return;
            }
        }
        Chip chip = new Chip(requireContext(), null, com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice);
        int chipId = View.generateViewId();
        chip.setId(chipId);
        chip.setText(buildCollectionChipLabel(collectionName, typeConstraint));
        chip.setCheckable(true);
        chip.setCloseIconVisible(true);
        chip.setCloseIconResource(R.drawable.ic_add);
        chip.setCloseIconTintResource(R.color.chip_unselected_text);
        chip.setCloseIconContentDescription(getString(R.string.auto_add_media_to_collection));
        chip.setOnCloseIconClickListener(v -> openCollectionPicker(chipId));
        chip.setChipBackgroundColorResource(R.color.chip_unselected_bg);
        chip.setTextColor(getResources().getColor(R.color.chip_unselected_text));
        chip.setOnLongClickListener(v -> {
            String[] options = {"Edit", "Delete"};
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Manage Collection")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            showEditCollectionDialog(chipId, chip);
                        } else {
                            CollectionFilter existing = customCollectionByChipId.get(chipId);
                            String name = existing != null ? existing.name : collectionName;
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Delete Collection")
                                    .setMessage("Delete \"" + name + "\" collection chip?")
                                    .setPositiveButton("Delete", (deleteDialog, deleteWhich) -> {
                                        chipGroupStatus.removeView(chip);
                                        customCollectionByChipId.remove(chipId);
                                        if (currentCollectionFilter.equalsIgnoreCase(name)) {
                                            chipGroupStatus.check(R.id.chip_collection_all);
                                            currentCollectionFilter = "All";
                                            currentCollectionTypeConstraint = COLLECTION_TYPE_ANY;
                                            applyFilters();
                                        }
                                        updateAddCollectionChipVisibility();
                                        persistCustomCollections();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        }
                    })
                    .show();
            return true;
        });
        customCollectionByChipId.put(chipId, new CollectionFilter(collectionName, typeConstraint));
        chipGroupStatus.addView(chip);
        if (selectChip) {
            chipGroupStatus.check(chipId);
            currentCollectionFilter = collectionName;
            currentCollectionTypeConstraint = typeConstraint;
        }
        updateAddCollectionChipVisibility();
        persistCustomCollections();
        if (showToast) {
            ToastUtils.showCustomToast(requireContext(), "Collection added");
        }
    }

    private void openCollectionPicker(int chipId) {
        CollectionFilter filter = customCollectionByChipId.get(chipId);
        if (filter == null || getContext() == null) {
            return;
        }
        Intent pickerIntent = new Intent(requireContext(), CollectionMediaPickerActivity.class);
        pickerIntent.putExtra(CollectionMediaPickerActivity.EXTRA_COLLECTION_NAME, filter.name);
        pickerIntent.putExtra(CollectionMediaPickerActivity.EXTRA_TYPE_CONSTRAINT, filter.typeConstraint);
        collectionPickerLauncher.launch(pickerIntent);
    }

    private void showEditCollectionDialog(int chipId, @NonNull Chip chip) {
        CollectionFilter existing = customCollectionByChipId.get(chipId);
        if (existing == null) {
            return;
        }

        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout dialogContainer = new LinearLayout(requireContext());
        dialogContainer.setOrientation(LinearLayout.VERTICAL);
        dialogContainer.setPadding(padding, padding / 2, padding, padding / 4);

        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setSingleLine(true);
        input.setHint("Collection name");
        input.setText(existing.name);
        inputLayout.addView(input);
        dialogContainer.addView(inputLayout);

        TextInputLayout typeLayout = new TextInputLayout(requireContext());
        typeLayout.setHint("Media type constraint");
        AutoCompleteTextView typeInput = new AutoCompleteTextView(requireContext());
        typeInput.setInputType(0);
        String[] mediaTypes = getResources().getStringArray(R.array.media_types);
        String[] typeOptions = new String[mediaTypes.length + 1];
        typeOptions[0] = COLLECTION_TYPE_ANY;
        System.arraycopy(mediaTypes, 0, typeOptions, 1, mediaTypes.length);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, typeOptions);
        typeInput.setAdapter(typeAdapter);
        typeInput.setText(existing.typeConstraint, false);
        typeLayout.addView(typeInput);
        dialogContainer.addView(typeLayout);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Collection")
                .setView(dialogContainer)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText() != null ? input.getText().toString().trim() : "";
                    String newType = typeInput.getText() != null ? typeInput.getText().toString().trim() : COLLECTION_TYPE_ANY;

                    if (newName.isEmpty()) {
                        ToastUtils.showCustomToast(requireContext(), "Collection name is required");
                        return;
                    }
                    if (newType.isEmpty()) {
                        newType = COLLECTION_TYPE_ANY;
                    }

                    for (Map.Entry<Integer, CollectionFilter> entry : customCollectionByChipId.entrySet()) {
                        if (entry.getKey() == chipId) {
                            continue;
                        }
                        CollectionFilter filter = entry.getValue();
                        if (filter.name.equalsIgnoreCase(newName) && filter.typeConstraint.equalsIgnoreCase(newType)) {
                            ToastUtils.showCustomToast(requireContext(), "Collection already exists");
                            return;
                        }
                    }

                    customCollectionByChipId.put(chipId, new CollectionFilter(newName, newType));
                    chip.setText(buildCollectionChipLabel(newName, newType));

                    if (chip.isChecked()) {
                        currentCollectionFilter = newName;
                        currentCollectionTypeConstraint = newType;
                        applyFilters();
                    } else if (currentCollectionFilter.equalsIgnoreCase(existing.name)) {
                        currentCollectionFilter = newName;
                        currentCollectionTypeConstraint = newType;
                    }

                    persistCustomCollections();
                    ToastUtils.showCustomToast(requireContext(), "Collection updated");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String buildCollectionChipLabel(@NonNull String collectionName, @NonNull String typeConstraint) {
        if (COLLECTION_TYPE_ANY.equalsIgnoreCase(typeConstraint)) {
            return collectionName;
        }
        return collectionName + " • " + typeConstraint;
    }

    private boolean isTypeConstraintMatch(@Nullable String itemType, @NonNull String typeConstraint) {
        if (COLLECTION_TYPE_ANY.equalsIgnoreCase(typeConstraint)) {
            return true;
        }
        if (itemType == null) {
            return false;
        }
        return itemType.equalsIgnoreCase(typeConstraint);
    }

    private void updateAddCollectionChipVisibility() {
        if (chipCollectionAdd == null) {
            return;
        }
        chipCollectionAdd.setVisibility(View.VISIBLE);
    }

    private boolean hasCollectionTag(@Nullable String genre, @Nullable String collectionName) {
        if (genre == null || genre.trim().isEmpty() || collectionName == null || collectionName.trim().isEmpty()) {
            return false;
        }
        String target = collectionName.trim().toLowerCase(Locale.ROOT);
        String[] tokens = genre.split(",");
        for (String token : tokens) {
            if (target.equals(token.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void restoreCustomCollections() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(COLLECTION_PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_CUSTOM_COLLECTIONS_JSON, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                String name = entry.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                String type = entry.optString("type", COLLECTION_TYPE_ANY).trim();
                if (type.isEmpty()) {
                    type = COLLECTION_TYPE_ANY;
                }
                addCustomCollectionChipInternal(name, type, false, false);
            }
        } catch (JSONException ignored) {
        }
    }

    private void persistCustomCollections() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (CollectionFilter filter : customCollectionByChipId.values()) {
            JSONObject entry = new JSONObject();
            try {
                entry.put("name", filter.name);
                entry.put("type", filter.typeConstraint);
                array.put(entry);
            } catch (JSONException ignored) {
            }
        }
        context.getSharedPreferences(COLLECTION_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CUSTOM_COLLECTIONS_JSON, array.toString())
                .apply();
    }

    private void refreshLibrary() {
        com.example.mediavault.AppExecutor.getInstance().diskIO().execute(() -> {
            java.util.List<MediaItem> loadedItems = new java.util.ArrayList<>();
            Cursor cursor = dbHelper.getAllMediaIncludingTrash();
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int idIndex = cursor.getColumnIndex(DatabaseHelper.COL_ID);
                    int titleIndex = cursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                    int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE);
                    int genreIndex = cursor.getColumnIndex(DatabaseHelper.COL_GENRE);
                    int statusIndex = cursor.getColumnIndex(DatabaseHelper.COL_STATUS);
                    int progressIndex = cursor.getColumnIndex(DatabaseHelper.COL_CURRENT_PROGRESS);
                    int prevProgressIndex = cursor.getColumnIndex(DatabaseHelper.COL_PREVIOUS_PROGRESS);
                    int capacityIndex = cursor.getColumnIndex(DatabaseHelper.COL_TOTAL_COUNT);
                    int unitIndex = cursor.getColumnIndex(DatabaseHelper.COL_UNIT);
                    int coverIndex = cursor.getColumnIndex(DatabaseHelper.COL_IMAGE_PATH);
                    int ratingIndex = cursor.getColumnIndex(DatabaseHelper.COL_RATING);
                    int favoriteIndex = cursor.getColumnIndex(DatabaseHelper.COL_IS_FAVORITE);
                    int sourceUrlIndex = cursor.getColumnIndex(DatabaseHelper.COL_SOURCE_URL);
                    int contentTypeIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONTENT_TYPE);

                    do {
                        int id = idIndex != -1 ? cursor.getInt(idIndex) : -1;
                        String title = titleIndex != -1 ? cursor.getString(titleIndex) : "Unknown";
                        String type = typeIndex != -1 ? cursor.getString(typeIndex) : "N/A";
                        String genre = genreIndex != -1 ? cursor.getString(genreIndex) : "";
                        String status = statusIndex != -1 ? cursor.getString(statusIndex) : "Planning";
                        float progress = progressIndex != -1 ? cursor.getFloat(progressIndex) : 0f;
                        float prevProgress = prevProgressIndex != -1 ? cursor.getFloat(prevProgressIndex) : progress;
                        int capacity = capacityIndex != -1 ? cursor.getInt(capacityIndex) : 0;
                        String unit = unitIndex != -1 ? cursor.getString(unitIndex) : "";
                        String cover = coverIndex != -1 ? cursor.getString(coverIndex) : null;
                        float rating = ratingIndex != -1 ? cursor.getFloat(ratingIndex) : 0f;
                        boolean isFavorite = favoriteIndex != -1 && cursor.getInt(favoriteIndex) == 1;
                        String sourceUrl = sourceUrlIndex != -1 ? cursor.getString(sourceUrlIndex) : null;
                        String contentType = contentTypeIndex != -1 ? cursor.getString(contentTypeIndex) : null;

                        loadedItems.add(new MediaItem(id, title, type, genre, status, progress, prevProgress, capacity, unit, cover, rating, isFavorite, sourceUrl, contentType));

                    } while (cursor.moveToNext());
                }
                cursor.close();
            }

            com.example.mediavault.AppExecutor.getInstance().mainThread().execute(() -> {
                allMediaItems.clear();
                allMediaItems.addAll(loadedItems);
                applyFilters();
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (!isReceiverRegistered && context != null) {
            IntentFilter filter = new IntentFilter(DescriptionActivity.ACTION_MEDIA_UPDATED);
            ContextCompat.registerReceiver(context, updateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            isReceiverRegistered = true;
        }
        refreshLibrary();
    }

    @Override
    public void onPause() {
        super.onPause();
        Context context = getContext();
        if (isReceiverRegistered && context != null) {
            context.unregisterReceiver(updateReceiver);
            isReceiverRegistered = false;
        }
    }
}
