package com.yuanyue.reader.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.yuanyue.reader.data.UpdateInfo

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 ${info.version}") },
        text = {
            Text(
                info.notes.ifBlank { "有新的版本可用，建议更新后继续使用。" },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                    }
                    onDismiss()
                },
            ) {
                Text("去更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        },
    )
}
