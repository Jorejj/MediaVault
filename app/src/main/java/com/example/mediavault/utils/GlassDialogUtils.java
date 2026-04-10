package com.example.mediavault.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.content.ContextCompat;

import com.example.mediavault.R;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

public class GlassDialogUtils {

    /**
     * Applies a glassmorphism effect to the dialog's window.
     * This setup assumes the Dialog's layout contains a BlurView with id R.id.blur_view_dialog
     * and the root view is passed.
     */
    public static void applyGlassEffect(Context context, Dialog dialog, View dialogLayout) {
        if (dialog == null || dialog.getWindow() == null) return;

        Window window = dialog.getWindow();
        
        // Make the dialog background transparent so our custom glass drawable shows
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        // Set dim amount for focus
        window.setDimAmount(0.6f);

        // If the layout has a BlurView, configure it
        // Note: The BlurView needs to be in the dialog's layout XML
        BlurView blurView = dialogLayout.findViewById(R.id.blur_view_dialog);
        if (blurView != null) {
            setupDialogBlur(context, blurView, window);
        }
    }

    private static void setupDialogBlur(Context context, BlurView blurView, Window window) {
        View decorView = window.getDecorView();
        // The root view of the window
        ViewGroup rootView = decorView.findViewById(android.R.id.content);
        Drawable windowBackground = decorView.getBackground();

        int overlayColor = ContextCompat.getColor(context, R.color.glass_surface_color);

        blurView.setupWith(rootView)
                .setFrameClearDrawable(windowBackground)
                .setBlurAlgorithm(new RenderScriptBlur(context))
                .setBlurRadius(16f)
                .setBlurAutoUpdate(true)
                .setOverlayColor(overlayColor);
    }
}
