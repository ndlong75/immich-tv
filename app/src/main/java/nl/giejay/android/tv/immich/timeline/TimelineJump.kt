package nl.giejay.android.tv.immich.timeline

/** Set by the photo viewer's "Show in timeline"; consumed once by HomeFragment to select the Timeline page. */
object TimelineJump {
    @Volatile
    var requested = false
}
