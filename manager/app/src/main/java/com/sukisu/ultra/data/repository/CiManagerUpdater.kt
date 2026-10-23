package com.sukisu.ultra.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.sukisu.ultra.BuildConfig
import com.sukisu.ultra.R
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.module.LatestVersionInfo
import com.sukisu.ultra.ui.util.getRootShell
import com.sukisu.ultra.ui.util.withNewRootShell
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile

/** Downloads the APK asset from the public rolling CI prerelease and installs it. */
class CiManagerUpdater(private val context: Context) {
    fun updateAndInstall(update: LatestVersionInfo): Boolean {
        if (!update.downloadUrl.startsWith(RELEASE_ASSET_PREFIX)) {
            throw IOException(context.getString(R.string.ci_update_request_failed))
        }
        val abi = Build.SUPPORTED_ABIS.firstOrNull()?.takeIf { it in SUPPORTED_ABIS }
            ?: throw IOException(context.getString(R.string.ci_update_unsupported_arch))
        val apk = File.createTempFile("manager-ci-", ".apk", context.cacheDir)
        try {
            download(update.downloadUrl, apk)
            verifyApk(apk, abi, update.versionCode)
            if (getRootShell().isRoot) {
                val result = withNewRootShell {
                    if (!isRoot) throw IOException(context.getString(R.string.ci_update_install_failed))
                    // Stream via stdin: system_server cannot open an app-private cache path on SELinux devices.
                    newJob().add("cat ${apk.absolutePath.shellQuote()} | pm install -r -S ${apk.length()}").exec()
                }
                if (!result.isSuccess) {
                    throw IOException((result.err + result.out).joinToString("\n").ifBlank {
                        context.getString(R.string.ci_update_install_failed)
                    })
                }
                return true
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                    throw IOException(context.getString(R.string.ci_update_install_permission))
                }
                submitInstall(apk)
                return false
            }
        } finally {
            apk.delete()
        }
    }

    private fun download(url: String, destination: File) {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(context.getString(R.string.ci_update_http_error, response.code))
            }
            val body = response.body
            if (body.contentLength() > MAX_APK_BYTES) {
                throw IOException(context.getString(R.string.ci_update_archive_too_large))
            }
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) {
                            throw IOException(context.getString(R.string.ci_update_archive_too_large))
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        }
    }

    private fun verifyApk(apk: File, abi: String, expectedVersionCode: Long) {
        val candidate = archivePackageInfo(apk)
            ?: throw IOException(context.getString(R.string.ci_update_invalid_apk))
        if (candidate.packageName != BuildConfig.APPLICATION_ID ||
            candidate.longVersionCodeCompat() != expectedVersionCode ||
            abi !in apkAbis(apk)) {
            throw IOException(context.getString(R.string.ci_update_invalid_apk))
        }
        val installed = try {
            context.packageManager.getPackageInfo(context.packageName, packageInfoFlags())
        } catch (e: Exception) {
            throw IOException(context.getString(R.string.ci_update_invalid_apk), e)
        }
        if (candidate.longVersionCodeCompat() <= installed.longVersionCodeCompat()) {
            throw IOException(context.getString(R.string.ci_update_not_newer))
        }
        if (!sameSignature(candidate, installed)) {
            throw IOException(context.getString(R.string.ci_update_signature_mismatch))
        }
    }

    private fun packageInfoFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
    }

    private fun archivePackageInfo(apk: File): PackageInfo? =
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, packageInfoFlags())

    private fun apkAbis(apk: File): Set<String> = ZipFile(apk).use { zip ->
        val entries = zip.entries()
        buildSet {
            while (entries.hasMoreElements()) {
                val parts = entries.nextElement().name.split('/')
                if (parts.size >= 3 && parts[0] == "lib" && parts[1] in SUPPORTED_ABIS) add(parts[1])
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun sameSignature(candidate: PackageInfo, installed: PackageInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signers = candidate.signingInfo?.apkContentsSigners ?: return false
            return signers.isNotEmpty() && signers.all { signer ->
                context.packageManager.hasSigningCertificate(
                    context.packageName,
                    signer.toByteArray(),
                    PackageManager.CERT_INPUT_RAW_X509,
                )
            }
        }
        val candidateSignatures = candidate.signatures ?: return false
        val installedSignatures = installed.signatures ?: return false
        return candidateSignatures.isNotEmpty() && candidateSignatures.all { signature ->
            installedSignatures.any { it == signature }
        }
    }

    private fun submitInstall(apk: File) {
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
            }
        )
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("manager.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, CiPackageInstallReceiver::class.java)
                    .setAction(CiPackageInstallReceiver.ACTION_INSTALL_RESULT)
                val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_MUTABLE
                } else 0
                val pending = android.app.PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callback,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or mutable,
                )
                session.commit(pending.intentSender)
                committed = true
            }
        } finally {
            if (!committed) installer.abandonSession(sessionId)
        }
    }

    private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

    companion object {
        private const val RELEASE_ASSET_PREFIX =
            "https://github.com/SukiSU-Ultra/SukiSU-Ultra/releases/download/manager-ci/"
        private const val MAX_APK_BYTES = 512L * 1024 * 1024
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "riscv64")
    }
}
