package com.example.mediavault;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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
import java.util.ArrayList;
import java.util.List;

public class ShakeFragment extends Fragment {

    private static final String TAG = "ShakeFragment";
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    private DatabaseHelper dbHelper;
    private SharedPreferences sharedPreferences;
    private View pulseView;
    private ImageView ivIdle, ivShaking;
    private Spinner spinnerShakeType, spinnerShakeGenre;
    private boolean isDialogShowing = false;
    private boolean isUpdateReceiverRegistered = false;
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DescriptionActivity.ACTION_MEDIA_UPDATED.equals(intent.getAction())) {
                setupFilterControls();
                ToastUtils.showCustomToast(context, "Shake filters updated");
            }
        }
    };

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
        spinnerShakeType = view.findViewById(R.id.spinner_shake_type);
        spinnerShakeGenre = view.findViewById(R.id.spinner_shake_genre);
        setupFilterControls();

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

        String selectedType = resolveFilterValue(spinnerShakeType, "All Types");
        String selectedGenre = resolveFilterValue(spinnerShakeGenre, "All Genres");
        Cursor cursor = dbHelper.getRandomPlanningMediaWeighted(selectedType, selectedGenre);
        if ((cursor == null || !cursor.moveToFirst()) && selectedGenre != null) {
            if (cursor != null) {
                cursor.close();
            }
            cursor = dbHelper.getRandomPlanningMediaWeighted(selectedType, null);
            ToastUtils.showCustomToast(getContext(), "No planning titles for that genre. Using all genres.");
        }
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
            
            AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            if (dialog.getWindow() != null) {
                // Ensure the dialog window background is transparent so the card corners show
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                // Soft dimming
                dialog.getWindow().setDimAmount(0.65f);
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
            vibrate(); // Feedback even if no results found
            revertIcons();
            ToastUtils.showCustomToast(getContext(), "No planning titles found for the selected filters.");
            if (cursor != null) cursor.close();
        }
    }

    private void setupFilterControls() {
        List<String> typeOptions = new ArrayList<>();
        typeOptions.add("All Types");
        String[] mediaTypes = getResources().getStringArray(R.array.media_types);
        for (String mediaType : mediaTypes) {
            typeOptions.add(mediaType);
        }
        spinnerShakeType.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, typeOptions));
        spinnerShakeType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshGenreOptionsForType();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                refreshGenreOptionsForType();
            }
        });
        refreshGenreOptionsForType();
    }

    private void refreshGenreOptionsForType() {
        String selectedType = resolveFilterValue(spinnerShakeType, "All Types");
        List<String> genreOptions = new ArrayList<>();
        genreOptions.add("All Genres");
        genreOptions.addAll(dbHelper.getShakeGenresByType(selectedType));
        spinnerShakeGenre.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, genreOptions));
    }

    private String resolveFilterValue(Spinner spinner, String allLabel) {
        if (spinner == null || spinner.getSelectedItem() == null) {
            return null;
        }
        String value = spinner.getSelectedItem().toString().trim();
        if (value.isEmpty() || value.equalsIgnoreCase(allLabel)) {
            return null;
        }
        return value;
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

    @Override
    public void onResume() {
        super.onResume();
        setupFilterControls();
        float sensitivity = sharedPreferences.getFloat("shake_sensitivity", 2.2f);
        mShakeDetector.setSensitivity(sensitivity);
        Context context = getContext();
        if (!isUpdateReceiverRegistered && context != null) {
            IntentFilter filter = new IntentFilter(DescriptionActivity.ACTION_MEDIA_UPDATED);
            ContextCompat.registerReceiver(context, updateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            isUpdateReceiverRegistered = true;
        }
        if (mAccelerometer != null) {
            mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        if (mShakeDetector != null) {
            mSensorManager.unregisterListener(mShakeDetector);
        }
        Context context = getContext();
        if (isUpdateReceiverRegistered && context != null) {
            context.unregisterReceiver(updateReceiver);
            isUpdateReceiverRegistered = false;
        }
        super.onPause();
    }
}
