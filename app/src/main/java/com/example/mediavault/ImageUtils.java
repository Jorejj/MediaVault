package com.example.mediavault;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class ImageUtils {
    private static final String TAG = "ImageUtils";

    /**
     * Downloads an image from a URL and saves it to the app's internal storage.
     * @param context The context to use for accessing files.
     * @param imageUrl The URL of the image to download.
     * @return The absolute path to the saved image file, or the original imageUrl if download fails.
     */
    public static String downloadAndSaveImage(Context context, String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return null;
        if (!imageUrl.startsWith("http")) return imageUrl; // Already a local path or invalid

        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoInput(true);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP " + responseCode);
                return imageUrl;
            }

            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from stream");
                return imageUrl;
            }

            // Create a unique file name
            String fileName = "media_" + UUID.randomUUID().toString() + ".jpg";
            File file = new File(context.getFilesDir(), fileName);
            
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            input.close();

            Log.d(TAG, "Image saved to: " + file.getAbsolutePath());
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error downloading image: " + e.getLocalizedMessage());
            return imageUrl; // Fallback to original URL
        }
    }
}
