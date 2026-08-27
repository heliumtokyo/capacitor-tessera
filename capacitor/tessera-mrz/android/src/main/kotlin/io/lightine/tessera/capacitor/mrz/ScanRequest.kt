package io.lightine.tessera.capacitor.mrz

import android.content.Intent

internal data class ScanRequest(
    val documentTypes: Set<String>,
    val allowPartialResults: Boolean,
    val timeoutMs: Long?,
    val torchEnabled: Boolean,
) {
    fun writeTo(intent: Intent) {
        intent.putExtra(EXTRA_DOCUMENT_TYPES, documentTypes.toTypedArray())
        intent.putExtra(EXTRA_ALLOW_PARTIAL_RESULTS, allowPartialResults)
        timeoutMs?.let { intent.putExtra(EXTRA_TIMEOUT_MS, it) }
        intent.putExtra(EXTRA_TORCH_ENABLED, torchEnabled)
    }

    companion object {
        private const val EXTRA_DOCUMENT_TYPES = "documentTypes"
        private const val EXTRA_ALLOW_PARTIAL_RESULTS = "allowPartialResults"
        private const val EXTRA_TIMEOUT_MS = "timeoutMs"
        private const val EXTRA_TORCH_ENABLED = "torchEnabled"

        fun from(intent: Intent): ScanRequest =
            ScanRequest(
                documentTypes = intent.getStringArrayExtra(EXTRA_DOCUMENT_TYPES)?.toSet().orEmpty(),
                allowPartialResults = intent.getBooleanExtra(EXTRA_ALLOW_PARTIAL_RESULTS, false),
                timeoutMs =
                    if (intent.hasExtra(EXTRA_TIMEOUT_MS)) {
                        intent.getLongExtra(EXTRA_TIMEOUT_MS, 0L)
                    } else {
                        null
                    },
                torchEnabled = intent.getBooleanExtra(EXTRA_TORCH_ENABLED, false),
            )
    }
}
