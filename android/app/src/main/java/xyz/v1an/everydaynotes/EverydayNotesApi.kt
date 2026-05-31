package xyz.v1an.everydaynotes

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AssetDto(
    val id: String,
    val kind: String,
    val mimeType: String?,
    val url: String
)

data class NoteDto(
    val id: String,
    val type: String,
    val title: String?,
    val author: String?,
    val remark: String?,
    val status: String,
    val createdAt: String,
    val tags: List<String>,
    val assets: List<AssetDto>
)

class EverydayNotesApi(
    private val context: Context,
    private val baseUrl: String,
    private val token: String? = null
) {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun login(password: String, deviceName: String = "android"): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("password", password)
            .put("device_name", deviceName)
            .toString()
            .toRequestBody(jsonMedia)
        val response = executeJson(Request.Builder().url(endpoint("/auth/login")).post(payload).build())
        response.getString("access_token")
    }

    suspend fun listNotes(query: String = ""): List<NoteDto> = withContext(Dispatchers.IO) {
        val url = if (query.isBlank()) endpoint("/notes") else endpoint("/notes?query=${Uri.encode(query)}")
        executeJson(authenticated(Request.Builder().url(url))).getJSONArray("items").toNotes()
    }

    suspend fun randomNote(): NoteDto = withContext(Dispatchers.IO) {
        parseNote(executeJson(authenticated(Request.Builder().url(endpoint("/notes/random")))))
    }

    suspend fun captureDouyin(shareText: String, remark: String = ""): NoteDto = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("share_text", shareText)
            .put("remark", remark)
            .put("tags", JSONArray())
            .toString()
            .toRequestBody(jsonMedia)
        parseNote(executeJson(authenticated(Request.Builder().url(endpoint("/captures/douyin")).post(payload))))
    }

    suspend fun uploadScreenshot(uri: Uri, remark: String = ""): NoteDto = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read screenshot")
        val mimeType = resolver.getType(uri) ?: "image/png"
        val fileBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("remark", remark)
            .addFormDataPart("file", "screenshot.png", fileBody)
            .build()
        parseNote(executeJson(authenticated(Request.Builder().url(endpoint("/captures/screenshot")).post(body))))
    }

    fun assetUrl(asset: AssetDto): String {
        val clean = asset.url.removePrefix("/api")
        val separator = if (clean.contains("?")) "&" else "?"
        return "${baseUrl.trimEnd('/')}$clean${separator}token=${Uri.encode(token ?: "")}"
    }

    private fun endpoint(path: String): String {
        val clean = path.removePrefix("/api")
        return "${baseUrl.trimEnd('/')}${if (clean.startsWith('/')) clean else "/$clean"}"
    }

    private fun authenticated(builder: Request.Builder): Request {
        val actualToken = token ?: error("Missing token")
        return builder.headers(Headers.headersOf("Authorization", "Bearer $actualToken")).build()
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(text.ifBlank { "HTTP ${response.code}" })
            return JSONObject(text)
        }
    }
}

private fun JSONArray.toNotes(): List<NoteDto> {
    return (0 until length()).map { parseNote(getJSONObject(it)) }
}

private fun parseNote(obj: JSONObject): NoteDto {
    return NoteDto(
        id = obj.getString("id"),
        type = obj.getString("type"),
        title = obj.optString("title").ifBlank { null },
        author = obj.optString("author").ifBlank { null },
        remark = obj.optString("remark").ifBlank { null },
        status = obj.optString("status", "ready"),
        createdAt = obj.optString("created_at"),
        tags = obj.optJSONArray("tags")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty(),
        assets = obj.optJSONArray("assets")?.let { array ->
            (0 until array.length()).map {
                val item = array.getJSONObject(it)
                AssetDto(
                    id = item.getString("id"),
                    kind = item.getString("kind"),
                    mimeType = item.optString("mime_type").ifBlank { null },
                    url = item.getString("url")
                )
            }
        }.orEmpty()
    )
}

