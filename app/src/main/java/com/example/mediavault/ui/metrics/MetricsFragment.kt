package com.example.mediavault.ui.metrics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.mediavault.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class MetricsFragment : Fragment() {

    private lateinit var chartMonthlyActivity: BarChart
    private lateinit var chartVaultComposition: PieChart

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_metrics, container, false)
        chartMonthlyActivity = view.findViewById(R.id.chart_monthly_activity)
        chartVaultComposition = view.findViewById(R.id.chart_vault_composition)
        
        setupMonthlyActivityChart()
        setupVaultCompositionChart()
        
        return view
    }

    private fun setupMonthlyActivityChart() {
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(0f, floatArrayOf(25f, 10f)))
        entries.add(BarEntry(1f, floatArrayOf(30f, 15f)))
        entries.add(BarEntry(2f, floatArrayOf(10f, 5f)))
        entries.add(BarEntry(3f, floatArrayOf(50f, 20f)))
        entries.add(BarEntry(4f, floatArrayOf(8f, 3f)))
        entries.add(BarEntry(5f, floatArrayOf(35f, 18f)))
        entries.add(BarEntry(6f, floatArrayOf(55f, 12f)))
        entries.add(BarEntry(7f, floatArrayOf(20f, 8f)))
        entries.add(BarEntry(8f, floatArrayOf(15f, 5f)))

        val dataSet = BarDataSet(entries, "")
        dataSet.setColors(
            ContextCompat.getColor(requireContext(), R.color.accent_blue),
            ContextCompat.getColor(requireContext(), R.color.accent_cyan)
        )
        dataSet.setDrawValues(false)

        val barData = BarData(dataSet)
        barData.barWidth = 0.5f

        chartMonthlyActivity.data = barData
        chartMonthlyActivity.description.isEnabled = false
        chartMonthlyActivity.legend.isEnabled = false
        chartMonthlyActivity.setDrawGridBackground(false)
        chartMonthlyActivity.setDrawBarShadow(false)

        val xAxis = chartMonthlyActivity.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep"))

        chartMonthlyActivity.axisLeft.isEnabled = false
        chartMonthlyActivity.axisRight.setDrawGridLines(false)

        chartMonthlyActivity.invalidate()
    }

    private fun setupVaultCompositionChart() {
        val entries = ArrayList<PieEntry>()
        entries.add(PieEntry(45f, getString(R.string.books)))
        entries.add(PieEntry(35f, getString(R.string.videos)))
        entries.add(PieEntry(20f, getString(R.string.comics)))

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.accent_blue),
            ContextCompat.getColor(requireContext(), R.color.accent_cyan),
            ContextCompat.getColor(requireContext(), R.color.accent_purple)
        )
        dataSet.sliceSpace = 3f
        dataSet.setDrawValues(true)
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f

        val data = PieData(dataSet)
        chartVaultComposition.data = data
        chartVaultComposition.description.isEnabled = false
        chartVaultComposition.legend.isEnabled = true
        chartVaultComposition.isDrawHoleEnabled = true
        chartVaultComposition.setHoleColor(Color.TRANSPARENT)
        chartVaultComposition.setCenterTextSize(14f)
        chartVaultComposition.setEntryLabelColor(Color.WHITE)
        chartVaultComposition.animateY(1400)
        chartVaultComposition.invalidate()
    }
}