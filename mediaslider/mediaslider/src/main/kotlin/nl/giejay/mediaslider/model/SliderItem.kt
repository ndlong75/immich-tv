package nl.giejay.mediaslider.model

import java.util.Objects

interface MetaDataProvider {
    suspend fun getValue(): String?
}

class StaticMetaDataProvider(private val value: String?) : MetaDataProvider {
    override suspend fun getValue(): String? = value
}

data class SliderPerson(val id: String, val name: String?)

/** Detail shown by the info panel: people in the photo and "label" to "value" lines. */
data class SliderInfo(val people: List<SliderPerson>, val lines: List<Pair<String, String>>)

class SliderItem(
    var id: String,
    val url: String?,
    val type: SliderItemType,
    val orientation: Int,
    private val metaData: Map<MetaDataType, MetaDataProvider>,
    val thumbnailUrl: String?,
    val isPanorama: Boolean,
    var isFavorite: Boolean = false,
    val people: List<SliderPerson> = emptyList(),
    /** Capture time in epoch millis, used to jump to the matching day in the timeline. */
    val takenAt: Long? = null
) {
    suspend fun get(metaDataType: MetaDataType): String? {
        return this.metaData[metaDataType]?.getValue()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as SliderItem
        return url == that.url
    }

    override fun hashCode(): Int {
        return Objects.hash(url)
    }
}
