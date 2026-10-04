package nl.giejay.android.tv.immich.timeline

/** Set by the photo viewer's "Show in timeline"; consumed once by HomeFragment to select the Timeline page. */
object TimelineJump {
    /** HomeFragment should select the Timeline page. */
    @Volatile
    var requested = false

    /** TimelineFragment should keep retrying to scroll to and focus [TimelineViewModel.pendingResumeAssetId]. */
    @Volatile
    var scrollPending = false
}
