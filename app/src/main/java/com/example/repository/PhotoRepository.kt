package com.example.repository

import com.example.data.PhotoDao
import com.example.data.PhotoEntry
import kotlinx.coroutines.flow.Flow

class PhotoRepository(private val photoDao: PhotoDao) {
    val allPhotosFlow: Flow<List<PhotoEntry>> = photoDao.getAllPhotosFlow()

    suspend fun insertPhoto(photo: PhotoEntry): Long {
        return photoDao.insertPhoto(photo)
    }

    suspend fun deletePhoto(photo: PhotoEntry) {
        photoDao.deletePhoto(photo)
    }

    suspend fun deletePhotoById(id: Int) {
        photoDao.deletePhotoById(id)
    }
}
