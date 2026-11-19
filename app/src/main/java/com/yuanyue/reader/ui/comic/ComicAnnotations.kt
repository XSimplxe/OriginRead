package com.yuanyue.reader.ui.comic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.model.AnnotationDraft
import com.yuanyue.reader.model.AnnotationType
import com.yuanyue.reader.model.ReaderAnnotationEntity

data class ComicAnnotationDraft(
    val pageIndex: Int,
    val type: AnnotationType,
    val note: String? = null,
    val points: String? = null,
    val color: Long = 0x66FFEB3B,
)

@Composable
fun ComicDrawingOverlay(
    annotations: List<ReaderAnnotationEntity>,
    pageIndex: Int,
    drawMode: Boolean,
    onSaveStroke: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var stroke by remember(pageIndex) { mutableStateOf<List<Offset>>(emptyList()) }
    val strokes = remember(annotations, pageIndex) {
        annotations.filter { it.pageIndex == pageIndex && it.type == AnnotationType.DRAW.name }
            .mapNotNull { parsePoints(it.note) }
    }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (drawMode) {
                    Modifier.pointerInput(pageIndex, drawMode) {
                        detectDragGestures(
                            onDragStart = { stroke = listOf(it) },
                            onDrag = { change, _ ->
                                change.consume()
                                stroke = stroke + change.position
                            },
                            onDragEnd = {
                                if (stroke.size > 1) onSaveStroke(serializePoints(stroke, size.width, size.height))
                                stroke = emptyList()
                            },
                            onDragCancel = { stroke = emptyList() },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        annotations.filter { it.pageIndex == pageIndex && it.type == AnnotationType.DRAW.name }
            .forEach { annotation ->
                val points = parsePoints(annotation.note).orEmpty()
                if (points.isEmpty()) return@forEach
                val path = Path()
                points.forEachIndexed { index, point ->
                    val offset = Offset(point.x * size.width, point.y * size.height)
                    if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                }
                drawPath(
                    path = path,
                    color = Color(annotation.color),
                    style = Stroke(width = 5f),
                )
            }
        if (stroke.size > 1) {
            val path = Path()
            stroke.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path = path, color = Color.Black, style = Stroke(width = 5f))
        }
    }
}

@Composable
fun ComicNoteDialog(
    pageIndex: Int,
    existing: List<ReaderAnnotationEntity>,
    onDismiss: () -> Unit,
    onSave: (ComicAnnotationDraft) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var note by remember(pageIndex) {
        mutableStateOf(existing.firstOrNull { it.type == AnnotationType.NOTE.name }?.note.orEmpty())
    }
    val pageAnnotations = existing.filter { it.pageIndex == pageIndex }
    val hasHighlight = pageAnnotations.any { it.type == AnnotationType.HIGHLIGHT.name }
    val hasNote = pageAnnotations.any { it.type == AnnotationType.NOTE.name }
    val hasDraw = pageAnnotations.any { it.type == AnnotationType.DRAW.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("漫画第 ${pageIndex + 1} 页") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("页面笔记") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "高亮和自由划线会叠加在图片上；点击上方铅笔按钮随时涂鸦。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hasNote || hasHighlight || hasDraw) {
                    TextButton(
                        onClick = {
                            pageAnnotations.forEach { onDelete(it.id) }
                            onDismiss()
                        },
                    ) {
                        Text("清除本页标注", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(
                    onClick = {
                        onSave(
                            ComicAnnotationDraft(
                                pageIndex = pageIndex,
                                type = if (note.isNotBlank()) AnnotationType.NOTE else AnnotationType.HIGHLIGHT,
                                note = note.trim().ifBlank { null },
                                color = 0x66FFEB3B,
                            ),
                        )
                        onDismiss()
                    },
                ) {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (hasHighlight) {
                        pageAnnotations.filter { it.type == AnnotationType.HIGHLIGHT.name }
                            .forEach { onDelete(it.id) }
                    } else {
                        onSave(
                            ComicAnnotationDraft(
                                pageIndex = pageIndex,
                                type = AnnotationType.HIGHLIGHT,
                                color = 0x66FFEB3B,
                            ),
                        )
                    }
                    onDismiss()
                },
            ) {
                Text(if (hasHighlight) "取消高亮" else "高亮本页")
            }
        },
    )
}

private fun serializePoints(points: List<Offset>, width: Int, height: Int): String =
    points.joinToString(";") {
        "${(it.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)}," +
            "${(it.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)}"
    }

private fun parsePoints(raw: String?): List<Offset>? {
    if (raw.isNullOrBlank()) return null
    return raw.split(';').mapNotNull { point ->
        val parts = point.split(',')
        val x = parts.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
        val y = parts.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        Offset(x, y)
    }.takeIf { it.isNotEmpty() }
}
