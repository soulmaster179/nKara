package com.ntech.nkara.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FavoriteSongEntity::class, SingingHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class KaraDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
}
