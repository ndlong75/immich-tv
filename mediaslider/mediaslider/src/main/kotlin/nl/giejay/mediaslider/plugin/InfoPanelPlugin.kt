package nl.giejay.mediaslider.plugin

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.zeuskartik.mediaslider.R
import kotlinx.coroutines.launch
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.model.SliderPerson

/**
 * Up / Menu toggles an info panel: the metadata overlay at the bottom plus, on the right,
 * avatars of the people in the current photo and a "Show in timeline" entry.
 * While open: Up/Down move the selection, OK activates it, Back/Menu (or Up on the first
 * entry) closes. Left/Right are swallowed so the photo does not change underneath.
 */
class InfoPanelPlugin(
    private val loadPeople: suspend (assetId: String) -> List<SliderPerson>,
    private val avatarUrl: (SliderPerson) -> String,
    private val onPerson: (SliderPerson) -> Unit,
    private val onShowInTimeline: (SliderItem) -> Unit
) : SliderViewPlugin<Unit>, SliderKeyEventPlugin {

    private var panel: LinearLayout? = null
    private var rootView: ConstraintLayout? = null
    private var current: SliderItem? = null
    private var people: List<SliderPerson> = emptyList()
    private val peopleCache = HashMap<String, List<SliderPerson>>()
    private var open = false
    private var selected = 0
    private val consumedDown = HashSet<Int>()

    private val total get() = people.size + 1 // people + "Show in timeline"

    override fun attachView(rootView: ConstraintLayout, state: Unit?) {
        this.rootView = rootView
        val ctx = rootView.context
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
            background = rounded(Color.parseColor("#B3000000"), dp(ctx, 16).toFloat())
        }
        rootView.addView(panel, ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            marginEnd = dp(ctx, 32)
        })
        this.panel = panel
        metadataHolder()?.visibility = View.GONE
    }

    override fun onPageSettled(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        sliderItem: SliderItemViewHolder,
        sliderItemIndex: Int,
        handler: Handler,
        state: Unit?
    ) {
        val item = sliderItem.mainItem
        if (current?.id != item.id) selected = 0
        current = item
        people = item.people.ifEmpty { peopleCache[item.id] ?: emptyList() }
        render()
        if (item.people.isEmpty() && !peopleCache.containsKey(item.id)) {
            context.ioScope.launch {
                val loaded = runCatching { loadPeople(item.id) }.getOrDefault(emptyList())
                peopleCache[item.id] = loaded
                panel?.post {
                    if (current?.id == item.id) {
                        people = loaded
                        render()
                    }
                }
            }
        }
    }

    override fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        val key = event.keyCode
        if (!open) {
            if (key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_MENU) return SliderKeyEventResult.UNHANDLED
            if (state.isControllerVisible || state.isSlideshowPlaying) return SliderKeyEventResult.UNHANDLED
            val zoomed = (state.controller.currentTouchImageView()?.currentZoom ?: 1f) > 1.05f
            if (zoomed && key == KeyEvent.KEYCODE_DPAD_UP) return SliderKeyEventResult.UNHANDLED // pan instead
            show()
            return consume(key)
        }
        when (key) {
            KeyEvent.KEYCODE_DPAD_UP -> if (selected == 0) hide() else select(selected - 1)
            KeyEvent.KEYCODE_DPAD_DOWN -> select((selected + 1).coerceAtMost(total - 1))
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> activate()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BACK -> hide()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> Unit
            else -> return SliderKeyEventResult.UNHANDLED
        }
        return consume(key)
    }

    override fun onKeyUp(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult =
        if (consumedDown.remove(event.keyCode)) SliderKeyEventResult.HANDLED_CONSUME else SliderKeyEventResult.UNHANDLED

    private fun consume(key: Int): SliderKeyEventResult {
        consumedDown.add(key)
        return SliderKeyEventResult.HANDLED_CONSUME
    }

    private fun show() {
        open = true
        selected = 0
        render()
        panel?.visibility = View.VISIBLE
        metadataHolder()?.visibility = View.VISIBLE
    }

    private fun hide() {
        open = false
        panel?.visibility = View.GONE
        metadataHolder()?.visibility = View.GONE
    }

    private fun select(index: Int) {
        selected = index
        render()
    }

    private fun activate() {
        val item = current ?: return hide()
        val person = people.getOrNull(selected)
        hide()
        if (person != null) onPerson(person) else onShowInTimeline(item)
    }

    private fun metadataHolder(): View? = rootView?.findViewById(R.id.meta_data_holder)

    /** Shows at most [WINDOW] entries around the selection so tall lists stay on screen. */
    private fun render() {
        val panel = panel ?: return
        val ctx = panel.context
        panel.removeAllViews()
        val first = (selected - WINDOW / 2).coerceIn(0, (total - WINDOW).coerceAtLeast(0))
        val last = (first + WINDOW).coerceAtMost(total)
        for (index in first until last) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
                background = if (open && index == selected) outline(dp(ctx, 12).toFloat(), dp(ctx, 3)) else null
            }
            val person = people.getOrNull(index)
            if (person != null) {
                row.addView(ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 72), dp(ctx, 72))
                    Glide.with(ctx).load(avatarUrl(person)).circleCrop().into(this)
                })
                row.addView(label(ctx, person.name?.takeIf { it.isNotBlank() } ?: "?", 12f, false))
            } else {
                row.addView(label(ctx, "Show in timeline", 14f, true))
            }
            panel.addView(row, LinearLayout.LayoutParams(dp(ctx, 110), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun label(ctx: Context, text: String, sizeSp: Float, bold: Boolean) = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = sizeSp
        gravity = Gravity.CENTER
        maxLines = 2
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun outline(radius: Float, strokePx: Int) = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        setStroke(strokePx, Color.WHITE)
        cornerRadius = radius
    }

    private fun dp(ctx: Context, value: Int) = (value * ctx.resources.displayMetrics.density).toInt()

    private companion object {
        const val WINDOW = 5
    }
}
