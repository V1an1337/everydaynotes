package xyz.v1an.everydaynotes

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class CaptureCandidates(
    val douyinText: String? = null,
    val douyinUrl: String? = null,
    val douyinCopiedTimeMillis: Long? = null,
    val screenshotUri: Uri? = null,
    val screenshotName: String? = null,
    val screenshotTimeMillis: Long? = null
) {
    val isEmpty: Boolean get() = douyinText == null && screenshotUri == null
}

object CaptureCandidateDetector {
    fun detect(context: Context): CaptureCandidates {
        val douyin = readDouyinClipboard(context)
        val screenshot = findRecentScreenshot(context)
        return CaptureCandidates(
            douyinText = douyin?.text,
            douyinUrl = douyin?.url,
            douyinCopiedTimeMillis = douyin?.copiedTimeMillis,
            screenshotUri = screenshot?.uri,
            screenshotName = screenshot?.name,
            screenshotTimeMillis = screenshot?.timeMillis
        )
    }

    fun readDouyinClipboard(context: Context): DouyinClipboardCandidate? {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip ?: return null
        val description = clipboard.primaryClipDescription ?: clip.description
        val hasText = description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
            description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true
        if (!hasText || clip.itemCount <= 0) return null

        val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
        val url = DouyinUrlExtractor.extract(text) ?: return null
        return DouyinClipboardCandidate(
            text = text,
            url = url,
            copiedTimeMillis = description?.timestamp?.takeIf { it > 0L }
        )
    }

    fun findRecentScreenshot(context: Context): ScreenshotCandidate? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val nowSeconds = System.currentTimeMillis() / 1000
            var checked = 0
            while (cursor.moveToNext() && checked < MAX_IMAGES_TO_SCAN) {
                checked += 1
                val id = cursor.getLong(0)
                val name = cursor.getString(1).orEmpty()
                val relativePath = cursor.getString(2).orEmpty()
                val dateAdded = cursor.getLong(3)
                if (nowSeconds - dateAdded > RECENT_SCREENSHOT_WINDOW_SECONDS) continue
                if (!isScreenshotPath("$name/$relativePath".lowercase())) continue
                return ScreenshotCandidate(
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    name = name.ifBlank { null },
                    timeMillis = dateAdded * 1000
                )
            }
        }
        return null
    }

    private fun isScreenshotPath(value: String): Boolean {
        return value.contains("screenshot") ||
            value.contains("screen_shot") ||
            value.contains("\u622a\u5c4f") ||
            value.contains("\u622a\u56fe")
    }

    private const val MAX_IMAGES_TO_SCAN = 50
    private const val RECENT_SCREENSHOT_WINDOW_SECONDS = 2 * 60 * 60
}

data class ScreenshotCandidate(
    val uri: Uri,
    val name: String?,
    val timeMillis: Long
)

data class DouyinClipboardCandidate(
    val text: String,
    val url: String,
    val copiedTimeMillis: Long?
)
