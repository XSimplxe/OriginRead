package com.yuanyue.reader.data

import com.yuanyue.reader.core.db.ReaderAnnotationDao
import com.yuanyue.reader.model.AnnotationDraft
import com.yuanyue.reader.model.ReaderAnnotationEntity
import kotlinx.coroutines.flow.Flow

class AnnotationRepository(private val dao: ReaderAnnotationDao) {
    fun observe(bookKey: String): Flow<List<ReaderAnnotationEntity>> =
        dao.observeByBook(bookKey)

    suspend fun add(bookKey: String, draft: AnnotationDraft): Long =
        dao.insert(
            ReaderAnnotationEntity(
                bookKey = bookKey,
                chapterIndex = draft.chapterIndex,
                pageIndex = draft.pageIndex,
                paragraphIndex = draft.paragraphIndex,
                text = draft.text,
                note = draft.note,
                type = draft.type.name,
                color = draft.color,
            ),
        )

    suspend fun delete(id: Long) = dao.delete(id)
}
