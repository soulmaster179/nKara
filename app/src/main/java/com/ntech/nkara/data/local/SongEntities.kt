package com.ntech.nkara.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val addedAtEpochMillis: Long,
)

@Entity(tableName = "singing_history")
data class SingingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val playedAtEpochMillis: Long,
)
