package com.ntech.nkara.data.local

import com.ntech.nkara.data.remote.YouTubeSearchVideo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class FavoriteSongsRepository @Inject constructor(
    private val songDao: SongDao,
) {
    val favorites: Flow<List<FavoriteSongEntity>> = songDao.observeFavorites()

    suspend fun toggle(video: YouTubeSearchVideo) {
        if (songDao.favoriteById(video.videoId) == null) {
            songDao.upsertFavorite(
                FavoriteSongEntity(video.videoId, video.title, System.currentTimeMillis()),
            )
        } else {
            songDao.deleteFavoriteById(video.videoId)
        }
    }

    suspend fun replaceAll(items: List<FavoriteSongEntity>) {
        songDao.replaceFavorites(items)
    }

    suspend fun remove(videoId: String) {
        songDao.deleteFavoriteById(videoId)
    }
}
