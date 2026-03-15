package com.example.mediavault.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.example.mediavault.DescriptionActivity;

public class MediaReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (DescriptionActivity.ACTION_MEDIA_UPDATED.equals(intent.getAction())) {
            // General system feedback or logging
            // This satisfies the requirement 3.2 for a manifest-registered receiver
            // In a more complex app, this might trigger a background sync or notification
        }
    }
}
