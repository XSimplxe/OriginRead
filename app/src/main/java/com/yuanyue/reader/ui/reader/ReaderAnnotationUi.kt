package com.yuanyue.reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.model.AnnotationDraft
import com.yuanyue.reader.model.AnnotationType
import com.yuanyue.reader.model.ReaderAnnotationEntity

data class AnnotationTarget(
    val chapterIndex: Int,
    val pageIndex: Int,
    val paragraphIndex: Int,
    val text: String,
)

@Composable
fun AnnotationActionDialog(
    target: AnnotationTarget,
    onDismiss: () -> Unit,
    onSave: (AnnotationDraft) -> Unit,
) {
    var noteMode by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (noteMode) "写笔记" else "标记这段文字") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = target.text.take(180),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (noteMode) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("笔记") },
                        minLines = 2,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                onSave(
                                    AnnotationDraft(
                                        chapterIndex = target.chapterIndex,
                                        pageIndex = target.pageIndex,
                                        paragraphIndex = target.paragraphIndex,
                                        text = target.text,
                                        type = AnnotationType.HIGHLIGHT,
                                    ),
                                )
                                onDismiss()
                            },
                        ) { Text("高亮") }
                        TextButton(
                            onClick = {
                                onSave(
                                    AnnotationDraft(
                                        chapterIndex = target.chapterIndex,
                                        pageIndex = target.pageIndex,
                                        paragraphIndex = target.paragraphIndex,
                                        text = target.text,
                                        type = AnnotationType.UNDERLINE,
                                    ),
                                )
                                onDismiss()
                            },
                        ) { Text("划线") }
                        TextButton(onClick = { noteMode = true }) { Text("笔记") }
                        TextButton(
                            onClick = {
                                onSave(
                                    AnnotationDraft(
                                        chapterIndex = target.chapterIndex,
                                        pageIndex = target.pageIndex,
                                        paragraphIndex = target.paragraphIndex,
                                        text = target.text,
                                        type = AnnotationType.BOOKMARK,
                                    ),
                                )
                                onDismiss()
                            },
                        ) { Text("书签") }
                    }
                }
            }
        },
        confirmButton = {
            if (noteMode) {
                Button(
                    onClick = {
                        onSave(
                            AnnotationDraft(
                                chapterIndex = target.chapterIndex,
                                pageIndex = target.pageIndex,
                                paragraphIndex = target.paragraphIndex,
                                text = target.text,
                                type = AnnotationType.NOTE,
                                note = note.trim().ifBlank { null },
                            ),
                        )
                        onDismiss()
                    },
                ) { Text("保存笔记") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationsSheet(
    annotations: List<ReaderAnnotationEntity>,
    onDismiss: () -> Unit,
    onJump: (ReaderAnnotationEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onAddBookmark: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = "笔记与书签",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onAddBookmark) { Text("添加书签") }
            }
            if (annotations.isEmpty()) {
                Text(
                    text = "在阅读页长按文字，可以高亮、划线、写笔记或添加书签。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(annotations, key = { it.id }) { annotation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJump(annotation) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (annotation.type) {
                                        AnnotationType.BOOKMARK.name -> "书签"
                                        AnnotationType.NOTE.name -> "笔记"
                                        AnnotationType.UNDERLINE.name -> "划线"
                                        else -> "高亮"
                                    },
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    text = annotation.text,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                annotation.note?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        text = it,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                    )
                                }
                            }
                            TextButton(onClick = { onDelete(annotation.id) }) {
                                Text("删除")
                            }
                        }
                    }
                }
            }
        }
    }
}
