package com.wein.fasttrack.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wein.fasttrack.R
import com.wein.fasttrack.utils.AppUpdateManager
import com.wein.fasttrack.utils.GitHubReleaseInfo
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    currentVersion: String,
    release: GitHubReleaseInfo,
    onDismiss: () -> Unit,
    onSkipVersion: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var isDownloading by remember(release.versionName) { mutableStateOf(false) }
    var downloadProgress by remember(release.versionName) { mutableIntStateOf(-1) }
    var downloadedApk by remember(release.versionName) { mutableStateOf<File?>(null) }
    var errorMessage by remember(release.versionName) { mutableStateOf<String?>(null) }
    var canInstall by remember(release.versionName) {
        mutableStateOf(AppUpdateManager.canInstallPackages(context))
    }
    var needsPermissionPrompt by remember(release.versionName) { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstall = AppUpdateManager.canInstallPackages(context)
                if (canInstall && needsPermissionPrompt) {
                    needsPermissionPrompt = false
                    downloadedApk?.let { apk ->
                        AppUpdateManager.installApk(context, apk)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun startDownload() {
        val url = release.apkDownloadUrl ?: return
        val fileName = release.apkFileName ?: "fasttrack-${release.versionName}.apk"
        errorMessage = null
        isDownloading = true
        downloadProgress = -1
        downloadJob = scope.launch {
            val result = AppUpdateManager.downloadApk(
                context = context,
                downloadUrl = url,
                fileName = fileName,
                onProgress = { pct -> downloadProgress = pct }
            )
            isDownloading = false
            result.onSuccess { file ->
                downloadedApk = file
                canInstall = AppUpdateManager.canInstallPackages(context)
                if (canInstall) {
                    val launched = AppUpdateManager.installApk(context, file)
                    if (!launched) {
                        errorMessage = "Tidak dapat membuka pemasang APK sistem."
                    }
                } else {
                    needsPermissionPrompt = true
                }
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Gagal mengunduh berkas APK."
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        title = {
            Column {
                Text(
                    text = stringResource(R.string.update_available),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "v$currentVersion",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "→",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = release.tagName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (release.apkSizeBytes > 0L) {
                        Text(
                            text = formatFileSize(release.apkSizeBytes),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (release.releaseName.isNotBlank() && release.releaseName != release.tagName) {
                    Text(
                        text = release.releaseName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.release_notes),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = release.releaseNotes.ifBlank {
                            "Perbaikan bug dan peningkatan performa."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (downloadProgress in 0..100) {
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = stringResource(R.string.downloading_progress, downloadProgress),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = stringResource(R.string.downloading_update),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (needsPermissionPrompt || (!canInstall && downloadedApk != null)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.install_permission_needed),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = { AppUpdateManager.openInstallUnknownAppsSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.open_settings))
                        }
                    }
                }

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (release.apkDownloadUrl.isNullOrBlank()) {
                    Text(
                        text = "Berkas APK belum tersedia pada rilis ini. Anda dapat membuka halaman rilis di GitHub.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (release.htmlUrl.isNotBlank()) {
                    TextButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(stringResource(R.string.view_on_github))
                    }
                }
            }
        },
        confirmButton = {
            when {
                isDownloading -> {
                    TextButton(
                        onClick = {
                            downloadJob?.cancel()
                            isDownloading = false
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                downloadedApk != null && canInstall -> {
                    Button(
                        onClick = {
                            downloadedApk?.let { apk ->
                                if (!AppUpdateManager.installApk(context, apk)) {
                                    errorMessage = "Tidak dapat membuka pemasang APK sistem."
                                }
                            }
                        }
                    ) {
                        Text("Pasang Sekarang")
                    }
                }
                !release.apkDownloadUrl.isNullOrBlank() -> {
                    Button(
                        onClick = {
                            canInstall = AppUpdateManager.canInstallPackages(context)
                            if (!canInstall) {
                                needsPermissionPrompt = true
                            } else {
                                startDownload()
                            }
                        }
                    ) {
                        Text(
                            if (errorMessage != null) "Coba Lagi"
                            else stringResource(R.string.download_and_install)
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSkipVersion(release.versionName) }) {
                        Text("Lewati Versi Ini")
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.later))
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format(Locale("id", "ID"), "%.0f KB", kb)
    }
    val mb = kb / 1024.0
    return String.format(Locale("id", "ID"), "%.1f MB", mb)
}
