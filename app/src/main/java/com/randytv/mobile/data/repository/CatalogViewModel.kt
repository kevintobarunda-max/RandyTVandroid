package com.randytv.mobile.data.repository

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.randytv.mobile.data.catalog.CatalogDiskCache
import java.io.File

/** Retain indexes across keyboard/configuration recreation; disk restores after process death. */
class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    val repository = DataRepository(cache = CatalogDiskCache(File(application.noBackupFilesDir, "catalog-v1")))
    init { repository.loadAll() }
    override fun onCleared() { repository.cancel() }
}
