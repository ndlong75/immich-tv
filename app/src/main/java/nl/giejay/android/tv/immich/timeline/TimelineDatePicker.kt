package nl.giejay.android.tv.immich.timeline

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import java.text.NumberFormat
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** "Jump to date" dialog: years on the left, months of the highlighted year on the right. */
object TimelineDatePicker {

    private data class MonthEntry(val bucketKey: String, val month: Month, val count: Int)

    fun show(context: Context, buckets: List<TimeBucketSummary>, onPick: (bucketKey: String) -> Unit): Dialog {
        val byYear: Map<Int, List<MonthEntry>> = buckets
            .mapNotNull { bucket ->
                runCatching { YearMonth.parse(bucket.timeBucket.take(7)) }.getOrNull()?.let { it to bucket }
            }
            .groupBy({ it.first.year }) { (ym, bucket) -> MonthEntry(bucket.timeBucket, ym.month, bucket.count) }
            .mapValues { (_, months) -> months.sortedByDescending { it.month.value } }
        val years = byYear.keys.sortedDescending()
        val numbers = NumberFormat.getInstance()

        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)

        val yearList = list(context)
        val monthList = list(context)
        var shownMonths: List<MonthEntry> = emptyList()

        yearList.adapter = adapter(context, years.map { year ->
            "$year   (${numbers.format(byYear.getValue(year).sumOf { it.count })})"
        })
        fun showMonths(yearIndex: Int) {
            shownMonths = byYear[years.getOrNull(yearIndex)].orEmpty()
            monthList.adapter = adapter(context, shownMonths.map {
                "${it.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}   (${numbers.format(it.count)})"
            })
        }
        yearList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = showMonths(position)
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        yearList.setOnItemClickListener { _, _, _, _ -> monthList.requestFocus() }
        monthList.setOnItemClickListener { _, _, position, _ ->
            shownMonths.getOrNull(position)?.let {
                dialog.dismiss()
                onPick(it.bucketKey)
            }
        }
        showMonths(0)

        val lists = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(yearList, LinearLayout.LayoutParams(dp(context, 260), dp(context, 420)))
            addView(monthList, LinearLayout.LayoutParams(dp(context, 300), dp(context, 420)).apply { marginStart = dp(context, 16) })
        }
        val title = TextView(context).apply {
            text = "Jump to date"
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(0, 0, 0, dp(context, 16))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 28), dp(context, 24), dp(context, 28), dp(context, 24))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2202020"))
                cornerRadius = dp(context, 16).toFloat()
            }
            addView(title)
            addView(lists)
        }
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        yearList.requestFocus()
        return dialog
    }

    private fun list(context: Context) = ListView(context).apply {
        divider = null
        selector = ColorDrawable(Color.parseColor("#55FFFFFF"))
        isFocusable = true
        isVerticalScrollBarEnabled = false
    }

    private fun adapter(context: Context, labels: List<String>) =
        object : ArrayAdapter<String>(context, 0, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (convertView as? TextView ?: TextView(context)).apply {
                    text = getItem(position)
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
                }
        }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
