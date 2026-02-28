package com.example.mediavault.ui.metrics;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.mediavault.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import java.util.ArrayList;

public class MetricsFragment extends Fragment {

    private BarChart chartMonthlyActivity;
    private PieChart chartVaultComposition;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_metrics, container, false);
        
        chartMonthlyActivity = view.findViewById(R.id.chart_monthly_activity);
        chartVaultComposition = view.findViewById(R.id.chart_vault_composition);
        
        setupMonthlyActivityChart();
        setupVaultCompositionChart();
        
        return view;
    }

    private void setupMonthlyActivityChart() {
        ArrayList<BarEntry> entries = new ArrayList<>();
        // Dummy data for Monthly Activity
        entries.add(new BarEntry(0f, new float[]{25f, 10f})); // Jan
        entries.add(new BarEntry(1f, new float[]{30f, 15f})); // Feb
        entries.add(new BarEntry(2f, new float[]{45f, 20f})); // Mar
        entries.add(new BarEntry(3f, new float[]{15f, 5f}));  // Apr

        BarDataSet dataSet = new BarDataSet(entries, "Activity");
        dataSet.setColors(
            ContextCompat.getColor(requireContext(), R.color.accent_blue),
            ContextCompat.getColor(requireContext(), R.color.accent_cyan)
        );
        dataSet.setDrawValues(false);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);

        chartMonthlyActivity.setData(barData);
        chartMonthlyActivity.getDescription().setEnabled(false);
        chartMonthlyActivity.getLegend().setEnabled(false);
        
        XAxis xAxis = chartMonthlyActivity.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Jan", "Feb", "Mar", "Apr"}));

        chartMonthlyActivity.getAxisLeft().setEnabled(false);
        chartMonthlyActivity.getAxisRight().setDrawGridLines(false);
        chartMonthlyActivity.invalidate();
    }

    private void setupVaultCompositionChart() {
        ArrayList<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(45f, "Books"));
        entries.add(new PieEntry(35f, "Anime"));
        entries.add(new PieEntry(20f, "Movies"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(new int[]{
            ContextCompat.getColor(requireContext(), R.color.accent_blue),
            ContextCompat.getColor(requireContext(), R.color.accent_cyan),
            ContextCompat.getColor(requireContext(), R.color.accent_purple)
        });
        dataSet.setSliceSpace(3f);
        dataSet.setValueTextColor(Color.WHITE);

        chartVaultComposition.setData(new PieData(dataSet));
        chartVaultComposition.getDescription().setEnabled(false);
        chartVaultComposition.setHoleColor(Color.TRANSPARENT);
        chartVaultComposition.animateY(1400);
        chartVaultComposition.invalidate();
    }
}