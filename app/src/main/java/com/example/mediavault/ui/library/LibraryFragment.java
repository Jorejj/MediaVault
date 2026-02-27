package com.example.mediavault.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mediavault.R;
import java.util.ArrayList;
import java.util.List;

public class LibraryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.library_recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        List<MediaItem> mediaItems = new ArrayList<>();
        mediaItems.add(new MediaItem("Title", "Anime • 24 episodes", "★ 4.6"));
        mediaItems.add(new MediaItem("Title", "Anime • 24 episodes", "★ 4.6"));
        mediaItems.add(new MediaItem("Title", "Anime • 24 episodes", "★ 4.6"));
        mediaItems.add(new MediaItem("Title", "Anime • 24 episodes", "★ 4.6"));
        mediaItems.add(new MediaItem("Title", "Anime • 24 episodes", "★ 4.6"));
        mediaItems.add(new MediaItem("Title", "Anime • 24 episodes", "★ 4.6"));

        MediaAdapter adapter = new MediaAdapter(mediaItems);
        recyclerView.setAdapter(adapter);

        return view;
    }
}
