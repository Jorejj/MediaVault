package com.example.mediavault.ui.library;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import java.util.ArrayList;
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

        refreshLibrary();

        return view;
    }

    private void refreshLibrary() {
        mediaItems.clear();
        Cursor cursor = dbHelper.getAllMedia();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                String type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TYPE));
                String genre = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GENRE));
                String status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS));
                int progress = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROGRESS));
                int capacity = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAPACITY));
                String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
                String cover = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COVER));
                float rating = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING));

                mediaItems.add(new MediaItem(id, title, type, genre, status, progress, capacity, unit, cover, rating));
            } while (cursor.moveToNext());
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
