package nl.giejay.android.tv.immich.slider

import nl.giejay.android.tv.immich.api.model.Asset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Turns an [Asset] into "label" to "value" lines for the viewer's info panel; skips missing fields. */
object AssetInfoFormatter {

    fun lines(asset: Asset): List<Pair<String, String>> {
        val exif = asset.exifInfo
        val out = mutableListOf<Pair<String, String>>()

        (exif?.dateTimeOriginal ?: asset.fileCreatedAt ?: asset.fileModifiedAt)?.let { date ->
            val format = SimpleDateFormat("EEEE, d MMMM yyyy  HH:mm", Locale.getDefault())
            exif?.timeZone?.let { tz -> format.timeZone = TimeZone.getTimeZone(tz.replace("UTC", "GMT")) }
            out += "Date" to format.format(date)
        }

        val place = listOfNotNull(exif?.city, exif?.state, exif?.country).filter { it.isNotBlank() }.joinToString(", ")
        val coordinates = if (exif?.latitude != null && exif.longitude != null) {
            String.format(Locale.US, "%.5f, %.5f", exif.latitude, exif.longitude)
        } else null
        listOfNotNull(place.ifBlank { null }, coordinates?.let { "($it)" }).joinToString(" ").ifBlank { null }
            ?.let { out += "Location" to it }

        val make = exif?.make?.trim().orEmpty()
        val model = exif?.model?.trim().orEmpty()
        val camera = if (model.startsWith(make, ignoreCase = true)) model else "$make $model".trim()
        if (camera.isNotBlank()) out += "Camera" to camera
        exif?.lensModel?.takeIf { it.isNotBlank() }?.let { out += "Lens" to it }

        val settings = listOfNotNull(
            exif?.fNumber?.let { "f/${trim(it)}" },
            exif?.exposureTime?.takeIf { it.isNotBlank() }?.let { "$it s" },
            exif?.iso?.let { "ISO $it" },
            exif?.focalLength?.let { "${trim(it)} mm" }
        )
        if (settings.isNotEmpty()) out += "Exposure" to settings.joinToString("  ·  ")

        if (exif?.exifImageWidth != null && exif.exifImageHeight != null) {
            val megapixels = exif.exifImageWidth.toLong() * exif.exifImageHeight / 1_000_000.0
            out += "Resolution" to "${exif.exifImageWidth} × ${exif.exifImageHeight}  (${String.format(Locale.US, "%.1f", megapixels)} MP)"
        }
        exif?.fileSizeInByte?.let { out += "Size" to humanSize(it) }
        asset.originalFileName?.takeIf { it.isNotBlank() }?.let { out += "File" to it }
        exif?.description?.takeIf { it.isNotBlank() }?.let { out += "Description" to it }
        return out
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }
}
