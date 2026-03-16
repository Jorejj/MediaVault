package com.example.mediavault.widget;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.mediavault.R;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

public class ToastUtils {

    public static void showCustomToast(Context context, String message) {
        if (context == null) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(R.layout.layout_custom_toast, null);

        TextView text = layout.findViewById(R.id.toast_text);
        text.setText(message);

        BlurView blurView = layout.findViewById(R.id.blur_toast);
        if (context instanceof Activity) {
            View decorView = ((Activity) context).getWindow().getDecorView();
            ViewGroup rootView = decorView.findViewById(android.R.id.content);
            blurView.setupWith(rootView)
                    .setFrameClearDrawable(decorView.getBackground())
                    .setBlurAlgorithm(new RenderScriptBlur(context))
                    .setBlurRadius(15f)
                    .setHasFixedTransformationMatrix(true);
        }

        Toast toast = new Toast(context.getApplicationContext());
        toast.setGravity(Gravity.BOTTOM, 0, 100);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }
}
