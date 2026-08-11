package com.ntech.nkara.di

import android.content.Context
import androidx.room.Room
import com.ntech.nkara.data.local.KaraDatabase
import com.ntech.nkara.data.local.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KaraDatabase =
        Room.databaseBuilder(context, KaraDatabase::class.java, "kara.db").build()

    @Provides
    fun provideSongDao(database: KaraDatabase): SongDao = database.songDao()
}
