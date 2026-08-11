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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(song: FavoriteSongEntity)

    @Delete
    suspend fun deleteFavorite(song: FavoriteSongEntity)

    @Insert
    suspend fun insertHistory(entry: SingingHistoryEntity)

    @Query("SELECT * FROM singing_history ORDER BY playedAtEpochMillis DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<SingingHistoryEntity>>
}
