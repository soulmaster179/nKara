package com.ntech.nkara.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM favorite_songs ORDER BY addedAtEpochMillis DESC")
    fun observeFavorites(): Flow<List<FavoriteSongEntity>>

    @Query("SELECT * FROM favorite_songs WHERE videoId = :videoId LIMIT 1")
    suspend fun favoriteById(videoId: String): FavoriteSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(song: FavoriteSongEntity)

    @Delete
    suspend fun deleteFavorite(song: FavoriteSongEntity)

    @Query("DELETE FROM favorite_songs WHERE videoId = :videoId")
    suspend fun deleteFavoriteById(videoId: String)

    @Query("DELETE FROM favorite_songs")
    suspend fun clearFavorites()

    @androidx.room.Transaction
    suspend fun replaceFavorites(songs: List<FavoriteSongEntity>) {
        clearFavorites()
        songs.forEach { upsertFavorite(it) }
    }

    @Insert
    suspend fun insertHistory(entry: SingingHistoryEntity)

    @Query("SELECT * FROM singing_history ORDER BY playedAtEpochMillis DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<SingingHistoryEntity>>
}
