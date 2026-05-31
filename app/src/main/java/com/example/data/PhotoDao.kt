package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photo_entries ORDER BY timestamp DESC")
    fun getAllPhotosFlow(): Flow<List<PhotoEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntry): Long

    @Delete
    suspend fun deletePhoto(photo: PhotoEntry)

    @Query("DELETE FROM photo_entries WHERE id = :id")
    suspend fun deletePhotoById(id: Int)
}
