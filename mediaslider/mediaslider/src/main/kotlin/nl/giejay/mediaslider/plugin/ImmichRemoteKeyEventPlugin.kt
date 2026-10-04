package nl.giejay.mediaslider.plugin

import android.view.KeyEvent
import nl.giejay.mediaslider.model.SliderItemType

/**
 * D-pad zoom/pan for photos and the Menu-button metadata toggle:
 * - OK on a photo: zoom 1x → 2x → 4x → 1x
 * - Arrow keys while zoomed: pan in all four directions
 * - Back while zoomed: reset zoom
 * - Menu: show / hide the metadata overlay
 */
class ImmichRemoteKeyEventPlugin : SliderKeyEventPlugin {

    override fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        val isOk = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            val ok = state.controller.toggleMetadataOverlay()
            state.controller.debugToast("DIAG MENU holder=${if (ok) "found" else "MISSING"}")
            return if (ok) SliderKeyEventResult.HANDLED_CONSUME else SliderKeyEventResult.UNHANDLED
        }
        if (isOk) {
            state.controller.debugToast("DIAG OK type=${state.currentItemType} slideshow=${state.isSlideshowPlaying} controller=${state.isControllerVisible} img=${state.controller.currentTouchImageView()?.currentZoom}")
        }
        if (state.currentItemType != SliderItemType.IMAGE || state.isSlideshowPlaying || state.isControllerVisible) {
            return SliderKeyEventResult.UNHANDLED
        }
        val image = state.controller.currentTouchImageView() ?: return SliderKeyEventResult.UNHANDLED

        if (image.currentZoom <= 1.05f) {
            if (isOk) {
                image.setZoom(2f)
                return SliderKeyEventResult.HANDLED_CONSUME
            }
            return SliderKeyEventResult.UNHANDLED
        }

        val r = image.zoomedRect
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> image.setScrollPosition(r.centerX(), (r.centerY() - PAN_STEP).coerceIn(0f, 1f))
            KeyEvent.KEYCODE_DPAD_DOWN -> image.setScrollPosition(r.centerX(), (r.centerY() + PAN_STEP).coerceIn(0f, 1f))
            KeyEvent.KEYCODE_DPAD_LEFT -> image.setScrollPosition((r.centerX() - PAN_STEP).coerceIn(0f, 1f), r.centerY())
            KeyEvent.KEYCODE_DPAD_RIGHT -> image.setScrollPosition((r.centerX() + PAN_STEP).coerceIn(0f, 1f), r.centerY())
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                if (image.currentZoom < 3f) image.setZoom(4f) else image.resetZoom()
            KeyEvent.KEYCODE_BACK -> image.resetZoom()
            else -> return SliderKeyEventResult.UNHANDLED
        }
        return SliderKeyEventResult.HANDLED_CONSUME
    }

    private companion object {
        const val PAN_STEP = 0.1f
    }
}
