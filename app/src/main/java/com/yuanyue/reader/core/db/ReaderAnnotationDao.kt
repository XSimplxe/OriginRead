package com.yuanyue.reader.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yuanyue.reader.model.ReaderAnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderAnnotationDao {
    @Query("SELECT * FROM reader_annotations WHERE bookKey = :bookKey ORDER BY chapterIndex ASC, pageIndex ASC, paragraphIndex ASC, createdAt ASC")
    fun observeByBook(bookKey: String): Flow<List<ReaderAnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: ReaderAnnotationEntity): Long

    @Query("DELETE FROM reader_annotations WHERE id = :id")
    suspend fun delete(id: Long)
}
