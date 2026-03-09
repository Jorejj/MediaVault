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
    private List<MediaItem> mediaItems;
    private RecyclerView recyclerView;

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

        mediaItems = new ArrayList<>();
        adapter = new MediaAdapter(mediaItems);
        recyclerView.setAdapter(adapter);
    }

        refreshLibrary();

        return view;
    }

    private void refreshLibrary() {
        mediaItems.clear();
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

                    mediaItems.add(new MediaItem(id, title, type, genre, status, progress, capacity, unit, cover, rating));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        adapter.notifyDataSetChanged();
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
