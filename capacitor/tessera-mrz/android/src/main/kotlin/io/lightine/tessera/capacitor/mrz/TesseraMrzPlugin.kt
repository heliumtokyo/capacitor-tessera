package io.lightine.tessera.capacitor.mrz

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.getcapacitor.annotation.PluginMethod

@CapacitorPlugin(
    name = "TesseraMrz",
    permissions = [Permission(strings = [Manifest.permission.CAMERA], alias = CAMERA_PERMISSION)],
)
class TesseraMrzPlugin : Plugin() {
    private var activeScanCall: PluginCall? = null
    private var cancelRequested: Boolean = false

    @PluginMethod
    fun scan(call: PluginCall) {
        if (activeScanCall != null) {
            call.reject("A scanner session is already active.", "SCANNER_ALREADY_ACTIVE")
            return
        }

        val request = parseRequest(call) ?: return
        TesseraScannerActivity.clearPendingCancellation()
        activeScanCall = call
        cancelRequested = false

        if (getPermissionState(CAMERA_PERMISSION) == PermissionState.GRANTED) {
            launchScanner(call, request)
        } else {
            requestPermissionForAlias(CAMERA_PERMISSION, call, "cameraPermissionResult")
        }
    }

    @PluginMethod
    fun cancelScan(call: PluginCall) {
        val scanCall = activeScanCall
        if (scanCall != null && !TesseraScannerActivity.cancelActiveOrNext()) {
            // Android's permission dialog belongs to the OS and cannot be dismissed by the plugin.
            // Resolve the pending scan as cancelled as soon as that dialog returns.
            cancelRequested = true
        }
        call.resolve()
    }

    @PluginMethod
    fun isSupported(call: PluginCall) {
        val cameraAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && cameraAvailable
        val result = JSObject().put("supported", supported)
        if (!supported) {
            result.put(
                "reason",
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    "unsupported-os-version"
                } else {
                    "camera-unavailable"
                },
            )
        }
        call.resolve(result)
    }

    @PluginMethod
    fun getPluginInfo(call: PluginCall) {
        call.resolve(
            JSObject()
                .put("name", "capacitor-tessera-mrz")
                .put("version", "0.1.0")
                .put("tesseraVersion", "0.5.0")
                .put("capacitorMajor", 8)
                .put("platform", "android"),
        )
    }

    @PermissionCallback
    private fun cameraPermissionResult(call: PluginCall) {
        if (activeScanCall !== call) return
        if (cancelRequested) {
            call.resolve(JSObject().put("status", "cancelled"))
            clearActiveScan()
            return
        }
        if (getPermissionState(CAMERA_PERMISSION) != PermissionState.GRANTED) {
            call.reject("Camera permission was denied.", "CAMERA_PERMISSION_DENIED")
            clearActiveScan()
            return
        }
        val request = parseRequest(call)
        if (request == null) {
            clearActiveScan()
            return
        }
        launchScanner(call, request)
    }

    @ActivityCallback
    private fun scannerResult(
        call: PluginCall?,
        result: ActivityResult,
    ) {
        val scanCall = call ?: activeScanCall
        clearActiveScan()
        if (scanCall == null) return

        val data = result.data
        if (result.resultCode == Activity.RESULT_OK) {
            val json = data?.getStringExtra(TesseraScannerActivity.EXTRA_RESULT_JSON)
            if (json == null) {
                scanCall.reject("The scanner returned no result.", "INTERNAL_ERROR")
            } else {
                runCatching { JSObject(json) }
                    .onSuccess(scanCall::resolve)
                    .onFailure { scanCall.reject("The scanner returned an invalid result.", "INTERNAL_ERROR") }
            }
            return
        }

        val code = data?.getStringExtra(TesseraScannerActivity.EXTRA_ERROR_CODE) ?: "INTERNAL_ERROR"
        scanCall.reject(errorMessage(code), code)
    }

    private fun launchScanner(
        call: PluginCall,
        request: ScanRequest,
    ) {
        val intent = Intent(context, TesseraScannerActivity::class.java)
        request.writeTo(intent)
        startActivityForResult(call, intent, "scannerResult")
    }

    private fun parseRequest(call: PluginCall): ScanRequest? {
        val documentTypesArray = call.getArray("documentTypes")
        val documentTypes =
            try {
                val rawValues = documentTypesArray?.toList<Any?>().orEmpty()
                if (rawValues.any { it !is String }) {
                    throw IllegalArgumentException("documentTypes contains a non-string value")
                }
                rawValues.map { it as String }.toSet()
            } catch (_: Exception) {
                call.reject("documentTypes must be an array of strings.", "UNSUPPORTED_OPTION")
                return null
            }
        if (documentTypesArray != null && documentTypes.isEmpty()) {
            call.reject("documentTypes must not be empty.", "UNSUPPORTED_OPTION")
            return null
        }
        if (!documentTypes.all { it in SUPPORTED_DOCUMENT_TYPES }) {
            call.reject("documentTypes contains an unsupported value.", "UNSUPPORTED_OPTION")
            return null
        }

        val camera = call.getString("camera", "back")
        if (camera != "back") {
            call.reject("Only the back camera is supported by Tessera 0.5.0.", "UNSUPPORTED_OPTION")
            return null
        }

        val timeoutMs = call.getLong("timeoutMs")
        if (timeoutMs != null && timeoutMs <= 0L) {
            call.reject("timeoutMs must be greater than zero.", "UNSUPPORTED_OPTION")
            return null
        }

        return ScanRequest(
            documentTypes = documentTypes,
            allowPartialResults = call.getBoolean("allowPartialResults", false) ?: false,
            timeoutMs = timeoutMs,
            torchEnabled = call.getBoolean("torchEnabled", false) ?: false,
        )
    }

    private fun clearActiveScan() {
        activeScanCall = null
        cancelRequested = false
    }

    private fun errorMessage(code: String): String =
        when (code) {
            "SCAN_TIMEOUT" -> "The scanner session timed out."
            "CAMERA_UNAVAILABLE" -> "The camera is unavailable."
            "CAMERA_PERMISSION_DENIED" -> "Camera permission was denied."
            else -> "The scanner could not complete."
        }

    private companion object {
        const val CAMERA_PERMISSION = "camera"
        val SUPPORTED_DOCUMENT_TYPES = setOf("passport", "id-card", "visa")
    }
}
