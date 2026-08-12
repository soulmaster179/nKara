package com.ntech.nkara.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("drive_backup", Context.MODE_PRIVATE)

    var isAutoBackupEnabled: Boolean
        get() = preferences.getBoolean(AUTO_BACKUP, false)
        set(value) { preferences.edit().putBoolean(AUTO_BACKUP, value).apply() }

    var lastSyncEpochMillis: Long
        get() = preferences.getLong(LAST_SYNC, 0L)
        set(value) { preferences.edit().putLong(LAST_SYNC, value).apply() }

    private companion object {
        const val AUTO_BACKUP = "auto_backup"
        const val LAST_SYNC = "last_sync"
    }
}
