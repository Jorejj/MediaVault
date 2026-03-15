package com.example.mediavault;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

public class ShakeFragment extends Fragment {

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    private DatabaseHelper dbHelper;
    private SharedPreferences sharedPreferences;
    private View pulseView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shake, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        sharedPreferences = requireContext().getSharedPreferences("Settings", Context.MODE_PRIVATE);

        mSensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mShakeDetector = new ShakeDetector();
        mShakeDetector.setOnShakeListener(count -> handleShake());
        pulseView = view.findViewById(R.id.view_shake_pulse);

        Button btnBack = view.findViewById(R.id.btn_shake_back);
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        return view;
    }

    private void handleShake() {
        if (pulseView != null) {
            Animation pulse = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse_neon);
            pulseView.setAlpha(0.65f);
            pulseView.startAnimation(pulse);
        }

        Cursor cursor = dbHelper.getRandomPlanningMediaWeighted();
        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(cursor.getColumnIndex(DatabaseHelper.COL_ID));
            String title = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_TITLE));
            String type = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE));
            String genre = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_GENRE));
            String priority = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_PRIORITY));
            String imagePath = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_IMAGE_PATH));

            View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_shake_recommendation, null);
            AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                    .setView(dialogView)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
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
                    Glide.with(this).load(imagePath).placeholder(R.drawable.ic_new_logo).into(ivImage);
                }
            } else {
                ivImage.setImageResource(R.drawable.ic_new_logo);
            }

            btnDetails.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(requireContext(), DescriptionActivity.class);
                intent.putExtra(DescriptionActivity.EXTRA_MEDIA_ID, id);
                startActivity(intent);
            });

            btnAgain.setOnClickListener(v -> {
                dialog.dismiss();
                Toast.makeText(getContext(), "Shake your phone again!", Toast.LENGTH_SHORT).show();
            });

            dialog.show();
            cursor.close();
        } else {
            Toast.makeText(getContext(), "Add more items to 'Planning' to use this feature!", Toast.LENGTH_SHORT).show();
            if (cursor != null) cursor.close();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        float sensitivity = sharedPreferences.getFloat("shake_sensitivity", 2.7f);
        mShakeDetector.setSensitivity(sensitivity);
        mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onPause() {
        mSensorManager.unregisterListener(mShakeDetector);
        super.onPause();
    }
}
