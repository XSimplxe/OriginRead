package com.yuanyue.reader.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reader_annotations",
    indices = [Index(value = ["bookKey", "chapterIndex", "pageIndex"])],
)
data class ReaderAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookKey: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val paragraphIndex: Int = -1,
    val text: String,
    val note: String? = null,
    val type: String = "HIGHLIGHT",
    val color: Long = 0x66FFEB3B,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class AnnotationType(val label: String) {
    HIGHLIGHT("高亮"),
    UNDERLINE("划线"),
    NOTE("笔记"),
    BOOKMARK("书签"),
}

data class AnnotationDraft(
    val chapterIndex: Int,
    val pageIndex: Int,
    val paragraphIndex: Int = -1,
    val text: String,
    val type: AnnotationType,
    val note: String? = null,
    val color: Long = 0x66FFEB3B,
)
