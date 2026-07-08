package com.example.data

import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()

    suspend fun getDownloadById(id: Int): DownloadItem? {
        return downloadDao.getDownloadById(id)
    }

    suspend fun insertDownload(item: DownloadItem): Long {
        return downloadDao.insertDownload(item)
    }

    suspend fun updateDownload(item: DownloadItem) {
        downloadDao.updateDownload(item)
    }

    suspend fun deleteDownload(item: DownloadItem) {
        downloadDao.deleteDownload(item)
    }

    suspend fun clearAllDownloads() {
        downloadDao.clearAllDownloads()
    }
}
