package com.ntech.nkara.data.backup

import com.ntech.nkara.data.local.FavoriteSongEntity
import com.ntech.nkara.data.local.FavoriteSongsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class DriveBackupRepository @Inject constructor(
    private val favoritesRepository: FavoriteSongsRepository,
) {
    private val client = OkHttpClient()

    suspend fun backup(accessToken: String): Int = withContext(Dispatchers.IO) {
        val favorites = favoritesRepository.favorites.first()
        val body = JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("favorites", JSONArray(favorites.map { item ->
                JSONObject().apply {
                    put("videoId", item.videoId)
                    put("title", item.title)
                    put("addedAt", item.addedAtEpochMillis)
                }
            }))
        }.toString()
        val existingId = findBackupId(accessToken)
        if (existingId == null) createBackupFile(accessToken, body) else updateBackupFile(accessToken, existingId, body)
        favorites.size
    }

    suspend fun restore(accessToken: String): Int = withContext(Dispatchers.IO) {
        val fileId = findBackupId(accessToken) ?: error("Chưa có bản sao lưu trên Google Drive.")
        val request = authorizedRequest("https://www.googleapis.com/drive/v3/files/$fileId?alt=media", accessToken).build()
        val json = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Không tải được bản sao lưu (${response.code}).")
            JSONObject(response.body.string())
        }
        if (json.optInt("version") != BACKUP_VERSION) error("Phiên bản sao lưu chưa được hỗ trợ.")
        val array = json.optJSONArray("favorites") ?: JSONArray()
        val items = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val videoId = item.optString("videoId")
                val title = item.optString("title")
                if (videoId.matches(Regex("[A-Za-z0-9_-]{11}")) && title.isNotBlank()) {
                    add(FavoriteSongEntity(videoId, title, item.optLong("addedAt", System.currentTimeMillis())))
                }
            }
        }
        favoritesRepository.replaceAll(items)
        items.size
    }

    private fun findBackupId(accessToken: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name%3D%27$BACKUP_FILE_NAME%27&orderBy=modifiedTime%20desc&fields=files(id,name,modifiedTime)&pageSize=1"
        val request = authorizedRequest(url, accessToken).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Không truy cập được Google Drive (${response.code}).")
            JSONObject(response.body.string()).optJSONArray("files")?.optJSONObject(0)?.optString("id")?.takeIf(String::isNotBlank)
        }
    }

    private fun createBackupFile(accessToken: String, content: String) {
        val metadata = JSONObject().apply {
            put("name", BACKUP_FILE_NAME)
            put("parents", JSONArray().put("appDataFolder"))
            put("mimeType", JSON_MEDIA_TYPE.toString())
        }.toString()
        val boundary = "nkara-${System.nanoTime()}"
        val multipart = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n")
            append("--$boundary\r\nContent-Type: application/json\r\n\r\n$content\r\n")
            append("--$boundary--")
        }
        val request = authorizedRequest("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", accessToken)
            .post(multipart.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()
        executeWrite(request)
    }

    private fun updateBackupFile(accessToken: String, fileId: String, content: String) {
        val request = authorizedRequest("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media", accessToken)
            .patch(content.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        executeWrite(request)
    }

    private fun executeWrite(request: Request) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Không thể ghi bản sao lưu (${response.code}).")
        }
    }

    private fun authorizedRequest(url: String, accessToken: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $accessToken")

    private companion object {
        const val BACKUP_VERSION = 1
        const val BACKUP_FILE_NAME = "nkara-backup.json"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
