package com.reinchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.reinchat.app.update.AppUpdate
import com.reinchat.app.update.UpdateDownloadState
import com.reinchat.app.ui.theme.ReinColors

@Composable
fun UpdateDialog(
    update: AppUpdate,
    currentVersion: String,
    download: UpdateDownloadState,
    onDownload: () -> Unit,
    onBrowse: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!update.mandatory) onDismiss() },
        title = {
            Text(
                text = if (update.mandatory) "必须更新" else "发现新版本",
                color = ReinColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "当前版本：$currentVersion\n最新版本：${update.latestVersion}",
                    color = ReinColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (update.notes.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "更新内容",
                        color = ReinColors.TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = update.notes,
                        color = ReinColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(14.dp))
                when (download) {
                    is UpdateDownloadState.Downloading -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .background(ReinColors.Hairline)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(download.progress.coerceIn(0f, 1f))
                                    .height(5.dp)
                                    .background(ReinColors.Mint)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (download.totalBytes > 0) {
                                "正在下载 ${(download.progress * 100).toInt()}%"
                            } else {
                                "正在下载…"
                            },
                            color = ReinColors.TextTertiary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    is UpdateDownloadState.Ready -> Text(
                        text = "下载完成，点击“安装更新”。",
                        color = ReinColors.Mint,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    is UpdateDownloadState.Failed -> Text(
                        text = download.message,
                        color = ReinColors.Danger,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    UpdateDownloadState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            val downloading = download is UpdateDownloadState.Downloading
            TextButton(
                enabled = !downloading,
                onClick = when (download) {
                    is UpdateDownloadState.Ready -> onInstall
                    else -> onDownload
                },
                colors = ButtonDefaults.textButtonColors(contentColor = ReinColors.Mint),
            ) {
                Text(
                    when (download) {
                        is UpdateDownloadState.Ready -> "安装更新"
                        is UpdateDownloadState.Downloading -> "下载中…"
                        is UpdateDownloadState.Failed -> "重试下载"
                        UpdateDownloadState.Idle -> "立即更新"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onBrowse,
                colors = ButtonDefaults.textButtonColors(contentColor = ReinColors.TextSecondary),
            ) {
                Text("前往浏览器下载")
            }
        },
        containerColor = ReinColors.Surface,
        titleContentColor = ReinColors.TextPrimary,
        textContentColor = ReinColors.TextSecondary,
    )
}
