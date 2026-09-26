package com.karalo.feature.update.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.karalo.feature.update.domain.InstallResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/** The installed app's own version, and the hand-off of a downloaded one to Android's installer. */
interface AppPackage {
    val versionCode: Long
    val versionName: String

    fun install(apk: File): InstallResult
}

internal class AndroidAppPackage
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppPackage {
        private val packageInfo by lazy { context.packageManager.getPackageInfo(context.packageName, 0) }

        override val versionCode: Long
            get() =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

        override val versionName: String get() = packageInfo.versionName.orEmpty()

        /**
         * A sideloaded app can't install anything silently: this opens the system installer,
         * where the user confirms. The first time, Android wants "Install unknown apps" allowed
         * for Karalo, so its settings page opens instead. Installing ends this process, and the
         * installer's last screen offers "Open" to start the new version.
         *
         * ACTION_VIEW rather than a PackageInstaller session, for that "Open" button: Android TV's
         * installer only shows it for intent-started installs.
         */
        override fun install(apk: File): InstallResult {
            val mayInstall =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
            if (!mayInstall) {
                val permission =
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Some TVs don't have the per-app page; their general security settings have it.
                val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val opened = start(permission) || start(fallback)
                return if (opened) InstallResult.NEEDS_PERMISSION else InstallResult.FAILED
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            val intent =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            return if (start(intent)) InstallResult.STARTED else InstallResult.FAILED
        }

        private fun start(intent: Intent): Boolean =
            try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
    }
