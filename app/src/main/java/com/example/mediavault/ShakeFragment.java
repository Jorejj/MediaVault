package com.example.mediavault;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.example.mediavault.ShakeDetector;
import com.example.mediavault.widget.ToastUtils;

import java.io.File;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

public class ShakeFragment extends Fragment {

    private static final String TAG = "ShakeFragment";
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    private DatabaseHelper dbHelper;
    private SharedPreferences sharedPreferences;
    private View pulseView;
    private ImageView ivIdle, ivShaking;
    private boolean isDialogShowing = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shake, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        sharedPreferences = requireContext().getSharedPreferences("Settings", Context.MODE_PRIVATE);

        mSensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mShakeDetector = new ShakeDetector();
        mShakeDetector.setOnShakeListener(count -> {
            if (!isDialogShowing) {
                handleShake();
            }
        });
        
        pulseView = view.findViewById(R.id.view_shake_pulse);
        ivIdle = view.findViewById(R.id.iv_shake_idle);
        ivShaking = view.findViewById(R.id.iv_shaking);

        // Ensure idle icon uses the 9-patch version
        if (ivIdle != null) {
            ivIdle.setImageResource(R.drawable.mediavault_logo);
        }

        Button btnBack = view.findViewById(R.id.btn_shake_back);
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        return view;
    }

    private void handleShake() {
        // Toggle icons to shaking state
        if (ivIdle != null) ivIdle.setVisibility(View.GONE);
        if (ivShaking != null) ivShaking.setVisibility(View.VISIBLE);

        if (pulseView != null) {
            Animation pulse = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse_neon);
            pulse.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override public void onAnimationEnd(Animation animation) {
                    if (!isDialogShowing) {
                        revertIcons();
                    }
                }
                @Override public void onAnimationRepeat(Animation animation) {}
            });
            pulseView.setAlpha(0.65f);
            pulseView.startAnimation(pulse);
        }

        Cursor cursor = dbHelper.getRandomPlanningMediaWeighted();
        if (cursor != null && cursor.moveToFirst()) {
            isDialogShowing = true;
            vibrate();

            int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
            String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
            String type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE));
            String genre = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GENRE));
            String priority = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PRIORITY));
            String imagePath = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IMAGE_PATH));

            View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_shake_recommendation, null);
            
            // Safer Blur implementation
            setupDialogBlurSafely(dialogView);

            AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                // Heavy dimming is more stable than BlurView on some devices
                dialog.getWindow().setDimAmount(0.85f);
            }

            TextView tvTitle = dialogView.findViewById(R.id.tv_recom_title);
            TextView tvType = dialogView.findViewById(R.id.tv_recom_type);
            ImageView ivImage = dialogView.findViewById(R.id.iv_recom_image);
            Button btnDetails = dialogView.findViewById(R.id.btn_recom_details);
            Button btnAgain = dialogView.findViewById(R.id.btn_recom_again);

            tvTitle.setText(title);
            tvType.setText(type + " • " + (genre != null ? genre : "General") + " • " + (priority != null ? priority : "Medium"));

            if (imagePath != null && !imagePath.isEmpty()) {
                File file = new File(imagePath);
                if (file.exists()) {
                    Glide.with(this).load(file).centerCrop().into(ivImage);
                } else {
                    Glide.with(this).load(imagePath).placeholder(R.drawable.mediavault_logo).error(R.drawable.mediavault_logo).into(ivImage);
                }
            } else {
                ivImage.setImageResource(R.drawable.mediavault_logo);
            }

            btnDetails.setOnClickListener(v -> {
                isDialogShowing = false;
                dialog.dismiss();
                revertIcons();
                Intent intent = new Intent(requireContext(), DescriptionActivity.class);
                intent.putExtra(DescriptionActivity.EXTRA_MEDIA_ID, id);
                startActivity(intent);
            });

            btnAgain.setOnClickListener(v -> {
                isDialogShowing = false;
                dialog.dismiss();
                revertIcons();
                ToastUtils.showCustomToast(getContext(), "Shake your phone again!");
            });

            dialog.show();
            cursor.close();
        } else {
            revertIcons();
            ToastUtils.showCustomToast(getContext(), "Add more items to 'Planning' to use this feature!");
            if (cursor != null) cursor.close();
        }
    }

    private void revertIcons() {
        if (ivIdle != null) ivIdle.setVisibility(View.VISIBLE);
        if (ivShaking != null) ivShaking.setVisibility(View.GONE);
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(180);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Vibration failed: " + e.getMessage());
        }
    }

    private void setupDialogBlurSafely(View dialogView) {
        // RenderScriptBlur is deprecated and crashes on newer SDKs with high compileSdk
        // We will only attempt to use BlurView if we can safely initialize the algorithm
        try {
            BlurView blurView = dialogView.findViewById(R.id.dialog_blur_view);
            if (blurView == null) return;
            
            float radius = 20f;
            ViewGroup rootView = (ViewGroup) requireActivity().getWindow().getDecorView().findViewById(android.R.id.content);
            Drawable windowBackground = requireActivity().getWindow().getDecorView().getBackground();

            // Only setup if we are on a version that definitely supports RS or if we handle the error
            blurView.setupWith(rootView)
                    .setFrameClearDrawable(windowBackground)
                    .setBlurAlgorithm(new RenderScriptBlur(requireContext()))
                    .setBlurRadius(radius)
                    .setBlurAutoUpdate(true)
                    .setHasFixedTransformationMatrix(true);
        } catch (Throwable t) {
            Log.e(TAG, "BlurView setup failed (Throwable): " + t.getMessage());
            // Most likely a NoClassDefFoundError or UnsatisfiedLinkError from RenderScript
            View blurView = dialogView.findViewById(R.id.dialog_blur_view);
            if (blurView != null) {
                blurView.setBackgroundColor(0x99000000); // Fallback to semi-transparent dark
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        float sensitivity = sharedPreferences.getFloat("shake_sensitivity", 2.2f);
        mShakeDetector.setSensitivity(sensitivity);
        if (mAccelerometer != null) {
            mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        if (mShakeDetector != null) {
            mSensorManager.unregisterListener(mShakeDetector);
        }
        super.onPause();
    }
}
