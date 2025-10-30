package com.example.shieldx.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import java.util.Locale
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener

/**
 * DeepGuard v3.2 - Permission Utilities
 * -------------------------------------
 * ✅ Centralized permission handling for camera, storage, and notification listener
 * ✅ Handles Android 13+ scoped storage and notification permissions
 * ✅ Integrates with Dexter for graceful UX
 */
object PermissionUtils {

    // Permission request codes
    const val PERMISSION_REQUEST_CODE = 1001
    const val CAMERA_PERMISSION_CODE = 1002
    const val STORAGE_PERMISSION_CODE = 1003
    const val NOTIFICATION_PERMISSION_CODE = 1004

    // Storage permissions (adjusted for Android 13+)
    val STORAGE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    // Camera + Storage
    val CAMERA_PERMISSIONS = STORAGE_PERMISSIONS + Manifest.permission.CAMERA

    // Notification permissions (Android 13+)
    val NOTIFICATION_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf()
    }

    val ALL_PERMISSIONS = CAMERA_PERMISSIONS +
            Manifest.permission.ACCESS_NETWORK_STATE +
            Manifest.permission.INTERNET

    /**
     * Check if permission is granted
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Check if multiple permissions are granted
     */
    fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean =
        permissions.all { isPermissionGranted(context, it) }

    /**
     * Request single permission
     */
    fun requestPermission(activity: Activity, permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
    }

    /**
     * Request multiple permissions
     */
    fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    /**
     * Check & request storage permissions
     */
    fun checkStoragePermissions(activity: Activity, callback: PermissionCallback) {
        if (arePermissionsGranted(activity, STORAGE_PERMISSIONS)) {
            callback.onPermissionGranted()
        } else {
            requestPermissionsWithDexter(activity, STORAGE_PERMISSIONS.toList(), callback)
        }
    }

    /**
     * Check & request camera permissions
     */
    fun checkCameraPermissions(activity: Activity, callback: PermissionCallback) {
        if (arePermissionsGranted(activity, CAMERA_PERMISSIONS)) {
            callback.onPermissionGranted()
        } else {
            requestPermissionsWithDexter(activity, CAMERA_PERMISSIONS.toList(), callback)
        }
    }

    /**
     * Check if Notification Listener access is enabled
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }

    /**
     * Open settings for Notification Listener access
     */
    fun requestNotificationListenerPermission(context: Context) {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * Request permissions with Dexter for better UX
     */
    private fun requestPermissionsWithDexter(
        activity: Activity,
        permissions: List<String>,
        callback: PermissionCallback
    ) {
        Dexter.withActivity(activity)
            .withPermissions(permissions)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    report?.let {
                        if (it.areAllPermissionsGranted()) {
                            callback.onPermissionGranted()
                        } else {
                            val denied = it.deniedPermissionResponses.map { res -> res.permissionName }
                            callback.onPermissionDenied(denied)
                            Logger.w("PermissionUtils", "Denied: $denied")
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest()
                }
            }).check()
    }

    /**
     * Handle manual permission results
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        callback: PermissionCallback
    ) {
        when (requestCode) {
            STORAGE_PERMISSION_CODE,
            CAMERA_PERMISSION_CODE,
            NOTIFICATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    callback.onPermissionGranted()
                } else {
                    callback.onPermissionDenied(permissions.toList())
                    Logger.w("PermissionUtils", "Permissions denied: ${permissions.joinToString()}")
                }
            }
        }
    }

    /**
     * Show rationale dialog with explanation
     */
    fun showPermissionRationale(
        activity: Activity,
        title: String,
        message: String,
        permissions: Array<String>,
        requestCode: Int
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant") { _, _ ->
                requestPermissions(activity, permissions, requestCode)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /**
     * Callback interface for permission results
     */
    interface PermissionCallback {
        fun onPermissionGranted()
        fun onPermissionDenied(deniedPermissions: List<String>)
    }
}

/**
 * DeepGuard v3.2 - File Utilities
 * -------------------------------
 * File identification, validation, and formatting helpers
 */
object FileUtils {

    fun getFileExtension(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())

    fun isImageFile(fileName: String): Boolean =
        getFileExtension(fileName) in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

    fun isVideoFile(fileName: String): Boolean =
        getFileExtension(fileName) in listOf("mp4", "avi", "mov", "mkv", "wmv", "flv", "webm")

    fun isDocumentFile(fileName: String): Boolean =
        getFileExtension(fileName) in listOf("pdf", "doc", "docx", "txt", "rtf")

    fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return String.format("%.1f %s", size, units[unitIndex])
    }

    fun getMimeType(fileName: String): String = when (getFileExtension(fileName)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    fun getSafeFileName(uri: Uri?): String {
        return uri?.lastPathSegment?.substringAfterLast('/') ?: "unknown_file"
    }
}
