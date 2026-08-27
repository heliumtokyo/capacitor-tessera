package io.lightine.tessera.capacitor.mrz

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.lightine.tessera.mrz.camera.ui.DismissReason
import io.lightine.tessera.mrz.camera.ui.MrzScannerConfig
import io.lightine.tessera.mrz.camera.ui.MrzScannerResult
import io.lightine.tessera.mrz.camera.ui.MrzScannerScreen
import io.lightine.tessera.mrz.camera.ui.ReviewMode
import io.lightine.tessera.mrz.camera.ui.ScanMethod
import io.lightine.tessera.mrz.parsing.ParseResult
import org.json.JSONObject
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

class TesseraScannerActivity : ComponentActivity() {
    private lateinit var request: ScanRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = ScanRequest.from(intent)
        if (registerAndConsumeCancellation(this)) {
            finishCancelled()
            return
        }

        setContent {
            var sessionKey by remember { mutableIntStateOf(0) }
            key(sessionKey) {
                MrzScannerScreen(
                    config =
                        MrzScannerConfig {
                            enabledMethods = setOf(ScanMethod.CAMERA)
                            reviewMode = ReviewMode.INSTANT_RETURN
                            request.timeoutMs?.let { scanTimeout = it.milliseconds }
                            torchOnByDefault = request.torchEnabled
                        },
                    onResult = { result ->
                        when (result) {
                            is MrzScannerResult.Confirmed -> {
                                val parse = result.result.parse
                                if (shouldContinueScanning(parse)) {
                                    sessionKey += 1
                                } else {
                                    MrzResultMapper.map(parse)?.let(::finishWithResult)
                                        ?: finishWithError("MRZ_PARSE_FAILED")
                                }
                            }

                            is MrzScannerResult.Cancelled -> {
                                handleCancellation(result.reason)
                            }
                        }
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        unregister(this)
        super.onDestroy()
    }

    private fun shouldContinueScanning(parse: ParseResult): Boolean {
        if (parse is ParseResult.PartialSuccess && !request.allowPartialResults) return true
        if (request.documentTypes.isEmpty()) return false
        return MrzResultMapper.documentFamily(parse) !in request.documentTypes
    }

    private fun handleCancellation(reason: DismissReason) {
        when (reason) {
            DismissReason.USER_DISMISSED -> finishCancelled()
            DismissReason.TIMED_OUT -> finishWithError("SCAN_TIMEOUT")
            DismissReason.CAMERA_UNAVAILABLE -> finishWithError("CAMERA_UNAVAILABLE")
            DismissReason.PERMISSION_DENIED -> finishWithError("CAMERA_PERMISSION_DENIED")
        }
    }

    private fun finishWithResult(result: MappedScanResult) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT_JSON, result.toJson().toString()))
        finish()
    }

    private fun finishCancelled() {
        val json = JSONObject().put("status", "cancelled")
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT_JSON, json.toString()))
        finish()
    }

    private fun finishWithError(code: String) {
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR_CODE, code))
        finish()
    }

    companion object {
        const val EXTRA_RESULT_JSON = "resultJson"
        const val EXTRA_ERROR_CODE = "errorCode"

        private val cancellationLock = Any()
        private var activeActivity: WeakReference<TesseraScannerActivity> = WeakReference(null)
        private var pendingCancellation: Boolean = false

        fun cancelActiveOrNext(): Boolean {
            val activity =
                synchronized(cancellationLock) {
                    activeActivity.get().also {
                        if (it == null) pendingCancellation = true
                    }
                }
            if (activity == null) {
                return false
            }
            activity.runOnUiThread { activity.finishCancelled() }
            return true
        }

        fun clearPendingCancellation() {
            synchronized(cancellationLock) { pendingCancellation = false }
        }

        private fun registerAndConsumeCancellation(activity: TesseraScannerActivity): Boolean =
            synchronized(cancellationLock) {
                activeActivity = WeakReference(activity)
                val pending = pendingCancellation
                pendingCancellation = false
                pending
            }

        private fun unregister(activity: TesseraScannerActivity) {
            synchronized(cancellationLock) {
                if (activeActivity.get() === activity) {
                    activeActivity = WeakReference(null)
                }
            }
        }
    }
}
